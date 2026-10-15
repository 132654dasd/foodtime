package com.foodie.file;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;

import net.coobird.thumbnailator.Thumbnails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.foodie.common.BizException;
import com.foodie.config.UploadProperties;

/**
 * 图片校验与缩略图生成。
 *
 * <p><b>校验用的是文件头魔数，不是扩展名、也不是 Content-Type。</b>
 * 后两者都由客户端提供，改个名字就能绕过；
 * 只有真实字节序列能说明它到底是不是一张图片。
 */
@Service
public class ImageProcessor {

    private static final Logger log = LoggerFactory.getLogger(ImageProcessor.class);

    private final UploadProperties properties;
    private final FileStorageService storage;
    private final AsyncImageResizer asyncResizer;

    public ImageProcessor(UploadProperties properties, FileStorageService storage, AsyncImageResizer asyncResizer) {
        this.properties = properties;
        this.storage = storage;
        this.asyncResizer = asyncResizer;
    }

    /**
     * 处理上传的图片：校验 → 存原图 → 生成缩略图。
     *
     * <p>缩略图是<b>同步</b>生成的，因为发布帖子后列表页立刻就要用到它。
     * 相比之下中等尺寸图只有详情页会拉，交给 {@link AsyncImageResizer} 异步补。
     *
     * @return 原图 URL、缩略图 URL 与原图尺寸
     */
    public UploadResult store(MultipartFile file) {
        validateBasics(file);

        ImageFormat format = detect(file);
        if (format == null || !isAllowed(format, file.getContentType())) {
            throw BizException.badRequest("只支持 JPG / PNG / WebP / GIF 格式的图片");
        }

        String originalUrl = storage.store(file, "", format.extension());
        Path originalPath = storage.resolve(originalUrl);

        int[] size = readSize(originalPath);
        String thumbUrl = generateThumbnail(originalPath, originalUrl, format);
        asyncResizer.scheduleMedium(originalPath, originalUrl, format);

        return new UploadResult(originalUrl, thumbUrl, size[0], size[1]);
    }

    // ------------------------------------------------------------ 校验

    private void validateBasics(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的图片");
        }
        if (file.getSize() > properties.maxSizeBytes()) {
            throw BizException.badRequest("图片不能超过 %d MB"
                    .formatted(properties.maxSizeBytes() / 1024 / 1024));
        }
    }

    private boolean isAllowed(ImageFormat format, String contentType) {
        // 白名单同时看「真实格式」和「声明的 MIME」：
        // 只信其一会留下绕过空间（改扩展名，或伪造 Content-Type）
        boolean formatAllowed = properties.allowedContentTypes().contains(format.mimeType());
        boolean declaredAllowed = contentType == null
                || properties.allowedContentTypes().contains(contentType.toLowerCase(Locale.ROOT));
        return formatAllowed && declaredAllowed;
    }

    /** 读取文件头若干字节判断真实格式。 */
    private ImageFormat detect(MultipartFile file) {
        byte[] header = new byte[12];
        try (var in = file.getInputStream()) {
            int read = in.readNBytes(header, 0, header.length);
            if (read < 4) {
                return null;
            }
        } catch (IOException ex) {
            throw BizException.badRequest("图片读取失败");
        }
        return ImageFormat.detect(header);
    }

    private int[] readSize(Path path) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                // 能被魔数识别但 ImageIO 读不了（例如 WebP，JDK 无内置解码器），
                // 不影响存储，只是拿不到宽高
                return new int[]{0, 0};
            }
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException ex) {
            log.warn("读取图片尺寸失败 path={}", path, ex);
            return new int[]{0, 0};
        }
    }

    /**
     * 生成缩略图。
     *
     * <p>若当前 JDK 无法解码/编码该格式（典型是 WebP），
     * 就直接把原图复制一份作为缩略图。
     * 这样做的目的是保证「{@code _thumb} 一定存在」这个约定成立——
     * 否则前端按约定拼出的缩略图地址会 404。
     */
    private String generateThumbnail(Path originalPath, String originalUrl, ImageFormat format) {
        String thumbUrl = storage.variantUrl(originalUrl, "_thumb", format.extension());
        Path thumbPath = storage.resolve(thumbUrl);
        try {
            Thumbnails.of(originalPath.toFile())
                    .size(properties.thumbnailWidth(), properties.thumbnailWidth())
                    .outputQuality(0.82)
                    .toFile(thumbPath.toFile());
            return thumbUrl;
        } catch (Exception ex) {
            log.warn("缩略图生成失败，改用原图代替（格式 {}）", format, ex);
            try {
                Files.copy(originalPath, thumbPath, StandardCopyOption.REPLACE_EXISTING);
                return thumbUrl;
            } catch (IOException copyEx) {
                log.error("缩略图兜底复制也失败", copyEx);
                throw new BizException(com.foodie.common.ErrorCode.INTERNAL, "图片处理失败，请重试");
            }
        }
    }

    // ------------------------------------------------------------ 支持格式

    /** 支持的图片格式与魔数。 */
    public enum ImageFormat {
        JPEG("image/jpeg", "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        PNG("image/png", "png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}),
        GIF("image/gif", "gif", new byte[]{'G', 'I', 'F', '8'}),
        WEBP("image/webp", "webp", null);

        private final String mimeType;
        private final String extension;
        private final byte[] magic;

        ImageFormat(String mimeType, String extension, byte[] magic) {
            this.mimeType = mimeType;
            this.extension = extension;
            this.magic = magic;
        }

        public String mimeType() {
            return mimeType;
        }

        public String extension() {
            return extension;
        }

        static ImageFormat detect(byte[] header) {
            for (ImageFormat format : values()) {
                if (format.magic != null && startsWith(header, format.magic)) {
                    return format;
                }
            }
            // WebP 的魔数是 RIFF....WEBP，标志位在偏移 8 处
            if (header.length >= 12
                    && startsWith(header, new byte[]{'R', 'I', 'F', 'F'})
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return WEBP;
            }
            return null;
        }

        private static boolean startsWith(byte[] data, byte[] prefix) {
            if (data.length < prefix.length) {
                return false;
            }
            for (int i = 0; i < prefix.length; i++) {
                if (data[i] != prefix[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    /** 供静态检查用：允许的扩展名集合。 */
    public static Set<String> allowedExtensions() {
        return Set.of("jpg", "jpeg", "png", "gif", "webp");
    }

    /** 上传结果。 */
    public record UploadResult(String url, String thumbUrl, int width, int height) {
    }
}
