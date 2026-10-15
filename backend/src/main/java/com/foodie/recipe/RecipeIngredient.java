package com.foodie.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.Setter;

/**
 * 菜谱食材。
 *
 * <p>{@code name} 是忌口过滤的核心匹配字段，
 * 所以它单独建了索引（见 V1 的 {@code idx_ingredient_name}）。
 */
@Entity
@Table(name = "recipe_ingredient")
@Getter
@Setter
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 30)
    private String amount;

    @Column(length = 20)
    private String unit;

    /** 1 主料，0 辅料。 */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "is_main", nullable = false)
    private Integer isMain = 1;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;

    public boolean isMainIngredient() {
        return isMain != null && isMain == 1;
    }
}
