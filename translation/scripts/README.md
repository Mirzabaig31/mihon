# Translation Model Scripts

Quick tools to set up bubble detection models for Mihon translation.

---

## 🚀 Quick Start (One Command)

```bash
cd mihon/translation/scripts
./download_model.sh
```

This will:
1. ✅ Install Python dependencies
2. ✅ Download YOLOv8 model (you choose variant)
3. ✅ Convert to TFLite format
4. ✅ Copy to assets/models/ folder
5. ✅ Ready to build!

**Time**: ~5 minutes (depending on internet speed)

---

## 📋 Manual Setup (3 Steps)

If you prefer manual control:

### Step 1: Install Dependencies

```bash
pip install ultralytics>=8.0.0 tensorflow>=2.13.0
```

### Step 2: Download Model

**Option A: Quick Testing (YOLOv8m - 52MB)**
```bash
wget https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8m.pt
```

**Option B: Best for Manga (Comic-trained - 45MB)**
```bash
wget https://huggingface.co/ogkalu/comic-speech-bubble-detector-yolov8m/resolve/main/best.pt
mv best.pt comic_trained.pt
```

**Option C: Fast/Small (YOLOv8n - 6MB)**
```bash
wget https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8n.pt
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

| Model | Size | Download Time | Accuracy | Best For |
|-------|------|---------------|----------|----------|
| **YOLOv8n** | 6MB | 10 sec | 80-85% | Testing, low-end devices |
| **YOLOv8m** | 52MB | 1 min | 85-90% | **General use** |
| **Comic-trained** | 45MB | 1 min | 87-92% | **Manga/Comics** |

*After conversion, models are reduced 3-4x via INT8 quantization*

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
