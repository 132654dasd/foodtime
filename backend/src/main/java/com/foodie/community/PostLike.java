package com.foodie.community;

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
 * 帖子点赞。
 *
 * <p>{@code uk_post_like_post_user} 是点赞幂等的<b>最终防线</b>：
 * 应用层的「先查再插」在并发下必然有竞态窗口，
 * 只有数据库唯一约束能真正保证同一个人对同一帖只留一条记录。
 */
@Entity
@Table(name = "post_like",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_like_post_user",
                columnNames = {"post_id", "user_id"}))
@Getter
@Setter
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
