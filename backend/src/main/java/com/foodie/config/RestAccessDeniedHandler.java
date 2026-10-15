package com.foodie.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.foodie.common.ErrorCode;
import com.foodie.common.Result;

/**
 * 已登录但权限不足时的响应（例如普通用户尝试新增菜谱）。
 *
 * <p>这是「只有管理员能上传菜谱」这条规则的<b>服务端</b>执行点：
 * 前端隐藏按钮只是体验优化，真正的防线在这里。
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> body = Result.fail(ErrorCode.FORBIDDEN, "只有管理员可以执行该操作");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
