package com.bub6le.crackserver.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bub6le.crackserver.common.Result;
import com.bub6le.crackserver.common.UserContext;
import com.bub6le.crackserver.entity.Image;
import com.bub6le.crackserver.entity.ImageResult;
import com.bub6le.crackserver.mapper.ImageMapper;
import com.bub6le.crackserver.mapper.ImageResultMapper;
import com.bub6le.crackserver.service.ImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ImageServiceImpl implements ImageService {

    @Autowired
    private ImageMapper imageMapper;

    @Autowired
    private ImageResultMapper imageResultMapper;

    @Value("${app.upload.root:${user.dir}/uploads/}")
    private String uploadRoot;

    @Value("${app.base-url:http://127.0.0.1:7022}")
    private String baseUrl;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB
    private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "webp");

    @Override
    public Result uploadImage(MultipartFile file, String description) {
        Long userId = UserContext.getUserId();

        if (file.isEmpty()) {
            return Result.error("EMPTY_FILE", "请选择要上传的图片");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("FILE_TOO_LARGE", "图片大小不能超过10MB");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = FileNameUtil.extName(originalFilename).toLowerCase();
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            return Result.error("UNSUPPORTED_FILE_TYPE", "仅支持jpg/png/webp格式的图片");
        }

        try {
            String dateDir = DateUtil.format(new Date(), "yyyyMMdd");
            String newFileName = IdUtil.simpleUUID() + "." + ext;
            String relativePath = "/uploads/images/" + dateDir + "/" + newFileName;
            String absolutePath = uploadRoot + "images" + File.separator + dateDir + File.separator + newFileName;

            File dest = new File(absolutePath);
            FileUtil.touch(dest);
            file.transferTo(dest);

            Image image = new Image();
            image.setUserId(userId);
            image.setFileName(originalFilename);
            image.setFilePath(relativePath);
            image.setFileSize(file.getSize());
            image.setFileType(ext);
            
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

            Map<String, Object> data = new HashMap<>();
            data.put("imageId", image.getId());
            data.put("fileName", image.getFileName());
            data.put("filePath", image.getFilePath());
            data.put("fileSize", image.getFileSize());
            data.put("fileType", image.getFileType());
            data.put("width", image.getWidth());
            data.put("height", image.getHeight());
            
            return Result.success("图片上传成功").put("data", data);

        } catch (IOException e) {
            log.error("Image upload failed", e);
            return Result.error("UPLOAD_FAILED", "图片上传失败，请重试");
        }
    }

    @Override
    public Result listImages(int page, int pageSize, String fileType, String keyword, String label) {
        Long userId = UserContext.getUserId();

        Page<Image> p = new Page<>(page, pageSize);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Image> queryWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        queryWrapper.eq("userId", userId)
                    .eq("status", 1);
        
        if (fileType != null && !fileType.isEmpty()) {
            queryWrapper.eq("fileType", fileType);
        }

        if (keyword != null && !keyword.isEmpty()) {
            queryWrapper.like("fileName", keyword);
        }

        if (label != null && !label.isEmpty()) {
            queryWrapper.orderByDesc("(SELECT COUNT(1) FROM image_results ir WHERE ir.imageId = images.id AND ir.label = '" + label + "')");
        }

        queryWrapper.orderByDesc("createdAt");

        Page<Image> imagePage = imageMapper.selectPage(p, queryWrapper);

        Map<String, Object> data = new HashMap<>();
        data.put("list", imagePage.getRecords());
        data.put("total", imagePage.getTotal());
        data.put("page", imagePage.getCurrent());
        data.put("pageSize", imagePage.getSize());
        return Result.success().put("data", data);
    }

    @Override
    public Result deleteImage(Long imageId) {
        Long userId = UserContext.getUserId();

        Image image = imageMapper.selectById(imageId);
        if (image == null || !image.getUserId().equals(userId)) {
            return Result.error("IMAGE_NOT_FOUND", "图片不存在或无权限删除");
        }

        image.setStatus(0);
        image.setUpdatedAt(LocalDateTime.now());
        imageMapper.updateById(image);

        try {
            String storedPath = image.getFilePath();
            String fullPath = uploadRoot.replace("uploads/", "") + storedPath; 
            if (FileUtil.exist(fullPath)) {
                FileUtil.del(fullPath);
            }
        } catch (Exception e) {
            log.warn("Physical file deletion failed for imageId=" + imageId, e);
        }

        return Result.success("图片删除成功");
    }

    @Override
    public Result getImageIds() {
        Long userId = UserContext.getUserId();

        List<Image> images = imageMapper.selectList(new LambdaQueryWrapper<Image>()
                .eq(Image::getUserId, userId)
                .eq(Image::getStatus, 1)
                .select(Image::getId));
        
        List<Long> imageIds = images.stream().map(Image::getId).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("imageIds", imageIds);
        return Result.success().put("data", data);
    }

    @Override
    public Result getImageById(Long imageId) {
        Long userId = UserContext.getUserId();

        Image image = imageMapper.selectById(imageId);
        if (image == null || image.getStatus() == 0) {
            return Result.error("IMAGE_NOT_FOUND", "图片不存在");
        }

        if (!image.getUserId().equals(userId)) {
            return Result.error("NO_PERMISSION", "无权限访问该图片");
        }

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
        
        String accessUrl = baseUrl + image.getFilePath();
        data.put("accessUrl", accessUrl);
        
        List<ImageResult> results = imageResultMapper.selectList(new LambdaQueryWrapper<ImageResult>().eq(ImageResult::getImageId, imageId));
        if (results != null && !results.isEmpty()) {
            data.put("isDetected", true);
            ImageResult first = results.get(0);
            data.put("detectionDate", DateUtil.formatDateTime(DateUtil.date(java.sql.Timestamp.valueOf(first.getCreatedAt()))));
            data.put("modelName", first.getModelName());
            
            List<Map<String, Object>> detectionResults = new ArrayList<>();
            for (ImageResult ir : results) {
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
        
        return Result.success().put("data", data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result batchDeleteImages(List<Long> imageIds) {
        Long userId = UserContext.getUserId();

        if (imageIds == null || imageIds.isEmpty()) {
            return Result.error("EMPTY_IMAGE_IDS", "请选择要删除的图片");
        }

        // Fetch all images in one query to avoid N+1 problem
        List<Image> images = imageMapper.selectBatchIds(imageIds);
        
        List<Long> successIds = new ArrayList<>();
        List<Long> failIds = new ArrayList<>();
        Map<String, String> failReasons = new HashMap<>();

        for (Long id : imageIds) {
            Image image = images.stream().filter(img -> img.getId().equals(id)).findFirst().orElse(null);
            
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

            successIds.add(id);
            try {
                String fullPath = uploadRoot.replace("uploads/", "") + image.getFilePath();
                if (FileUtil.exist(fullPath)) {
                    FileUtil.del(fullPath);
                }
            } catch (Exception e) {
                log.warn("Physical file deletion failed for imageId=" + id, e);
            }
        }

        // Batch update database (logical delete)
        if (!successIds.isEmpty()) {
            imageMapper.update(null, new LambdaUpdateWrapper<Image>()
                    .in(Image::getId, successIds)
                    .set(Image::getStatus, 0)
                    .set(Image::getUpdatedAt, LocalDateTime.now()));
        }

        Result result = new Result();
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
