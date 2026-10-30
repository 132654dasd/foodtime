package com.foodie.price;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用量文本解析。
 *
 * <p>菜谱里的 {@code amount} 是给人看的自由文本，真实数据里出现了
 * 「2」「500」「半」「少许」「适量」「几滴」这几种写法。算钱时前两类能精确相乘，
 * 后四类只能按经验值折算——这一点必须在返回值里如实区分，
 * 而不是把「少许」当成 0 悄悄算出一个偏低的价钱。
 *
 * <p>折算基准（有意选得保守，见 docs/ARCHITECTURE.md）：
 * <ul>
 *   <li>少许 ≈ 3 g（真的只是提味）</li>
 *   <li>适量 ≈ 5 g（比少许多，但通常仍是辅料量级）</li>
 *   <li>几滴 ≈ 1 ml</li>
 * </ul>
 */
public final class AmountParser {

    private static final BigDecimal TRACE_GRAMS = new BigDecimal("3");
    private static final BigDecimal MODERATE_GRAMS = new BigDecimal("5");
    private static final BigDecimal DROP_GRAMS = BigDecimal.ONE;
    private static final BigDecimal HALF = new BigDecimal("0.5");

    /** 形如「2个」「500g」：取开头的数字，单位由单独的 unit 列负责。 */
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)");

    private AmountParser() {
    }

    /**
     * @param quantity   可精确参与计算的数量；模糊用量时为 {@code null}
     * @param vagueGrams 模糊用量折算出的克数；能精确计算时为 {@code null}
     */
    public record Parsed(BigDecimal quantity, BigDecimal vagueGrams) {

        public boolean estimated() {
            return vagueGrams != null;
        }
    }

    public static Parsed parse(String amount) {
        if (amount == null || amount.isBlank()) {
            // 完全没写用量：按「适量」处理并标记为估算
            return new Parsed(null, MODERATE_GRAMS);
        }
        String text = amount.trim();
        if (text.contains("少许") || text.contains("微量") || text.contains("一点点")) {
            return new Parsed(null, TRACE_GRAMS);
        }
        if (text.contains("适量") || text.contains("酌情") || text.contains("随意")) {
            return new Parsed(null, MODERATE_GRAMS);
        }
        if (text.contains("几滴")) {
            return new Parsed(null, DROP_GRAMS);
        }
        if (text.equals("半") || text.startsWith("半")) {
            return new Parsed(HALF, null);
        }
        try {
            return new Parsed(new BigDecimal(text), null);
        } catch (NumberFormatException ignored) {
            // 继续尝试抽取前缀数字
        }
        Matcher matcher = LEADING_NUMBER.matcher(text);
        if (matcher.find()) {
            return new Parsed(new BigDecimal(matcher.group(1)), null);
        }
        // 兜底：认不出来的写法按「适量」估算，至少不会让这道菜变成 0 元
        return new Parsed(null, MODERATE_GRAMS);
    }
}
