package com.foodie.fridge.dto;

/**
 * 食材字典条目：用于冰箱录入时的自动补全。
 *
 * @param name        食材名
 * @param recipeCount 用到它的菜谱数——按这个降序，引导用户优先添加「能解锁更多菜」的食材
 * @param category    食材分类（来自价目表，可能为 null）
 */
public record CatalogItemVO(String name, Long recipeCount, String category) {
}
