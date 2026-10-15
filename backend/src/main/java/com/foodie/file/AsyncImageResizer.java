package com.foodie.file;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.foodie.config.AsyncConfig;
import com.foodie.config.UploadProperties;

import net.coobird.thumbnailator.Thumbnails;

/**
 * 异步生成中等尺寸图。
 *
 * <p>为什么这一半可以异步、缩略图那一半不行：
 * 帖子一旦发布，列表页立刻就要拉缩略图，它必须已经存在；
 * 而中等尺寸图只有用户点进详情页才会请求——从「上传完成」到「点进详情」
 * 之间隔着至少一次网络往返和人工操作，几百毫秒的生成时间完全来得及。
 *
 * <p>失败也不影响主流程：前端在中等图 404 时会回退到原图。
 */
@Component
public class AsyncImageResizer {

    private static final Logger log = LoggerFactory.getLogger(AsyncImageResizer.class);

    private final UploadProperties properties;
    private final FileStorageService storage;

    public AsyncImageResizer(UploadProperties properties, FileStorageService storage) {
        this.properties = properties;
        this.storage = storage;
    }

    @Async(AsyncConfig.IMAGE_EXECUTOR)
    public void scheduleMedium(Path originalPath, String originalUrl, ImageProcessor.ImageFormat format) {
        String mediumUrl = storage.variantUrl(originalUrl, "_medium", format.extension());
        try {
            Thumbnails.of(originalPath.toFile())
                    .size(properties.mediumWidth(), properties.mediumWidth())
                    .outputQuality(0.85)
                    .toFile(storage.resolve(mediumUrl).toFile());
        } catch (Exception ex) {
            // 生成不了就算了，前端会回退到原图；不能因为一张缩略图把上传接口搞失败
            log.warn("中等尺寸图生成失败 url={} （前端将回退原图）", originalUrl, ex);
        }
    }
}
