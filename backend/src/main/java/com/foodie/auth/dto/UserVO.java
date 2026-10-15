package com.foodie.auth.dto;

import java.util.List;

import com.foodie.user.SysUser;

/**
 * 用户信息视图对象。
 *
 * <p>刻意与 {@link SysUser} 实体分离：实体携带 password 哈希，
 * 只要不把实体直接返回，就不存在「忘记加 @JsonIgnore 导致哈希泄漏」的风险。
 */
public record UserVO(
        Long id,
        String username,
        String nickname,
        String avatarUrl,
        String email,
        String role,
        List<String> avoidances) {

    public static UserVO of(SysUser user, List<String> avoidances) {
        return new UserVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getEmail(),
                user.getRole().name(),
                avoidances == null ? List.of() : List.copyOf(avoidances));
    }
}
