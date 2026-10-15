package com.foodie.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 单 jar 部署时的前端静态资源与 SPA 回退。
 *
 * <p>问题：前端的路由是浏览器端路由（history 模式）。
 * 用户在 <code>/recipes/1</code> 上按刷新，请求会直接打到服务端，
 * 而服务端并没有这个路径的静态文件，于是返回 404——
 * 明明页面刚才是好好的，一刷新就白屏，这是 SPA 部署里最常见的坑。
 *
 * <p>手段：用一个兜底的 {@link PathResourceResolver}。
 * 请求的文件存在就正常返回；不存在则回退到 {@code index.html}，
 * 交给前端路由去解析。
 *
 * <p>关键是<b>必须把 API 与静态资源前缀排除在外</b>：
 * 否则一个拼错的 <code>/api/xxx</code> 会返回 200 + HTML，
 * 前端拿到一坨 HTML 去 JSON.parse 之后报出与真实原因毫不相干的错误，
 * 排查成本极高。
 */
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

    /** 这些前缀不参与 SPA 回退，找不到就是 404。 */
    private static final String[] PASSTHROUGH_PREFIXES = {
            "api/", "uploads/", "actuator/", "v3/", "swagger-ui", "error"
    };

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 顺序靠后的通配处理器承担兜底；更具体的 /uploads/** 仍由 WebMvcConfig 优先匹配
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /** 找不到实体文件时回退到 index.html，但 API 类路径除外。 */
    private static final class SpaFallbackResolver extends PathResourceResolver {

        private static final Resource INDEX = new ClassPathResource("static/index.html");

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            for (String prefix : PASSTHROUGH_PREFIXES) {
                if (resourcePath.startsWith(prefix)) {
                    // 返回 null 让 Spring 走正常的 404 流程，由 GlobalExceptionHandler 输出统一 JSON
                    return null;
                }
            }
            // 前端尚未构建时（纯后端开发）index.html 不存在，此时同样交给 404
            return INDEX.exists() ? INDEX : null;
        }
    }
}
