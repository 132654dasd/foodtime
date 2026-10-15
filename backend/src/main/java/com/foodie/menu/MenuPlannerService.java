package com.foodie.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.foodie.common.BizException;
import com.foodie.menu.dto.MenuRequest;
import com.foodie.menu.dto.MenuResponse;
import com.foodie.recipe.DishCategory;
import com.foodie.recipe.Recipe;
import com.foodie.recipe.RecipeCandidate;
import com.foodie.recipe.RecipeRepository;
import com.foodie.recipe.dto.RecipeCardVO;
import com.foodie.user.UserAvoidance;
import com.foodie.user.UserAvoidanceRepository;

@Service
public class MenuPlannerService {

    private static final Logger log = LoggerFactory.getLogger(MenuPlannerService.class);

    private static final List<DishCategory> DISH_CATEGORIES = List.of(
            DishCategory.MEAT, DishCategory.VEGETABLE, DishCategory.COLD,
            DishCategory.STAPLE, DishCategory.DESSERT);

    /** 历史保留天数。 */
    private static final int HISTORY_RETENTION_DAYS = 30;

    private final RecipeRepository recipeRepository;
    private final UserAvoidanceRepository avoidanceRepository;
    private final MenuHistoryRepository historyRepository;
    private final MenuStrategyRegistry strategyRegistry;
    private final AvoidanceFilterService avoidanceFilterService;
    private final ObjectMapper objectMapper;

    public MenuPlannerService(RecipeRepository recipeRepository,
                              UserAvoidanceRepository avoidanceRepository,
                              MenuHistoryRepository historyRepository,
                              MenuStrategyRegistry strategyRegistry,
                              AvoidanceFilterService avoidanceFilterService,
                              ObjectMapper objectMapper) {
        this.recipeRepository = recipeRepository;
        this.avoidanceRepository = avoidanceRepository;
        this.historyRepository = historyRepository;
        this.strategyRegistry = strategyRegistry;
        this.avoidanceFilterService = avoidanceFilterService;
        this.objectMapper = objectMapper;
    }

    /**
     * 按规格与忌口配一桌菜。
     *
     * <p>流程：规格校验 → 合并忌口 → 拉候选池 → 剔除忌口 → 去重近期 → 策略抽样 → 汇总 → 记历史。
     * 全程只有 3~4 次查询（候选池 1 次 + 命中详情 1 次 + 历史 1 次，忌口命中走缓存）。
     */
    @Transactional
    public MenuResponse plan(Long userId, MenuRequest request) {
        if (request.dishCount() + request.soupCount() == 0) {
            throw BizException.badRequest("请至少选择一道菜或一道汤");
        }
        List<String> keywords = collectAvoidKeywords(userId, request.avoid());

        // 忌口并集：每个关键词单独走缓存，循环放在这里（跨 bean 调用才走得到缓存代理）
        List<Set<Long>> blockedSets = keywords.stream()
                .map(avoidanceFilterService::blockedBy)
                .toList();
        Set<Long> blocked = avoidanceFilterService.unionOf(blockedSets);

        // 一次拉全量候选（只 6 个标量列），忌口剔除与抽样都在内存里做
        List<RecipeCandidate> allCandidates =
                recipeRepository.findCandidates(List.of(DishCategory.values()));

        long excluded = allCandidates.stream().filter(candidate -> blocked.contains(candidate.id())).count();

        List<RecipeCandidate> dishPool = allCandidates.stream()
                .filter(candidate -> !candidate.category().isSoup())
                .filter(candidate -> !blocked.contains(candidate.id()))
                .toList();
        List<RecipeCandidate> soupPool = allCandidates.stream()
                .filter(candidate -> candidate.category().isSoup())
                .filter(candidate -> !blocked.contains(candidate.id()))
                .toList();

        List<String> warnings = new ArrayList<>();
        if (!keywords.isEmpty() && excluded > 0) {
            warnings.add("已按忌口（%s）排除 %d 道菜".formatted(String.join("、", keywords), excluded));
        }

        if (request.excludeRecent()) {
            Set<Long> recent = recentRecipeIds(userId);
            if (!recent.isEmpty()) {
                dishPool = avoidRecent(dishPool, recent);
                soupPool = avoidRecent(soupPool, recent);
            }
        }

        if (dishPool.isEmpty() && request.dishCount() > 0) {
            throw BizException.badRequest("按当前忌口条件已经没有可选的菜了，试着放宽一些");
        }
        if (soupPool.isEmpty() && request.soupCount() > 0) {
            throw BizException.badRequest("按当前忌口条件已经没有可选的汤了，试着放宽一些");
        }

        MenuStrategy strategy = strategyRegistry.get(request.strategy());
        MenuStrategy.Selection selection =
                strategy.pick(dishPool, soupPool, request.dishCount(), request.soupCount());
        warnings.addAll(selection.warnings());

        List<Long> orderedIds = new ArrayList<>(selection.dishIds());
        orderedIds.addAll(selection.soupIds());
        Map<Long, RecipeCardVO> cards = loadCards(orderedIds);

        List<RecipeCardVO> dishes = selection.dishIds().stream().map(cards::get).filter(Objects::nonNull).toList();
        List<RecipeCardVO> soups = selection.soupIds().stream().map(cards::get).filter(Objects::nonNull).toList();

        MenuResponse.Summary summary = summarize(dishes, soups, keywords);
        saveHistory(userId, request, orderedIds);

        log.debug("配餐完成 userId={} 菜={} 汤={} 忌口={} 警告={}",
                userId, dishes.size(), soups.size(), keywords, warnings.size());
        return new MenuResponse(dishes, soups, summary, warnings, (int) excluded);
    }

    /** 对已有菜单「换一道」：排除当前所有 id，重抽指定数量的同类菜。 */
    @Transactional(readOnly = true)
    public List<RecipeCardVO> reroll(Long userId, MenuRequest request, List<Long> excludeIds) {
        List<String> keywords = collectAvoidKeywords(userId, request.avoid());
        Set<Long> blocked = avoidanceFilterService.unionOf(
                keywords.stream().map(avoidanceFilterService::blockedBy).toList());

        Set<Long> excluded = new LinkedHashSet<>(excludeIds == null ? List.of() : excludeIds);
        boolean wantSoup = request.soupCount() > 0;

        List<RecipeCandidate> pool = recipeRepository.findCandidates(List.of(DishCategory.values())).stream()
                .filter(candidate -> candidate.category().isSoup() == wantSoup)
                .filter(candidate -> !blocked.contains(candidate.id()))
                .filter(candidate -> !excluded.contains(candidate.id()))
                .toList();

        if (pool.isEmpty()) {
            throw BizException.badRequest("没有更多可换的菜了，试试放宽忌口或减少数量");
        }

        int count = Math.max(1, wantSoup ? request.soupCount() : request.dishCount());
        MenuStrategy strategy = strategyRegistry.get(request.strategy());
        MenuStrategy.Selection selection = strategy.pick(
                wantSoup ? List.of() : pool,
                wantSoup ? pool : List.of(),
                wantSoup ? 0 : count,
                wantSoup ? count : 0);

        List<Long> ids = new ArrayList<>(selection.dishIds());
        ids.addAll(selection.soupIds());
        Map<Long, RecipeCardVO> cards = loadCards(ids);
        return ids.stream().map(cards::get).filter(Objects::nonNull).toList();
    }

    /** 历史清理：每天凌晨 3:10 清掉超过保留期的记录。 */
    @Scheduled(cron = "0 10 3 * * *")
    @Transactional
    public void purgeExpiredHistory() {
        int removed = historyRepository.deleteOlderThan(java.time.LocalDateTime.now().minusDays(HISTORY_RETENTION_DAYS));
        if (removed > 0) {
            log.info("清理配餐历史 {} 条", removed);
        }
    }

    // ============================================================ 内部

    private List<String> collectAvoidKeywords(Long userId, List<String> requestAvoid) {
        Set<String> keywords = new LinkedHashSet<>();
        if (requestAvoid != null) {
            requestAvoid.stream()
                    .map(AvoidanceFilterService::sanitize)
                    .filter(Objects::nonNull)
                    .limit(20)
                    .forEach(keywords::add);
        }
        if (userId != null) {
            avoidanceRepository.findByUserId(userId).stream()
                    .map(UserAvoidance::getKeyword)
                    .map(AvoidanceFilterService::sanitize)
                    .filter(Objects::nonNull)
                    .forEach(keywords::add);
        }
        return List.copyOf(keywords);
    }

    /** 只有在过滤后池子仍够用时才真的排除，否则宁可重复也不要空手而归。 */
    private List<RecipeCandidate> avoidRecent(List<RecipeCandidate> pool, Set<Long> recent) {
        List<RecipeCandidate> filtered = pool.stream()
                .filter(candidate -> !recent.contains(candidate.id()))
                .toList();
        return filtered.size() >= Math.min(pool.size(), 3) ? filtered : pool;
    }

    private Set<Long> recentRecipeIds(Long userId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (MenuHistory history : historyRepository.findTop3ByUserIdOrderByCreatedAtDesc(userId)) {
            try {
                long[] parsed = objectMapper.readValue(history.getResultJson(), long[].class);
                for (long id : parsed) {
                    ids.add(id);
                }
            } catch (JacksonException ex) {
                log.warn("配餐历史解析失败 id={}", history.getId(), ex);
            }
        }
        return ids;
    }

    private Map<Long, RecipeCardVO> loadCards(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, RecipeCardVO> cards = new HashMap<>();
        for (Recipe recipe : recipeRepository.findByIdIn(ids)) {
            cards.put(recipe.getId(), RecipeCardVO.of(recipe));
        }
        return cards;
    }

    private MenuResponse.Summary summarize(List<RecipeCardVO> dishes, List<RecipeCardVO> soups, List<String> keywords) {
        List<RecipeCardVO> all = new ArrayList<>(dishes);
        all.addAll(soups);

        int totalCalories = all.stream().mapToInt(card -> card.calories() == null ? 0 : card.calories()).sum();
        int totalMinutes = all.stream().mapToInt(card -> card.cookMinutes() == null ? 0 : card.cookMinutes()).sum();
        int totalServings = all.stream().mapToInt(card -> card.servings() == null ? 0 : card.servings()).sum();

        Map<String, Integer> difficultyMix = new LinkedHashMap<>();
        Map<String, Integer> categoryMix = new LinkedHashMap<>();
        for (RecipeCardVO card : all) {
            difficultyMix.merge(card.difficultyLabel() == null ? "未知" : card.difficultyLabel(), 1, Integer::sum);
            categoryMix.merge(card.category() == null ? "未知" : card.category(), 1, Integer::sum);
        }
        // 难度按 低→中→高 展示，不用字典序
        Map<String, Integer> orderedDifficulty = new LinkedHashMap<>();
        for (String label : List.of("低", "中", "高", "未知")) {
            if (difficultyMix.containsKey(label)) {
                orderedDifficulty.put(label, difficultyMix.get(label));
            }
        }
        // 分类按出现次数从多到少
        Map<String, Integer> orderedCategory = new LinkedHashMap<>();
        categoryMix.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> orderedCategory.put(entry.getKey(), entry.getValue()));

        return new MenuResponse.Summary(
                dishes.size(), soups.size(), totalCalories, totalMinutes, totalServings,
                orderedDifficulty, orderedCategory, keywords);
    }

    private void saveHistory(Long userId, MenuRequest request, List<Long> ids) {
        if (userId == null) {
            return;
        }
        try {
            MenuHistory history = new MenuHistory();
            history.setUserId(userId);
            history.setDishCount(request.dishCount());
            history.setSoupCount(request.soupCount());
            history.setSpecJson(objectMapper.writeValueAsString(Map.of(
                    "strategy", String.valueOf(request.strategy()),
                    "avoid", request.avoid() == null ? List.of() : request.avoid())));
            history.setResultJson(objectMapper.writeValueAsString(ids));
            historyRepository.save(history);
        } catch (JacksonException ex) {
            // 历史只是辅助功能，写失败不能影响用户拿到菜单
            log.warn("配餐历史写入失败 userId={}", userId, ex);
        }
    }
}
