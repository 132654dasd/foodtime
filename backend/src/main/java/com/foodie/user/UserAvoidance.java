package com.foodie.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户忌口关键词。
 *
 * <p>随机配餐时用它做菜谱过滤：命中任一关键词（匹配食材名/标签/菜名）的菜谱会被剔除。
 * 用独立表而不是在用户表里逗号拼接，是为了能建唯一索引、支持按关键词反查统计。
 */
@Entity
@Table(name = "user_avoidance")
@Getter
@Setter
public class UserAvoidance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String keyword;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
