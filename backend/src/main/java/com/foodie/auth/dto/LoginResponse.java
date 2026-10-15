package com.foodie.auth.dto;

/**
 * 登录/注册成功后的响应。
 *
 * @param token     访问令牌，前端放 Authorization: Bearer &lt;token&gt;
 * @param expiresIn 有效期（秒），前端据此提前续期或提示
 * @param user      当前用户信息
 */
public record LoginResponse(String token, long expiresIn, UserVO user) {
}
