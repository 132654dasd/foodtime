package com.foodie.fridge.dto;

import java.util.List;

import com.foodie.recipe.dto.RecipeCardVO;

/**
 * 一道菜与冰箱的匹配结果。
 *
 * @param recipe             菜谱卡片（与列表页同一个 VO，前端可复用现有渲染逻辑）
 * @param cookable           主料是否齐了——即「现在就能做」
 * @param mainTotal          主料总数
 * @param mainMatched        命中主料数
 * @param matchedCount      命中食材总数（主料 + 辅料）
 * @param missingMain        缺少的主料名
 * @param missingAux         缺少的辅料/调料名
 * @param matchedIngredients 命中明细（含近似标记与数量状态）
 * @param expiringUsed       用到的「快过期」食材名
 */
public record RecipeMatchVO(
        RecipeCardVO recipe,
        boolean cookable,
        int mainTotal,
        int mainMatched,
        int matchedCount,
        List<String> missingMain,
        List<String> missingAux,
        List<MatchedIngredientVO> matchedIngredients,
        List<String> expiringUsed) {
}
