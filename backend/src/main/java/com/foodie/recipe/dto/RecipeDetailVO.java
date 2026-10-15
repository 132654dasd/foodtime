package com.foodie.recipe.dto;

import java.math.BigDecimal;
import java.util.List;

import com.foodie.recipe.Difficulty;
import com.foodie.recipe.Recipe;
import com.foodie.recipe.RecipeIngredient;
import com.foodie.recipe.RecipeStep;
import com.foodie.recipe.RecipeTag;

/**
 * 菜谱详情视图。
 *
 * <p>这是会被放进缓存的对象，因此必须是<b>不可变</b>的 record：
 * 一旦有调用方改到缓存里的实例，就会出现跨请求的脏数据。
 *
 * <p>{@code favorited} 是跟人走的，不能进缓存。做法是缓存里始终存 false，
 * 取出来之后由 {@link #withFavorited(boolean)} 复制一份改掉。
 */
public record RecipeDetailVO(
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
        List<IngredientVO> ingredients,
        List<StepVO> steps,
        boolean favorited) {

    /** 食材项。 */
    public record IngredientVO(String name, String amount, String unit, boolean main) {
        static IngredientVO of(RecipeIngredient ingredient) {
            return new IngredientVO(
                    ingredient.getName(),
                    ingredient.getAmount(),
                    ingredient.getUnit(),
                    ingredient.isMainIngredient());
        }
    }

    /** 步骤项。 */
    public record StepVO(Integer stepNo, String content, String imageUrl) {
        static StepVO of(RecipeStep step) {
            return new StepVO(step.getStepNo(), step.getContent(), step.getImageUrl());
        }
    }

    public static RecipeDetailVO of(Recipe recipe) {
        Difficulty difficulty = recipe.getDifficulty();
        return new RecipeDetailVO(
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
                recipe.getIngredients().stream().map(IngredientVO::of).toList(),
                recipe.getSteps().stream().map(StepVO::of).toList(),
                false);
    }

    /** 基于缓存副本派生一个带真实收藏态的新实例。 */
    public RecipeDetailVO withFavorited(boolean value) {
        return new RecipeDetailVO(id, title, coverUrl, description, cuisine, category,
                difficulty, difficultyLabel, difficultyLevel, cookMinutes, servings, calories,
                ratingAvg, ratingCount, viewCount, favoriteCount, tags, ingredients, steps, value);
    }
}
