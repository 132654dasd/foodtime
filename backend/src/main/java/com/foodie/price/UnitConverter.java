package com.foodie.price;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 用量单位换算器。
 *
 * <p>整个「算钱」功能的技术核心：菜谱里写的是「2 个」「500 g」「1 勺」，
 * 而价格表里可能是「元/斤」。必须把它们统一到同一个度量上才能相乘。
 *
 * <p><b>做法：先把任意单位折算成「克」，再从克折算回计价单位。</b>
 * 一条换算阶梯覆盖全部组合，比写 N×M 个 case 分支可靠得多：
 *
 * <pre>
 *   g / 克 / kg / 斤 / 两 / 公斤        → 重量，有精确系数
 *   ml / 毫升 / l / 升                  → 体积，按 1ml ≈ 1g 处理（调料密度接近水）
 *   勺 / 汤匙(15) · 小勺 / 茶匙(5)      → 常见调味勺的固定容量
 *   个/根/片/瓣/只/粒/块/棵/条/朵/颗     → 计数单位，只能靠价格表的 grams_per_unit
 * </pre>
 *
 * <p><b>已知误差（如实记录，不假装精确）：</b>
 * <ol>
 *   <li>体积与重量按 1:1 互换，油比水轻约 10%，对几毛钱的调料成本可以忽略；</li>
 *   <li>{@code grams_per_unit} 每样食材只能标一个值。像「姜」既有「片」又有「块」，
 *       只能按更常见的「片」标定，用「块」时会有偏差；</li>
 *   <li>计数单位与重量之间的换算本质上是估算，因此 UI 会把这类行标记为「估算」。</li>
 * </ol>
 */
public final class UnitConverter {

    /** 中间结果的精度；最终金额才四舍五入到 2 位，避免逐级舍入放大误差。 */
    private static final int SCALE = 6;

    /** 重量单位 → 克。 */
    private static final Map<String, BigDecimal> WEIGHT = Map.ofEntries(
            Map.entry("g", BigDecimal.ONE),
            Map.entry("kg", new BigDecimal("1000")),
            Map.entry("斤", new BigDecimal("500")),
            Map.entry("两", new BigDecimal("50")));

    /** 体积单位 → 毫升。 */
    private static final Map<String, BigDecimal> VOLUME = Map.ofEntries(
            Map.entry("ml", BigDecimal.ONE),
            Map.entry("l", new BigDecimal("1000")));

    /** 调味勺 → 克（按 1ml ≈ 1g）。 */
    private static final Map<String, BigDecimal> SPOON = Map.of(
            "勺", new BigDecimal("15"),
            "小勺", new BigDecimal("5"));

    /**
     * 认识的计数单位。
     *
     * <p>为什么要白名单而不是「凡是不认识的都当计数单位」：
     * 后者会把「3 把」当成「3 个」去乘克当量，算出一个明显偏低的成本却毫无提示。
     * 这类静默算错比算不出来危险得多——宁可报「未定价」让用户看到，
     * 也不能给一个看起来精确的错数字。新增食材若用了别的计数单位，
     * 在这里补上即可（同时要在价格表里标好克当量）。
     */
    private static final Set<String> COUNT = Set.of(
            "个", "根", "片", "段", "瓣", "只", "粒", "块", "棵", "条", "朵", "颗");

    /** 别名归一：把菜谱里可能出现的各种写法收敛到上面三张表的键。 */
    private static final Map<String, String> ALIAS = Map.of(
            "小块", "块",
            "大勺", "勺",
            "汤匙", "勺",
            "茶匙", "小勺",
            "克", "g",
            "千克", "kg",
            "公斤", "kg",
            "毫升", "ml",
            "升", "l");

    private UnitConverter() {
    }

    /** 归一化单位写法：去空格、拉丁字母转小写、别名收敛。 */
    public static String normalize(String unit) {
        if (unit == null) {
            return "";
        }
        String trimmed = unit.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return ALIAS.getOrDefault(lower, lower);
    }

    /**
     * 把「数量 + 单位」折算成克。
     *
     * @param gramsPerUnit 计数单位每 1 个约多少克；重量/体积/勺类单位用不到
     * @return 克数；无法换算时返回 {@code null}（调用方据此判定「未定价」）
     */
    public static BigDecimal toGrams(BigDecimal quantity, String unit, BigDecimal gramsPerUnit) {
        if (quantity == null) {
            return null;
        }
        String normalized = normalize(unit);
        if (normalized.isEmpty()) {
            return null;
        }
        BigDecimal factor = WEIGHT.get(normalized);
        if (factor == null) {
            factor = VOLUME.get(normalized);
        }
        if (factor == null) {
            factor = SPOON.get(normalized);
        }
        if (factor != null) {
            return quantity.multiply(factor);
        }
        // 剩下的是白名单里的计数单位，只能依赖价格表里为该食材标定的克当量
        if (COUNT.contains(normalized) && isPositive(gramsPerUnit)) {
            return quantity.multiply(gramsPerUnit);
        }
        return null;
    }

    /** 把克数折算回目标单位下的数量。 */
    public static BigDecimal fromGrams(BigDecimal grams, String unit, BigDecimal gramsPerUnit) {
        if (grams == null) {
            return null;
        }
        String normalized = normalize(unit);
        if (normalized.isEmpty()) {
            return null;
        }
        BigDecimal factor = WEIGHT.get(normalized);
        if (factor == null) {
            factor = VOLUME.get(normalized);
        }
        if (factor == null) {
            factor = SPOON.get(normalized);
        }
        if (factor != null) {
            return grams.divide(factor, SCALE, RoundingMode.HALF_UP);
        }
        if (COUNT.contains(normalized) && isPositive(gramsPerUnit)) {
            return grams.divide(gramsPerUnit, SCALE, RoundingMode.HALF_UP);
        }
        return null;
    }

    /**
     * 把菜谱里的用量换算成计价单位下的数量。
     *
     * @return 计价单位的数量；为 {@code null} 表示这个单位的组合换算不了
     */
    public static BigDecimal convert(BigDecimal quantity, String fromUnit,
                                     String priceUnit, BigDecimal gramsPerUnit) {
        if (quantity == null) {
            return null;
        }
        if (normalize(fromUnit).equals(normalize(priceUnit))) {
            // 同单位无需换算，直接返回，避免走一遍乘除引入浮点误差
            return quantity;
        }
        BigDecimal grams = toGrams(quantity, fromUnit, gramsPerUnit);
        if (grams == null) {
            return null;
        }
        return fromGrams(grams, priceUnit, gramsPerUnit);
    }

    /** 是否为「已知系数」的单位（重量/体积/勺），这类单位换算结果可视为精确。 */
    public static boolean isExactUnit(String unit) {
        String normalized = normalize(unit);
        return WEIGHT.containsKey(normalized) || VOLUME.containsKey(normalized) || SPOON.containsKey(normalized);
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
