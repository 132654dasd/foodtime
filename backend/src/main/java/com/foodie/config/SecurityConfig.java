package com.foodie.config;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

import com.foodie.auth.JwtAuthenticationFilter;

/**
 * 安全配置。
 *
 * <p>三条主线：
 * <ol>
 *   <li><b>无状态</b>：不用 Session，认证完全靠 JWT，天然可水平扩展；</li>
 *   <li><b>读写权限分离</b>：菜谱的读对所有人生效（含游客），
 *       写操作硬性限制为 ADMIN——这是需求「只有管理员能上传食谱」的服务端落点；</li>
 *   <li><b>不泄漏内部信息</b>：401/403 都返回统一 JSON，且不透露资源是否存在。</li>
 * </ol>
 *
 * <p>规则是<b>顺序敏感</b>的：更具体的路径必须排在通配之前，
 * 例如 {@code POST /api/recipes/*&#47;favorite}（登录即可）
 * 必须排在 {@code POST /api/recipes/**}（仅 ADMIN）之前，
 * 否则普通用户连收藏都用不了。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CorsProperties corsProperties;
    private final Environment environment;

    /**
     * 接口文档是否仅管理员可访问（默认是，安全优先）。
     *
     * <p>为什么读<b>进程级环境变量</b>而不是 Spring 配置属性：
     * 实测在 {@code @Configuration} 类上，{@code @Value} 与 {@code Environment.getProperty}
     * 都拿不到 profile 专属配置里的值 —— 这个 Bean 被创建时属性源还没绑定完，
     * 于是本该收紧的规则会静默退化成 {@code permitAll}。
     * 「看起来设了防、实际没设」比不设防更危险，所以换成一个时序上绝对可靠的机制。
     *
     * <p>本地要用 Swagger 调试时：设 {@code FOODIE_DOCS_ADMIN_ONLY=false} 即可放开。
     */
    private boolean docsAdminOnly() {
        String raw = System.getenv("FOODIE_DOCS_ADMIN_ONLY");
        return raw == null || !"false".equalsIgnoreCase(raw.trim());
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);

    public SecurityConfig(CorsProperties corsProperties, Environment environment) {
        this.corsProperties = corsProperties;
        this.environment = environment;
    }

    /** 仅供诊断：当前激活的 profile 列表。 */
    private String activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "(default)" : String.join(",", profiles);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // 无状态 + JWT，不存在 CSRF 的攻击面（浏览器不会自动携带凭据）
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/api/auth/register", "/api/auth/login").permitAll();

                    // ---- 接口文档
                    // 为什么不用 springdoc.api-docs.enabled / swagger-ui.enabled？
                    // 实测在 springdoc 3.1.1 + Boot 4.1.1 下这两个属性不生效：
                    // 即使显式传 --springdoc.api-docs.enabled=false，/v3/api-docs 依然可访问。
                    // 把开关放在安全层，才是真正可验证的。
                    // 诊断：无条件限制（先确认 matcher 本身是否对 springdoc 端点生效）
                    if (docsAdminOnly()) {
                        log.warn("API docs are ADMIN-only (set FOODIE_DOCS_ADMIN_ONLY=false to open them)");
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                                .hasRole("ADMIN");
                    } else {
                        log.warn("API docs are public because FOODIE_DOCS_ADMIN_ONLY=false");
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                                .permitAll();
                    }
                    auth.requestMatchers("/uploads/**", "/favicon.ico",
                                    "/actuator/health", "/actuator/info").permitAll()

                            // ---- 菜谱：读开放给所有人（游客也能浏览）
                            .requestMatchers(HttpMethod.GET, "/api/recipes", "/api/recipes/**").permitAll()

                            // ---- 食材价格看板：公开只读。
                            // 写操作走 /api/admin/ingredient-prices（下面的 ADMIN 规则拦着），
                            // 普通用户物理上没有改价入口。
                            .requestMatchers(HttpMethod.GET, "/api/prices/**").permitAll()

                            // ---- 社区：读开放
                            .requestMatchers(HttpMethod.GET, "/api/community/**").permitAll()

                            // ---- 写操作里「登录即可」的几个，必须排在管理员通配之前
                            .requestMatchers(HttpMethod.POST, "/api/recipes/*/favorite").authenticated()
                            .requestMatchers(HttpMethod.DELETE, "/api/recipes/*/favorite").authenticated()
                            .requestMatchers(HttpMethod.POST, "/api/recipes/*/rating").authenticated()

                            // ---- 菜谱写操作：仅管理员
                            .requestMatchers(HttpMethod.POST, "/api/recipes", "/api/recipes/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.PUT, "/api/recipes/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.DELETE, "/api/recipes/**").hasRole("ADMIN")

                            // ---- 后台
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")

                            // ---- 除 health/info 之外的监控端点也要管理员。
                            // 不能跟着下面的 SPA 规则一起放开，否则 /actuator/metrics 会变成公开的。
                            .requestMatchers("/actuator/**").hasRole("ADMIN")

                            // ---- 其余 API 一律要登录（社区写操作、配餐、上传、个人中心…）
                            .requestMatchers("/api/**").authenticated()

                            // ---- 非 /api 路径是前端静态资源与浏览器端路由，必须放开。
                            // 否则用户在 /recipes/1 上按刷新会被拦成 401，SPA 的深链接永远打不开。
                            // 所有 API 与监控规则都排在它之前，因此这里放开是安全的。
                            .anyRequest().permitAll();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // strength=10 是安全性与登录耗时的常用平衡点（约 50-100ms/次，能有效拖慢离线爆破）
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Trace-Id", "Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 关闭 JwtAuthenticationFilter 的容器级自动注册。
     *
     * <p>它是 {@code @Component}，Boot 默认会把它注册成 Servlet Filter，
     * 于是既在容器链里跑、又在 Security 链里跑。虽然 OncePerRequestFilter
     * 能防止重复执行，但顺序变得不可控。这里显式关掉，只让它在 Security 链中生效。
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
