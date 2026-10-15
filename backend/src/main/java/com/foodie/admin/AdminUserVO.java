package com.foodie.admin;

import java.time.LocalDateTime;

import com.foodie.user.SysUser;

/**
 * 后台用户列表项。
 *
 * <p>刻意不复用 {@code UserVO}：那个是「当前登录用户」的视图，
 * 带忌口偏好，而管理员列表不需要也不该看到这些。
 */
public record AdminUserVO(
        Long id,
        String username,
        String nickname,
        String email,
        String role,
        Integer status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt) {

    public static AdminUserVO of(SysUser user) {
        return new AdminUserVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
