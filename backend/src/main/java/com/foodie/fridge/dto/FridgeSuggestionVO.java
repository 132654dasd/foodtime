package com.foodie.fridge.dto;

import java.util.List;

/**
 * 按冰箱推荐的整体结果。
 *
 * @param items               推荐菜谱（已排序、已按 limit 截断）
 * @param fridgeCount         冰箱里的食材条数
 * @param cookableCount       主料齐全、现在就能做的菜谱数
 * @param candidateCount      命中至少一样主料的菜谱总数（截断前）
 * @param excludedByAvoidance 因忌口被排除的菜谱数
 * @param unusedFridgeItems   冰箱里没有任何菜谱用到的食材名——必须告诉用户，
 *                            否则他会以为功能坏了
 * @param warnings            需要展示给用户的说明
 */
public record FridgeSuggestionVO(
        List<RecipeMatchVO> items,
        int fridgeCount,
        int cookableCount,
        int candidateCount,
        int excludedByAvoidance,
        List<String> unusedFridgeItems,
        List<String> warnings) {
}
