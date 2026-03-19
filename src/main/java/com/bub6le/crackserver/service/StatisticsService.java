package com.bub6le.crackserver.service;

import com.bub6le.crackserver.common.Result;

public interface StatisticsService {
    // 2.1 图片检测结果概览统计
    Result getImageOverview(String scope, String startDate, String endDate);

    // 2.2 视频检测结果详情统计
    Result getVideoDetail(Long videoId);

    // 2.3 全平台检测结果汇总（仅管理员）
    Result getAdminTotalOverview(String type, String timeRange);

    // 2.4 异常类型分布统计
    Result getAnomalyTypeDistribution(String mediaType, String startDate, String endDate);

    // 4.1 用户注册趋势统计
    Result getUserRegisterTrend(String scope, String type, String startDate, String endDate);

    // 4.2 用户活跃度统计
    Result getUserActivity(String scope, String timeRange);

    // 4.3 用户角色 / 状态分布统计
    Result getUserDistribution();

    // 4.4 用户上传文件统计
    Result getUserUploadStat(Long userId, String startDate, String endDate);
}
