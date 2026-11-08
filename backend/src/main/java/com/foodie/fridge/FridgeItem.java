package com.foodie.fridge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户冰箱里的一条食材。
 *
 * <p>三个字段的语义刻意区分开：
 * <ul>
 *   <li>{@code quantity} 为 <b>null</b> 表示「不关心数量」——用户只想记「我有一袋粉丝」。
 *       这类条目会参与「有没有」的判断，但不参与「够不够」的判断。</li>
 *   <li>{@code unit} 用空串而不是 null 表示「无单位」。MySQL 唯一索引允许多个 NULL，
 *       若 unit 可空，{@code (user_id, name, NULL)} 就能无限插入，唯一约束会形同虚设。</li>
 *   <li>{@code expireAt} 只用于排序与提醒，<b>到期不会删除数据</b>——
 *       用户自己买的东西，系统自作主张删掉才是帮倒忙。</li>
 * </ul>
 */
@Entity
@Table(name = "fridge_item")
@Getter
@Setter
public class FridgeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(precision = 10, scale = 2)
    private BigDecimal quantity;

    /** 空串 = 无单位（不关心数量）。 */
    @Column(nullable = false, length = 20)
    private String unit = "";

    @Column(name = "expire_at")
    private LocalDate expireAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 是否「有数量可比较」。
     *
     * <p>数量与单位必须同时存在才有意义：「3」没有单位无法换算，
     * 「个」没有数量也不知道有多少。
     */
    public boolean hasQuantity() {
        return quantity != null && quantity.signum() > 0 && unit != null && !unit.isBlank();
    }
}
