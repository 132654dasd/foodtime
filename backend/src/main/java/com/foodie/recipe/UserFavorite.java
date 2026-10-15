package com.foodie.recipe;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.CreationTimestamp;

import lombok.Getter;
import lombok.Setter;

/**
 * 菜谱收藏。
 *
 * <p>{@code uk_favorite_user_recipe} 唯一键让「重复收藏」在数据库层就是幂等的：
 * 并发双击最多只有一条能插进去。
 */
@Entity
@Table(name = "user_favorite",
        uniqueConstraints = @UniqueConstraint(name = "uk_favorite_user_recipe",
                columnNames = {"user_id", "recipe_id"}))
@Getter
@Setter
public class UserFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
