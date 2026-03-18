package com.bub6le.crackserver.service;

import java.util.Map;

public interface StatisticsService {
    // 2.1 图片检测结果概览统计
    Map<String, Object> getImageOverview(String scope, String startDate, String endDate, String token);

    // 2.2 视频检测结果详情统计
    Map<String, Object> getVideoDetail(Long videoId, String token);

    // 2.3 全平台检测结果汇总（仅管理员）
    Map<String, Object> getAdminTotalOverview(String type, String timeRange, String token);

    // 2.4 异常类型分布统计
    Map<String, Object> getAnomalyTypeDistribution(String mediaType, String startDate, String endDate, String token);

    // 4.1 用户注册趋势统计
    Map<String, Object> getUserRegisterTrend(String scope, String type, String startDate, String endDate, String token);

    // 4.2 用户活跃度统计
    Map<String, Object> getUserActivity(String scope, String timeRange, String token);

    // 4.3 用户角色 / 状态分布统计
    Map<String, Object> getUserDistribution(String token);

    // 4.4 用户上传文件统计
    Map<String, Object> getUserUploadStat(Long userId, String startDate, String endDate, String token);
}
