package com.foodie.price.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单道菜的成本明细。
 *
 * <p>诚实地把「算不出来」的信息带出去：{@code unpricedCount} 表示有几项食材没有价目，
 * {@code estimatedCount} 表示有几项是按「少许/适量」折算的。
 * 前端据此提示「含 N 项未定价」，而不是让用户看到一个看似精确、实际漏算了部分食材的金额。
 *
 * @param totalCost   整道菜的成本（元），已四舍五入到 2 位
 * @param servingCost 折合每人份的成本
 * @param complete    是否所有食材都已定价
 */
public record DishCostVO(
        Long recipeId,
        String title,
        Integer servings,
        BigDecimal totalCost,
        BigDecimal servingCost,
        int pricedCount,
        int unpricedCount,
        int estimatedCount,
        boolean complete,
        List<Line> lines) {

    /**
     * 一行食材的成本。
     *
     * @param quantity  换算到 {@code priceUnit} 后的数量，例如「0.6000 斤」
     * @param cost      该项金额；未定价时为 null
     * @param estimated 是否由「少许/适量/几滴」或跨单位换算估算而来
     */
    public record Line(
            String name,
            String amount,
            String unit,
            String category,
            BigDecimal quantity,
            String priceUnit,
            BigDecimal unitPrice,
            BigDecimal cost,
            boolean priced,
            boolean estimated) {
    }
}
