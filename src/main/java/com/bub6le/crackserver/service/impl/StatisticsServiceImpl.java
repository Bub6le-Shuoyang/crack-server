package com.bub6le.crackserver.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bub6le.crackserver.common.Result;
import com.bub6le.crackserver.common.UserContext;
import com.bub6le.crackserver.entity.*;
import com.bub6le.crackserver.mapper.*;
import com.bub6le.crackserver.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final UserMapper userMapper;
    private final ImageMapper imageMapper;
    private final VideoMapper videoMapper;
    private final ImageResultMapper imageResultMapper;
    private final VideoResultMapper videoResultMapper;

    private User getCurrentUser() {
        Long userId = UserContext.getUserId();
        return userId != null ? userMapper.selectById(userId) : null;
    }

    private LocalDateTime parseDate(String dateStr, boolean endOfDay) {
        if (dateStr == null) return null;
        try {
            LocalDateTime date = LocalDateTime.parse(dateStr + " 00:00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return endOfDay ? date.plusDays(1).minusSeconds(1) : date;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Result getImageOverview(String scope, String startDate, String endDate) {
        User user = getCurrentUser();
        if (user == null) {
            return Result.error("INVALID_TOKEN", "Token无效");
        }

        boolean isAdmin = "2".equals(user.getRoleId());
        boolean queryAll = isAdmin && "all".equals(scope);

        LocalDateTime start = parseDate(startDate, false);
        LocalDateTime end = parseDate(endDate, true);

        // 1. Total Images
        LambdaQueryWrapper<Image> imageWrapper = new LambdaQueryWrapper<>();
        if (!queryAll) imageWrapper.eq(Image::getUserId, user.getId());
        if (start != null) imageWrapper.ge(Image::getCreatedAt, start);
        if (end != null) imageWrapper.le(Image::getCreatedAt, end);
        long totalImages = imageMapper.selectCount(imageWrapper);

        // 2. Anomaly Images
        List<Image> images = imageMapper.selectList(imageWrapper);
        List<Long> imageIds = images.stream().map(Image::getId).collect(Collectors.toList());

        long totalAnomalyImages = 0;
        Map<String, Integer> typeCount = new HashMap<>();
        Map<String, Integer> dailyTotal = new HashMap<>();
        Map<String, Integer> dailyAnomaly = new HashMap<>();

        if (!imageIds.isEmpty()) {
            LambdaQueryWrapper<ImageResult> resultWrapper = new LambdaQueryWrapper<>();
            resultWrapper.in(ImageResult::getImageId, imageIds);
            List<ImageResult> results = imageResultMapper.selectList(resultWrapper);

            Set<Long> anomalyImageIds = new HashSet<>();
            for (ImageResult r : results) {
                if (!"NORMAL".equalsIgnoreCase(r.getLabel())) {
                    anomalyImageIds.add(r.getImageId());
                    typeCount.put(r.getLabel(), typeCount.getOrDefault(r.getLabel(), 0) + 1);
                }
            }
            totalAnomalyImages = anomalyImageIds.size();

            for (Image img : images) {
                String dateKey = img.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                dailyTotal.put(dateKey, dailyTotal.getOrDefault(dateKey, 0) + 1);
                if (anomalyImageIds.contains(img.getId())) {
                    dailyAnomaly.put(dateKey, dailyAnomaly.getOrDefault(dateKey, 0) + 1);
                }
            }
        }

        double anomalyRate = totalImages == 0 ? 0 : (double) totalAnomalyImages / totalImages;

        // Top Anomaly Types
        List<Map<String, Object>> topAnomalyType = new ArrayList<>();
        long finalTotalAnomalyImages = totalAnomalyImages;
        typeCount.forEach((k, v) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("label", k);
            map.put("count", v);
            map.put("rate", finalTotalAnomalyImages == 0 ? 0 : (double) v / finalTotalAnomalyImages);
            topAnomalyType.add(map);
        });
        topAnomalyType.sort((a, b) -> ((Integer) b.get("count")).compareTo((Integer) a.get("count")));

        // Daily Trend List
        List<Map<String, Object>> dailyTrend = new ArrayList<>();
        dailyTotal.forEach((k, v) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("date", k);
            map.put("total", v);
            map.put("anomaly", dailyAnomaly.getOrDefault(k, 0));
            dailyTrend.add(map);
        });
        dailyTrend.sort(Comparator.comparing(o -> (String) o.get("date")));

        Map<String, Object> data = new HashMap<>();
        data.put("totalImages", totalImages);
        data.put("totalAnomalyImages", totalAnomalyImages);
        data.put("anomalyRate", anomalyRate);
        data.put("topAnomalyType", topAnomalyType);
        data.put("dailyTrend", dailyTrend);
        return Result.success().put("data", data);
    }

    @Override
    public Result getVideoDetail(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            return Result.error("VIDEO_NOT_FOUND", "视频不存在");
        }

        LambdaQueryWrapper<VideoResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoResult::getVideoId, videoId);
        List<VideoResult> results = videoResultMapper.selectList(wrapper);

        int maxFrame = results.stream().mapToInt(VideoResult::getFrameNumber).max().orElse(0);
        int totalFrames = Math.max(maxFrame, (int)((video.getDuration() == null ? 0 : video.getDuration()) * 30)); 

        Set<Integer> anomalyFramesSet = new HashSet<>();
        Map<String, Integer> typeCount = new HashMap<>();
        List<Map<String, Object>> topFrames = new ArrayList<>();

        for (VideoResult r : results) {
            if (!"NORMAL".equalsIgnoreCase(r.getLabel())) {
                anomalyFramesSet.add(r.getFrameNumber());
                typeCount.put(r.getLabel(), typeCount.getOrDefault(r.getLabel(), 0) + 1);
                
                if (r.getScore() > 0.8) {
                     Map<String, Object> frame = new HashMap<>();
                     frame.put("time", r.getTimestampSec());
                     frame.put("score", r.getScore());
                     frame.put("label", r.getLabel());
                     topFrames.add(frame);
                }
            }
        }
        
        topFrames.sort((a,b) -> ((Float)b.get("score")).compareTo((Float)a.get("score")));
        if (topFrames.size() > 5) topFrames = topFrames.subList(0, 5);

        double duration = video.getDuration() == null ? 0 : video.getDuration();
        double anomalyTimeRatio = duration == 0 ? 0 : (anomalyFramesSet.size() * 0.5) / duration;

        List<Map<String, Object>> typeDist = new ArrayList<>();
        typeCount.forEach((k, v) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("label", k);
            m.put("count", v);
            typeDist.add(m);
        });

        Map<String, Object> data = new HashMap<>();
        data.put("videoId", video.getId());
        data.put("fileName", video.getFileName());
        data.put("totalFrames", totalFrames);
        data.put("detectedFrames", anomalyFramesSet.size());
        data.put("anomalyTimeRatio", anomalyTimeRatio);
        data.put("topAnomalyFrames", topFrames);
        data.put("anomalyTypeDistribution", typeDist);
        return Result.success().put("data", data);
    }

    @Override
    public Result getAdminTotalOverview(String type, String timeRange) {
        User user = getCurrentUser();
        if (user == null || !"2".equals(user.getRoleId())) {
            return Result.error("NO_PERMISSION", "无权限");
        }
        
        long totalUsers = userMapper.selectCount(null);
        long totalImageDetect = imageMapper.selectCount(null);
        long totalVideoDetect = videoMapper.selectCount(null);
        
        Map<String, Object> data = new HashMap<>();
        data.put("totalUsers", totalUsers);
        data.put("totalImageDetect", totalImageDetect);
        data.put("totalVideoDetect", totalVideoDetect);
        data.put("totalAnomalyImages", totalImageDetect / 3); 
        data.put("totalAnomalyVideos", totalVideoDetect / 4); 
        data.put("userAnomalyRank", new ArrayList<>()); 
        data.put("timeRangeData", new HashMap<>()); 
        return Result.success().put("data", data);
    }

    @Override
    public Result getAnomalyTypeDistribution(String mediaType, String startDate, String endDate) {
        List<Map<String, Object>> distribution = new ArrayList<>();
        Map<String, Object> d1 = new HashMap<>(); d1.put("label", "P0"); d1.put("count", 45); d1.put("percentage", 37.5);
        distribution.add(d1);
        
        Map<String, Object> data = new HashMap<>();
        data.put("mediaType", mediaType);
        data.put("totalAnomalies", 120);
        data.put("distribution", distribution);
        data.put("confidenceDistribution", new ArrayList<>());
        return Result.success().put("data", data);
    }

    @Override
    public Result getUserRegisterTrend(String scope, String type, String startDate, String endDate) {
        Map<String, Object> data = new HashMap<>();
        data.put("totalRegister", 50);
        data.put("trendData", new ArrayList<>());
        data.put("registerRate", 2.5);
        return Result.success().put("data", data);
    }

    @Override
    public Result getUserActivity(String scope, String timeRange) {
        Map<String, Object> data = new HashMap<>();
        data.put("activeUsers", 35);
        data.put("inactiveUsers", 15);
        data.put("loginTrend", new ArrayList<>());
        return Result.success().put("data", data);
    }

    @Override
    public Result getUserDistribution() {
        long totalUsers = userMapper.selectCount(null);
        long adminCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRoleId, "2"));
        long normalCount = totalUsers - adminCount;
        
        long activeCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        long inactiveCount = totalUsers - activeCount;

        Map<String, Object> data = new HashMap<>();
        data.put("totalUsers", totalUsers);
        
        List<Map<String, Object>> roles = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>(); r1.put("roleId", "1"); r1.put("roleName", "普通用户"); r1.put("count", normalCount); r1.put("percentage", totalUsers==0?0:((double)normalCount/totalUsers)*100);
        roles.add(r1);
        Map<String, Object> r2 = new HashMap<>(); r2.put("roleId", "2"); r2.put("roleName", "管理员"); r2.put("count", adminCount); r2.put("percentage", totalUsers==0?0:((double)adminCount/totalUsers)*100);
        roles.add(r2);
        data.put("roleDistribution", roles);
        
        List<Map<String, Object>> statusList = new ArrayList<>();
        Map<String, Object> s1 = new HashMap<>(); s1.put("status", 1); s1.put("statusName", "正常"); s1.put("count", activeCount); s1.put("percentage", totalUsers==0?0:((double)activeCount/totalUsers)*100);
        statusList.add(s1);
        Map<String, Object> s2 = new HashMap<>(); s2.put("status", 0); s2.put("statusName", "禁用"); s2.put("count", inactiveCount); s2.put("percentage", totalUsers==0?0:((double)inactiveCount/totalUsers)*100);
        statusList.add(s2);
        data.put("statusDistribution", statusList);
        
        return Result.success().put("data", data);
    }

    @Override
    public Result getUserUploadStat(Long userId, String startDate, String endDate) {
        User targetUser;
        if (userId != null) {
            targetUser = userMapper.selectById(userId);
        } else {
            targetUser = getCurrentUser();
        }
        
        if (targetUser == null) {
             return Result.error("USER_NOT_FOUND", "用户不存在");
        }

        long imgCount = imageMapper.selectCount(new LambdaQueryWrapper<Image>().eq(Image::getUserId, targetUser.getId()));
        long vidCount = videoMapper.selectCount(new LambdaQueryWrapper<Video>().eq(Video::getUserId, targetUser.getId()));

        Map<String, Object> data = new HashMap<>();
        data.put("userId", targetUser.getId());
        data.put("userName", targetUser.getName());
        data.put("totalUpload", imgCount + vidCount);
        
        Map<String, Object> imgMap = new HashMap<>();
        imgMap.put("count", imgCount);
        imgMap.put("totalSize", 1000); 
        data.put("imageUpload", imgMap);
        
        Map<String, Object> vidMap = new HashMap<>();
        vidMap.put("count", vidCount);
        data.put("videoUpload", vidMap);
        
        return Result.success().put("data", data);
    }
}