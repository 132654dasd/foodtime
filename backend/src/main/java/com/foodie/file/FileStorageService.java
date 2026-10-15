package com.foodie.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.foodie.common.BizException;
import com.foodie.config.UploadProperties;

/**
 * 本地文件存储。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>文件名一律用 UUID</b>，绝不使用用户上传的原始文件名——
 *       原始名既可能包含 {@code ../} 造成路径穿越，也可能碰撞覆盖；</li>
 *   <li><b>按年月日分目录</b>：单目录堆几万个文件会让文件系统变慢，
 *       也让备份和清理难以按时间切片；</li>
 *   <li><b>先写临时文件再原子移动</b>：直接写目标路径的话，
 *       并发读到半截文件的概率不为零（Nginx 之类的静态服务不会帮你加锁）。</li>
 * </ul>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final UploadProperties properties;
    private final Path root;

    public FileStorageService(UploadProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.dir()).toAbsolutePath().normalize();
    }

    /**
     * 保存上传文件。
     *
     * @param file    上传的文件
     * @param variant 变体后缀，如 {@code ""}（原图）、{@code "_thumb"}、{@code "_medium"}
     * @return 可直接对外访问的相对 URL
     */
    public String store(MultipartFile file, String variant, String extension) {
        String datePath = LocalDate.now().format(DATE_PATH);
        String filename = UUID.randomUUID().toString().replace("-", "") + variant + "." + extension;

        Path directory = root.resolve(datePath).normalize();
        if (!directory.startsWith(root)) {
            throw BizException.badRequest("非法的存储路径");
        }
        Path target = directory.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            throw BizException.badRequest("非法的存储路径");
        }

        try {
            Files.createDirectories(directory);
            // 先落临时文件，再原子移动，避免读到写了一半的文件
            Path temp = Files.createTempFile(directory, "upload-", ".tmp");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            log.error("文件写入失败 target={}", target, ex);
            throw new BizException(com.foodie.common.ErrorCode.INTERNAL, "图片保存失败，请重试");
        }

        return properties.urlPrefix() + "/" + datePath + "/" + filename;
    }

    /** 把对外 URL 还原成磁盘路径，用于后续生成缩略图。 */
    public Path resolve(String url) {
        if (url == null || !url.startsWith(properties.urlPrefix())) {
            throw BizException.badRequest("非法的图片地址");
        }
        String relative = url.substring(properties.urlPrefix().length());
        Path path = root.resolve(relative.startsWith("/") ? relative.substring(1) : relative).normalize();
        if (!path.startsWith(root)) {
            throw BizException.badRequest("非法的图片地址");
        }
        return path;
    }

    /** 对外 URL 的变体形式（与原图同目录、文件名带后缀）。 */
    public String variantUrl(String url, String variant, String extension) {
        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');
        if (dot < slash) {
            return url + variant;
        }
        return url.substring(0, dot) + variant + "." + extension;
    }

    public Path root() {
        return root;
    }
}
