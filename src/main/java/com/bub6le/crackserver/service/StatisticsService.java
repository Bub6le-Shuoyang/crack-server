package com.bub6le.crackserver.service;

import java.util.Map;

public interface StatisticsService {
    
    Map<String, Object> getImageOverview(String token);

    Map<String, Object> getVideoDetail(Long videoId, String token);

    Map<String, Object> getAdminTotalOverview(String startDate, String endDate, String token);

    Map<String, Object> getAnomalyTypeDistribution(String mediaType, String startDate, String endDate, String token);

    // ========== 新增的6个统计接口 ==========
    
    /**
     * 存储空间统计
     */
    Map<String, Object> getStorageStats(String token);

    /**
     * 用户个人统计
     */
    Map<String, Object> getPersonalStats(String token);

    /**
     * 视频检测概览
     */
    Map<String, Object> getVideoOverview(String token);

    /**
     * 文件类型分布
     */
    Map<String, Object> getFileTypeDistribution(String token);

    /**
     * 实时监控面板
     */
    Map<String, Object> getRealtimeDashboard(String token);

    /**
     * 异常热点时段分析
     */
    Map<String, Object> getAnomalyTimeAnalysis(String token);
}