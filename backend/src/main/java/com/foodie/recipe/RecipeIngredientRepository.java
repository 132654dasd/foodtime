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
}
