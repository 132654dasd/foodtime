package com.foodie.recipe.dto;

import java.util.List;

/**
 * 菜谱字典：给前端筛选栏与忌口多选使用。
 *
 * @param cuisines   已有菜谱的菜系（去重）
 * @param categories 分类及数量
 * @param difficulties 难度及数量
 * @param tags       标签字典
 * @param difficultyLabels 难度英文名 → 中文标签的映射，前端不必硬编码
 */
public record RecipeMetaVO(
        List<String> cuisines,
        List<Option> categories,
        List<Option> difficulties,
        List<String> tags,
        List<Option> avoidSuggestions) {

    /** 筛选项：值 + 展示名 + 数量。 */
    public record Option(String value, String label, long count) {
    }
}
