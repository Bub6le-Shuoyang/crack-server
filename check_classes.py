import sys
print("Script starting...", flush=True)
try:
    from ultralytics import YOLO
    print("Imported YOLO", flush=True)
    model_path = 'src/main/resources/model/best.pt'
    print(f"Loading model from {model_path}", flush=True)
    model = YOLO(model_path)
    print("Model loaded", flush=True)
    print("Model classes:", flush=True)
    print(model.names, flush=True)
except Exception as e:
    print(f"Error: {e}", file=sys.stderr, flush=True)
print("Script finished", flush=True)
