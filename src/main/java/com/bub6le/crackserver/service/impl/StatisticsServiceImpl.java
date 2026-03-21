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
}