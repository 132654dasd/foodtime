package com.foodie.recipe;

/**
 * 随机配餐的候选菜谱投影。
 *
 * <p>只取加权抽样真正需要的 6 个标量列，不碰 steps/ingredients 关联，
 * 因此一次查询就能把整个候选池拉进内存，再在 Java 侧做加权抽样与忌口剔除。
 *
 * @param id              菜谱 id
 * @param category        分类，用于判断进菜池还是汤池
 * @param cookMinutes     耗时，QUICK 策略的权重因子
 * @param difficultyLevel 难度 1/2/3
 * @param viewCount       浏览量，热度权重因子
 * @param favoriteCount   收藏数，热度权重因子
 */
public record RecipeCandidate(
        Long id,
        DishCategory category,
        Integer cookMinutes,
        Integer difficultyLevel,
        Integer viewCount,
        Integer favoriteCount) {
}
