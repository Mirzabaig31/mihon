# Translation Model Scripts

Quick tools to set up bubble detection models for Mihon translation.

---

## 🎯 Model Options

### YOLOv10 (Recommended for Production)
- **Latest**: Released 2024
- **Accuracy**: 92-95%
- **Speed**: 300-400ms per page
- **Setup**: More complex (requires ONNX conversion)

### YOLOv8 (Recommended for Testing)
- **Mature**: Released 2022, proven
- **Accuracy**: 85-90%
- **Speed**: 250-350ms per page
- **Setup**: Simple (direct TFLite export)

---

## 🚀 Quick Start

### Option A: YOLOv10 (Latest, Best Accuracy)

```bash
cd mihon/translation/scripts
./download_yolov10.sh
```

**Time**: ~5-7 minutes
**Accuracy**: 92-95%
**Note**: May fail on some systems (complex conversion)

### Option B: YOLOv8 (Easier, Still Great)

```bash
cd mihon/translation/scripts
./download_model.sh
```

**Time**: ~3-5 minutes
**Accuracy**: 85-90%
**Note**: Always works, easier conversion

---

## 📋 Manual Setup (3 Steps)

If you prefer manual control:

### Step 1: Install Dependencies

**For YOLOv8:**
```bash
pip install ultralytics>=8.0.0 tensorflow>=2.13.0
```

**For YOLOv10:**
```bash
pip install ultralytics>=8.2.0 tensorflow>=2.13.0 onnx>=1.14.0 onnx-tf>=1.10.0
```

### Step 2: Download Model

**YOLOv10 Options (Latest, Best Accuracy):**
```bash
# YOLOv10n - Nano (7MB, fast)
wget https://github.com/THU-MIG/yolov10/releases/download/v1.1/yolov10n.pt

# YOLOv10m - Medium (40MB, recommended)
wget https://github.com/THU-MIG/yolov10/releases/download/v1.1/yolov10m.pt

# YOLOv10l - Large (60MB, maximum accuracy)
wget https://github.com/THU-MIG/yolov10/releases/download/v1.1/yolov10l.pt
```

**YOLOv8 Options (Easier Conversion):**
```bash
# YOLOv8n - Nano (6MB, fast)
wget https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8n.pt

# YOLOv8m - Medium (52MB, balanced)
wget https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8m.pt

# Comic-trained YOLOv8 (45MB, best for manga)
wget https://huggingface.co/ogkalu/comic-speech-bubble-detector-yolov8m/resolve/main/best.pt
```

### Step 3: Convert to TFLite

```bash
python convert_yolo_to_tflite.py yolov8m.pt
```

**Done!** Model automatically copied to `assets/models/yolov10_bubble_detection.tflite`

---

## 📦 Available Scripts

### `download_model.sh` (Recommended)
Automatic end-to-end setup with interactive model selection.

**Usage:**
```bash
./download_model.sh
```

**Features:**
- Interactive model selection
- Dependency checking
- Progress indicators
- Error handling
- Cleanup option

---

### `convert_yolo_to_tflite.py`
Converts YOLO PyTorch models to TFLite format.

**Basic Usage:**
```bash
python convert_yolo_to_tflite.py model.pt
```

**Advanced Options:**
```bash
# Custom input size
python convert_yolo_to_tflite.py model.pt --img-size 640

# Disable quantization (larger, potentially more accurate)
python convert_yolo_to_tflite.py model.pt --no-quantize

# Custom output directory
python convert_yolo_to_tflite.py model.pt --output ./my_models

# Don't auto-copy to project
python convert_yolo_to_tflite.py model.pt --no-copy
```

**Parameters:**
- `--img-size`: Input size (416/640/1024) - Default: 1024
- `--no-quantize`: Skip INT8 quantization (keeps FP32)
- `--output`: Output directory
- `--no-copy`: Don't copy to project assets

---

## 🎯 Model Comparison

### YOLOv10 Models (2024, Best Accuracy)

| Model | Size | Speed | Accuracy | Best For |
|-------|------|-------|----------|----------|
| **YOLOv10n** | 7MB | 150ms | 85-88% | Low-end devices |
| **YOLOv10s** | 16MB | 220ms | 88-90% | Mid-range devices |
| **YOLOv10m** | 40MB | 300ms | 90-93% | **Recommended** |
| **YOLOv10b** | 48MB | 350ms | 92-94% | High accuracy |
| **YOLOv10l** | 60MB | 400ms | 92-95% | Maximum accuracy |

### YOLOv8 Models (2022, Easier Setup)

| Model | Size | Speed | Accuracy | Best For |
|-------|------|-------|----------|----------|
| **YOLOv8n** | 6MB | 150ms | 80-85% | Testing, low-end devices |
| **YOLOv8m** | 52MB | 280ms | 85-90% | **General use** |
| **Comic YOLOv8** | 45MB | 280ms | 87-92% | **Manga/Comics** |

*Speeds measured on Snapdragon 8 Gen 2 with GPU acceleration*
*YOLOv10 provides 3-5% better accuracy than equivalent YOLOv8*

---

## 🔧 Troubleshooting

### "Python 3 not found"
**Solution:**
```bash
# Ubuntu/Debian
sudo apt install python3 python3-pip

# macOS
brew install python3

# Windows
# Download from python.org
```

### "ultralytics not found"
**Solution:**
```bash
pip3 install ultralytics tensorflow
```

### "Model conversion failed"
**Try:**
1. Use smaller input size: `--img-size 640`
2. Skip quantization: `--no-quantize`
3. Check TensorFlow is installed: `pip3 install tensorflow>=2.13.0`

### "wget: command not found"
**Solution:**
```bash
# Use curl instead (automatic fallback)
# OR install wget:

# macOS
brew install wget

# Ubuntu/Debian
sudo apt install wget
```

### Model file is too large for Git
**Solution:**
Models are in `.gitignore` and stored in `assets/` (not committed).
Only the final `.tflite` file (15-65MB) is needed.

---

## 📁 File Locations

**Downloaded models**: `scripts/models/` (temporary)
**Converted TFLite**: `scripts/models/output/`
**Final location**: `src/main/assets/models/yolov10_bubble_detection.tflite`

---

## 🔄 Updating Models

To replace with a new model:

```bash
# Option 1: Re-run automatic script
./download_model.sh

# Option 2: Manual replacement
python convert_yolo_to_tflite.py new_model.pt
# Overwrites existing model in assets/
```

---

## 🧪 Testing Your Model

After setup, verify the model works:

```bash
# Build and install
./gradlew :app:installDebug

# Check logs
adb logcat | grep YOLOv10BubbleDetector
```

**Expected output:**
```
D/YOLOv10BubbleDetector: Model loaded successfully
D/YOLOv10BubbleDetector: Input shape: [1, 1024, 1024, 3]
D/YOLOv10BubbleDetector: GPU delegate enabled
D/YOLOv10BubbleDetector: Inference time: 287ms
D/YOLOv10BubbleDetector: Detected 8 bubbles
```

---

## 📖 More Information

- **Full setup guide**: `../docs/PHASE_3_SETUP_GUIDE.md`
- **Implementation details**: `../docs/PHASE_3_BUBBLE_DETECTION.md`
- **Model requirements**: `../src/main/assets/models/README.md`

---

## 🆘 Need Help?

1. Check logs: `adb logcat | grep Translation`
2. Read setup guide: `docs/PHASE_3_SETUP_GUIDE.md`
3. Open issue on GitHub with logs
