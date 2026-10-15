package com.foodie.recipe.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 管理员新增/修改菜谱的请求体。
 *
 * <p>{@code difficultyLevel} 不在入参里：它由 {@code difficulty} 推导，
 * 避免前端传一个和 difficulty 矛盾的值把排序键写脏。
 */
public record RecipeSaveRequest(

        @NotBlank(message = "菜名不能为空")
        @Size(max = 100, message = "菜名不能超过 100 字")
        String title,

        @Size(max = 500, message = "封面地址不能超过 500 字")
        String coverUrl,

        @Size(max = 1000, message = "简介不能超过 1000 字")
        String description,

        @NotBlank(message = "菜系不能为空")
        @Size(max = 30, message = "菜系不能超过 30 字")
        String cuisine,

        @NotBlank(message = "分类不能为空")
        String category,

        @NotBlank(message = "难度不能为空")
        String difficulty,

        @Min(value = 1, message = "耗时至少 1 分钟")
        @Max(value = 2880, message = "耗时不能超过 2880 分钟")
        Integer cookMinutes,

        @Min(value = 1, message = "份量至少 1 人份")
        @Max(value = 50, message = "份量不能超过 50 人份")
        Integer servings,

        @Min(value = 0, message = "热量不能为负")
        @Max(value = 10000, message = "热量数值不合理")
        Integer calories,

        @Size(max = 10, message = "标签最多 10 个")
        List<String> tags,

        @NotEmpty(message = "至少需要一条食材")
        @Valid
        List<Ingredient> ingredients,

        @NotEmpty(message = "至少需要一个步骤")
        @Valid
        List<Step> steps) {

    /** 入参食材项。 */
    public record Ingredient(
            @NotBlank(message = "食材名不能为空")
            @Size(max = 50, message = "食材名不能超过 50 字")
            String name,

            @Size(max = 30, message = "用量不能超过 30 字")
            String amount,

            @Size(max = 20, message = "单位不能超过 20 字")
            String unit,

            Boolean main) {
    }

    /** 入参步骤项。 */
    public record Step(
            @NotBlank(message = "步骤内容不能为空")
            @Size(max = 1000, message = "步骤内容不能超过 1000 字")
            String content,

            @Size(max = 500, message = "步骤图片地址不能超过 500 字")
            String imageUrl) {
    }
}
