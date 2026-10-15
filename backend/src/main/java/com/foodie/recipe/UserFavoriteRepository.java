package com.foodie.recipe;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    boolean existsByUserIdAndRecipeId(Long userId, Long recipeId);

    long deleteByUserIdAndRecipeId(Long userId, Long recipeId);

    /** 我的收藏列表：按收藏时间倒序。 */
    Page<UserFavorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<UserFavorite> findByUserIdAndRecipeIdIn(Long userId, List<Long> recipeIds);
}
