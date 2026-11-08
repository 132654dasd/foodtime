package com.foodie.recipe;

/**
 * 菜谱食材的扁平投影，供「按冰箱推荐」使用。
 *
 * <p>为什么不直接加载 {@link RecipeIngredient} 实体：
 * 推荐算法要遍历<b>全部</b>菜谱的食材行（30 道菜约 1200 行）才能判断
 * 「这道菜用到了哪些冰箱里的东西、还缺什么」。加载实体意味着 1200 个
 * Hibernate 受管对象 + 一次「按 recipe 反查」的关联解析，而我们只需要
 * 5 个标量列。用投影一次查询拉平，匹配全程在内存里做。
 *
 * <p>这条捷径的适用前提是菜谱总量不大。菜谱涨到上万道时应该先用
 * {@code idx_ingredient_name} 反查候选菜谱 id 再取详情，而不是全量拉取——
 * 这条演进路径记在 docs/ARCHITECTURE.md，不在当前代码里提前实现。
 *
 * @param recipeId 所属菜谱 id
 * @param name     食材名，匹配的核心字段
 * @param main     1 主料 / 0 辅料；{@link #mainFlag()} 负责空值兜底
 * @param amount   用量原文，如「2」「适量」
 * @param unit     单位，如「个」「g」
 */
public record IngredientRow(
        Long recipeId,
        String name,
        Integer main,
        String amount,
        String unit) {

    /** 为 null 时按辅料处理：宁可少算一个主料，也不要因为脏数据把菜判成「做不了」。 */
    public boolean mainFlag() {
        return main != null && main == 1;
    }
}
