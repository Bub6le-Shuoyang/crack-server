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
}