package com.bub6le.crackserver.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bub6le.crackserver.common.Result;
import com.bub6le.crackserver.common.UserContext;
import com.bub6le.crackserver.entity.Video;
import com.bub6le.crackserver.mapper.VideoMapper;
import com.bub6le.crackserver.service.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.upload.root:${user.dir}/uploads/}")
    private String uploadRoot;

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024L; // 100MB
    private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList("mp4", "mov", "webm");

    @Override
    public Result uploadVideo(MultipartFile file, String description, Boolean generateCover) {
        Long userId = UserContext.getUserId();

        if (file.isEmpty()) {
            return Result.error("EMPTY_FILE", "请选择要上传的视频");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("FILE_TOO_LARGE", "视频大小不能超过100MB");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = FileNameUtil.extName(originalFilename).toLowerCase();
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            return Result.error("UNSUPPORTED_FILE_TYPE", "仅支持mp4/mov/webm格式的视频");
        }

        try {
            String dateDir = DateUtil.format(new Date(), "yyyyMMdd");
            String newFileName = IdUtil.simpleUUID() + "." + ext;
            String relativePath = "/uploads/videos/" + dateDir + "/" + newFileName;
            String absolutePath = uploadRoot + "videos" + File.separator + dateDir + File.separator + newFileName;

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
                    return Result.error("DURATION_EXCEEDED", "视频时长不能超过3分钟");
                }
                
                if (Boolean.TRUE.equals(generateCover)) {
                    Frame frame = grabber.grabImage();
                    if (frame != null) {
                        try (Java2DFrameConverter converter = new Java2DFrameConverter()) {
                            BufferedImage bufferedImage = converter.getBufferedImage(frame);
                            String coverName = IdUtil.simpleUUID() + ".jpg";
                            String coverRelativePath = "/uploads/videos/cover/" + dateDir + "/" + coverName;
                            String coverAbsolutePath = uploadRoot + "videos" + File.separator + "cover" + File.separator + dateDir + File.separator + coverName;
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

            Map<String, Object> data = new HashMap<>();
            data.put("videoId", video.getId());
            data.put("fileName", video.getFileName());
            data.put("filePath", video.getFilePath());
            data.put("fileSize", video.getFileSize());
            data.put("fileType", video.getFileType());
            data.put("duration", video.getDuration());
            data.put("coverPath", video.getCoverPath());

            return Result.success("视频上传成功").put("data", data);

        } catch (IOException e) {
            log.error("Video upload failed", e);
            return Result.error("UPLOAD_FAILED", "视频上传失败，请重试");
        }
    }

    @Override
    public Result listVideos(int page, int pageSize, String fileType) {
        Long userId = UserContext.getUserId();

        Page<Video> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getUserId, userId)
                .eq(Video::getStatus, 1)
                .orderByDesc(Video::getCreatedAt);

        if (fileType != null && !fileType.isEmpty()) {
            wrapper.eq(Video::getFileType, fileType);
        }

        Page<Video> videoPage = videoMapper.selectPage(p, wrapper);

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

        return Result.success().put("data", data);
    }

    @Override
    public Result deleteVideo(Long videoId) {
        Long userId = UserContext.getUserId();

        Video video = videoMapper.selectById(videoId);
        if (video == null || !video.getUserId().equals(userId)) {
            return Result.error("VIDEO_NOT_FOUND", "视频不存在或无权限删除");
        }

        video.setStatus(0);
        video.setUpdatedAt(LocalDateTime.now());
        videoMapper.updateById(video);

        try {
            String fullPath = uploadRoot.replace("uploads/", "") + video.getFilePath();
            if (FileUtil.exist(fullPath)) {
                FileUtil.del(fullPath);
            }
            if (video.getCoverPath() != null) {
                String coverFullPath = uploadRoot.replace("uploads/", "") + video.getCoverPath();
                if (FileUtil.exist(coverFullPath)) {
                    FileUtil.del(coverFullPath);
                }
            }
        } catch (Exception e) {
            log.warn("Physical file deletion failed for videoId=" + videoId, e);
        }

        return Result.success("视频删除成功");
    }
}