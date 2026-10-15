package com.foodie.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.foodie.recipe.dto.RecipeCardVO;

/**
 * 后台看板数据。
 *
 * @param recipeCount    菜谱总数
 * @param userCount      用户总数
 * @param postCount      社区帖子数
 * @param quickRecipeCount 30 分钟内可完成的菜谱数
 * @param recipesByCategory   分类分布
 * @param recipesByDifficulty 难度分布
 * @param topRecipes     浏览量 TOP 菜谱
 * @param generatedAt    数据生成时间（看板有缓存，需要告诉使用者数据的新鲜度）
 */
public record StatsVO(
        long recipeCount,
        long userCount,
        long postCount,
        long quickRecipeCount,
        Map<String, Long> recipesByCategory,
        Map<String, Long> recipesByDifficulty,
        List<RecipeCardVO> topRecipes,
        LocalDateTime generatedAt) {
}
