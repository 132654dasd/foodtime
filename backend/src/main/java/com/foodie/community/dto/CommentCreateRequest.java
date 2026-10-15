package com.foodie.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发表评论请求。
 *
 * @param parentId 为空是一级评论；非空表示回复某条评论（只支持两级）
 */
public record CommentCreateRequest(

        @NotBlank(message = "评论内容不能为空")
        @Size(max = 1000, message = "评论不能超过 1000 字")
        String content,

        Long parentId) {
}
