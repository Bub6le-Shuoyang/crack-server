from ultralytics import YOLO
import os

model_path = 'src/main/resources/model/best.pt'
print(f"Loading model from {model_path}")

try:
    model = YOLO(model_path)
    print("Model loaded successfully")
    # Export with opset=17 for compatibility with ONNX Runtime 1.18.0
    success = model.export(format='onnx', opset=17)
    print(f"Export result: {success}")
except Exception as e:
    print(f"Error: {e}")
