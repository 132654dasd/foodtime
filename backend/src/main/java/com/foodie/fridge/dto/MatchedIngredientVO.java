package com.foodie.fridge.dto;

/**
 * 命中的一条食材明细。
 *
 * @param fridgeName     冰箱里的原始写法
 * @param ingredientName 菜谱里的原始写法
 * @param approximate    是否近似匹配（UI 上标「≈」）
 * @param stock          数量状态：ENOUGH / PRESENT / NOT_ENOUGH
 */
public record MatchedIngredientVO(
        String fridgeName,
        String ingredientName,
        boolean approximate,
        String stock) {
}
