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

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

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

            double durationInSeconds = 0.0;
            String coverPath = null;
            
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(dest)) {
                grabber.start();
                durationInSeconds = grabber.getLengthInTime() / 1000000.0;
                
                if (durationInSeconds > 180.0) {
                    grabber.stop();
                    FileUtil.del(dest);
                    result.put("error", true);
                    result.put("message", "视频时长不能超过3分钟");
                    result.put("code", "DURATION_EXCEEDED");
                    return result;
                }
                
                if (Boolean.TRUE.equals(generateCover)) {
                    Frame frame = grabber.grabImage();
                    if (frame != null) {
                        try (Java2DFrameConverter converter = new Java2DFrameConverter()) {
                            BufferedImage bufferedImage = converter.getBufferedImage(frame);
                            String coverName = IdUtil.simpleUUID() + ".jpg";
                            String coverRelativePath = "/uploads/videos/cover/" + dateDir + "/" + coverName;
                            String coverAbsolutePath = UPLOAD_ROOT + "videos" + File.separator + "cover" + File.separator + dateDir + File.separator + coverName;
                            File coverFile = new File(coverAbsolutePath);
                            FileUtil.touch(coverFile);
                            ImageIO.write(bufferedImage, "jpg", coverFile);
                            coverPath = coverRelativePath;
                        }
                    }
                }
                grabber.stop();
            } catch (Exception e) {
                log.warn("处理视频失败: {}", dest.getAbsolutePath(), e);
            }

            Video video = new Video();
            video.setUserId(userId);
            video.setFileName(originalFilename);
            video.setFilePath(relativePath);
            video.setFileSize(file.getSize());
            video.setFileType(ext);
            video.setDuration(durationInSeconds);
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
        
        List<Map<String, Object>> list = new ArrayList<>();
        for (Video v : videoPage.getRecords()) {
            Map<String, Object> map = new HashMap<>();
            map.put("videoId", v.getId());
            map.put("fileName", v.getFileName());
            map.put("filePath", v.getFilePath());
            map.put("fileSize", v.getFileSize());
            map.put("fileType", v.getFileType());
            map.put("duration", v.getDuration());
            map.put("coverPath", v.getCoverPath());
            map.put("createdAt", DateUtil.format(v.getCreatedAt(), "yyyy-MM-dd HH:mm:ss"));
            list.add(map);
        }
        
        data.put("list", list);
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
