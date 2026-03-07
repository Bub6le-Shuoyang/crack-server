package com.bub6le.crackserver.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bub6le.crackserver.entity.UserToken;
import com.bub6le.crackserver.entity.Video;
import com.bub6le.crackserver.mapper.UserTokenMapper;
import com.bub6le.crackserver.mapper.VideoMapper;
import com.bub6le.crackserver.service.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class VideoServiceImpl implements VideoService {

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private UserTokenMapper userTokenMapper;

    private final String UPLOAD_ROOT = System.getProperty("user.dir") + File.separator + "uploads" + File.separator;

    private Long getUserIdByToken(String token) {
        if (token == null) return null;
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        UserToken userToken = userTokenMapper.selectOne(new LambdaQueryWrapper<UserToken>()
                .eq(UserToken::getToken, token)
                .eq(UserToken::getIsValid, 1)
                .gt(UserToken::getExpiredAt, LocalDateTime.now()));
        return userToken != null ? userToken.getUserId() : null;
    }

    @Override
    public Map<String, Object> uploadVideo(MultipartFile file, String description, Boolean generateCover, String token) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        if (file.isEmpty()) {
            result.put("error", true);
            result.put("message", "请选择要上传的视频");
            result.put("code", "EMPTY_FILE");
            return result;
        }

        // Check file size (100MB)
        if (file.getSize() > 100 * 1024 * 1024) {
            result.put("error", true);
            result.put("message", "视频大小不能超过100MB");
            result.put("code", "FILE_TOO_LARGE");
            return result;
        }

        // Check file type
        String originalFilename = file.getOriginalFilename();
        String ext = FileNameUtil.extName(originalFilename).toLowerCase();
        if (!Arrays.asList("mp4", "mov", "webm").contains(ext)) {
            result.put("error", true);
            result.put("message", "仅支持mp4/mov/webm格式的视频");
            result.put("code", "UNSUPPORTED_FILE_TYPE");
            return result;
        }

        try {
            String dateDir = DateUtil.format(new Date(), "yyyyMMdd");
            String newFileName = IdUtil.simpleUUID() + "." + ext;
            String relativePath = "/uploads/videos/" + dateDir + "/" + newFileName;
            String absolutePath = UPLOAD_ROOT + "videos" + File.separator + dateDir + File.separator + newFileName;

            File dest = new File(absolutePath);
            FileUtil.touch(dest);
            file.transferTo(dest);

            // Handle cover generation (Mock implementation)
            String coverPath = null;
            if (Boolean.TRUE.equals(generateCover)) {
                // TODO: Implement real video cover generation using ffmpeg or similar
                // For now, we skip it or use a default if available
                // coverPath = "/uploads/videos/cover/" + dateDir + "/" + IdUtil.simpleUUID() + ".jpg";
            }

            Video video = new Video();
            video.setUserId(userId);
            video.setFileName(originalFilename);
            video.setFilePath(relativePath);
            video.setFileSize(file.getSize());
            video.setFileType(ext);
            video.setDuration(0.0); // Placeholder
            video.setCoverPath(coverPath);
            video.setStatus(1);
            video.setCreatedAt(LocalDateTime.now());
            video.setUpdatedAt(LocalDateTime.now());
            videoMapper.insert(video);

            result.put("ok", true);
            result.put("message", "视频上传成功");
            Map<String, Object> data = new HashMap<>();
            data.put("videoId", video.getId());
            data.put("fileName", video.getFileName());
            data.put("filePath", video.getFilePath());
            data.put("fileSize", video.getFileSize());
            data.put("fileType", video.getFileType());
            data.put("duration", video.getDuration());
            data.put("coverPath", video.getCoverPath());
            result.put("data", data);

        } catch (IOException e) {
            log.error("Video upload failed", e);
            result.put("error", true);
            result.put("message", "视频上传失败，请重试");
            result.put("code", "UPLOAD_FAILED");
        }

        return result;
    }

    @Override
    public Map<String, Object> listVideos(int page, int pageSize, String fileType, String token) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        Page<Video> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getUserId, userId)
                .eq(Video::getStatus, 1)
                .orderByDesc(Video::getCreatedAt);

        if (fileType != null && !fileType.isEmpty()) {
            wrapper.eq(Video::getFileType, fileType);
        }

        Page<Video> videoPage = videoMapper.selectPage(p, wrapper);

        result.put("ok", true);
        Map<String, Object> data = new HashMap<>();
        data.put("list", videoPage.getRecords());
        data.put("total", videoPage.getTotal());
        data.put("page", videoPage.getCurrent());
        data.put("pageSize", videoPage.getSize());
        result.put("data", data);

        return result;
    }

    @Override
    public Map<String, Object> deleteVideo(Long videoId, String token) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        Video video = videoMapper.selectById(videoId);
        if (video == null || !video.getUserId().equals(userId)) {
            result.put("error", true);
            result.put("message", "视频不存在或无权限删除");
            result.put("code", "VIDEO_NOT_FOUND");
            return result;
        }

        // Logical delete
        video.setStatus(0);
        video.setUpdatedAt(LocalDateTime.now());
        videoMapper.updateById(video);

        // Physical delete
        try {
            String projectRoot = System.getProperty("user.dir");
            String fullPath = projectRoot + video.getFilePath();
            if (FileUtil.exist(fullPath)) {
                FileUtil.del(fullPath);
            }
            if (video.getCoverPath() != null) {
                String coverFullPath = projectRoot + video.getCoverPath();
                if (FileUtil.exist(coverFullPath)) {
                    FileUtil.del(coverFullPath);
                }
            }
        } catch (Exception e) {
            log.warn("Physical file deletion failed for videoId=" + videoId, e);
        }

        result.put("ok", true);
        result.put("message", "视频删除成功");
        return result;
    }
}
