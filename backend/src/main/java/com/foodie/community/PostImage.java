package com.foodie.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/** 帖子图片。列表页只用 {@code thumbUrl}，详情页才用 {@code imageUrl}。 */
@Entity
@Table(name = "post_image")
@Getter
@Setter
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private CommunityPost post;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "thumb_url", nullable = false, length = 500)
    private String thumbUrl;

    private Integer width;

    private Integer height;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;
}
