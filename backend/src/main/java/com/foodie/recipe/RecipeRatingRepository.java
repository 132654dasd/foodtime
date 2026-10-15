package com.foodie.recipe;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRatingRepository extends JpaRepository<RecipeRating, Long> {

    Optional<RecipeRating> findByUserIdAndRecipeId(Long userId, Long recipeId);

    long countByRecipeId(Long recipeId);

    /**
     * 平均分。
     *
     * <p>返回 {@code Double} 而不是 {@code BigDecimal}：JPQL 的 avg 在 Hibernate 里
     * 映射为 Double，由 Service 再决定精度与舍入，避免在这里做隐式转换导致精度漂移。
     */
    @Query("select avg(r.score) from RecipeRating r where r.recipeId = :recipeId")
    Double averageScoreByRecipeId(@Param("recipeId") Long recipeId);
}
