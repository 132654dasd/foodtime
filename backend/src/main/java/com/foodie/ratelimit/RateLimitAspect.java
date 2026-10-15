package com.foodie.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.foodie.auth.SecurityUser;
import com.foodie.common.BizException;

/**
 * 限流切面。
 *
 * <p>为什么用 AOP 而不是拦截器：限流是「方法级策略」，
 * 同一个 Controller 里不同方法的阈值完全不同
 * （登录 5 次/分、发帖 5 次/5 分、配餐 30 次/分）。
 * 注解挂在方法上，规则一目了然，也不用维护一张 URL 匹配表。
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RateLimiterRegistry registry;

    public RateLimitAspect(RateLimiterRegistry registry) {
        this.registry = registry;
    }

    @Before("@annotation(rateLimit)")
    public void enforce(JoinPoint joinPoint, RateLimit rateLimit) {
        String identity = resolveIdentity(rateLimit.scope());
        if (identity == null) {
            // 拿不到身份就不限流：宁可少一层保护，也不要误伤正常用户
            return;
        }
        if (!registry.tryConsume(rateLimit.value(), identity)) {
            log.warn("触发限流 rule={} identity={} method={}",
                    rateLimit.value(), identity, joinPoint.getSignature().toShortString());
            throw BizException.tooManyRequests("操作太频繁了，休息一下再试");
        }
    }

    private String resolveIdentity(RateLimit.Scope scope) {
        if (scope == RateLimit.Scope.USER) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof SecurityUser user) {
                return "u:" + user.id();
            }
            // 未登录时按 IP 兜底，至少能挡住匿名刷接口
        }
        return "ip:" + clientIp();
    }

    private String clientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            // 取第一跳；后面的都是中间代理
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
