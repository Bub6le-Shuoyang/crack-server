package com.bub6le.crackserver.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

public interface VideoService {
    Map<String, Object> uploadVideo(MultipartFile file, String description, Boolean generateCover, String token);
    Map<String, Object> listVideos(int page, int pageSize, String fileType, String token);
    Map<String, Object> deleteVideo(Long videoId, String token);
}
