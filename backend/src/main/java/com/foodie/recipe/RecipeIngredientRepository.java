package com.foodie.recipe;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    /**
     * 忌口反查：哪些菜谱的食材名里出现了该关键词。
     *
     * <p>只返回 id，配合 Caffeine 按关键词缓存结果集，
     * 避免每次配餐都重新扫一遍 ingredient 表。
     */
    @Query("select distinct i.recipe.id from RecipeIngredient i where i.name like concat('%', :keyword, '%')")
    List<Long> findRecipeIdsByIngredientNameContaining(@Param("keyword") String keyword);

    // ------------------------------------------------------------ 按冰箱推荐

    /**
     * 一次拉平全部菜谱的食材行，供「按冰箱推荐」在内存里做匹配。
     *
     * <p>为什么是全量而不是"先按食材名反查候选菜谱"：当前菜谱只有 30 道、
     * 食材行约 1200 行，一次索引扫描（毫秒级）+ 内存匹配，比"反查候选 → 再取详情"
     * 的多次往返更快，也避免了分步查询之间的一致性问题。
     * 这与 MenuPlannerService 拉全量候选池的思路一致。
     *
     * <p>菜谱涨到上万道时应改为用 {@code idx_ingredient_name} 先收窄候选，
     * 见 docs/ARCHITECTURE.md 里记录的演进路径。
     */
    @Query("""
            select new com.foodie.recipe.IngredientRow(
                       i.recipe.id, i.name, i.isMain, i.amount, i.unit)
            from RecipeIngredient i
            """)
    List<IngredientRow> findAllRows();

    /**
     * 食材字典：每个食材名被多少道菜用到。
     *
     * <p>冰箱录入页按 recipeCount 降序给自动补全建议，
     * 引导用户优先添加「能解锁更多菜谱」的食材。
     */
    @Query("""
            select new com.foodie.recipe.IngredientUsage(i.name, count(distinct i.recipe.id))
            from RecipeIngredient i
            group by i.name
            """)
    List<IngredientUsage> findUsage();
}
