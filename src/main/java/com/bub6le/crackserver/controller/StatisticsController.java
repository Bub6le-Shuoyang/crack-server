package com.bub6le.crackserver.controller;

import com.bub6le.crackserver.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "数据统计", description = "检测结果数据统计相关接口")
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
@Slf4j
public class StatisticsController {

    private final StatisticsService statisticsService;

    // ========== 已有接口 ==========

    @Operation(summary = "图片检测结果概览统计")
    @GetMapping("/image-overview")
    public Map<String, Object> getImageOverview(
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("请求图片检测结果概览统计");
        return statisticsService.getImageOverview(token);
    }

    @Operation(summary = "视频检测结果详情统计")
    @GetMapping("/video-detail/{videoId}")
    public Map<String, Object> getVideoDetail(
            @PathVariable("videoId") Long videoId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("请求视频检测结果详情统计 videoId={}", videoId);
        return statisticsService.getVideoDetail(videoId, token);
    }

    @Operation(summary = "全平台检测结果汇总")
    @GetMapping("/admin/total-overview")
    public Map<String, Object> getAdminTotalOverview(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("请求全平台检测结果汇总 startDate={} endDate={}", startDate, endDate);
        return statisticsService.getAdminTotalOverview(startDate, endDate, token);
    }

    @Operation(summary = "异常类型分布统计")
    @GetMapping("/anomaly-type-distribution")
    public Map<String, Object> getAnomalyTypeDistribution(
            @RequestParam(value = "mediaType", defaultValue = "image") String mediaType,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("请求异常类型分布统计 mediaType={} startDate={} endDate={}", mediaType, startDate, endDate);
        return statisticsService.getAnomalyTypeDistribution(mediaType, startDate, endDate, token);
    }

    // ========== 新增的6个统计接口 ==========

    @Operation(summary = "存储空间统计", description = "统计总存储占用、图片/视频占比、各用户存储排名、存储增长趋势")
    @GetMapping("/storage-stats")
    public Map<String, Object> getStorageStats(
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("请求存储空间统计");
        return statisticsService.getStorageStats(token);
    }

    @Operation(summary = "用户个人统计", description = "统计我的上传量、检测量、存储占用、异常发现数")
    @GetMapping("/user/personal")
    public Map<String, Object> getPersonalStats(
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("请求用户个人统计");
        return statisticsService.getPersonalStats(token);
    }

    @Operation(summary = "视频检测概览", description = "统计视频总数、已检测数、异常视频数、总时长、平均异常时间占比")
    @GetMapping("/video-overview")
    public Map<String, Object> getVideoOverview(
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("请求视频检测概览统计");
        return statisticsService.getVideoOverview(token);
    }

    @Operation(summary = "文件类型分布", description = "统计图片格式占比、视频格式占比")
    @GetMapping("/file-type-distribution")
    public Map<String, Object> getFileTypeDistribution(
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("请求文件类型分布统计");
        return statisticsService.getFileTypeDistribution(token);
    }

    @Operation(summary = "实时监控面板", description = "今日实时数据：上传数、检测数、新用户数、活跃用户数")
    @GetMapping("/admin/realtime")
    public Map<String, Object> getRealtimeDashboard(
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("请求实时监控面板");
        return statisticsService.getRealtimeDashboard(token);
    }

    @Operation(summary = "异常热点时段分析", description = "分析一天中哪个时段上传的文件异常率最高")
    @GetMapping("/anomaly-time-analysis")
    public Map<String, Object> getAnomalyTimeAnalysis(
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("请求异常热点时段分析");
        return statisticsService.getAnomalyTimeAnalysis(token);
    }
}