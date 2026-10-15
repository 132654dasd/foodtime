package com.foodie.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式限流。
 *
 * <p>用法：{@code @RateLimit("login")}，规则名对应
 * {@code app.ratelimit.rules.login} 里配置的容量与周期。
 * 把阈值放在配置里而不是写死在注解上，是为了能按环境调整而不用重新编译。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 规则名，对应 application.yml 中的 {@code app.ratelimit.rules.<name>}。 */
    String value();

    /** 限流维度。 */
    Scope scope() default Scope.USER;

    enum Scope {
        /** 按登录用户限流；未登录时退化为按 IP。 */
        USER,
        /** 按客户端 IP 限流，用于登录、注册这类未认证接口。 */
        IP
    }
}
