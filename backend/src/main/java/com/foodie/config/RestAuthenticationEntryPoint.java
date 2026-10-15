package com.foodie.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.foodie.common.ErrorCode;
import com.foodie.common.Result;

/**
 * 未认证时的响应。
 *
 * <p>必须自己实现：Spring Security 默认会返回一个 HTML 登录页或空 401，
 * 而前后端分离下前端需要统一的 JSON 结构才能正确处理。
 * 注意这里在过滤器链里，走不到 {@code @RestControllerAdvice}。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> body = Result.fail(ErrorCode.UNAUTHORIZED, "请先登录后再操作");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
