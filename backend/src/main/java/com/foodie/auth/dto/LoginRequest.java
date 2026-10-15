package com.foodie.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 *
 * @param rememberMe true 时签发长效令牌（7 天），否则 2 小时
 */
public record LoginRequest(

        @NotBlank(message = "用户名不能为空")
        String username,

        @NotBlank(message = "密码不能为空")
        String password,

        boolean rememberMe) {
}
