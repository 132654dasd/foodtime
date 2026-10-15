package com.foodie.community.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发帖请求。
 *
 * @param imageUrls 已通过 {@code POST /api/files/image} 上传并拿到的图片地址；最多 9 张
 */
public record PostCreateRequest(

        @NotBlank(message = "标题不能为空")
        @Size(max = 100, message = "标题不能超过 100 字")
        String title,

        @NotBlank(message = "内容不能为空")
        @Size(max = 5000, message = "内容不能超过 5000 字")
        String content,

        @Size(max = 9, message = "最多上传 9 张图片")
        List<String> imageUrls) {
}
