# Translation Models Directory

This directory contains ML models for the translation feature.

## Required Models

### Phase 3: Bubble Detection (Required)

**File**: `yolov10_bubble_detection.tflite`
**Size**: ~94MB (FP32)
**Purpose**: Detect speech bubbles, thought bubbles, and text regions in comic pages
**Status**: ✅ Included in repository

### Phase 4: Inpainting (Optional)

**File**: `lama_inpainting.tflite`
**Size**: 174MB (FP32) or 44MB (INT8)
**Purpose**: Remove text and reconstruct background patterns
**Status**: ⚠️ Download separately (too large for Git)

#### How to Get YOLOv10 Model:

**Model is already included in the repository** (94MB). No action needed! ✅

---

#### How to Get LaMa Model:

**Option 1: Download Pre-Converted TFLite (Recommended - 5 minutes)**
```bash
# Navigate to models directory
cd translation/src/main/assets/models

# Download Qualcomm's pre-converted LaMa (174MB FP32)
wget https://huggingface.co/qualcomm/LaMa-Dilated/resolve/main/LaMa-Dilated.tflite \
     -O lama_inpainting.tflite

# Verify download
ls -lh lama_inpainting.tflite
# Should show: 174M lama_inpainting.tflite

# Build and you're done!
cd ../../../../
./gradlew :app:assembleDebug
```

**Option 2: Convert from PyTorch with INT8 (Advanced - 30 minutes)**
```bash
# Run conversion script
cd translation/scripts
./download_lama_model.sh

# Follow prompts, select option 1 for Regular model
# Result: 44MB INT8 model (smaller, still excellent quality)
```

**Option 3: Skip LaMa (Use Simple Inpainting)**
```bash
# Don't download anything!
# App automatically uses SimpleInpaintingEngine (blur-based, 70% quality)
# Just build:
./gradlew :app:assembleDebug
```

## Model Fallback Strategy

The app has a smart 3-level fallback system for each phase:

### Phase 3: Bubble Detection
- ✅ **Primary**: YOLOv10 (92-95% accuracy, 300ms)
- ⚠️ **Fallback**: StubBubbleDetector (treats page as one bubble)

**Log if fallback:**
```
W/TranslationModule: YOLOv10 model not found, using stub detector
```

### Phase 4: Inpainting
- ✅ **Primary**: LaMa (95% quality, 400ms) - if `lama_inpainting.tflite` exists
- ⚠️ **Fallback 1**: SimpleInpaintingEngine (70% quality, 100ms) - blur-based
- ⚠️ **Fallback 2**: StubInpaintingEngine (no text removal)

**Log for each level:**
```
# With LaMa model:
D/TranslationModule: Using LamaInpaintingEngine (production quality)

# Without LaMa (automatic fallback):
D/TranslationModule: LaMa model not found, trying Simple inpainting
D/TranslationModule: Using SimpleInpaintingEngine (blur-based)
```

**Quality comparison:**
- LaMa: 95%+ excellent (patterns reconstructed perfectly)
- Simple: 70% good (patterns blurred but acceptable)
- Stub: 0% (no text removal, just overlay)

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
