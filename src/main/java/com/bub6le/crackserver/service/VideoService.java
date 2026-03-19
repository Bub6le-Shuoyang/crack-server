package com.bub6le.crackserver.service;

import com.bub6le.crackserver.common.Result;
import org.springframework.web.multipart.MultipartFile;

public interface VideoService {
    Result uploadVideo(MultipartFile file, String description, Boolean generateCover);
    Result listVideos(int page, int pageSize, String fileType);
    Result deleteVideo(Long videoId);
}
