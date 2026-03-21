package com.bub6le.crackserver.service;

import java.util.Map;

public interface StatisticsService {
    
    Map<String, Object> getImageOverview(String token);

    Map<String, Object> getVideoDetail(Long videoId, String token);

    Map<String, Object> getAdminTotalOverview(String startDate, String endDate, String token);

    Map<String, Object> getAnomalyTypeDistribution(String mediaType, String startDate, String endDate, String token);
}