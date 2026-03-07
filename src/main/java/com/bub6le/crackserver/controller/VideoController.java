package com.bub6le.crackserver.controller;

import com.bub6le.crackserver.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "视频管理", description = "视频上传、列表、删除接口")
@RestController
@RequestMapping("/api/file")
@Slf4j
public class VideoController {

    @Autowired
    private VideoService videoService;

    @Operation(summary = "上传视频接口")
    @PostMapping("/upload-video")
    public Map<String, Object> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "generateCover", defaultValue = "true") Boolean generateCover,
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("请求上传视频 fileName={}", file.getOriginalFilename());
        return videoService.uploadVideo(file, description, generateCover, token);
    }

    @Operation(summary = "获取用户视频列表接口")
    @GetMapping("/list-videos")
    public Map<String, Object> listVideos(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "fileType", required = false) String fileType,
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("请求获取视频列表 page={} pageSize={}", page, pageSize);
        return videoService.listVideos(page, pageSize, fileType, token);
    }

    @Operation(summary = "删除视频接口")
    @DeleteMapping("/delete-video/{videoId}")
    public Map<String, Object> deleteVideo(
            @PathVariable("videoId") Long videoId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("请求删除视频 videoId={}", videoId);
        return videoService.deleteVideo(videoId, token);
    }
}
