package com.foodie.file;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.foodie.common.Result;
import com.foodie.ratelimit.RateLimit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/files")
@Tag(name = "文件", description = "图片上传")
public class FileController {

    private final ImageProcessor imageProcessor;

    public FileController(ImageProcessor imageProcessor) {
        this.imageProcessor = imageProcessor;
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit("upload")
    @Operation(summary = "上传图片",
            description = "返回原图与缩略图地址，可直接填进发帖的 imageUrls；"
                    + "服务端会按文件头魔数校验真实格式，改扩展名无法绕过")
    public Result<ImageProcessor.UploadResult> upload(@RequestPart("file") MultipartFile file) {
        return Result.ok(imageProcessor.store(file));
    }
}
