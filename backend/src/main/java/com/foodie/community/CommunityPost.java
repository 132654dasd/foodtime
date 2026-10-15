package com.foodie.community;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.Setter;

/**
 * 社区帖子。
 *
 * <p>用户在这里分享成品图与制作过程，是普通用户唯一能产出内容的地方
 * （菜谱库只有管理员能写）。
 *
 * <p>计数列（点赞/评论/浏览）冗余存储，配合原子 UPDATE 使用，
 * 避免每次列表渲染都去做三个 COUNT 子查询。
 */
@Entity
@Table(name = "community_post")
@Getter
@Setter
public class CommunityPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    /*
     * 不用 @Lob：它会把字段映射成 CLOB，Hibernate 校验时期望 TINYTEXT，
     * 而 MySQL 的 TEXT 列被报告为 LONGVARCHAR，ddl-auto=validate 会直接启动失败。
     * 显式声明 SqlTypes.LONGVARCHAR 才能和 TEXT 对上。
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String content;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount = 0;

    @Column(name = "comment_count", nullable = false)
    private Integer commentCount = 0;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    /** 1 正常，0 已删除（软删，保住历史评论的引用）。 */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer status = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortNo ASC")
    private List<PostImage> images = new ArrayList<>();

    public void addImage(PostImage image) {
        image.setPost(this);
        this.images.add(image);
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(this.userId);
    }
}
