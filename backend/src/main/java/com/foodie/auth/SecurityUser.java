package com.foodie.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.foodie.user.UserRole;

/**
 * Spring Security 的当前用户主体。
 *
 * <p>做成不可变 record：一旦放进 SecurityContext，就不该再被任何代码改写。
 * 同时提供静态工厂 {@link #ofJwt}，让 JWT 过滤器无需查库也能构造主体。
 *
 * @param id       用户 id
 * @param username 登录名
 * @param password BCrypt 哈希；JWT 认证路径下为空串（本来也用不到）
 * @param role     角色
 * @param enabled  是否启用
 */
public record SecurityUser(
        Long id,
        String username,
        String password,
        UserRole role,
        boolean enabled) implements UserDetails {

    public static SecurityUser ofJwt(Long id, String username, UserRole role) {
        return new SecurityUser(id, username, "", role, true);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAdmin() {
        return role != null && role.isAdmin();
    }
}
