package com.bub6le.crackserver.service.impl;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.bub6le.crackserver.entity.Image;
import com.bub6le.crackserver.mapper.ImageMapper;
import com.bub6le.crackserver.service.ModelService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.List;

@Service
@Slf4j
public class ModelServiceImpl implements ModelService {

    @Autowired
    private ImageMapper imageMapper;

    private OrtEnvironment env;
    private OrtSession session;
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.25f;
    private static final float IOU_THRESHOLD = 0.45f;
    private static final String[] LABELS = {"P0", "P1", "P2", "P3"};

    @PostConstruct
    public void init() {
        try {
            env = OrtEnvironment.getEnvironment();
            // 尝试加载模型，如果不存在则仅记录警告
            ClassPathResource resource = new ClassPathResource("model/best.onnx");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    byte[] modelBytes = is.readAllBytes();
                    session = env.createSession(modelBytes, new OrtSession.SessionOptions());
                    log.info("YOLOv8 ONNX model loaded successfully");
                }
            } else {
                log.warn("Model file 'best.onnx' not found in resources/model. Please convert 'best.pt' to ONNX format.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize ONNX model", e);
        }
    }

    @PreDestroy
    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (OrtException e) {
            log.error("Error closing ONNX resources", e);
        }
    }

    @Override
    public List<Map<String, Object>> detect(MultipartFile file) {
        if (session == null) {
            throw new RuntimeException("Model not initialized. Please ensure 'best.onnx' exists.");
        }

        try {
            BufferedImage originalImage = ImageIO.read(file.getInputStream());
            if (originalImage == null) {
                throw new IllegalArgumentException("Invalid image file");
            }

            // Preprocess
            float[] floatData = preprocess(originalImage);
            
            // Create tensor
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatData), new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
            
            // Run inference
            OrtSession.Result result = session.run(Collections.singletonMap(session.getInputNames().iterator().next(), inputTensor));
            float[][][] output = (float[][][]) result.get(0).getValue();
            
            // Postprocess
            return postprocess(output[0], originalImage.getWidth(), originalImage.getHeight());

        } catch (Exception e) {
            log.error("Detection failed", e);
            throw new RuntimeException("Detection failed: " + e.getMessage());
        }
    }

    @Override
    public Map<Long, List<Map<String, Object>>> detectBatch(List<Long> imageIds) {
        if (session == null) {
            throw new RuntimeException("Model not initialized.");
        }

        Map<Long, List<Map<String, Object>>> batchResults = new HashMap<>();

        for (Long imageId : imageIds) {
            try {
                // 1. 获取图片信息
                Image image = imageMapper.selectById(imageId);
                if (image == null) {
                    log.warn("Image ID {} not found", imageId);
                    continue;
                }

                // 2. 读取文件
                File file = new File(image.getFilePath());
                if (!file.exists()) {
                    log.warn("File not found for Image ID {}: {}", imageId, image.getFilePath());
                    continue;
                }

                BufferedImage originalImage = ImageIO.read(file);
                if (originalImage == null) {
                    log.warn("Invalid image content for Image ID {}", imageId);
                    continue;
                }

                // 3. 执行推理
                float[] floatData = preprocess(originalImage);
                OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatData), new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
                OrtSession.Result result = session.run(Collections.singletonMap(session.getInputNames().iterator().next(), inputTensor));
                float[][][] output = (float[][][]) result.get(0).getValue();
                
                // 4. 后处理并保存结果
                List<Map<String, Object>> detections = postprocess(output[0], originalImage.getWidth(), originalImage.getHeight());
                batchResults.put(imageId, detections);

            } catch (Exception e) {
                log.error("Error processing Image ID {}", imageId, e);
                // 可以选择记录错误或继续处理下一个
            }
        }

        return batchResults;
    }

    private float[] preprocess(BufferedImage originalImage) {
        BufferedImage resizedImage = new BufferedImage(INPUT_SIZE, INPUT_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(originalImage, 0, 0, INPUT_SIZE, INPUT_SIZE, null);
        g.dispose();

        float[] data = new float[3 * INPUT_SIZE * INPUT_SIZE];
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int rgb = resizedImage.getRGB(x, y);
                data[y * INPUT_SIZE + x] = ((rgb >> 16) & 0xFF) / 255.0f; // R
                data[INPUT_SIZE * INPUT_SIZE + y * INPUT_SIZE + x] = ((rgb >> 8) & 0xFF) / 255.0f; // G
                data[2 * INPUT_SIZE * INPUT_SIZE + y * INPUT_SIZE + x] = (rgb & 0xFF) / 255.0f; // B
            }
        }
        return data;
    }

    private List<Map<String, Object>> postprocess(float[][] output, int originalWidth, int originalHeight) {
        // Output shape is usually [dimensions, anchors] -> [4 + classes, 8400]
        // Need to transpose to iterate over anchors
        int dimensions = output.length; // 4 + num_classes
        int anchors = output[0].length; // 8400
        int numClasses = dimensions - 4;

        List<Detection> detections = new ArrayList<>();

        for (int i = 0; i < anchors; i++) {
            // Find class with max score
            float maxScore = 0;
            int classId = -1;
            for (int c = 0; c < numClasses; c++) {
                float score = output[4 + c][i];
                if (score > maxScore) {
                    maxScore = score;
                    classId = c;
                }
            }

            if (maxScore > CONFIDENCE_THRESHOLD) {
                float cx = output[0][i];
                float cy = output[1][i];
                float w = output[2][i];
                float h = output[3][i];

                float x = (cx - w / 2) * originalWidth / INPUT_SIZE;
                float y = (cy - h / 2) * originalHeight / INPUT_SIZE;
                float width = w * originalWidth / INPUT_SIZE;
                float height = h * originalHeight / INPUT_SIZE;

                detections.add(new Detection(x, y, width, height, maxScore, classId));
            }
        }

        // Apply NMS
        List<Detection> nmsDetections = applyNMS(detections);

        // Convert to result map
        List<Map<String, Object>> results = new ArrayList<>();
        for (Detection d : nmsDetections) {
            Map<String, Object> map = new HashMap<>();
            map.put("x", d.x);
            map.put("y", d.y);
            map.put("width", d.width);
            map.put("height", d.height);
            map.put("score", d.score);
            map.put("classId", d.classId);
            map.put("label", d.classId < LABELS.length ? LABELS[d.classId] : "Unknown");
            results.add(map);
        }
        return results;
    }

    private List<Detection> applyNMS(List<Detection> detections) {
        detections.sort((a, b) -> Float.compare(b.score, a.score));
        List<Detection> result = new ArrayList<>();
        
        while (!detections.isEmpty()) {
            Detection best = detections.remove(0);
            result.add(best);
            
            Iterator<Detection> it = detections.iterator();
            while (it.hasNext()) {
                Detection other = it.next();
                if (calculateIoU(best, other) > IOU_THRESHOLD) {
                    it.remove();
                }
            }
        }
        return result;
    }

    private float calculateIoU(Detection a, Detection b) {
        float x1 = Math.max(a.x, b.x);
        float y1 = Math.max(a.y, b.y);
        float x2 = Math.min(a.x + a.width, b.x + b.width);
        float y2 = Math.min(a.y + a.height, b.y + b.height);

        if (x2 < x1 || y2 < y1) return 0.0f;

        float intersection = (x2 - x1) * (y2 - y1);
        float areaA = a.width * a.height;
        float areaB = b.width * b.height;

        return intersection / (areaA + areaB - intersection);
    }

    private static class Detection {
        float x, y, width, height, score;
        int classId;

        public Detection(float x, float y, float width, float height, float score, int classId) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.score = score;
            this.classId = classId;
        }
    }
}
