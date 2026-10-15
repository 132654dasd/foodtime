package com.foodie.common;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 链路 ID 过滤器。
 *
 * <p>优先级设为最高，保证后续所有日志（含 Spring Security 过滤器链里的）都带上 traceId。
 * 客户端可通过 {@code X-Trace-Id} 头透传；没传就生成一个。
 * 同时在响应头回写，前端 DevTools 里能直接看到。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 限制长度并只保留安全字符，避免客户端塞入超长或含换行的值污染日志。 */
    private static final int MAX_LENGTH = 64;
    private static final String SAFE_PATTERN = "[^A-Za-z0-9._-]";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = sanitize(request.getHeader(TraceIds.HEADER));
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TraceIds.MDC_KEY, traceId);
        response.setHeader(TraceIds.HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 线程池复用线程，必须清理，否则会串到下个请求
            MDC.remove(TraceIds.MDC_KEY);
        }
    }

    private String sanitize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw.trim().replaceAll(SAFE_PATTERN, "");
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }
}
