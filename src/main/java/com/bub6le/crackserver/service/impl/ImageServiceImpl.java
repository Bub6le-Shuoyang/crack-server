package com.bub6le.crackserver.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bub6le.crackserver.entity.Image;
import com.bub6le.crackserver.entity.UserToken;
import com.bub6le.crackserver.mapper.ImageMapper;
import com.bub6le.crackserver.mapper.UserTokenMapper;
import com.bub6le.crackserver.entity.ImageResult;
import com.bub6le.crackserver.mapper.ImageResultMapper;
import com.bub6le.crackserver.service.ImageService;
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
public class ImageServiceImpl implements ImageService {

    @Autowired
    private ImageMapper imageMapper;

    @Autowired
    private UserTokenMapper userTokenMapper;

    @Autowired
    private ImageResultMapper imageResultMapper;

    // Save path: current directory + /uploads/
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
    public Map<String, Object> uploadImage(MultipartFile file, String description, String token) {
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
            result.put("message", "请选择要上传的图片");
            result.put("code", "EMPTY_FILE");
            return result;
        }

        // Check file size (10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            result.put("error", true);
            result.put("message", "图片大小不能超过10MB");
            result.put("code", "FILE_TOO_LARGE");
            return result;
        }

        // Check file type
        String originalFilename = file.getOriginalFilename();
        String ext = FileNameUtil.extName(originalFilename).toLowerCase();
        if (!Arrays.asList("jpg", "jpeg", "png", "webp").contains(ext)) {
            result.put("error", true);
            result.put("message", "仅支持jpg/png/webp格式的图片");
            result.put("code", "UNSUPPORTED_FILE_TYPE");
            return result;
        }

        try {
            // Generate path: uploads/images/yyyyMMdd/filename
            String dateDir = DateUtil.format(new Date(), "yyyyMMdd");
            String newFileName = IdUtil.simpleUUID() + "." + ext;
            String relativePath = "/uploads/images/" + dateDir + "/" + newFileName;
            String absolutePath = UPLOAD_ROOT + "images" + File.separator + dateDir + File.separator + newFileName;

            File dest = new File(absolutePath);
            FileUtil.touch(dest); // Ensure parent directories exist
            file.transferTo(dest);

            // Save to DB
            Image image = new Image();
            image.setUserId(userId);
            image.setFileName(originalFilename);
            image.setFilePath(relativePath); // Store relative path for access
            image.setFileSize(file.getSize());
            image.setFileType(ext);
            // Get width/height if possible (skipping for now to avoid extra deps or complex logic, setting to 0 or null)
            try {
                java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(dest);
                if (bi != null) {
                    image.setWidth(bi.getWidth());
                    image.setHeight(bi.getHeight());
                }
            } catch (Exception e) {
                log.warn("Failed to read image dimensions", e);
            }

            image.setStatus(1);
            image.setCreatedAt(LocalDateTime.now());
            image.setUpdatedAt(LocalDateTime.now());
            imageMapper.insert(image);

            result.put("ok", true);
            result.put("message", "图片上传成功");
            Map<String, Object> data = new HashMap<>();
            data.put("imageId", image.getId());
            data.put("fileName", image.getFileName());
            data.put("filePath", image.getFilePath());
            data.put("fileSize", image.getFileSize());
            data.put("fileType", image.getFileType());
            data.put("width", image.getWidth());
            data.put("height", image.getHeight());
            result.put("data", data);

        } catch (IOException e) {
            log.error("Image upload failed", e);
            result.put("error", true);
            result.put("message", "图片上传失败，请重试");
            result.put("code", "UPLOAD_FAILED");
        }

        return result;
    }

    @Override
    public Map<String, Object> listImages(int page, int pageSize, String fileType, String keyword, String label, String token) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        Page<Image> p = new Page<>(page, pageSize);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Image> queryWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        queryWrapper.eq("userId", userId)
                    .eq("status", 1);
        
        if (fileType != null && !fileType.isEmpty()) {
            queryWrapper.eq("fileType", fileType);
        }

        if (keyword != null && !keyword.isEmpty()) {
            // Search function: Filter by fileName
            queryWrapper.like("fileName", keyword);
        }

        // Label prioritization logic: matches go first, others follow
        if (label != null && !label.isEmpty()) {
            // Sort by whether the image has the specified label (1 for match, 0 for no match)
            // In MySQL, DESC puts 1 (match) before 0 (no match)
            queryWrapper.orderByDesc("(SELECT COUNT(1) FROM image_results ir WHERE ir.imageId = images.id AND ir.label = '" + label + "')");
        }

        // Always use createdAt as a tie-breaker or primary sort
        queryWrapper.orderByDesc("createdAt");

        Page<Image> imagePage = imageMapper.selectPage(p, queryWrapper);

        result.put("ok", true);
        Map<String, Object> data = new HashMap<>();
        data.put("list", imagePage.getRecords());
        data.put("total", imagePage.getTotal());
        data.put("page", imagePage.getCurrent());
        data.put("pageSize", imagePage.getSize());
        result.put("data", data);

        return result;
    }

    @Override
    public Map<String, Object> deleteImage(Long imageId, String token) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        Image image = imageMapper.selectById(imageId);
        if (image == null || !image.getUserId().equals(userId)) {
            result.put("error", true);
            result.put("message", "图片不存在或无权限删除");
            result.put("code", "IMAGE_NOT_FOUND");
            return result;
        }

        // Logical delete in DB
        image.setStatus(0);
        image.setUpdatedAt(LocalDateTime.now());
        imageMapper.updateById(image);

        // Physical delete
        try {
            String storedPath = image.getFilePath();
            String relativePath = storedPath.startsWith("/") ? storedPath.substring(1) : storedPath;
            String projectRoot = System.getProperty("user.dir");
            String fullPath = projectRoot + storedPath; 
            
            if (FileUtil.exist(fullPath)) {
                FileUtil.del(fullPath);
            }
        } catch (Exception e) {
            log.warn("Physical file deletion failed for imageId=" + imageId, e);
        }

        result.put("ok", true);
        result.put("message", "图片删除成功");
        return result;
    }

    @Override
    public Map<String, Object> getImageIds(String token) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        List<Image> images = imageMapper.selectList(new LambdaQueryWrapper<Image>()
                .eq(Image::getUserId, userId)
                .eq(Image::getStatus, 1)
                .select(Image::getId));
        
        List<Long> imageIds = new ArrayList<>();
        for (Image img : images) {
            imageIds.add(img.getId());
        }

        result.put("ok", true);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("imageIds", imageIds);
        result.put("data", data);

        return result;
    }

    @Override
    public Map<String, Object> getImageById(Long imageId, String token) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        Image image = imageMapper.selectById(imageId);
        if (image == null || image.getStatus() == 0) {
            result.put("error", true);
            result.put("message", "图片不存在");
            result.put("code", "IMAGE_NOT_FOUND");
            return result;
        }

        if (!image.getUserId().equals(userId)) {
            result.put("error", true);
            result.put("message", "无权限访问该图片");
            result.put("code", "NO_PERMISSION");
            return result;
        }

        result.put("ok", true);
        Map<String, Object> data = new HashMap<>();
        data.put("imageId", image.getId());
        data.put("userId", image.getUserId());
        data.put("fileName", image.getFileName());
        data.put("filePath", image.getFilePath());
        data.put("fileSize", image.getFileSize());
        data.put("fileType", image.getFileType());
        data.put("width", image.getWidth());
        data.put("height", image.getHeight());
        data.put("status", image.getStatus());
        data.put("createdAt", DateUtil.formatDateTime(DateUtil.date(java.sql.Timestamp.valueOf(image.getCreatedAt()))));
        
        String accessUrl = "http://127.0.0.1:7022" + image.getFilePath();
        data.put("accessUrl", accessUrl);
        
        // 查询检测结果
        List<ImageResult> results = imageResultMapper.selectList(new LambdaQueryWrapper<ImageResult>().eq(ImageResult::getImageId, imageId));
        if (results != null && !results.isEmpty()) {
            data.put("isDetected", true);
            // Get common info from the first result (date, modelName)
            ImageResult first = results.get(0);
            data.put("detectionDate", DateUtil.formatDateTime(DateUtil.date(java.sql.Timestamp.valueOf(first.getCreatedAt()))));
            data.put("modelName", first.getModelName());
            
            List<Map<String, Object>> detectionResults = new ArrayList<>();
            for (ImageResult ir : results) {
                // 过滤掉 label="NORMAL" 的记录，不返回给前端作为检测框，但保留isDetected=true
                if ("NORMAL".equals(ir.getLabel())) {
                    continue;
                }
                Map<String, Object> map = new HashMap<>();
                map.put("label", ir.getLabel());
                map.put("score", ir.getScore());
                map.put("x", ir.getX());
                map.put("y", ir.getY());
                map.put("width", ir.getWidth());
                map.put("height", ir.getHeight());
                map.put("classId", ir.getClassId());
                detectionResults.add(map);
            }
            data.put("results", detectionResults);
        } else {
            data.put("isDetected", false);
            data.put("results", new ArrayList<>());
        }
        
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> batchDeleteImages(List<Long> imageIds, String token) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        if (imageIds == null || imageIds.isEmpty()) {
            result.put("error", true);
            result.put("message", "请选择要删除的图片");
            result.put("code", "EMPTY_IMAGE_IDS");
            return result;
        }

        List<Long> successIds = new ArrayList<>();
        List<Long> failIds = new ArrayList<>();
        Map<String, String> failReasons = new HashMap<>();

        for (Long id : imageIds) {
            Image image = imageMapper.selectById(id);
            if (image == null || image.getStatus() == 0) {
                failIds.add(id);
                failReasons.put(String.valueOf(id), "图片不存在");
                continue;
            }

            if (!image.getUserId().equals(userId)) {
                failIds.add(id);
                failReasons.put(String.valueOf(id), "无权限删除");
                continue;
            }

            try {
                // Logical
                image.setStatus(0);
                image.setUpdatedAt(LocalDateTime.now());
                imageMapper.updateById(image);

                // Physical
                String projectRoot = System.getProperty("user.dir");
                String fullPath = projectRoot + image.getFilePath();
                if (FileUtil.exist(fullPath)) {
                    FileUtil.del(fullPath);
                }
                successIds.add(id);
            } catch (Exception e) {
                log.error("Failed to delete image id=" + id, e);
                failIds.add(id);
                failReasons.put(String.valueOf(id), "删除失败: " + e.getMessage());
            }
        }

        if (successIds.isEmpty() && !failIds.isEmpty()) {
            result.put("ok", false);
            result.put("message", "批量删除失败");
        } else {
            result.put("ok", true);
            result.put("message", "批量删除成功");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("successIds", successIds);
        data.put("failIds", failIds);
        data.put("failReasons", failReasons);
        result.put("data", data);

        return result;
    }
}
