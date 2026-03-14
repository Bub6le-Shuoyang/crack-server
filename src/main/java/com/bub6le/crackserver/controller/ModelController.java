package com.bub6le.crackserver.controller;

import com.bub6le.crackserver.dto.DetectRequest;
import com.bub6le.crackserver.service.ModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "AI模型检测", description = "异常检测相关接口")
@RestController
@RequestMapping("/model")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    @Operation(summary = "上传图片进行异常检测 (旧接口)", deprecated = true)
    @PostMapping(value = "/detect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<Map<String, Object>> detect(@RequestPart("file") MultipartFile file) {
        return modelService.detect(file);
    }

    @Operation(summary = "批量检测图片异常 (新接口)")
    @PostMapping("/detectBatch")
    public Map<Long, List<Map<String, Object>>> detectBatch(@RequestBody DetectRequest request) {
        return modelService.detectBatch(request.getImageIds());
    }
}

