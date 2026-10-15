package com.foodie.recipe.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.foodie.recipe.Difficulty;
import com.foodie.recipe.Recipe;
import com.foodie.recipe.RecipeTag;

/**
 * 菜谱卡片视图（列表页用）。
 *
 * <p>只包含 {@code recipe} 表的标量字段 + 标签名，<b>不含</b>食材与步骤。
 * 这样列表页渲染完全不触发关联集合的加载，
 * 一页 12 条就是 1 次分页查询 + 1 次 count，而不是 1 + 12 次。
 *
 * @param favorited 当前登录用户是否已收藏；未登录时恒为 false
 */
public record RecipeCardVO(
        Long id,
        String title,
        String coverUrl,
        String description,
        String cuisine,
        String category,
        String difficulty,
        String difficultyLabel,
        Integer difficultyLevel,
        Integer cookMinutes,
        Integer servings,
        Integer calories,
        BigDecimal ratingAvg,
        Integer ratingCount,
        Integer viewCount,
        Integer favoriteCount,
        List<String> tags,
        boolean favorited) {

    public static RecipeCardVO of(Recipe recipe) {
        return of(recipe, Set.of());
    }

    /**
     * @param favoriteRecipeIds 当前用户已收藏的菜谱 id；未登录传空集合
     */
    public static RecipeCardVO of(Recipe recipe, Set<Long> favoriteRecipeIds) {
        Difficulty difficulty = recipe.getDifficulty();
        return new RecipeCardVO(
                recipe.getId(),
                recipe.getTitle(),
                recipe.getCoverUrl(),
                recipe.getDescription(),
                recipe.getCuisine(),
                recipe.getCategory() == null ? null : recipe.getCategory().label(),
                difficulty == null ? null : difficulty.name(),
                difficulty == null ? null : difficulty.label(),
                recipe.getDifficultyLevel(),
                recipe.getCookMinutes(),
                recipe.getServings(),
                recipe.getCalories(),
                recipe.getRatingAvg(),
                recipe.getRatingCount(),
                recipe.getViewCount(),
                recipe.getFavoriteCount(),
                recipe.getTags().stream().map(RecipeTag::getName).sorted().toList(),
                favoriteRecipeIds.contains(recipe.getId()));
    }
}
