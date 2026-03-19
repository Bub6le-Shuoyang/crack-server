package com.bub6le.crackserver.controller;

import com.bub6le.crackserver.common.Result;
import com.bub6le.crackserver.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "数据统计", description = "检测结果及用户状况统计接口")
@RestController
@RequestMapping("/api/statistics")
@Slf4j
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    // 2.1 图片检测结果概览统计
    @Operation(summary = "图片检测结果概览统计")
    @GetMapping("/image-overview")
    public Result getImageOverview(
            @RequestParam(value = "scope", defaultValue = "self") String scope,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        return statisticsService.getImageOverview(scope, startDate, endDate);
    }

    // 2.2 视频检测结果详情统计
    @Operation(summary = "视频检测结果详情统计")
    @GetMapping("/video-detail/{videoId}")
    public Result getVideoDetail(
            @PathVariable Long videoId) {
        return statisticsService.getVideoDetail(videoId);
    }

    // 2.3 全平台检测结果汇总（仅管理员）
    @Operation(summary = "全平台检测结果汇总")
    @GetMapping("/admin/total-overview")
    public Result getAdminTotalOverview(
            @RequestParam(value = "type", defaultValue = "all") String type,
            @RequestParam(value = "timeRange", defaultValue = "30days") String timeRange) {
        return statisticsService.getAdminTotalOverview(type, timeRange);
    }

    // 2.4 异常类型分布统计
    @Operation(summary = "异常类型分布统计")
    @GetMapping("/anomaly-type-distribution")
    public Result getAnomalyTypeDistribution(
            @RequestParam(value = "mediaType") String mediaType,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        return statisticsService.getAnomalyTypeDistribution(mediaType, startDate, endDate);
    }

    // 4.1 用户注册趋势统计
    @Operation(summary = "用户注册趋势统计")
    @GetMapping("/user/register-trend")
    public Result getUserRegisterTrend(
            @RequestParam(value = "scope", defaultValue = "self") String scope,
            @RequestParam(value = "type", defaultValue = "day") String type,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        return statisticsService.getUserRegisterTrend(scope, type, startDate, endDate);
    }

    // 4.2 用户活跃度统计
    @Operation(summary = "用户活跃度统计")
    @GetMapping("/user/activity")
    public Result getUserActivity(
            @RequestParam(value = "scope", defaultValue = "self") String scope,
            @RequestParam(value = "timeRange", defaultValue = "30days") String timeRange) {
        return statisticsService.getUserActivity(scope, timeRange);
    }

    // 4.3 用户角色 / 状态分布统计
    @Operation(summary = "用户角色 / 状态分布统计")
    @GetMapping("/user/distribution")
    public Result getUserDistribution() {
        return statisticsService.getUserDistribution();
    }

    // 4.4 用户上传文件统计
    @Operation(summary = "用户上传文件统计")
    @GetMapping("/user/upload-stat")
    public Result getUserUploadStat(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        return statisticsService.getUserUploadStat(userId, startDate, endDate);
    }
}
