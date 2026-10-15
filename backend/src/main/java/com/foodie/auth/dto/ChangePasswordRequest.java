package com.foodie.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求。
 *
 * <p>必须提供旧密码：即使已持有有效 token，
 * 也不能凭一个可能被盗用的会话直接把密码改掉（防会话劫持提权）。
 */
public record ChangePasswordRequest(

        @NotBlank(message = "请输入当前密码")
        String oldPassword,

        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 32, message = "新密码长度需在 6-32 位之间")
        String newPassword) {
}
