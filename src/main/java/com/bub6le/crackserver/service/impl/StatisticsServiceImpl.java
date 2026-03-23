package com.bub6le.crackserver.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bub6le.crackserver.entity.*;
import com.bub6le.crackserver.mapper.*;
import com.bub6le.crackserver.service.StatisticsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final ImageMapper imageMapper;
    private final ImageResultMapper imageResultMapper;
    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final UserTokenMapper userTokenMapper;
    private final ObjectMapper objectMapper;

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

    private User getUserByToken(String token) {
        Long userId = getUserIdByToken(token);
        if (userId == null) return null;
        return userMapper.selectById(userId);
    }

    @Override
    public Map<String, Object> getImageOverview(String token) {
        Map<String, Object> result = new HashMap<>();
        User user = getUserByToken(token);
        if (user == null) {
            return error("Token无效或已过期");
        }

        boolean isAdmin = "admin".equals(user.getRoleId());
        
        // 1. 获取用户可见的图片
        LambdaQueryWrapper<Image> imgWrapper = new LambdaQueryWrapper<Image>().eq(Image::getStatus, 1);
        if (!isAdmin) {
            imgWrapper.eq(Image::getUserId, user.getId());
        }
        List<Image> images = imageMapper.selectList(imgWrapper);
        long totalImages = images.size();
        
        if (totalImages == 0) {
            return successImageOverviewEmpty();
        }

        List<Long> imageIds = images.stream().map(Image::getId).collect(Collectors.toList());
        
        // 2. 获取这些图片的检测结果
        List<ImageResult> allResults = new ArrayList<>();
        if (!imageIds.isEmpty()) {
            allResults = imageResultMapper.selectList(new LambdaQueryWrapper<ImageResult>()
                    .in(ImageResult::getImageId, imageIds));
        }

        Set<Long> detectedImageIds = new HashSet<>();
        Set<Long> anomalyImageIds = new HashSet<>();
        Map<String, Integer> anomalyTypeCount = new HashMap<>();
        
        for (ImageResult ir : allResults) {
            detectedImageIds.add(ir.getImageId());
            if (!"NORMAL".equals(ir.getLabel())) {
                anomalyImageIds.add(ir.getImageId());
                anomalyTypeCount.put(ir.getLabel(), anomalyTypeCount.getOrDefault(ir.getLabel(), 0) + 1);
            }
        }
        
        long totalAnomalyImages = anomalyImageIds.size();
        double anomalyRate = (double) totalAnomalyImages / totalImages;

        // 3. 计算 top 异常类型
        int totalAnomalies = anomalyTypeCount.values().stream().mapToInt(Integer::intValue).sum();
        List<Map<String, Object>> topAnomalyType = new ArrayList<>();
        
        anomalyTypeCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    Map<String, Object> typeData = new HashMap<>();
                    typeData.put("label", entry.getKey());
                    typeData.put("count", entry.getValue());
                    typeData.put("rate", totalAnomalies > 0 ? (double) entry.getValue() / totalAnomalies : 0.0);
                    topAnomalyType.add(typeData);
                });

        // 4. 计算近 7 天检测趋势
        List<Map<String, Object>> dailyTrend = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate today = LocalDate.now();
        
        // 预先按日期对结果进行分组统计，避免嵌套循环
        // 这里以检测结果的创建时间为准，代表"检测时间"
        Map<LocalDate, Long> dailyDetectCount = new HashMap<>();
        Map<LocalDate, Long> dailyAnomalyCount = new HashMap<>();
        
        // 我们需要按天统计去重后的图片ID
        Map<LocalDate, Set<Long>> dailyDetectImageIds = new HashMap<>();
        Map<LocalDate, Set<Long>> dailyAnomalyImageIds = new HashMap<>();

        for (ImageResult ir : allResults) {
            if (ir.getCreatedAt() != null) {
                // 注意：数据库里存的是 LocalDateTime 或者 Timestamp，需要转为 LocalDate
                // 有时候 mybatis-plus 查出来的 LocalDateTime 其实是 java.sql.Timestamp，这里保险起见处理一下
                LocalDate date;
                if (ir.getCreatedAt() instanceof java.time.LocalDateTime) {
                    date = ir.getCreatedAt().toLocalDate();
                } else {
                    date = new java.sql.Timestamp(((java.util.Date)(Object)ir.getCreatedAt()).getTime()).toLocalDateTime().toLocalDate();
                }

                dailyDetectImageIds.computeIfAbsent(date, k -> new HashSet<>()).add(ir.getImageId());
                if (!"NORMAL".equals(ir.getLabel())) {
                    dailyAnomalyImageIds.computeIfAbsent(date, k -> new HashSet<>()).add(ir.getImageId());
                }
            }
        }

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.format(formatter);
            
            long dailyTotal = dailyDetectImageIds.containsKey(date) ? dailyDetectImageIds.get(date).size() : 0;
            long dailyAnomaly = dailyAnomalyImageIds.containsKey(date) ? dailyAnomalyImageIds.get(date).size() : 0;
            
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", dateStr);
            dayData.put("total", dailyTotal);
            dayData.put("anomaly", dailyAnomaly);
            dailyTrend.add(dayData);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("totalImages", totalImages);
        data.put("totalAnomalyImages", totalAnomalyImages);
        data.put("anomalyRate", anomalyRate);
        data.put("topAnomalyType", topAnomalyType);
        data.put("dailyTrend", dailyTrend);

        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    private Map<String, Object> successImageOverviewEmpty() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("totalImages", 0);
        data.put("totalAnomalyImages", 0);
        data.put("anomalyRate", 0.0);
        data.put("topAnomalyType", new ArrayList<>());
        data.put("dailyTrend", new ArrayList<>());
        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> getVideoDetail(Long videoId, String token) {
        Map<String, Object> result = new HashMap<>();
        User user = getUserByToken(token);
        if (user == null) {
            return error("Token无效或已过期");
        }

        Video video = videoMapper.selectById(videoId);
        if (video == null || video.getStatus() == 0) {
            return error("视频不存在");
        }

        if (!"admin".equals(user.getRoleId()) && !video.getUserId().equals(user.getId())) {
            return error("无权限查看该视频");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("videoId", video.getId());
        data.put("fileName", video.getFileName());

        if (video.getIsDetected() == 1 && video.getDetectionResults() != null) {
            try {
                Map<String, Object> detectionData = objectMapper.readValue(video.getDetectionResults(), new TypeReference<Map<String, Object>>() {});
                
                int totalFrames = (int) detectionData.getOrDefault("totalFramesProcessed", 0);
                List<Map<String, Object>> anomalyFrames = (List<Map<String, Object>>) detectionData.getOrDefault("anomalyFrames", new ArrayList<>());
                int detectedFrames = anomalyFrames.size();
                
                data.put("totalFrames", totalFrames);
                data.put("detectedFrames", detectedFrames);
                data.put("anomalyTimeRatio", totalFrames > 0 ? (double) detectedFrames / totalFrames : 0.0);

                List<Map<String, Object>> topAnomalyFrames = new ArrayList<>();
                Map<String, Integer> typeDistMap = new HashMap<>();

                for (Map<String, Object> frame : anomalyFrames) {
                    double time = Double.parseDouble(frame.get("time").toString());
                    List<Map<String, Object>> detections = (List<Map<String, Object>>) frame.get("detections");
                    
                    if (detections != null && !detections.isEmpty()) {
                        // Find max score in this frame
                        Map<String, Object> topDet = detections.get(0);
                        double maxScore = Double.parseDouble(topDet.get("score").toString());
                        
                        for (Map<String, Object> det : detections) {
                            String label = (String) det.get("label");
                            typeDistMap.put(label, typeDistMap.getOrDefault(label, 0) + 1);
                            
                            double score = Double.parseDouble(det.get("score").toString());
                            if (score > maxScore) {
                                maxScore = score;
                                topDet = det;
                            }
                        }
                        
                        Map<String, Object> topFrameData = new HashMap<>();
                        topFrameData.put("time", time);
                        topFrameData.put("score", maxScore);
                        topFrameData.put("label", topDet.get("label"));
                        topAnomalyFrames.add(topFrameData);
                    }
                }

                // Sort top frames by score desc, take top 10
                topAnomalyFrames.sort((a, b) -> Double.compare((Double)b.get("score"), (Double)a.get("score")));
                if (topAnomalyFrames.size() > 10) {
                    topAnomalyFrames = topAnomalyFrames.subList(0, 10);
                }
                
                data.put("topAnomalyFrames", topAnomalyFrames);

                List<Map<String, Object>> anomalyTypeDistribution = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : typeDistMap.entrySet()) {
                    Map<String, Object> typeData = new HashMap<>();
                    typeData.put("label", entry.getKey());
                    typeData.put("count", entry.getValue());
                    anomalyTypeDistribution.add(typeData);
                }
                data.put("anomalyTypeDistribution", anomalyTypeDistribution);

            } catch (Exception e) {
                log.error("Failed to parse video detection results", e);
                return error("解析检测结果失败");
            }
        } else {
            data.put("totalFrames", 0);
            data.put("detectedFrames", 0);
            data.put("anomalyTimeRatio", 0.0);
            data.put("topAnomalyFrames", new ArrayList<>());
            data.put("anomalyTypeDistribution", new ArrayList<>());
        }

        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> getAdminTotalOverview(String startDate, String endDate, String token) {
        User user = getUserByToken(token);
        if (user == null || !"admin".equals(user.getRoleId())) {
            return error("无管理员权限");
        }

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = new HashMap<>();

        // Query total active users
        long totalUsers = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        data.put("totalUsers", totalUsers);

        // Query image and video base counts
        LambdaQueryWrapper<Image> imgWq = new LambdaQueryWrapper<Image>().eq(Image::getStatus, 1);
        LambdaQueryWrapper<Video> vidWq = new LambdaQueryWrapper<Video>().eq(Video::getStatus, 1);

        List<Image> allImages = imageMapper.selectList(imgWq);
        List<Video> allVideos = videoMapper.selectList(vidWq);

        data.put("totalImageDetect", allImages.stream().filter(i -> {
            return imageResultMapper.selectCount(new LambdaQueryWrapper<ImageResult>().eq(ImageResult::getImageId, i.getId())) > 0;
        }).count());
        
        data.put("totalVideoDetect", allVideos.stream().filter(v -> v.getIsDetected() == 1).count());

        // Count anomalies
        Set<Long> anomalyImageIds = new HashSet<>();
        List<ImageResult> irs = imageResultMapper.selectList(new QueryWrapper<>());
        for (ImageResult ir : irs) {
            if (!"NORMAL".equals(ir.getLabel())) {
                anomalyImageIds.add(ir.getImageId());
            }
        }
        data.put("totalAnomalyImages", anomalyImageIds.size());

        long totalAnomalyVideos = 0;
        for (Video v : allVideos) {
            if (v.getIsDetected() == 1 && v.getDetectionResults() != null) {
                try {
                    Map<String, Object> det = objectMapper.readValue(v.getDetectionResults(), new TypeReference<Map<String, Object>>() {});
                    int ac = (int) det.getOrDefault("anomalyCount", 0);
                    if (ac > 0) totalAnomalyVideos++;
                } catch (Exception e) {}
            }
        }
        data.put("totalAnomalyVideos", totalAnomalyVideos);

        // User rank (Mocking data logic for brevity, ideally should aggregate by userId)
        Map<Long, Integer> userAnomalyCount = new HashMap<>();
        for (Image img : allImages) {
            if (anomalyImageIds.contains(img.getId())) {
                userAnomalyCount.put(img.getUserId(), userAnomalyCount.getOrDefault(img.getUserId(), 0) + 1);
            }
        }
        for (Video v : allVideos) {
            if (v.getIsDetected() == 1 && v.getDetectionResults() != null) {
                try {
                    Map<String, Object> det = objectMapper.readValue(v.getDetectionResults(), new TypeReference<Map<String, Object>>() {});
                    int ac = (int) det.getOrDefault("anomalyCount", 0);
                    if (ac > 0) {
                        userAnomalyCount.put(v.getUserId(), userAnomalyCount.getOrDefault(v.getUserId(), 0) + 1);
                    }
                } catch (Exception e) {}
            }
        }

        List<Map<String, Object>> userRank = new ArrayList<>();
        userAnomalyCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    User u = userMapper.selectById(entry.getKey());
                    if (u != null) {
                        Map<String, Object> rm = new HashMap<>();
                        rm.put("userId", u.getId());
                        rm.put("userName", u.getName() != null ? u.getName() : u.getEmail());
                        rm.put("anomalyCount", entry.getValue());
                        userRank.add(rm);
                    }
                });
        data.put("userAnomalyRank", userRank);

        // Time Range Data (Mocking if dates are provided)
        Map<String, Object> timeRangeData = new HashMap<>();
        Map<String, Object> trImage = new HashMap<>();
        trImage.put("total", 0); trImage.put("anomaly", 0);
        Map<String, Object> trVideo = new HashMap<>();
        trVideo.put("total", 0); trVideo.put("anomaly", 0);
        
        // TODO: Filter lists by startDate and endDate
        
        timeRangeData.put("image", trImage);
        timeRangeData.put("video", trVideo);
        data.put("timeRangeData", timeRangeData);

        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> getAnomalyTypeDistribution(String mediaType, String startDate, String endDate, String token) {
        User user = getUserByToken(token);
        if (user == null) {
            return error("Token无效或已过期");
        }

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("mediaType", mediaType);

        Map<String, Integer> distMap = new HashMap<>();
        int[] confRanges = new int[3]; // [0.0-0.5, 0.5-0.8, 0.8-1.0]

        int totalAnomalies = 0;

        if ("image".equalsIgnoreCase(mediaType)) {
            LambdaQueryWrapper<ImageResult> qw = new LambdaQueryWrapper<>();
            qw.ne(ImageResult::getLabel, "NORMAL");
            // Add date filter and user filter if needed
            if (!"admin".equals(user.getRoleId())) {
                List<Long> userImageIds = imageMapper.selectList(new LambdaQueryWrapper<Image>().eq(Image::getUserId, user.getId()))
                        .stream().map(Image::getId).collect(Collectors.toList());
                if(userImageIds.isEmpty()) {
                    qw.eq(ImageResult::getId, -1L); // dummy false condition
                } else {
                    qw.in(ImageResult::getImageId, userImageIds);
                }
            }
            List<ImageResult> results = imageResultMapper.selectList(qw);
            totalAnomalies = results.size();
            for (ImageResult ir : results) {
                distMap.put(ir.getLabel(), distMap.getOrDefault(ir.getLabel(), 0) + 1);
                float s = ir.getScore();
                if (s >= 0.8f) confRanges[2]++;
                else if (s >= 0.5f) confRanges[1]++;
                else confRanges[0]++;
            }
        } else {
            // video logic
            LambdaQueryWrapper<Video> qw = new LambdaQueryWrapper<Video>().eq(Video::getIsDetected, 1);
            if (!"admin".equals(user.getRoleId())) {
                qw.eq(Video::getUserId, user.getId());
            }
            List<Video> videos = videoMapper.selectList(qw);
            for (Video v : videos) {
                if (v.getDetectionResults() != null) {
                    try {
                        Map<String, Object> det = objectMapper.readValue(v.getDetectionResults(), new TypeReference<Map<String, Object>>() {});
                        List<Map<String, Object>> frames = (List<Map<String, Object>>) det.getOrDefault("anomalyFrames", new ArrayList<>());
                        for (Map<String, Object> f : frames) {
                            List<Map<String, Object>> detections = (List<Map<String, Object>>) f.get("detections");
                            if (detections != null) {
                                for (Map<String, Object> d : detections) {
                                    totalAnomalies++;
                                    String label = (String) d.get("label");
                                    distMap.put(label, distMap.getOrDefault(label, 0) + 1);
                                    double s = Double.parseDouble(d.get("score").toString());
                                    if (s >= 0.8) confRanges[2]++;
                                    else if (s >= 0.5) confRanges[1]++;
                                    else confRanges[0]++;
                                }
                            }
                        }
                    } catch (Exception e) {}
                }
            }
        }

        data.put("totalAnomalies", totalAnomalies);

        List<Map<String, Object>> distribution = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : distMap.entrySet()) {
            Map<String, Object> dm = new HashMap<>();
            dm.put("label", entry.getKey());
            dm.put("count", entry.getValue());
            dm.put("percentage", totalAnomalies > 0 ? (entry.getValue() * 100.0 / totalAnomalies) : 0.0);
            distribution.add(dm);
        }
        data.put("distribution", distribution);

        List<Map<String, Object>> confDist = new ArrayList<>();
        Map<String, Object> c1 = new HashMap<>(); c1.put("range", "0.8-1.0"); c1.put("count", confRanges[2]); confDist.add(c1);
        Map<String, Object> c2 = new HashMap<>(); c2.put("range", "0.5-0.8"); c2.put("count", confRanges[1]); confDist.add(c2);
        Map<String, Object> c3 = new HashMap<>(); c3.put("range", "0.0-0.5"); c3.put("count", confRanges[0]); confDist.add(c3);
        data.put("confidenceDistribution", confDist);

        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("error", true);
        map.put("message", message);
        return map;
    }

    // ========== 新增的6个统计接口实现 ==========

    /**
     * 1. 存储空间统计
     */
    @Override
    public Map<String, Object> getStorageStats(String token) {
        User user = getUserByToken(token);
        if (user == null) {
            return error("Token无效或已过期");
        }

        boolean isAdmin = "admin".equals(user.getRoleId());
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = new HashMap<>();

        // 获取所有图片
        LambdaQueryWrapper<Image> imgWrapper = new LambdaQueryWrapper<Image>().eq(Image::getStatus, 1);
        if (!isAdmin) {
            imgWrapper.eq(Image::getUserId, user.getId());
        }
        List<Image> images = imageMapper.selectList(imgWrapper);

        // 获取所有视频
        LambdaQueryWrapper<Video> vidWrapper = new LambdaQueryWrapper<Video>().eq(Video::getStatus, 1);
        if (!isAdmin) {
            vidWrapper.eq(Video::getUserId, user.getId());
        }
        List<Video> videos = videoMapper.selectList(vidWrapper);

        // 计算总存储
        long imageStorage = images.stream().mapToLong(img -> img.getFileSize() != null ? img.getFileSize() : 0L).sum();
        long videoStorage = videos.stream().mapToLong(vid -> vid.getFileSize() != null ? vid.getFileSize() : 0L).sum();
        long totalStorage = imageStorage + videoStorage;

        data.put("totalStorage", totalStorage);
        data.put("totalStorageFormatted", formatFileSize(totalStorage));
        data.put("imageStorage", imageStorage);
        data.put("imageStorageFormatted", formatFileSize(imageStorage));
        data.put("videoStorage", videoStorage);
        data.put("videoStorageFormatted", formatFileSize(videoStorage));
        data.put("imageCount", images.size());
        data.put("videoCount", videos.size());

        // 存储占比
        if (totalStorage > 0) {
            data.put("imageStoragePercent", (imageStorage * 100.0 / totalStorage));
            data.put("videoStoragePercent", (videoStorage * 100.0 / totalStorage));
        } else {
            data.put("imageStoragePercent", 0.0);
            data.put("videoStoragePercent", 0.0);
        }

        // 管理员视角：各用户存储排名 Top 10
        if (isAdmin) {
            Map<Long, Long> userStorageMap = new HashMap<>();
            for (Image img : images) {
                userStorageMap.merge(img.getUserId(), img.getFileSize() != null ? img.getFileSize() : 0L, Long::sum);
            }
            for (Video vid : videos) {
                userStorageMap.merge(vid.getUserId(), vid.getFileSize() != null ? vid.getFileSize() : 0L, Long::sum);
            }

            List<Map<String, Object>> storageByUser = new ArrayList<>();
            userStorageMap.entrySet().stream()
                    .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> {
                        User u = userMapper.selectById(entry.getKey());
                        if (u != null) {
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("userId", u.getId());
                            userData.put("userName", u.getName() != null ? u.getName() : u.getEmail());
                            userData.put("storage", entry.getValue());
                            userData.put("storageFormatted", formatFileSize(entry.getValue()));
                            userData.put("percentage", totalStorage > 0 ? (entry.getValue() * 100.0 / totalStorage) : 0.0);
                            storageByUser.add(userData);
                        }
                    });
            data.put("storageByUser", storageByUser);
        }

        // 近30天存储增长趋势
        List<Map<String, Object>> storageTrend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        // 按日期统计上传的文件大小
        Map<LocalDate, Long> dailyStorage = new HashMap<>();
        for (Image img : images) {
            if (img.getCreatedAt() != null) {
                LocalDate date = img.getCreatedAt().toLocalDate();
                dailyStorage.merge(date, img.getFileSize() != null ? img.getFileSize() : 0L, Long::sum);
            }
        }
        for (Video vid : videos) {
            if (vid.getCreatedAt() != null) {
                LocalDate date = vid.getCreatedAt().toLocalDate();
                dailyStorage.merge(date, vid.getFileSize() != null ? vid.getFileSize() : 0L, Long::sum);
            }
        }

        // 累计存储
        long cumulativeStorage = 0;
        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            cumulativeStorage += dailyStorage.getOrDefault(date, 0L);
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.format(formatter));
            dayData.put("total", cumulativeStorage);
            dayData.put("dailyNew", dailyStorage.getOrDefault(date, 0L));
            storageTrend.add(dayData);
        }
        data.put("storageTrend", storageTrend);

        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    /**
     * 2. 用户个人统计
     */
    @Override
    public Map<String, Object> getPersonalStats(String token) {
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            return error("Token无效或已过期");
        }

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = new HashMap<>();

        // 图片统计
        List<Image> myImages = imageMapper.selectList(new LambdaQueryWrapper<Image>()
                .eq(Image::getUserId, userId)
                .eq(Image::getStatus, 1));
        data.put("totalImages", myImages.size());

        // 视频统计
        List<Video> myVideos = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                .eq(Video::getUserId, userId)
                .eq(Video::getStatus, 1));
        data.put("totalVideos", myVideos.size());

        // 存储占用
        long imageStorage = myImages.stream().mapToLong(img -> img.getFileSize() != null ? img.getFileSize() : 0L).sum();
        long videoStorage = myVideos.stream().mapToLong(vid -> vid.getFileSize() != null ? vid.getFileSize() : 0L).sum();
        long totalStorage = imageStorage + videoStorage;
        data.put("totalStorage", totalStorage);
        data.put("totalStorageFormatted", formatFileSize(totalStorage));

        // 检测量统计
        List<Long> myImageIds = myImages.stream().map(Image::getId).collect(Collectors.toList());
        long detectedImages = 0;
        if (!myImageIds.isEmpty()) {
            // 使用 Set 去重统计有检测结果的图片ID
            List<ImageResult> imageResults = imageResultMapper.selectList(new LambdaQueryWrapper<ImageResult>()
                    .in(ImageResult::getImageId, myImageIds)
                    .select(ImageResult::getImageId));
            Set<Long> detectedImageIdSet = new HashSet<>();
            for (ImageResult ir : imageResults) {
                detectedImageIdSet.add(ir.getImageId());
            }
            detectedImages = detectedImageIdSet.size();
        }
        long detectedVideos = myVideos.stream().filter(v -> v.getIsDetected() != null && v.getIsDetected() == 1).count();
        data.put("totalDetections", detectedImages + detectedVideos);
        data.put("detectedImages", detectedImages);
        data.put("detectedVideos", detectedVideos);

        // 异常统计
        Set<Long> anomalyImageIds = new HashSet<>();
        if (!myImageIds.isEmpty()) {
            List<ImageResult> results = imageResultMapper.selectList(new LambdaQueryWrapper<ImageResult>()
                    .in(ImageResult::getImageId, myImageIds)
                    .ne(ImageResult::getLabel, "NORMAL"));
            for (ImageResult ir : results) {
                anomalyImageIds.add(ir.getImageId());
            }
        }

        long anomalyVideos = 0;
        for (Video v : myVideos) {
            if (v.getIsDetected() != null && v.getIsDetected() == 1 && v.getDetectionResults() != null) {
                try {
                    Map<String, Object> det = objectMapper.readValue(v.getDetectionResults(), new TypeReference<Map<String, Object>>() {});
                    int ac = (int) det.getOrDefault("anomalyCount", 0);
                    if (ac > 0) anomalyVideos++;
                } catch (Exception e) {}
            }
        }

        data.put("anomalyCount", anomalyImageIds.size() + (int) anomalyVideos);
        data.put("anomalyImages", anomalyImageIds.size());
        data.put("anomalyVideos", anomalyVideos);

        // 异常率
        long totalDetected = detectedImages + detectedVideos;
        long totalAnomaly = anomalyImageIds.size() + anomalyVideos;
        data.put("anomalyRate", totalDetected > 0 ? (double) totalAnomaly / totalDetected : 0.0);

        // 近7天活动
        List<Map<String, Object>> recentActivity = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        Map<LocalDate, Integer> dailyUploads = new HashMap<>();
        Map<LocalDate, Integer> dailyDetections = new HashMap<>();

        for (Image img : myImages) {
            if (img.getCreatedAt() != null) {
                LocalDate date = img.getCreatedAt().toLocalDate();
                dailyUploads.merge(date, 1, Integer::sum);
            }
        }
        for (Video vid : myVideos) {
            if (vid.getCreatedAt() != null) {
                LocalDate date = vid.getCreatedAt().toLocalDate();
                dailyUploads.merge(date, 1, Integer::sum);
            }
        }

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.format(formatter));
            dayData.put("uploads", dailyUploads.getOrDefault(date, 0));
            dayData.put("detections", dailyDetections.getOrDefault(date, 0));
            recentActivity.add(dayData);
        }
        data.put("recentActivity", recentActivity);

        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    /**
     * 3. 视频检测概览
     */
    @Override
    public Map<String, Object> getVideoOverview(String token) {
        User user = getUserByToken(token);
        if (user == null) {
            return error("Token无效或已过期");
        }

        boolean isAdmin = "admin".equals(user.getRoleId());
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = new HashMap<>();

        // 获取视频列表
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>().eq(Video::getStatus, 1);
        if (!isAdmin) {
            wrapper.eq(Video::getUserId, user.getId());
        }
        List<Video> videos = videoMapper.selectList(wrapper);

        long totalVideos = videos.size();
        data.put("totalVideos", totalVideos);

        if (totalVideos == 0) {
            data.put("totalDuration", 0.0);
            data.put("totalDurationFormatted", "0:00");
            data.put("detectedVideos", 0);
            data.put("anomalyVideos", 0);
            data.put("anomalyVideoRate", 0.0);
            data.put("avgAnomalyTimeRatio", 0.0);
            data.put("topAnomalyTypes", new ArrayList<>());
            data.put("dailyTrend", new ArrayList<>());
            result.put("ok", true);
            result.put("data", data);
            return result;
        }

        // 总时长
        double totalDuration = videos.stream()
                .mapToDouble(v -> v.getDuration() != null ? v.getDuration() : 0.0)
                .sum();
        data.put("totalDuration", totalDuration);
        data.put("totalDurationFormatted", formatDuration(totalDuration));

        // 已检测视频数
        long detectedVideos = videos.stream()
                .filter(v -> v.getIsDetected() != null && v.getIsDetected() == 1)
                .count();
        data.put("detectedVideos", detectedVideos);

        // 异常视频统计
        Map<String, Integer> anomalyTypeCount = new HashMap<>();
        long anomalyVideos = 0;
        double totalAnomalyTimeRatio = 0.0;

        for (Video v : videos) {
            if (v.getIsDetected() != null && v.getIsDetected() == 1 && v.getDetectionResults() != null) {
                try {
                    Map<String, Object> det = objectMapper.readValue(v.getDetectionResults(), new TypeReference<Map<String, Object>>() {});
                    int anomalyCount = (int) det.getOrDefault("anomalyCount", 0);
                    if (anomalyCount > 0) {
                        anomalyVideos++;
                        int totalFrames = (int) det.getOrDefault("totalFramesProcessed", 1);
                        totalAnomalyTimeRatio += (double) anomalyCount / totalFrames;
                    }

                    // 统计异常类型
                    List<Map<String, Object>> frames = (List<Map<String, Object>>) det.getOrDefault("anomalyFrames", new ArrayList<>());
                    for (Map<String, Object> frame : frames) {
                        List<Map<String, Object>> detections = (List<Map<String, Object>>) frame.get("detections");
                        if (detections != null) {
                            for (Map<String, Object> d : detections) {
                                String label = (String) d.get("label");
                                anomalyTypeCount.merge(label, 1, Integer::sum);
                            }
                        }
                    }
                } catch (Exception e) {}
            }
        }

        data.put("anomalyVideos", anomalyVideos);
        data.put("anomalyVideoRate", detectedVideos > 0 ? (double) anomalyVideos / detectedVideos : 0.0);
        data.put("avgAnomalyTimeRatio", detectedVideos > 0 ? totalAnomalyTimeRatio / detectedVideos : 0.0);

        // Top 异常类型
        int totalAnomalies = anomalyTypeCount.values().stream().mapToInt(Integer::intValue).sum();
        List<Map<String, Object>> topAnomalyTypes = new ArrayList<>();
        anomalyTypeCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    Map<String, Object> typeData = new HashMap<>();
                    typeData.put("label", entry.getKey());
                    typeData.put("count", entry.getValue());
                    typeData.put("percentage", totalAnomalies > 0 ? (entry.getValue() * 100.0 / totalAnomalies) : 0.0);
                    topAnomalyTypes.add(typeData);
                });
        data.put("topAnomalyTypes", topAnomalyTypes);

        // 近7天检测趋势
        List<Map<String, Object>> dailyTrend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        Map<LocalDate, Integer> dailyDetect = new HashMap<>();
        Map<LocalDate, Integer> dailyAnomaly = new HashMap<>();

        for (Video v : videos) {
            if (v.getCreatedAt() != null) {
                LocalDate date = v.getCreatedAt().toLocalDate();
                if (v.getIsDetected() != null && v.getIsDetected() == 1) {
                    dailyDetect.merge(date, 1, Integer::sum);
                    // 检查是否异常
                    if (v.getDetectionResults() != null) {
                        try {
                            Map<String, Object> det = objectMapper.readValue(v.getDetectionResults(), new TypeReference<Map<String, Object>>() {});
                            int ac = (int) det.getOrDefault("anomalyCount", 0);
                            if (ac > 0) {
                                dailyAnomaly.merge(date, 1, Integer::sum);
                            }
                        } catch (Exception e) {}
                    }
                }
            }
        }

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.format(formatter));
            dayData.put("total", dailyDetect.getOrDefault(date, 0));
            dayData.put("anomaly", dailyAnomaly.getOrDefault(date, 0));
            dailyTrend.add(dayData);
        }
        data.put("dailyTrend", dailyTrend);

        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    /**
     * 4. 文件类型分布
     */
    @Override
    public Map<String, Object> getFileTypeDistribution(String token) {
        User user = getUserByToken(token);
        if (user == null) {
            return error("Token无效或已过期");
        }

        boolean isAdmin = "admin".equals(user.getRoleId());
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = new HashMap<>();

        // 图片类型统计
        LambdaQueryWrapper<Image> imgWrapper = new LambdaQueryWrapper<Image>().eq(Image::getStatus, 1);
        if (!isAdmin) {
            imgWrapper.eq(Image::getUserId, user.getId());
        }
        List<Image> images = imageMapper.selectList(imgWrapper);

        Map<String, Integer> imageTypeCount = new HashMap<>();
        Map<String, Long> imageTypeSize = new HashMap<>();
        for (Image img : images) {
            String fileType = normalizeFileType(img.getFileType());
            imageTypeCount.merge(fileType, 1, Integer::sum);
            imageTypeSize.merge(fileType, img.getFileSize() != null ? img.getFileSize() : 0L, Long::sum);
        }

        int totalImages = images.size();
        long totalImageSize = imageTypeSize.values().stream().mapToLong(Long::longValue).sum();

        List<Map<String, Object>> imageTypeDistribution = new ArrayList<>();
        imageTypeCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    Map<String, Object> typeData = new HashMap<>();
                    typeData.put("type", entry.getKey());
                    typeData.put("count", entry.getValue());
                    typeData.put("percentage", totalImages > 0 ? (entry.getValue() * 100.0 / totalImages) : 0.0);
                    typeData.put("totalSize", imageTypeSize.getOrDefault(entry.getKey(), 0L));
                    typeData.put("totalSizeFormatted", formatFileSize(imageTypeSize.getOrDefault(entry.getKey(), 0L)));
                    imageTypeDistribution.add(typeData);
                });
        data.put("imageTypes", imageTypeDistribution);
        data.put("totalImages", totalImages);
        data.put("totalImageSize", totalImageSize);
        data.put("totalImageSizeFormatted", formatFileSize(totalImageSize));

        // 视频类型统计
        LambdaQueryWrapper<Video> vidWrapper = new LambdaQueryWrapper<Video>().eq(Video::getStatus, 1);
        if (!isAdmin) {
            vidWrapper.eq(Video::getUserId, user.getId());
        }
        List<Video> videos = videoMapper.selectList(vidWrapper);

        Map<String, Integer> videoTypeCount = new HashMap<>();
        Map<String, Long> videoTypeSize = new HashMap<>();
        for (Video vid : videos) {
            String fileType = normalizeFileType(vid.getFileType());
            videoTypeCount.merge(fileType, 1, Integer::sum);
            videoTypeSize.merge(fileType, vid.getFileSize() != null ? vid.getFileSize() : 0L, Long::sum);
        }

        int totalVideos = videos.size();
        long totalVideoSize = videoTypeSize.values().stream().mapToLong(Long::longValue).sum();

        List<Map<String, Object>> videoTypeDistribution = new ArrayList<>();
        videoTypeCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    Map<String, Object> typeData = new HashMap<>();
                    typeData.put("type", entry.getKey());
                    typeData.put("count", entry.getValue());
                    typeData.put("percentage", totalVideos > 0 ? (entry.getValue() * 100.0 / totalVideos) : 0.0);
                    typeData.put("totalSize", videoTypeSize.getOrDefault(entry.getKey(), 0L));
                    typeData.put("totalSizeFormatted", formatFileSize(videoTypeSize.getOrDefault(entry.getKey(), 0L)));
                    videoTypeDistribution.add(typeData);
                });
        data.put("videoTypes", videoTypeDistribution);
        data.put("totalVideos", totalVideos);
        data.put("totalVideoSize", totalVideoSize);
        data.put("totalVideoSizeFormatted", formatFileSize(totalVideoSize));

        // 总计
        data.put("totalFiles", totalImages + totalVideos);
        data.put("totalStorage", totalImageSize + totalVideoSize);
        data.put("totalStorageFormatted", formatFileSize(totalImageSize + totalVideoSize));

        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    /**
     * 5. 实时监控面板
     */
    @Override
    public Map<String, Object> getRealtimeDashboard(String token) {
        User user = getUserByToken(token);
        if (user == null || !"admin".equals(user.getRoleId())) {
            return error("无管理员权限");
        }

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = new HashMap<>();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        // 今日新增用户
        long newUsersToday = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .ge(User::getCreatedAt, todayStart));
        data.put("newUsersToday", newUsersToday);

        // 今日活跃用户（今日有登录记录）
        long activeUsersToday = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .ge(User::getLastLoginAt, todayStart));
        data.put("activeUsersToday", activeUsersToday);

        // 今日上传图片数
        long imagesUploadedToday = imageMapper.selectCount(new LambdaQueryWrapper<Image>()
                .eq(Image::getStatus, 1)
                .ge(Image::getCreatedAt, todayStart));
        data.put("imagesUploadedToday", imagesUploadedToday);

        // 今日上传视频数
        long videosUploadedToday = videoMapper.selectCount(new LambdaQueryWrapper<Video>()
                .eq(Video::getStatus, 1)
                .ge(Video::getCreatedAt, todayStart));
        data.put("videosUploadedToday", videosUploadedToday);

        // 今日检测图片数
        long imagesDetectedToday = imageResultMapper.selectList(new LambdaQueryWrapper<ImageResult>()
                .ge(ImageResult::getCreatedAt, todayStart))
                .stream()
                .map(ImageResult::getImageId)
                .distinct()
                .count();
        data.put("imagesDetectedToday", imagesDetectedToday);

        // 今日检测视频数
        long videosDetectedToday = videoMapper.selectCount(new LambdaQueryWrapper<Video>()
                .eq(Video::getIsDetected, 1)
                .ge(Video::getUpdatedAt, todayStart));
        data.put("videosDetectedToday", videosDetectedToday);

        // 今日发现异常数
        long anomaliesToday = imageResultMapper.selectCount(new LambdaQueryWrapper<ImageResult>()
                .ne(ImageResult::getLabel, "NORMAL")
                .ge(ImageResult::getCreatedAt, todayStart));
        data.put("anomaliesToday", anomaliesToday);

        // 今日存储增量
        long storageIncreaseToday = 0;
        List<Image> todayImages = imageMapper.selectList(new LambdaQueryWrapper<Image>()
                .eq(Image::getStatus, 1)
                .ge(Image::getCreatedAt, todayStart));
        List<Video> todayVideos = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                .eq(Video::getStatus, 1)
                .ge(Video::getCreatedAt, todayStart));
        storageIncreaseToday = todayImages.stream().mapToLong(img -> img.getFileSize() != null ? img.getFileSize() : 0L).sum()
                + todayVideos.stream().mapToLong(vid -> vid.getFileSize() != null ? vid.getFileSize() : 0L).sum();
        data.put("storageIncreaseToday", storageIncreaseToday);
        data.put("storageIncreaseTodayFormatted", formatFileSize(storageIncreaseToday));

        // 实时时间戳
        data.put("timestamp", now.toString());

        // 今日每小时活动趋势
        List<Map<String, Object>> hourlyTrend = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            LocalDateTime hourStart = todayStart.plusHours(hour);
            LocalDateTime hourEnd = hourStart.plusHours(1);
            if (hourEnd.isAfter(now)) break;

            long hourUploads = imageMapper.selectCount(new LambdaQueryWrapper<Image>()
                    .ge(Image::getCreatedAt, hourStart)
                    .lt(Image::getCreatedAt, hourEnd))
                    + videoMapper.selectCount(new LambdaQueryWrapper<Video>()
                    .ge(Video::getCreatedAt, hourStart)
                    .lt(Video::getCreatedAt, hourEnd));

            long hourDetections = imageResultMapper.selectCount(new LambdaQueryWrapper<ImageResult>()
                    .ge(ImageResult::getCreatedAt, hourStart)
                    .lt(ImageResult::getCreatedAt, hourEnd));

            Map<String, Object> hourData = new HashMap<>();
            hourData.put("hour", hour);
            hourData.put("uploads", hourUploads);
            hourData.put("detections", hourDetections);
            hourlyTrend.add(hourData);
        }
        data.put("hourlyTrend", hourlyTrend);

        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    /**
     * 6. 异常热点时段分析
     */
    @Override
    public Map<String, Object> getAnomalyTimeAnalysis(String token) {
        User user = getUserByToken(token);
        if (user == null) {
            return error("Token无效或已过期");
        }

        boolean isAdmin = "admin".equals(user.getRoleId());
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = new HashMap<>();

        // 获取图片和检测结果
        LambdaQueryWrapper<Image> imgWrapper = new LambdaQueryWrapper<Image>().eq(Image::getStatus, 1);
        if (!isAdmin) {
            imgWrapper.eq(Image::getUserId, user.getId());
        }
        List<Image> images = imageMapper.selectList(imgWrapper);
        List<Long> imageIds = images.stream().map(Image::getId).collect(Collectors.toList());

        // 按上传时段统计
        int[] hourlyUploads = new int[24];
        int[] hourlyAnomalies = new int[24];

        // 按上传时间分组图片
        Map<Integer, List<Image>> imagesByHour = new HashMap<>();
        for (Image img : images) {
            if (img.getCreatedAt() != null) {
                int hour = img.getCreatedAt().getHour();
                imagesByHour.computeIfAbsent(hour, k -> new ArrayList<>()).add(img);
                hourlyUploads[hour]++;
            }
        }

        // 统计每个时段的异常
        if (!imageIds.isEmpty()) {
            List<ImageResult> results = imageResultMapper.selectList(new LambdaQueryWrapper<ImageResult>()
                    .in(ImageResult::getImageId, imageIds)
                    .ne(ImageResult::getLabel, "NORMAL"));

            // 按 imageId 分组
            Map<Long, Image> imageMap = images.stream().collect(Collectors.toMap(Image::getId, i -> i));

            for (ImageResult ir : results) {
                Image img = imageMap.get(ir.getImageId());
                if (img != null && img.getCreatedAt() != null) {
                    int hour = img.getCreatedAt().getHour();
                    hourlyAnomalies[hour]++;
                }
            }
        }

        // 构建时段分布数据
        List<Map<String, Object>> hourlyDistribution = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            Map<String, Object> hourData = new HashMap<>();
            hourData.put("hour", hour);
            hourData.put("hourLabel", String.format("%02d:00-%02d:00", hour, (hour + 1) % 24));
            hourData.put("uploads", hourlyUploads[hour]);
            hourData.put("anomalies", hourlyAnomalies[hour]);
            hourData.put("anomalyRate", hourlyUploads[hour] > 0 ? (double) hourlyAnomalies[hour] / hourlyUploads[hour] : 0.0);
            hourlyDistribution.add(hourData);
        }
        data.put("hourlyDistribution", hourlyDistribution);

        // 找出异常率最高的时段
        int peakHour = 0;
        double peakAnomalyRate = 0;
        for (int hour = 0; hour < 24; hour++) {
            double rate = hourlyUploads[hour] > 0 ? (double) hourlyAnomalies[hour] / hourlyUploads[hour] : 0;
            if (rate > peakAnomalyRate) {
                peakAnomalyRate = rate;
                peakHour = hour;
            }
        }
        data.put("peakAnomalyHour", peakHour);
        data.put("peakAnomalyHourLabel", String.format("%02d:00-%02d:00", peakHour, (peakHour + 1) % 24));
        data.put("peakAnomalyRate", peakAnomalyRate);

        // 按星期统计
        int[] weekdayUploads = new int[7];
        int[] weekdayAnomalies = new int[7];

        Map<Integer, List<Image>> imagesByWeekday = new HashMap<>();
        for (Image img : images) {
            if (img.getCreatedAt() != null) {
                int weekday = img.getCreatedAt().getDayOfWeek().getValue() % 7; // 0=周日, 1-6=周一到周六
                imagesByWeekday.computeIfAbsent(weekday, k -> new ArrayList<>()).add(img);
                weekdayUploads[weekday]++;
            }
        }

        if (!imageIds.isEmpty()) {
            List<ImageResult> results = imageResultMapper.selectList(new LambdaQueryWrapper<ImageResult>()
                    .in(ImageResult::getImageId, imageIds)
                    .ne(ImageResult::getLabel, "NORMAL"));

            Map<Long, Image> imageMap = images.stream().collect(Collectors.toMap(Image::getId, i -> i));

            for (ImageResult ir : results) {
                Image img = imageMap.get(ir.getImageId());
                if (img != null && img.getCreatedAt() != null) {
                    int weekday = img.getCreatedAt().getDayOfWeek().getValue() % 7;
                    weekdayAnomalies[weekday]++;
                }
            }
        }

        String[] weekdayNames = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        List<Map<String, Object>> weekdayDistribution = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("weekday", day);
            dayData.put("weekdayName", weekdayNames[day]);
            dayData.put("uploads", weekdayUploads[day]);
            dayData.put("anomalies", weekdayAnomalies[day]);
            dayData.put("anomalyRate", weekdayUploads[day] > 0 ? (double) weekdayAnomalies[day] / weekdayUploads[day] : 0.0);
            weekdayDistribution.add(dayData);
        }
        data.put("weekdayDistribution", weekdayDistribution);

        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    /**
     * 7. 原始检测数据
     */
    @Override
    public Map<String, Object> getRawDetectionData(String token) {
        User user = getUserByToken(token);
        if (user == null) {
            return error("Token无效或已过期");
        }

        boolean isAdmin = "admin".equals(user.getRoleId());
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> data = new HashMap<>();

        // 最近10条图片检测结果
        LambdaQueryWrapper<Image> imgWrapper = new LambdaQueryWrapper<Image>()
                .eq(Image::getStatus, 1)
                .orderByDesc(Image::getCreatedAt)
                .last("LIMIT 10");
        if (!isAdmin) {
            imgWrapper.eq(Image::getUserId, user.getId());
        }
        List<Image> recentImages = imageMapper.selectList(imgWrapper);

        List<Map<String, Object>> recentImageDetections = new ArrayList<>();
        for (Image img : recentImages) {
            Map<String, Object> imgData = new HashMap<>();
            imgData.put("id", img.getId());
            imgData.put("fileName", img.getFileName());
            imgData.put("fileSize", img.getFileSize());
            imgData.put("fileSizeFormatted", formatFileSize(img.getFileSize() != null ? img.getFileSize() : 0L));
            imgData.put("createdAt", img.getCreatedAt() != null ? img.getCreatedAt().toString() : null);

            // 获取上传用户信息
            User uploader = userMapper.selectById(img.getUserId());
            imgData.put("uploaderId", img.getUserId());
            imgData.put("uploaderName", uploader != null ? (uploader.getName() != null ? uploader.getName() : uploader.getEmail()) : "未知");

            // 获取检测结果
            List<ImageResult> results = imageResultMapper.selectList(new LambdaQueryWrapper<ImageResult>()
                    .eq(ImageResult::getImageId, img.getId()));
            if (!results.isEmpty()) {
                imgData.put("isDetected", true);
                imgData.put("detectionCount", results.size());
                // 异常数量
                long anomalyCount = results.stream().filter(r -> !"NORMAL".equals(r.getLabel())).count();
                imgData.put("anomalyCount", anomalyCount);
                // 最高置信度异常
                ImageResult topResult = results.stream()
                        .filter(r -> !"NORMAL".equals(r.getLabel()))
                        .max((a, b) -> Float.compare(a.getScore(), b.getScore()))
                        .orElse(null);
                if (topResult != null) {
                    imgData.put("topAnomalyLabel", topResult.getLabel());
                    imgData.put("topAnomalyScore", topResult.getScore());
                } else {
                    imgData.put("topAnomalyLabel", "NORMAL");
                    imgData.put("topAnomalyScore", results.get(0).getScore());
                }
            } else {
                imgData.put("isDetected", false);
                imgData.put("detectionCount", 0);
                imgData.put("anomalyCount", 0);
            }
            recentImageDetections.add(imgData);
        }
        data.put("recentImageDetections", recentImageDetections);

        // 最近3条视频检测结果
        LambdaQueryWrapper<Video> vidWrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getStatus, 1)
                .orderByDesc(Video::getCreatedAt)
                .last("LIMIT 3");
        if (!isAdmin) {
            vidWrapper.eq(Video::getUserId, user.getId());
        }
        List<Video> recentVideos = videoMapper.selectList(vidWrapper);

        List<Map<String, Object>> recentVideoDetections = new ArrayList<>();
        for (Video vid : recentVideos) {
            Map<String, Object> vidData = new HashMap<>();
            vidData.put("id", vid.getId());
            vidData.put("fileName", vid.getFileName());
            vidData.put("fileSize", vid.getFileSize());
            vidData.put("fileSizeFormatted", formatFileSize(vid.getFileSize() != null ? vid.getFileSize() : 0L));
            vidData.put("duration", vid.getDuration());
            vidData.put("createdAt", vid.getCreatedAt() != null ? vid.getCreatedAt().toString() : null);

            // 获取上传用户信息
            User uploader = userMapper.selectById(vid.getUserId());
            vidData.put("uploaderId", vid.getUserId());
            vidData.put("uploaderName", uploader != null ? (uploader.getName() != null ? uploader.getName() : uploader.getEmail()) : "未知");

            // 获取检测结果
            if (vid.getIsDetected() != null && vid.getIsDetected() == 1 && vid.getDetectionResults() != null) {
                vidData.put("isDetected", true);
                try {
                    Map<String, Object> det = objectMapper.readValue(vid.getDetectionResults(), new TypeReference<Map<String, Object>>() {});
                    int anomalyCount = (int) det.getOrDefault("anomalyCount", 0);
                    int totalFrames = (int) det.getOrDefault("totalFramesProcessed", 0);
                    vidData.put("anomalyCount", anomalyCount);
                    vidData.put("totalFrames", totalFrames);
                    vidData.put("anomalyRate", totalFrames > 0 ? (double) anomalyCount / totalFrames : 0.0);
                } catch (Exception e) {
                    vidData.put("anomalyCount", 0);
                    vidData.put("totalFrames", 0);
                    vidData.put("anomalyRate", 0.0);
                }
            } else {
                vidData.put("isDetected", false);
                vidData.put("anomalyCount", 0);
                vidData.put("totalFrames", 0);
                vidData.put("anomalyRate", 0.0);
            }
            recentVideoDetections.add(vidData);
        }
        data.put("recentVideoDetections", recentVideoDetections);

        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    // ========== 工具方法 ==========

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 格式化时长（秒 -> mm:ss 或 hh:mm:ss）
     */
    private String formatDuration(double seconds) {
        int totalSeconds = (int) seconds;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int secs = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }

    /**
     * 标准化文件类型（处理 MIME type 和扩展名）
     */
    private String normalizeFileType(String fileType) {
        if (fileType == null || fileType.isEmpty()) return "unknown";
        
        // 转小写
        String type = fileType.toLowerCase();
        
        // 如果是 MIME type，提取扩展名部分
        if (type.contains("/")) {
            type = type.substring(type.lastIndexOf("/") + 1);
        }
        
        // 常见格式映射
        switch (type) {
            case "jpeg":
            case "jpg":
                return "jpg";
            case "png":
                return "png";
            case "webp":
                return "webp";
            case "gif":
                return "gif";
            case "bmp":
                return "bmp";
            case "mp4":
            case "mpeg4":
                return "mp4";
            case "mov":
            case "quicktime":
                return "mov";
            case "avi":
                return "avi";
            case "mkv":
                return "mkv";
            case "webm":
                return "webm";
            default:
                return type;
        }
    }
}