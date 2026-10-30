package com.foodie.price;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.Setter;

/**
 * 食材价格（管理员维护）。
 *
 * <p>业务主键是 {@code name}——它与 {@code recipe_ingredient.name} 对应，
 * 是全局唯一的一份价格。同一份番茄出现在 20 道菜里，改一次价全部生效。
 *
 * <p>{@code priceUnit} 是计价单位，不一定等于菜谱里的用量单位：
 * 番茄按「斤」计价、菜谱写「2 个」，中间的换算由 {@link UnitConverter} 负责。
 */
@Entity
@Table(name = "ingredient_price")
@Getter
@Setter
public class IngredientPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IngredientCategory category;

    @Column(name = "price_unit", nullable = false, length = 10)
    private String priceUnit;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "grams_per_unit", precision = 10, scale = 3)
    private BigDecimal gramsPerUnit;

    @Column(length = 200)
    private String note;

    /** 1 启用，0 停用；停用后不参与成本计算。 */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer enabled = 1;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isEnabled() {
        return enabled != null && enabled == 1;
    }

    public boolean isVegetable() {
        return category != null && category.isVegetable();
    }
}
