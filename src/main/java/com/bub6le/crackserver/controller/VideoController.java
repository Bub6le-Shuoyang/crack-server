package com.bub6le.crackserver.controller;

import com.bub6le.crackserver.common.Result;
import com.bub6le.crackserver.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "视频管理", description = "视频上传、列表、删除接口")
@RestController
@RequestMapping("/api/file")
@Slf4j
public class VideoController {

    @Autowired
    private VideoService videoService;

    @Operation(summary = "上传视频接口")
    @PostMapping("/upload-video")
    public Result uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "generateCover", defaultValue = "true") Boolean generateCover) {
        log.info("请求上传视频 fileName={}", file.getOriginalFilename());
        return videoService.uploadVideo(file, description, generateCover);
    }

    @Operation(summary = "获取用户视频列表接口")
    @GetMapping("/list-videos")
    public Result listVideos(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "fileType", required = false) String fileType) {
        log.info("请求获取视频列表 page={} pageSize={}", page, pageSize);
        return videoService.listVideos(page, pageSize, fileType);
    }

    @Operation(summary = "删除视频接口")
    @DeleteMapping("/delete-video/{videoId}")
    public Result deleteVideo(
            @PathVariable("videoId") Long videoId) {
        log.info("请求删除视频 videoId={}", videoId);
        return videoService.deleteVideo(videoId);
    }
}
