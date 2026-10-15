package com.foodie.menu;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.Setter;

/**
 * 配餐历史。
 *
 * <p>存下来是给两个功能用的：
 * 一是「避免连续两次推同一道菜」，二是个人中心里回看之前配过的菜单。
 */
@Entity
@Table(name = "menu_history")
@Getter
@Setter
public class MenuHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "dish_count", nullable = false)
    private Integer dishCount;

    @Column(name = "soup_count", nullable = false)
    private Integer soupCount;

    /** 本次请求的规格与忌口，JSON 字符串。 */
    @Column(name = "spec_json", nullable = false, length = 500)
    private String specJson;

    /** 命中的菜谱 id 列表，JSON 数组字符串，用于下次去重。 */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "result_json", nullable = false)
    private String resultJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
