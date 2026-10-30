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

        /*
         * 用包装类型 Boolean 而不是基本类型 boolean —— 这是踩过的坑：
         * Jackson 3 把 FAIL_ON_NULL_FOR_PRIMITIVES 的默认值从 false 改成了 true，
         * 客户端不传这个字段时，null 映射到基本类型会抛反序列化异常，
         * 表现是整个登录接口返回 400「请求体格式不正确」，而字段本身其实是可选的。
         */
        Boolean rememberMe) {

    /** 字段可省略，省略时按 false 处理。 */
    public boolean rememberMeEnabled() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
