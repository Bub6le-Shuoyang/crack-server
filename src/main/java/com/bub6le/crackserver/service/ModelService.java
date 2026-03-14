package com.bub6le.crackserver.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ModelService {
    /**
     * 检测图片中的异常
     * @param file 上传的图片文件
     * @return 检测结果列表
     */
    List<Map<String, Object>> detect(MultipartFile file);
    /**
     * 批量检测图片中的异常
     * @param imageIds 图片ID列表
     * @return 检测结果列表
     */
    Map<Long, List<Map<String, Object>>> detectBatch(List<Long> imageIds);
}
