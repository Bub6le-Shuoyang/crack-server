package com.bub6le.crackserver.service.impl;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bub6le.crackserver.entity.Image;
import com.bub6le.crackserver.entity.ImageResult;
import com.bub6le.crackserver.entity.Video;
import com.bub6le.crackserver.entity.VideoResult;
import com.bub6le.crackserver.mapper.ImageMapper;
import com.bub6le.crackserver.mapper.ImageResultMapper;
import com.bub6le.crackserver.mapper.VideoMapper;
import com.bub6le.crackserver.mapper.VideoResultMapper;
import com.bub6le.crackserver.service.ModelService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

@Service
@Slf4j
public class ModelServiceImpl implements ModelService {

    @Autowired
    private ImageMapper imageMapper;
    
    @Autowired
    private ImageResultMapper imageResultMapper;

    private OrtEnvironment env;
    private OrtSession session;
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.25f;
    private static final float IOU_THRESHOLD = 0.45f;
    private static final String[] LABELS = {"P0", "P1", "P2", "P3"};
    private static final String MODEL_FILE_NAME = "best.onnx"; // Define constant for model filename
    private String modelName; // Store the full model name

    @PostConstruct
    public void init() {
        try {
            env = OrtEnvironment.getEnvironment();
            // 尝试加载模型，如果不存在则仅记录警告
            ClassPathResource resource = new ClassPathResource("model/" + MODEL_FILE_NAME);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    byte[] modelBytes = is.readAllBytes();
                    session = env.createSession(modelBytes, new OrtSession.SessionOptions());
                    modelName = "yolov8-" + MODEL_FILE_NAME; // Set the model name
                    log.info("YOLOv8 ONNX model loaded successfully");
                }
            } else {
                log.warn("Model file '{}' not found in resources/model. Please convert 'best.pt' to ONNX format.", MODEL_FILE_NAME);
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
                // 0. 检查图片是否存在
                Image image = imageMapper.selectById(imageId);
                if (image == null) {
                    log.warn("Image ID {} not found", imageId);
                    continue;
                }
                
                // 1. 读取文件
                // 如果是相对路径，需要转为绝对路径
                String filePath = image.getFilePath();
                File file = new File(filePath);
                if (!file.exists()) {
                    // 尝试拼接项目根目录
                    String projectRoot = System.getProperty("user.dir");
                    file = new File(projectRoot, filePath);
                    if (!file.exists()) {
                        log.warn("File not found for Image ID {}: {}", imageId, filePath);
                        continue;
                    }
                }

                BufferedImage originalImage = ImageIO.read(file);
                if (originalImage == null) {
                    log.warn("Invalid image content for Image ID {}", imageId);
                    continue;
                }
                
                // 2. 删除旧结果 (实现"重新检测"逻辑)
                imageResultMapper.delete(new QueryWrapper<ImageResult>().eq("imageId", imageId));

                // 3. 执行推理
                float[] floatData = preprocess(originalImage);
                OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatData), new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
                OrtSession.Result result = session.run(Collections.singletonMap(session.getInputNames().iterator().next(), inputTensor));
                float[][][] output = (float[][][]) result.get(0).getValue();
                
                // 4. 后处理
                List<Map<String, Object>> detections = postprocess(output[0], originalImage.getWidth(), originalImage.getHeight());
                batchResults.put(imageId, detections);
                
                // 5. 保存结果到数据库
                // 如果没有检测到任何目标，也需要标记已检测（比如存一条特殊记录，或者更新图片状态）
                // 之前的逻辑：detections为空则不插入image_results，导致下次查询时认为未检测。
                // 修正：我们应该在 image 表里增加一个 status 标记，或者插入一条特殊的“无异常”记录？
                // 更合理的做法：更新 image 表的 status 字段，或者有一个专门的 is_detected 字段。
                // 鉴于不修改表结构的要求，我们可以依赖“只要查询过详情就算检测过”？
                // 不行，列表页需要知道状态。
                // 方案：如果 detections 为空，我们插入一条 dummy 记录？不优雅。
                // 方案：我们还是需要更新 image 表的状态。
                // Image 实体里的 status: 1=正常, 0=禁用。
                // 我们可以借用 status 字段吗？不行，那是逻辑删除用的。
                // 我们在 ImageResult 表里，如果没有检测结果，是否可以不存？
                // 如果不存，前端怎么知道是“未检测”还是“检测了但无异常”？
                // 除非前端认为：只要没有结果就是“未检测”。但这会导致重复检测。
                // 让我们修改 Image 实体，利用 updatedAt 字段？或者，我们强制要求：
                // 如果检测结果为空，我们插入一条 label="NORMAL" 的记录？
                // 这是一个折中方案。
                
                if (detections.isEmpty()) {
                     // 插入一条代表“无异常”的记录，或者前端约定：
                     // 如果查不到结果，就显示“未检测”？
                     // 既然用户想要保存“检测状态”，那对于无异常的图片，也应该知道它被检测过了。
                     // 让我们插入一条特殊的记录：label="NORMAL", score=1.0
                     ImageResult ir = new ImageResult();
                     ir.setImageId(imageId);
                     ir.setLabel("NORMAL"); // 标记无异常
                     ir.setScore(1.0f);
                     ir.setX(0f); ir.setY(0f); ir.setWidth(0f); ir.setHeight(0f);
                     ir.setClassId(-1);
                     ir.setModelName(modelName != null ? modelName : "Unknown");
                     ir.setCreatedAt(LocalDateTime.now());
                     imageResultMapper.insert(ir);
                } else {
                    for (Map<String, Object> det : detections) {
                        ImageResult ir = new ImageResult();
                        ir.setImageId(imageId);
                        ir.setLabel((String) det.get("label"));
                        ir.setScore((Float) det.get("score"));
                        ir.setX((Float) det.get("x"));
                        ir.setY((Float) det.get("y"));
                        ir.setWidth((Float) det.get("width"));
                        ir.setHeight((Float) det.get("height"));
                        ir.setClassId((Integer) det.get("classId"));
                        ir.setModelName(modelName != null ? modelName : "Unknown");
                        ir.setCreatedAt(LocalDateTime.now());
                        imageResultMapper.insert(ir);
                    }
                }

            } catch (Exception e) {
                log.error("Error processing Image ID {}", imageId, e);
            }
        }

        return batchResults;
    }

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private VideoResultMapper videoResultMapper;

    @Override
    public Map<String, Object> detectVideo(Long videoId) {
        Map<String, Object> result = new HashMap<>();
        if (session == null) {
            result.put("error", true);
            result.put("message", "模型未初始化");
            return result;
        }

        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            result.put("error", true);
            result.put("message", "视频不存在");
            return result;
        }

        String projectRoot = System.getProperty("user.dir");
        String filePath = video.getFilePath();
        File file = new File(filePath);
        if (!file.exists()) {
            file = new File(projectRoot, filePath);
            if (!file.exists()) {
                result.put("error", true);
                result.put("message", "视频文件丢失");
                return result;
            }
        }
        
        // 删除旧结果 (实现"重新检测"逻辑)
        videoResultMapper.delete(new QueryWrapper<VideoResult>().eq("video_id", videoId));

        List<Map<String, Object>> anomalyFrames = new ArrayList<>();
        int totalFramesProcessed = 0;
        int anomalyCount = 0;

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file)) {
            grabber.start();
            double frameRate = grabber.getFrameRate();
            int lengthInFrames = grabber.getLengthInFrames();
            // 每秒抽2帧进行检测以平衡性能和精度
            double sampleRate = 2.0; 
            int step = (int) Math.max(1, Math.round(frameRate / sampleRate));

            try (Java2DFrameConverter converter = new Java2DFrameConverter()) {
                for (int i = 0; i < lengthInFrames; i += step) {
                    grabber.setFrameNumber(i);
                    Frame frame = grabber.grabImage();
                    if (frame == null) continue;

                    BufferedImage bufferedImage = converter.getBufferedImage(frame);
                    if (bufferedImage == null) continue;

                    totalFramesProcessed++;
                    
                    // 推理
                    float[] floatData = preprocess(bufferedImage);
                    OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatData), new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
                    OrtSession.Result ortResult = session.run(Collections.singletonMap(session.getInputNames().iterator().next(), inputTensor));
                    float[][][] output = (float[][][]) ortResult.get(0).getValue();
                    
                    List<Map<String, Object>> detections = postprocess(output[0], bufferedImage.getWidth(), bufferedImage.getHeight());
                    
                    if (!detections.isEmpty()) {
                        anomalyCount += detections.size();
                        double timestampSeconds = i / frameRate;
                        Map<String, Object> frameResult = new HashMap<>();
                        frameResult.put("time", timestampSeconds);
                        frameResult.put("timeFormatted", formatTime(timestampSeconds));
                        frameResult.put("frameNumber", i);
                        frameResult.put("detections", detections);
                        anomalyFrames.add(frameResult);
                        
                        // 保存检测到的异常结果到数据库
                        for (Map<String, Object> det : detections) {
                            VideoResult vr = new VideoResult();
                            vr.setVideoId(videoId);
                            vr.setFrameNumber(i);
                            vr.setTimestampSec(timestampSeconds);
                            vr.setLabel((String) det.get("label"));
                            vr.setScore((Float) det.get("score"));
                            vr.setX((Float) det.get("x"));
                            vr.setY((Float) det.get("y"));
                            vr.setWidth((Float) det.get("width"));
                            vr.setHeight((Float) det.get("height"));
                            vr.setClassId((Integer) det.get("classId"));
                            vr.setModelName(modelName != null ? modelName : "Unknown");
                            vr.setCreatedAt(LocalDateTime.now());
                            videoResultMapper.insert(vr);
                        }
                    }
                }
            }
            grabber.stop();
            
            // 如果没有任何异常，存一条标记数据
            if (anomalyCount == 0) {
                VideoResult vr = new VideoResult();
                vr.setVideoId(videoId);
                vr.setFrameNumber(0);
                vr.setTimestampSec(0.0);
                vr.setLabel("NORMAL");
                vr.setScore(1.0f);
                vr.setX(0f); vr.setY(0f); vr.setWidth(0f); vr.setHeight(0f);
                vr.setClassId(-1);
                vr.setModelName(modelName != null ? modelName : "Unknown");
                vr.setCreatedAt(LocalDateTime.now());
                videoResultMapper.insert(vr);
            }
            
        } catch (Exception e) {
            log.error("Video detection failed for videoId {}", videoId, e);
            result.put("error", true);
            result.put("message", "视频检测失败: " + e.getMessage());
            return result;
        }

        result.put("ok", true);
        result.put("message", "视频检测完成");
        Map<String, Object> data = new HashMap<>();
        data.put("videoId", videoId);
        data.put("totalFramesProcessed", totalFramesProcessed);
        data.put("anomalyCount", anomalyCount);
        data.put("anomalyFrames", anomalyFrames); // 仅包含有异常的帧信息
        result.put("data", data);

        return result;
    }

    private String formatTime(double seconds) {
        int m = (int) (seconds / 60);
        int s = (int) (seconds % 60);
        int ms = (int) ((seconds - (m * 60 + s)) * 1000);
        return String.format("%02d:%02d.%03d", m, s, ms);
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
