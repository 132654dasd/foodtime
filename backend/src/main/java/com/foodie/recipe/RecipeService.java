package com.foodie.recipe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.common.BizException;
import com.foodie.common.PageResult;
import com.foodie.config.CacheConfig;
import com.foodie.recipe.dto.RecipeCardVO;
import com.foodie.recipe.dto.RecipeDetailVO;
import com.foodie.recipe.dto.RecipeMetaVO;
import com.foodie.recipe.dto.RecipeQuery;
import com.foodie.recipe.dto.RecipeSaveRequest;

@Service
public class RecipeService {

    private static final int MAX_PAGE_SIZE = 60;
    private static final int DEFAULT_PAGE_SIZE = 12;

    /** 忌口多选建议：过敏原与常见忌口，前端直接展示成标签按钮。 */
    private static final List<String> AVOID_SUGGESTIONS = List.of(
            "香菜", "花生", "坚果", "乳制品", "麸质", "蛋类",
            "海鲜", "水产", "牛肉", "猪肉", "鸡肉", "辛辣", "豆制品");

    private final RecipeRepository recipeRepository;
    private final RecipeTagRepository tagRepository;
    private final UserFavoriteRepository favoriteRepository;
    private final RecipeRatingRepository ratingRepository;
    private final ViewCountBuffer viewCountBuffer;

    public RecipeService(RecipeRepository recipeRepository,
                         RecipeTagRepository tagRepository,
                         UserFavoriteRepository favoriteRepository,
                         RecipeRatingRepository ratingRepository,
                         ViewCountBuffer viewCountBuffer) {
        this.recipeRepository = recipeRepository;
        this.tagRepository = tagRepository;
        this.favoriteRepository = favoriteRepository;
        this.ratingRepository = ratingRepository;
        this.viewCountBuffer = viewCountBuffer;
    }

    // ============================================================ 查询

    /**
     * 菜谱列表检索。
     *
     * <p>两条路径：没有关键词走 JPQL（条件都走得上索引）；
     * 有关键词走 MySQL ngram 全文索引的 native 查询。
     */
    @Transactional(readOnly = true)
    public PageResult<RecipeCardVO> browse(RecipeQuery query, int page, int size, Long userId) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), clampSize(size), sortOf(query.sort()));
        String keyword = query.safeKeyword();

        Page<Recipe> result;
        if (keyword == null) {
            result = recipeRepository.browse(
                    blankToNull(query.cuisine()), query.category(), query.difficulty(),
                    query.maxMinutes(), pageable);
        } else {
            // 相关度排序写在 SQL 里，这里必须传 unsorted 的 Pageable，
            // 否则 Spring Data 会再追加一个 ORDER BY 把它盖掉
            Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
            result = recipeRepository.searchByFulltext(
                    keyword,
                    blankToNull(query.cuisine()),
                    query.category() == null ? null : query.category().label(),
                    query.difficulty() == null ? null : query.difficulty().name(),
                    query.maxMinutes(),
                    unsorted);
        }

        Set<Long> favorited = favoritedIds(userId, result.getContent().stream().map(Recipe::getId).toList());
        return PageResult.of(result, recipe -> RecipeCardVO.of(recipe, favorited));
    }

    /**
     * 菜谱详情。
     *
     * <p>缓存的 key 只有 id——因为放进去的 {@link RecipeDetailVO} 里 {@code favorited}
     * 恒为 false，与用户无关；真实收藏态由 {@link #detailFor} 取出后再派生。
     * 这是「不要把跟人走的数据放进共享缓存」这条规则的具体落地。
     */
    @Cacheable(value = CacheConfig.RECIPE_DETAIL, key = "#id", sync = true)
    @Transactional(readOnly = true)
    public RecipeDetailVO detail(Long id) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("菜谱不存在或已下架"));
        return RecipeDetailVO.of(recipe);
    }

    /** 供接口使用：详情 + 当前用户收藏态，并顺带记一次浏览。 */
    @Transactional(readOnly = true)
    public RecipeDetailVO detailFor(Long id, Long userId) {
        RecipeDetailVO detail = detail(id);
        viewCountBuffer.increment(id);
        if (userId == null) {
            return detail;
        }
        return detail.withFavorited(favoriteRepository.existsByUserIdAndRecipeId(userId, id));
    }

    @Cacheable(value = CacheConfig.HOT_RECIPES, key = "#limit", sync = true)
    @Transactional(readOnly = true)
    public List<RecipeCardVO> hot(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        Pageable pageable = PageRequest.of(0, safeLimit,
                Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("favoriteCount")));
        return recipeRepository.browse(null, null, null, null, pageable)
                .getContent().stream().map(RecipeCardVO::of).toList();
    }

    /** 筛选栏所需的字典，几乎不变，缓存 1 小时。 */
    @Cacheable(value = CacheConfig.RECIPE_META, sync = true)
    @Transactional(readOnly = true)
    public RecipeMetaVO meta() {
        List<RecipeMetaVO.Option> categories = recipeRepository.countGroupByCategory().stream()
                .map(row -> {
                    DishCategory category = (DishCategory) row[0];
                    return new RecipeMetaVO.Option(category.label(), category.label(), (Long) row[1]);
                })
                .toList();

        List<RecipeMetaVO.Option> difficulties = recipeRepository.countGroupByDifficulty().stream()
                .map(row -> {
                    Difficulty difficulty = (Difficulty) row[0];
                    return new RecipeMetaVO.Option(difficulty.name(), difficulty.label(), (Long) row[1]);
                })
                .sorted((a, b) -> a.value().compareTo(b.value()))
                .toList();

        List<String> tags = tagRepository.findAll().stream()
                .map(RecipeTag::getName).sorted().toList();

        return new RecipeMetaVO(
                recipeRepository.findDistinctCuisines(),
                categories,
                difficulties,
                tags,
                AVOID_SUGGESTIONS.stream().map(name -> new RecipeMetaVO.Option(name, name, 0)).toList());
    }

    // ============================================================ 收藏

    @Transactional
    public boolean addFavorite(Long userId, Long recipeId) {
        requireRecipe(recipeId);
        if (favoriteRepository.existsByUserIdAndRecipeId(userId, recipeId)) {
            return true;
        }
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setRecipeId(recipeId);
        try {
            favoriteRepository.saveAndFlush(favorite);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // 并发双击：唯一键挡住了第二条，这里视作「已收藏」，结果依然是幂等的
            return true;
        }
        recipeRepository.addFavoriteCount(recipeId, 1);
        return true;
    }

    @Transactional
    public boolean removeFavorite(Long userId, Long recipeId) {
        long removed = favoriteRepository.deleteByUserIdAndRecipeId(userId, recipeId);
        if (removed > 0) {
            recipeRepository.addFavoriteCount(recipeId, -1);
        }
        return false;
    }

    @Transactional(readOnly = true)
    public PageResult<RecipeCardVO> listFavorites(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), clampSize(size));
        Page<UserFavorite> favorites = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        List<Long> recipeIds = favorites.getContent().stream().map(UserFavorite::getRecipeId).toList();
        if (recipeIds.isEmpty()) {
            return new PageResult<>(List.of(), favorites.getTotalElements(),
                    favorites.getNumber() + 1, favorites.getSize(), favorites.getTotalPages());
        }
        // 收藏顺序是用户自己的排序，按 id 批量取回后要重新按收藏顺序排列
        Map<Long, Recipe> byId = new HashMap<>();
        recipeRepository.findByIdIn(recipeIds).forEach(recipe -> byId.put(recipe.getId(), recipe));
        Set<Long> favorited = new HashSet<>(recipeIds);
        List<RecipeCardVO> cards = recipeIds.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(recipe -> RecipeCardVO.of(recipe, favorited))
                .toList();
        return new PageResult<>(cards, favorites.getTotalElements(),
                favorites.getNumber() + 1, favorites.getSize(), favorites.getTotalPages());
    }

    // ============================================================ 评分

    @Transactional
    public RecipeDetailVO rate(Long userId, Long recipeId, int score) {
        if (score < 1 || score > 5) {
            throw BizException.badRequest("评分只能是 1 到 5 星");
        }
        requireRecipe(recipeId);
        RecipeRating rating = ratingRepository.findByUserIdAndRecipeId(userId, recipeId)
                .orElseGet(() -> {
                    RecipeRating created = new RecipeRating();
                    created.setUserId(userId);
                    created.setRecipeId(recipeId);
                    return created;
                });
        rating.setScore(score);
        ratingRepository.saveAndFlush(rating);

        // 重算冗余的评分统计，让列表页不必做聚合子查询
        long count = ratingRepository.countByRecipeId(recipeId);
        Double average = ratingRepository.averageScoreByRecipeId(recipeId);
        BigDecimal avg = average == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);
        recipeRepository.updateRatingStats(recipeId, (int) count, avg);

        return detailFor(recipeId, userId);
    }

    // ============================================================ 管理员写操作

    @Transactional
    @CacheEvict(value = {CacheConfig.RECIPE_DETAIL, CacheConfig.RECIPE_META,
            CacheConfig.HOT_RECIPES, CacheConfig.AVOID_BLOCKED}, allEntries = true)
    public RecipeDetailVO create(RecipeSaveRequest request, Long operatorId) {
        Recipe recipe = new Recipe();
        recipe.setCreatedBy(operatorId);
        apply(recipe, request);
        return RecipeDetailVO.of(recipeRepository.save(recipe));
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.RECIPE_DETAIL, CacheConfig.RECIPE_META,
            CacheConfig.HOT_RECIPES, CacheConfig.AVOID_BLOCKED}, allEntries = true)
    public RecipeDetailVO update(Long id, RecipeSaveRequest request) {
        Recipe recipe = requireRecipe(id);
        // 集合是 orphanRemoval，先清空再重建，避免旧步骤/食材残留。
        recipe.getSteps().clear();
        recipe.getIngredients().clear();
        recipe.getTags().clear();
        // 这里必须 flush 一次，是个真实的坑：
        // Hibernate 在同一次 flush 里的动作顺序是「先 INSERT，后 DELETE」，
        // 而 recipe_step 上有唯一键 uk_step_recipe_no(recipe_id, step_no)。
        // 清空后立刻重建 step_no 从 1 开始的步骤时，新行会在旧行被删除之前插入，
        // 必然撞唯一键 —— 表现就是 PUT /api/recipes/{id} 恒返回 500。
        // 先把删除刷到数据库，再插入新行，冲突自然消失。
        recipeRepository.flush();
        apply(recipe, request);
        return RecipeDetailVO.of(recipeRepository.save(recipe));
    }

    @Transactional
    @CacheEvict(value = {CacheConfig.RECIPE_DETAIL, CacheConfig.RECIPE_META,
            CacheConfig.HOT_RECIPES, CacheConfig.AVOID_BLOCKED}, allEntries = true)
    public void delete(Long id) {
        Recipe recipe = requireRecipe(id);
        recipeRepository.delete(recipe);
    }

    // ============================================================ 内部

    private void apply(Recipe recipe, RecipeSaveRequest request) {
        recipe.setTitle(request.title().trim());
        recipe.setCoverUrl(blankToNull(request.coverUrl()));
        recipe.setDescription(blankToNull(request.description()));
        recipe.setCuisine(request.cuisine().trim());
        recipe.setCategory(parseCategory(request.category()));
        recipe.setDifficulty(parseDifficulty(request.difficulty()));
        recipe.setCookMinutes(request.cookMinutes() == null ? 30 : request.cookMinutes());
        recipe.setServings(request.servings() == null ? 2 : request.servings());
        recipe.setCalories(request.calories());

        int stepNo = 1;
        for (RecipeSaveRequest.Step step : request.steps()) {
            RecipeStep entity = new RecipeStep();
            entity.setStepNo(stepNo++);
            entity.setContent(step.content().trim());
            entity.setImageUrl(blankToNull(step.imageUrl()));
            recipe.addStep(entity);
        }

        int sortNo = 1;
        for (RecipeSaveRequest.Ingredient ingredient : request.ingredients()) {
            RecipeIngredient entity = new RecipeIngredient();
            entity.setName(ingredient.name().trim());
            entity.setAmount(blankToNull(ingredient.amount()));
            entity.setUnit(blankToNull(ingredient.unit()));
            entity.setIsMain(Boolean.FALSE.equals(ingredient.main()) ? 0 : 1);
            entity.setSortNo(sortNo++);
            recipe.addIngredient(entity);
        }

        if (request.tags() != null) {
            Map<String, RecipeTag> resolved = new LinkedHashMap<>();
            for (String name : request.tags()) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                String trimmed = name.trim();
                // 标签必须已在字典里，避免管理员随手输入产生一堆孤儿标签
                RecipeTag tag = tagRepository.findByName(trimmed)
                        .orElseThrow(() -> BizException.badRequest("未知标签：" + trimmed));
                resolved.put(trimmed, tag);
            }
            recipe.getTags().addAll(resolved.values());
        }
    }

    private DishCategory parseCategory(String raw) {
        try {
            return DishCategory.fromLabel(raw);
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest("分类只能是：" + java.util.Arrays.stream(DishCategory.values())
                    .map(DishCategory::label).collect(java.util.stream.Collectors.joining("、")));
        }
    }

    private Difficulty parseDifficulty(String raw) {
        try {
            return Difficulty.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest("难度只能是 EASY / MEDIUM / HARD");
        }
    }

    private Recipe requireRecipe(Long id) {
        return recipeRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("菜谱不存在或已下架"));
    }

    private Set<Long> favoritedIds(Long userId, List<Long> recipeIds) {
        if (userId == null || recipeIds.isEmpty()) {
            return Set.of();
        }
        return favoriteRepository.findByUserIdAndRecipeIdIn(userId, recipeIds).stream()
                .map(UserFavorite::getRecipeId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private Sort sortOf(String sort) {
        String key = sort == null ? "latest" : sort;
        return switch (key) {
            case "hot" -> Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("favoriteCount"));
            case "difficulty_asc" -> Sort.by(Sort.Order.asc("difficultyLevel"), Sort.Order.desc("id"));
            case "difficulty_desc" -> Sort.by(Sort.Order.desc("difficultyLevel"), Sort.Order.desc("id"));
            case "cook_asc" -> Sort.by(Sort.Order.asc("cookMinutes"), Sort.Order.desc("id"));
            case "rating" -> Sort.by(Sort.Order.desc("ratingAvg"), Sort.Order.desc("ratingCount"));
            default -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
