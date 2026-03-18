package com.bub6le.crackserver.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

public interface ImageService {
    Map<String, Object> uploadImage(MultipartFile file, String description, String token);
    Map<String, Object> listImages(int page, int pageSize, String fileType, String keyword, String label, String token);
    Map<String, Object> deleteImage(Long imageId, String token);
    Map<String, Object> getImageIds(String token);
    Map<String, Object> getImageById(Long imageId, String token);
    Map<String, Object> batchDeleteImages(List<Long> imageIds, String token);
}
