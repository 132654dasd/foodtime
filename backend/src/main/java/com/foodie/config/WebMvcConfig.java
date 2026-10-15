package com.foodie.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态资源映射。
 *
 * <p>把本地上传目录挂到 {@code /uploads/**}。
 * 响应头带 {@code max-age=30d, immutable}：文件名是 UUID，
 * 内容不可变，因此可以让浏览器直接吃缓存，图片请求几乎不回到服务端。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    public WebMvcConfig(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(uploadProperties.dir()).toAbsolutePath().normalize();
        // toUri() 会正确处理 Windows 盘符与空格，拼出合法的 file: URL
        String location = root.toUri().toString();

        registry.addResourceHandler(uploadProperties.urlPrefix() + "/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS)
                        .cachePublic()
                        .immutable());
    }
}
