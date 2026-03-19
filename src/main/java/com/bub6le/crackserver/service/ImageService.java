package com.bub6le.crackserver.service;

import com.bub6le.crackserver.common.Result;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface ImageService {
    Result uploadImage(MultipartFile file, String description);
    Result listImages(int page, int pageSize, String fileType, String keyword, String label);
    Result deleteImage(Long imageId);
    Result getImageIds();
    Result getImageById(Long imageId);
    Result batchDeleteImages(List<Long> imageIds);
}
