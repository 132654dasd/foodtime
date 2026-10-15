package com.foodie.community.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.foodie.community.PostComment;

/**
 * 评论视图。
 *
 * <p>只做两级：{@code replies} 里的评论不会再带 replies。
 * 更深的树在手机屏幕上根本读不清，实现与展示成本却成倍上升。
 *
 * @param editable 是否可删除（本人或管理员）
 */
public record CommentVO(
        Long id,
        Long postId,
        Long parentId,
        String content,
        Long authorId,
        String authorNickname,
        String authorAvatar,
        LocalDateTime createdAt,
        boolean editable,
        List<CommentVO> replies) {

    public static CommentVO of(PostComment comment, String nickname, String avatar,
                               boolean editable, List<CommentVO> replies) {
        return new CommentVO(
                comment.getId(),
                comment.getPostId(),
                comment.getParentId(),
                comment.getContent(),
                comment.getUserId(),
                nickname,
                avatar,
                comment.getCreatedAt(),
                editable,
                replies);
    }
}
