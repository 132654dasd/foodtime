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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.Setter;

/**
 * 菜谱评分（1-5 星）。
 *
 * <p>唯一键保证一人一菜只有一条评分，重复提交是「改分」而不是「加分」。
 */
@Entity
@Table(name = "recipe_rating",
        uniqueConstraints = @UniqueConstraint(name = "uk_rating_user_recipe",
                columnNames = {"user_id", "recipe_id"}))
@Getter
@Setter
public class RecipeRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    /** 1-5 星，由 Service 层校验范围。 */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer score;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
