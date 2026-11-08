package com.foodie.recipe;

/**
 * 食材使用情况投影：某个食材名被多少道菜用到。
 *
 * <p>用于冰箱录入时的自动补全：按「用它能做几道菜」降序，
 * 让用户优先添加那些真正能解锁菜谱的食材，而不是加一堆没人用的名字。
 *
 * @param name        食材名
 * @param recipeCount 用到它的菜谱数
 */
public record IngredientUsage(String name, Long recipeCount) {
}
