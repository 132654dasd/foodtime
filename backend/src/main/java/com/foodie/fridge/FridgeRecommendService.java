package com.foodie.fridge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.fridge.dto.FridgeSuggestionVO;
import com.foodie.fridge.dto.MatchedIngredientVO;
import com.foodie.fridge.dto.RecipeMatchVO;
import com.foodie.menu.AvoidanceFilterService;
import com.foodie.price.PriceCatalogService;
import com.foodie.price.PriceSnapshot;
import com.foodie.recipe.IngredientRow;
import com.foodie.recipe.Recipe;
import com.foodie.recipe.RecipeIngredientRepository;
import com.foodie.recipe.RecipeRepository;
import com.foodie.recipe.dto.RecipeCardVO;
import com.foodie.user.UserAvoidance;
import com.foodie.user.UserAvoidanceRepository;

/**
 * 按冰箱推荐菜谱。
 *
 * <p>整个请求只做三件查库的事：拉冰箱、拉全部食材行、拉命中菜谱的卡片。
 * 匹配与排序全部在内存里完成（见 {@link RecipeMatcher}）。
 *
 * <p><b>为什么不做缓存</b>：结果同时依赖「冰箱内容」与「用户忌口」，
 * 两个输入都随用户操作变化，缓存命中率低而失效点分散在三个写接口里；
 * 而查询本身只有 2~3 次索引扫描。用一致性换这点性能不划算。
 *
 * <p>忌口直接复用 {@link AvoidanceFilterService}（自带按关键词的 Caffeine 缓存），
 * 不新写任何过滤逻辑——同一个用户在配餐页和冰箱页看到的忌口效果必须一致。
 */
@Service
public class FridgeRecommendService {

    private static final Logger log = LoggerFactory.getLogger(FridgeRecommendService.class);

    private static final int DEFAULT_LIMIT = 12;
    private static final int MAX_LIMIT = 30;

    /** 提示文案里最多列几个名字，避免一条警告刷满屏幕。 */
    private static final int NAMES_IN_WARNING = 5;

    private final FridgeItemRepository fridgeItemRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final RecipeRepository recipeRepository;
    private final PriceCatalogService priceCatalog;
    private final UserAvoidanceRepository avoidanceRepository;
    private final AvoidanceFilterService avoidanceFilterService;

    public FridgeRecommendService(FridgeItemRepository fridgeItemRepository,
                                  RecipeIngredientRepository ingredientRepository,
                                  RecipeRepository recipeRepository,
                                  PriceCatalogService priceCatalog,
                                  UserAvoidanceRepository avoidanceRepository,
                                  AvoidanceFilterService avoidanceFilterService) {
        this.fridgeItemRepository = fridgeItemRepository;
        this.ingredientRepository = ingredientRepository;
        this.recipeRepository = recipeRepository;
        this.priceCatalog = priceCatalog;
        this.avoidanceRepository = avoidanceRepository;
        this.avoidanceFilterService = avoidanceFilterService;
    }

    /**
     * @param limit        返回多少道菜，默认 12、上限 30
     * @param onlyCookable 只看「主料齐全、现在就能做」的
     */
    @Transactional(readOnly = true)
    public FridgeSuggestionVO suggest(Long userId, int limit, boolean onlyCookable) {
        int size = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        List<FridgeItem> items = fridgeItemRepository.findByUserIdOrderByNameAsc(userId);
        if (items.isEmpty()) {
            return new FridgeSuggestionVO(List.of(), 0, 0, 0, 0, List.of(),
                    List.of("冰箱还是空的，先加几样食材吧"));
        }

        LocalDate today = LocalDate.now();
        List<IngredientRow> rows = ingredientRepository.findAllRows();
        Map<String, PriceSnapshot> prices = priceCatalog.enabledByName();

        List<RecipeMatcher.RecipeMatch> allMatches = RecipeMatcher.match(
                rows, items, name -> gramsPerUnit(prices, name), today);

        // ---- 忌口：与随机配餐共用同一套关键词缓存
        Set<Long> blocked = avoidBlocked(userId);
        List<RecipeMatcher.RecipeMatch> allowed = new ArrayList<>(allMatches.size());
        for (RecipeMatcher.RecipeMatch match : allMatches) {
            if (!blocked.contains(match.recipeId())) {
                allowed.add(match);
            }
        }
        int excludedByAvoidance = allMatches.size() - allowed.size();

        // ---- 卡片与排序度量：候选池最多三十来道，一次批量取回
        List<Long> ids = allowed.stream().map(RecipeMatcher.RecipeMatch::recipeId).toList();
        Map<Long, RecipeCardVO> cards = new HashMap<>();
        Map<Long, RecipeMetric> metrics = new HashMap<>();
        for (Recipe recipe : recipeRepository.findByIdIn(ids)) {
            cards.put(recipe.getId(), RecipeCardVO.of(recipe));
            metrics.put(recipe.getId(), new RecipeMetric(recipe.getRatingAvg(), recipe.getViewCount()));
        }

        List<RecipeMatcher.RecipeMatch> ordered = allowed.stream()
                .sorted(RecipeMatcher.ranking(metrics))
                .toList();

        int cookableCount = (int) ordered.stream().filter(RecipeMatcher.RecipeMatch::cookable).count();
        List<RecipeMatchVO> shown = new ArrayList<>();
        for (RecipeMatcher.RecipeMatch match : ordered) {
            if (onlyCookable && !match.cookable()) {
                continue;
            }
            RecipeCardVO card = cards.get(match.recipeId());
            if (card == null) {
                // 菜谱在匹配与取卡片之间被删除：跳过而不是给一张空卡片
                continue;
            }
            shown.add(toVO(match, card));
            if (shown.size() >= size) {
                break;
            }
        }

        List<String> ingredientNames = rows.stream().map(IngredientRow::name).distinct().toList();
        List<String> unused = FridgeService.unusedNames(items, ingredientNames);
        List<String> warnings = buildWarnings(items, today, allMatches, allowed, unused, onlyCookable, shown);

        log.debug("冰箱推荐 userId={} 冰箱={} 候选={} 可做={} 忌口排除={} 返回={}",
                userId, items.size(), allowed.size(), cookableCount, excludedByAvoidance, shown.size());

        return new FridgeSuggestionVO(shown, items.size(), cookableCount, allowed.size(),
                excludedByAvoidance, unused, warnings);
    }

    // ============================================================ 内部

    private static BigDecimal gramsPerUnit(Map<String, PriceSnapshot> prices, String ingredientName) {
        PriceSnapshot snapshot = prices.get(ingredientName);
        return snapshot == null ? null : snapshot.gramsPerUnit();
    }

    private RecipeMatchVO toVO(RecipeMatcher.RecipeMatch match, RecipeCardVO card) {
        List<MatchedIngredientVO> matched = match.matched().stream()
                .map(item -> new MatchedIngredientVO(
                        item.fridgeName(),
                        item.ingredientName(),
                        item.approximate(),
                        item.stock().name()))
                .toList();
        return new RecipeMatchVO(
                card,
                match.cookable(),
                match.mainTotal(),
                match.mainMatched(),
                match.matchedCount(),
                match.missingMain(),
                match.missingAux(),
                matched,
                match.expiringUsed());
    }

    private Set<Long> avoidBlocked(Long userId) {
        List<String> keywords = avoidanceRepository.findByUserId(userId).stream()
                .map(UserAvoidance::getKeyword)
                .map(AvoidanceFilterService::sanitize)
                .filter(Objects::nonNull)
                .toList();
        if (keywords.isEmpty()) {
            return Set.of();
        }
        // 每个关键词单独走缓存，循环放在这里（跨 bean 调用才走得到缓存代理）
        return avoidanceFilterService.unionOf(
                keywords.stream().map(avoidanceFilterService::blockedBy).toList());
    }

    private List<String> buildWarnings(List<FridgeItem> items,
                                       LocalDate today,
                                       List<RecipeMatcher.RecipeMatch> allMatches,
                                       List<RecipeMatcher.RecipeMatch> allowed,
                                       List<String> unused,
                                       boolean onlyCookable,
                                       List<RecipeMatchVO> shown) {
        List<String> warnings = new ArrayList<>();

        if (allMatches.isEmpty()) {
            warnings.add("这几样食材还凑不出一道菜的主料，试着再加一两样（鸡蛋、番茄、土豆都很好用）");
        } else if (allowed.isEmpty()) {
            warnings.add("按你保存的忌口排除了全部候选，去「今天吃什么」页调整忌口后再试");
        }
        if (onlyCookable && shown.isEmpty() && !allowed.isEmpty()) {
            warnings.add("现在还没有主料齐全的菜，关掉「只看现在就能做的」看看还差什么");
        }

        long expired = items.stream()
                .filter(item -> item.getExpireAt() != null && item.getExpireAt().isBefore(today))
                .count();
        if (expired > 0) {
            warnings.add("有 " + expired + " 样食材已经过期，建议先处理掉");
        }

        if (!unused.isEmpty()) {
            warnings.add("这 " + unused.size() + " 样食材暂时没有菜谱用到：" + joinLimited(unused));
        }
        int excluded = allMatches.size() - allowed.size();
        if (excluded > 0) {
            warnings.add("已按你保存的忌口排除 " + excluded + " 道相关菜谱");
        }
        return warnings;
    }

    /** 最多列 5 个名字，其余用「等 N 样」概括。 */
    private static String joinLimited(List<String> names) {
        Set<String> unique = new LinkedHashSet<>(names);
        if (unique.size() <= NAMES_IN_WARNING) {
            return String.join("、", unique);
        }
        String head = unique.stream().limit(NAMES_IN_WARNING).reduce((a, b) -> a + "、" + b).orElse("");
        return head + " 等 " + unique.size() + " 样";
    }
}
