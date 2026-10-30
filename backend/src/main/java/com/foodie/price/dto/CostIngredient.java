package com.foodie.price.dto;

/**
 * 成本计算的输入项：一道菜里的一行食材。
 *
 * <p>之所以不直接吃 JPA 实体或菜谱 VO，是为了让 {@code DishCostService}
 * 不依赖任何上层包——它只关心「名字 / 用量 / 单位」这三件事，
 * 将来从购物清单、收藏夹还是别的什么地方拿数据都能复用。
 */
public record CostIngredient(String name, String amount, String unit) {
}
