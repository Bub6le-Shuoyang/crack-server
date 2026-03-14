from ultralytics import YOLO
import os

model_path = 'src/main/resources/model/best.pt'
print(f"Loading model from {model_path}")

try:
    model = YOLO(model_path)
    print("Model loaded successfully")
    success = model.export(format='onnx')
    print(f"Export result: {success}")
except Exception as e:
    print(f"Error: {e}")
