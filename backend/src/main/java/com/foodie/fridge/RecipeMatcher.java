package com.foodie.fridge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.foodie.price.AmountParser;
import com.foodie.price.UnitConverter;
import com.foodie.recipe.IngredientRow;

/**
 * 「用我的冰箱能做什么菜」的匹配与排序。
 *
 * <p><b>刻意做成纯函数</b>（不注入任何 Repository、不碰 Spring 上下文）：
 * 这段逻辑是本功能里最需要被反复验证的部分，把它做成纯函数就能像
 * {@code PriceCalculationTest} 测 {@code UnitConverter} 那样直接驱动，
 * 而不必为了断言一个排序结果去准备 MySQL 数据。
 *
 * <p>三道与产品强相关的判断，都在这里定死：
 * <ol>
 *   <li><b>只命中调料的菜谱不算候选。</b>用户要的是「能吃的菜」，不是「盐管够」。
 *       所以候选资格 = 至少命中 1 个主料（{@code is_main = 1}）。</li>
 *   <li><b>数量不够也照样推荐。</b>{@link Stock#NOT_ENOUGH} 只影响展示与排序，
 *       绝不用来排除菜谱——宁可多推一道并标「量不够」，也不能因为算不出来就藏掉。</li>
 *   <li><b>算不出就不猜。</b>菜谱写「适量/少许」、单位换算不了、价目表没有克当量时，
 *       一律退化为 {@link Stock#PRESENT}（有就行），而不是假定「够」或「不够」。</li>
 * </ol>
 */
public final class RecipeMatcher {

    /** 食材充足性。枚举顺序有意义：{@link #bestHit} 依赖 ordinal 越小越好。 */
    public enum Stock {
        /** 数量能换算，且够用。 */
        ENOUGH,
        /** 有，但无法比较数量（没填数量 / 用量模糊 / 单位不可换算）。 */
        PRESENT,
        /** 数量能换算，但不够。 */
        NOT_ENOUGH
    }

    /** 过期日在这个天数以内（含已过期）的食材，优先安排消耗。 */
    public static final int EXPIRING_DAYS = 3;

    /**
     * 一道菜与冰箱的匹配结果。
     *
     * @param recipeId          菜谱 id
     * @param mainTotal         主料总数
     * @param mainMatched       命中主料数
     * @param auxTotal          辅料/调料总数
     * @param auxMatched        命中辅料数
     * @param notEnoughMainCount 数量不够的主料数
     * @param expiringUsed      用到的「快过期」食材名
     * @param matched           命中的明细（含近似标记与充足性）
     * @param missingMain       缺少的主料名
     * @param missingAux        缺少的辅料/调料名
     */
    public record RecipeMatch(
            Long recipeId,
            int mainTotal,
            int mainMatched,
            int auxTotal,
            int auxMatched,
            int notEnoughMainCount,
            List<String> expiringUsed,
            List<MatchedIngredient> matched,
            List<String> missingMain,
            List<String> missingAux) {

        public int expiringUsedCount() {
            return expiringUsed.size();
        }


        public int missingMainCount() {
            return mainTotal - mainMatched;
        }

        public int missingAuxCount() {
            return auxTotal - auxMatched;
        }

        /** 主料齐了就算「现在就能做」，调料缺几样问题不大。 */
        public boolean cookable() {
            return missingMainCount() == 0;
        }

        public int matchedCount() {
            return matched.size();
        }
    }

    /**
     * 单条命中明细。
     *
     * @param fridgeName     冰箱里的原始写法（给用户看「用的是我填的那个」）
     * @param ingredientName 菜谱里的原始写法
     * @param approximate    是否为近似匹配（UI 上标「≈」）
     * @param stock          数量充足性
     */
    public record MatchedIngredient(
            String fridgeName,
            String ingredientName,
            boolean approximate,
            Stock stock) {
    }

    private RecipeMatcher() {
    }

    /**
     * 计算全部菜谱与冰箱的匹配结果。
     *
     * <p>返回顺序不承担排序语义（按 recipeId 稳定输出），排序请用 {@link #ranking}。
     *
     * @param rows         全部菜谱食材行
     * @param fridgeItems  用户冰箱
     * @param gramsPerUnit 食材名 → 每 1 个计量单位约多少克（用于跨单位比较数量），可为 null
     * @param today        基准日期；传进来而不是内部取 now()，测试才能稳定
     */
    public static List<RecipeMatch> match(List<IngredientRow> rows,
                                          List<FridgeItem> fridgeItems,
                                          Function<String, BigDecimal> gramsPerUnit,
                                          LocalDate today) {
        if (rows == null || rows.isEmpty() || fridgeItems == null || fridgeItems.isEmpty()) {
            return List.of();
        }

        Map<Long, List<IngredientRow>> byRecipe = new LinkedHashMap<>();
        for (IngredientRow row : rows) {
            byRecipe.computeIfAbsent(row.recipeId(), key -> new ArrayList<>()).add(row);
        }

        LocalDate expiringThreshold = today.plusDays(EXPIRING_DAYS);
        List<RecipeMatch> matches = new ArrayList<>();
        for (Map.Entry<Long, List<IngredientRow>> entry : byRecipe.entrySet()) {
            RecipeMatch match = matchOne(entry.getKey(), entry.getValue(), fridgeItems, gramsPerUnit, expiringThreshold);
            if (match != null) {
                matches.add(match);
            }
        }
        return List.copyOf(matches);
    }

    /**
     * 排序规则（依次比较，最后一档用 id 兜底保证结果稳定）：
     *
     * <ol>
     *   <li>缺几样<b>主料</b>——用户最关心的指标</li>
     *   <li>主料数量不够的有几样——缺得少但量不够的往后放</li>
     *   <li>缺几样辅料/调料</li>
     *   <li>用到的<b>快过期</b>食材越多越靠前——减少浪费</li>
     *   <li>评分高的优先</li>
     *   <li>浏览量高的优先</li>
     *   <li>id 升序</li>
     * </ol>
     */
    public static Comparator<RecipeMatch> ranking(Map<Long, RecipeMetric> metrics) {
        Map<Long, RecipeMetric> safe = metrics == null ? Map.of() : metrics;
        Comparator<RecipeMatch> byFoodstuff = Comparator
                .comparingInt(RecipeMatch::missingMainCount)
                .thenComparingInt(RecipeMatch::notEnoughMainCount)
                .thenComparingInt(RecipeMatch::missingAuxCount)
                .thenComparing(Comparator.comparingInt(RecipeMatch::expiringUsedCount).reversed());
        return byFoodstuff
                .thenComparing(match -> ratingOf(safe, match), Comparator.<BigDecimal>reverseOrder())
                .thenComparing(match -> viewsOf(safe, match), Comparator.<Integer>reverseOrder())
                .thenComparing(RecipeMatch::recipeId);
    }

    private static BigDecimal ratingOf(Map<Long, RecipeMetric> metrics, RecipeMatch match) {
        RecipeMetric metric = metrics.get(match.recipeId());
        return metric == null ? BigDecimal.ZERO : metric.rating();
    }

    private static int viewsOf(Map<Long, RecipeMetric> metrics, RecipeMatch match) {
        RecipeMetric metric = metrics.get(match.recipeId());
        return metric == null ? 0 : metric.views();
    }

    // ============================================================ 内部

    private static RecipeMatch matchOne(Long recipeId,
                                        List<IngredientRow> rows,
                                        List<FridgeItem> fridgeItems,
                                        Function<String, BigDecimal> gramsPerUnit,
                                        LocalDate expiringThreshold) {
        int mainTotal = 0;
        int mainMatched = 0;
        int auxTotal = 0;
        int auxMatched = 0;
        int notEnoughMain = 0;
        List<MatchedIngredient> matched = new ArrayList<>();
        List<String> missingMain = new ArrayList<>();
        List<String> missingAux = new ArrayList<>();
        // 一边遍历一边去重：同一道菜里同名食材重复出现时，不该把「快过期」提示也算两遍
        Set<String> expiringUsed = new LinkedHashSet<>();

        for (IngredientRow row : rows) {
            boolean main = row.mainFlag();
            if (main) {
                mainTotal++;
            } else {
                auxTotal++;
            }

            Hit hit = bestHit(row, fridgeItems, gramsPerUnit);
            if (hit == null) {
                if (main) {
                    missingMain.add(row.name());
                } else {
                    missingAux.add(row.name());
                }
                continue;
            }

            if (main) {
                mainMatched++;
            } else {
                auxMatched++;
            }
            matched.add(new MatchedIngredient(
                    hit.item().getName(),
                    row.name(),
                    hit.tier() == IngredientMatcher.Tier.APPROX,
                    hit.stock()));
            if (main && hit.stock() == Stock.NOT_ENOUGH) {
                notEnoughMain++;
            }
            if (isExpiring(hit.item(), expiringThreshold)) {
                expiringUsed.add(hit.item().getName());
            }
        }

        // 候选资格：至少命中 1 个主料
        if (mainMatched == 0) {
            return null;
        }
        return new RecipeMatch(recipeId, mainTotal, mainMatched, auxTotal, auxMatched,
                notEnoughMain, List.copyOf(expiringUsed),
                List.copyOf(matched), List.copyOf(missingMain), List.copyOf(missingAux));
    }

    /** 在冰箱里找最能覆盖这个菜谱食材的那一条：精确优先，同级别下「够用」优先。 */
    private static Hit bestHit(IngredientRow row,
                               List<FridgeItem> fridgeItems,
                               Function<String, BigDecimal> gramsPerUnit) {
        Hit best = null;
        for (FridgeItem item : fridgeItems) {
            IngredientMatcher.Tier tier = IngredientMatcher.match(item.getName(), row.name());
            if (tier == null) {
                continue;
            }
            Stock stock = stockOf(item, row, gramsPerUnit);
            if (best == null
                    || tier.ordinal() < best.tier().ordinal()
                    || (tier == best.tier() && stock.ordinal() < best.stock().ordinal())) {
                best = new Hit(item, tier, stock);
            }
        }
        return best;
    }

    /** 数量充足性判定；任何一环算不出来都退化为「有就行」，绝不猜。 */
    private static Stock stockOf(FridgeItem item, IngredientRow row, Function<String, BigDecimal> gramsPerUnit) {
        if (!item.hasQuantity()) {
            return Stock.PRESENT;
        }
        AmountParser.Parsed parsed = AmountParser.parse(row.amount());
        if (parsed.estimated()) {
            // 「少许 / 适量 / 几滴」已经是估算值，再拿它判断够不够只是假精确
            return Stock.PRESENT;
        }
        BigDecimal grams = gramsPerUnit == null ? null : gramsPerUnit.apply(row.name());
        BigDecimal have = UnitConverter.toGrams(item.getQuantity(), item.getUnit(), grams);
        BigDecimal need = UnitConverter.toGrams(parsed.quantity(), row.unit(), grams);
        if (have == null || need == null) {
            return Stock.PRESENT;
        }
        return have.compareTo(need) >= 0 ? Stock.ENOUGH : Stock.NOT_ENOUGH;
    }

    /** 已过期的也算「该赶紧用」——它比 3 天后过期的更急。 */
    private static boolean isExpiring(FridgeItem item, LocalDate threshold) {
        return item.getExpireAt() != null && !item.getExpireAt().isAfter(threshold);
    }

    private record Hit(FridgeItem item, IngredientMatcher.Tier tier, Stock stock) {
    }
}
