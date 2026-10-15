package com.foodie.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 图片上传配置。
 *
 * <p>本机没有对象存储，图片落本地磁盘。
 * 目录布局为 {@code <dir>/yyyy/MM/dd/<uuid>.<ext>}，
 * 按日期分目录可以避免单个目录堆积上万文件导致文件系统变慢。
 *
 * @param dir                 存储根目录（相对 backend 工作目录）
 * @param urlPrefix           对外访问前缀，会映射成静态资源处理器
 * @param maxSizeBytes        单文件大小上限
 * @param allowedContentTypes 允许的 MIME 白名单
 * @param thumbnailWidth      列表用缩略图宽度
 * @param mediumWidth         详情用中等图宽度
 */
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(
        String dir,
        String urlPrefix,
        long maxSizeBytes,
        List<String> allowedContentTypes,
        int thumbnailWidth,
        int mediumWidth) {

    public UploadProperties {
        dir = (dir == null || dir.isBlank()) ? "./data/uploads" : dir;
        urlPrefix = (urlPrefix == null || urlPrefix.isBlank()) ? "/uploads" : urlPrefix;
        maxSizeBytes = maxSizeBytes <= 0 ? 5L * 1024 * 1024 : maxSizeBytes;
        allowedContentTypes = (allowedContentTypes == null || allowedContentTypes.isEmpty())
                ? List.of("image/jpeg", "image/png", "image/webp", "image/gif")
                : List.copyOf(allowedContentTypes);
        thumbnailWidth = thumbnailWidth <= 0 ? 480 : thumbnailWidth;
        mediumWidth = mediumWidth <= 0 ? 1280 : mediumWidth;
    }
}
