package com.foodie.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置。
 *
 * <p>访问 {@code /swagger-ui.html}。声明 Bearer 认证方案后，
 * 可以在 Swagger UI 里直接粘 token 调需要登录的接口，省去用 curl 手拼请求头。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI foodieOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("食光 Foodie API")
                        .version("1.0.0")
                        .description("""
                                美食内容平台后端接口。

                                统一响应体为 `{ code, message, data, traceId }`，`code=0` 表示成功。
                                需要登录的接口请先调用 `/api/auth/login` 拿到 token，
                                再点右上角 Authorize 填入。
                                """))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("直接填 login 返回的 token，不用加 Bearer 前缀")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
