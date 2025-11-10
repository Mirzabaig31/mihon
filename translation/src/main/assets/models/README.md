# Translation Models Directory

This directory contains ML models for the translation feature.

## Required Models

### Phase 3: Bubble Detection

**File**: `yolov10_bubble_detection.tflite`
**Size**: ~15-65MB (depending on quantization)
**Purpose**: Detect speech bubbles, thought bubbles, and text regions in comic pages

#### How to Get the Model:

1. **Option A: Download Pre-Converted Model**
   - If available, download from project releases or team repository
   - Place file here: `translation/src/main/assets/models/yolov10_bubble_detection.tflite`

2. **Option B: Convert YOLOv8 Model** (Recommended)
   ```bash
   # Install dependencies
   pip install ultralytics>=8.0.0 tensorflow>=2.13.0

   # Download YOLOv8 model
   wget https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8m.pt

   # Convert using provided script
   cd ../../../scripts
   python convert_yolo_to_tflite.py yolov8m.pt

   # Model will be automatically copied to this directory
   ```

3. **Option C: Use Comic-Trained Model** (Best Accuracy)
   ```bash
   # Download comic-trained model
   wget https://huggingface.co/ogkalu/comic-speech-bubble-detector-yolov8m/resolve/main/best.pt

   # Convert to TFLite
   cd ../../../scripts
   python convert_yolo_to_tflite.py best.pt
   ```

## Model Fallback

If `yolov10_bubble_detection.tflite` is not found:
- ✅ App will still work
- ⚠️  Will use `StubBubbleDetector` (treats entire page as one bubble)
- ⚠️  Lower translation accuracy
- ⚠️  No per-bubble text detection

Check logs for:
```
W/TranslationModule: YOLOv10 model not found, using stub detector
```

## Model Specifications

| Property | Value |
|----------|-------|
| Input Shape | [1, 1024, 1024, 3] or [1, 640, 640, 3] |
| Input Type | Float32 (normalized [0, 1]) |
| Output Shape | [1, 8400, 7] |
| Output Format | [x, y, w, h, confidence, class_scores...] |
| Classes | 4 (Speech, Thought, Narration, Sound Effect) |

## Troubleshooting

### Model Not Loading

Check file:
```bash
ls -lh yolov10_bubble_detection.tflite
```

Expected: ~15-65MB file

If file is 0 bytes or missing, reconvert the model.

### Build Error

If build fails with "file too large":
```bash
# Use INT8 quantization to reduce size
python convert_yolo_to_tflite.py model.pt --img-size 640
```

This reduces model to ~15MB.

## For More Information

See full documentation:
- Setup Guide: `../../../docs/PHASE_3_SETUP_GUIDE.md`
- Implementation Details: `../../../docs/PHASE_3_BUBBLE_DETECTION.md`
- Conversion Script: `../../../scripts/convert_yolo_to_tflite.py`
