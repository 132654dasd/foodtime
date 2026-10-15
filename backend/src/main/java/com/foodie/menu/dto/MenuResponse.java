package com.foodie.menu.dto;

import java.util.List;
import java.util.Map;

import com.foodie.recipe.dto.RecipeCardVO;

/**
 * 随机配餐结果。
 *
 * @param dishes   选中的菜（复用菜谱卡片结构，前端一套渲染逻辑走到底）
 * @param soups    选中的汤
 * @param summary  汇总：总热量、总耗时、难度与分类分布
 * @param warnings 降级说明；为空表示完全满足规格
 * @param excludedCount 因忌口被剔除的菜谱总数，让用户知道过滤确实生效了
 */
public record MenuResponse(
        List<RecipeCardVO> dishes,
        List<RecipeCardVO> soups,
        Summary summary,
        List<String> warnings,
        int excludedCount) {

    /** 菜单汇总。 */
    public record Summary(
            int dishCount,
            int soupCount,
            int totalCalories,
            int totalMinutes,
            int totalServings,
            Map<String, Integer> difficultyMix,
            Map<String, Integer> categoryMix,
            List<String> appliedAvoid) {
    }
}
