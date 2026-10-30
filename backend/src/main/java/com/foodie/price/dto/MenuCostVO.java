package com.foodie.price.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一整桌菜的成本汇总。
 *
 * @param totalCost               全部选中菜品的总费用（元）
 * @param perServingCost          按总份数折合的人均费用
 * @param pricedDishCount         完全算得出价格的菜品数
 * @param unpricedIngredientCount 未定价食材的行数（跨菜去重前的原始行数）
 * @param warnings                口径提示，例如「有 3 项食材未定价，未计入总费用」
 */
public record MenuCostVO(
        List<DishCostVO> dishes,
        BigDecimal totalCost,
        BigDecimal perServingCost,
        int dishCount,
        int pricedDishCount,
        int unpricedIngredientCount,
        List<String> warnings) {
}
