package com.bub6le.crackserver.controller;

import com.bub6le.crackserver.common.Result;
import com.bub6le.crackserver.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "图片管理", description = "图片上传、列表、删除接口")
@RestController
@RequestMapping("/api/file")
@Slf4j
public class ImageController {

    @Autowired
    private ImageService imageService;

    @Operation(summary = "上传图片接口")
    @PostMapping("/upload-image")
    public Result uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        log.info("请求上传图片 fileName={}", file.getOriginalFilename());
        return imageService.uploadImage(file, description);
    }

    @Operation(summary = "获取用户图片列表接口")
    @GetMapping("/list-images")
    public Result listImages(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "fileType", required = false) String fileType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "label", required = false) String label) {
        log.info("请求获取图片列表 page={} pageSize={} keyword={} label={}", page, pageSize, keyword, label);
        return imageService.listImages(page, pageSize, fileType, keyword, label);
    }

    @Operation(summary = "删除图片接口")
    @DeleteMapping("/delete-image/{imageId}")
    public Result deleteImage(
            @PathVariable("imageId") Long imageId) {
        log.info("请求删除图片 imageId={}", imageId);
        return imageService.deleteImage(imageId);
    }

    @Operation(summary = "获取图片ID列表接口")
    @GetMapping("/get-image-ids")
    public Result getImageIds() {
        log.info("请求获取用户图片ID列表");
        return imageService.getImageIds();
    }

    @Operation(summary = "根据图片ID查询图片内容接口")
    @GetMapping("/get-image-by-id/{imageId}")
    public Result getImageById(
            @PathVariable("imageId") Long imageId) {
        log.info("请求获取图片详情 imageId={}", imageId);
        return imageService.getImageById(imageId);
    }

    @Operation(summary = "批量删除图片接口")
    @PostMapping("/batch-delete-images")
    public Result batchDeleteImages(
            @RequestBody Map<String, Object> body) {
        log.info("请求批量删除图片");
        java.util.List<Long> imageIds = new java.util.ArrayList<>();
        if (body.get("imageIds") instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) body.get("imageIds");
            for (Object item : list) {
                if (item instanceof Number) {
                    imageIds.add(((Number) item).longValue());
                }
            }
        }
        
        return imageService.batchDeleteImages(imageIds);
    }
}
