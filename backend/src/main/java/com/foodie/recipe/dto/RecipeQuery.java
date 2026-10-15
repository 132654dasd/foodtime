package com.foodie.recipe.dto;

import java.util.List;

import com.foodie.recipe.Difficulty;
import com.foodie.recipe.DishCategory;

/**
 * 菜谱库的筛选条件。
 *
 * @param keyword    中文关键词，走 MySQL ngram 全文索引；空则走普通检索
 * @param cuisine    菜系
 * @param category   分类
 * @param difficulty 难度
 * @param maxMinutes 最长耗时（分钟）
 * @param sort       排序：latest / hot / difficulty_asc / difficulty_desc / cook_asc / rating
 */
public record RecipeQuery(
        String keyword,
        String cuisine,
        DishCategory category,
        Difficulty difficulty,
        Integer maxMinutes,
        String sort) {

    /**
     * 清洗关键词。
     *
     * <p>必须剔除 {@code %} 与 {@code _}：它们是 LIKE 的通配符，
     * 用户输入一个 "%" 就能让查询退化成全表扫描。
     */
    public String safeKeyword() {
        if (keyword == null) {
            return null;
        }
        String cleaned = keyword.trim().replace("%", "").replace("_", "").replace("\\", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    public static final List<String> SORTS =
            List.of("latest", "hot", "difficulty_asc", "difficulty_desc", "cook_asc", "rating");
}
