package com.foodie.community.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.foodie.community.CommunityPost;
import com.foodie.community.PostImage;

/**
 * 帖子详情。
 *
 * @param editable 当前请求者是否有权编辑/删除（作者本人或管理员），
 *                 前端据此决定是否显示按钮；后端仍会独立校验一次
 */
public record PostDetailVO(
        Long id,
        String title,
        String content,
        Long authorId,
        String authorNickname,
        String authorAvatar,
        int likeCount,
        int commentCount,
        int viewCount,
        LocalDateTime createdAt,
        boolean liked,
        boolean editable,
        List<ImageVO> images) {

    /** 图片项：原图 + 缩略图，前端按场景取用。 */
    public record ImageVO(String url, String thumbUrl, Integer width, Integer height) {
        static ImageVO of(PostImage image) {
            return new ImageVO(image.getImageUrl(), image.getThumbUrl(), image.getWidth(), image.getHeight());
        }
    }

    public static PostDetailVO of(CommunityPost post, String nickname, String avatar,
                                  boolean liked, boolean editable) {
        return new PostDetailVO(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getUserId(),
                nickname,
                avatar,
                post.getLikeCount(),
                post.getCommentCount(),
                post.getViewCount(),
                post.getCreatedAt(),
                liked,
                editable,
                post.getImages().stream().map(ImageVO::of).toList());
    }
}
