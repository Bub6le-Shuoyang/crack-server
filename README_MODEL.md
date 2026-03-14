# AI 模型集成说明

本项目集成了 YOLOv8 模型用于图片异常检测。为了在 Java 环境中高效运行，需要将 PyTorch 模型 (`.pt`) 转换为 ONNX 格式 (`.onnx`)。

## 1. 环境准备

确保您安装了 Python 3.8+ 和 `ultralytics` 库。

```bash
pip install ultralytics
```

## 2. 模型转换

在项目根目录下，运行以下命令将 `best.pt` 转换为 `best.onnx`：

```bash
# 方式一：使用命令行
yolo export model=src/main/resources/model/best.pt format=onnx

# 方式二：使用提供的 Python 脚本
python export_model.py
```

转换成功后，`best.onnx` 文件应位于 `src/main/resources/model/` 目录下。

## 3. 验证

启动 Spring Boot 应用，访问 Swagger 文档 (`http://localhost:7022/swagger-ui.html`)，使用 `/model/detect` 接口上传图片进行测试。

## 注意事项

- 如果运行时报错 `Model file 'best.onnx' not found`，请确保转换步骤已成功执行。
- 目前 Java 代码默认使用 CPU 推理。如果需要 GPU 加速，请在 `pom.xml` 中引入 `onnxruntime-gpu` 并配置 CUDA 环境。
