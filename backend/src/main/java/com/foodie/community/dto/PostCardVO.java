package com.foodie.community.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.foodie.community.CommunityPost;
import com.foodie.community.PostImage;

/**
 * 社区帖子卡片（信息流用）。
 *
 * @param summary    正文摘要，列表页不返回全文，省流量也省 XSS 面
 * @param coverThumb 首图缩略图，列表页只加载缩略图
 * @param liked      当前用户是否点过赞
 */
public record PostCardVO(
        Long id,
        String title,
        String summary,
        String coverThumb,
        Long authorId,
        String authorNickname,
        String authorAvatar,
        int likeCount,
        int commentCount,
        int viewCount,
        int imageCount,
        LocalDateTime createdAt,
        boolean liked) {

    /** 摘要长度：太长会让卡片高度参差，太短又看不出内容。 */
    private static final int SUMMARY_LENGTH = 72;

    public static PostCardVO of(CommunityPost post, String nickname, String avatar, Set<Long> likedPostIds) {
        List<PostImage> images = post.getImages();
        String cover = images.isEmpty() ? null : images.get(0).getThumbUrl();
        return new PostCardVO(
                post.getId(),
                post.getTitle(),
                summarize(post.getContent()),
                cover,
                post.getUserId(),
                nickname,
                avatar,
                post.getLikeCount(),
                post.getCommentCount(),
                post.getViewCount(),
                images.size(),
                post.getCreatedAt(),
                likedPostIds.contains(post.getId()));
    }

    private static String summarize(String content) {
        if (content == null) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= SUMMARY_LENGTH ? flat : flat.substring(0, SUMMARY_LENGTH) + "…";
    }
}
