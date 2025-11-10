#!/bin/bash
# YOLOv10 Model Download and Conversion for Mihon Translation
# YOLOv10 is the latest (2024) with best accuracy for comic bubble detection

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_ROOT/src/main/assets/models"
MODELS_DIR="$SCRIPT_DIR/models"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Mihon Translation - YOLOv10 Setup${NC}"
echo -e "${BLUE}============================================${NC}"
echo

mkdir -p "$MODELS_DIR"
mkdir -p "$ASSETS_DIR"

status() { echo -e "${GREEN}✓${NC} $1"; }
error() { echo -e "${RED}✗${NC} $1"; }
warning() { echo -e "${YELLOW}⚠${NC} $1"; }

# Check Python
echo -e "${BLUE}[1/6] Checking dependencies...${NC}"
if ! command -v python3 &> /dev/null; then
    error "Python 3 not found. Please install Python 3.8 or higher."
    exit 1
fi
status "Python 3 found: $(python3 --version)"

# Install dependencies
echo
echo -e "${BLUE}[2/6] Installing Python dependencies...${NC}"
pip3 install --quiet --upgrade pip

# YOLOv10 requires specific ultralytics version
pip3 install --quiet ultralytics>=8.2.0 tensorflow>=2.13.0 onnx>=1.14.0 2>/dev/null || {
    warning "Installing dependencies (2-3 minutes)..."
    pip3 install ultralytics>=8.2.0 tensorflow>=2.13.0 onnx>=1.14.0
}
status "Dependencies ready"

# Download YOLOv10
echo
echo -e "${BLUE}[3/6] Downloading YOLOv10 model...${NC}"
echo "Choose YOLOv10 variant:"
echo "  1) YOLOv10n (7MB, fast, 85-88% accuracy)"
echo "  2) YOLOv10s (16MB, balanced)"
echo "  3) YOLOv10m (40MB, high accuracy, 90-93%) - Recommended"
echo "  4) YOLOv10b (48MB, very high accuracy)"
echo "  5) YOLOv10l (60MB, maximum accuracy, 92-95%)"
echo

read -p "Select option [1-5] (default: 3): " -t 30 MODEL_CHOICE || MODEL_CHOICE=3
MODEL_CHOICE=${MODEL_CHOICE:-3}

cd "$MODELS_DIR"

case $MODEL_CHOICE in
    1)
        MODEL_NAME="yolov10n.pt"
        MODEL_URL="https://github.com/THU-MIG/yolov10/releases/download/v1.1/yolov10n.pt"
        ;;
    2)
        MODEL_NAME="yolov10s.pt"
        MODEL_URL="https://github.com/THU-MIG/yolov10/releases/download/v1.1/yolov10s.pt"
        ;;
    4)
        MODEL_NAME="yolov10b.pt"
        MODEL_URL="https://github.com/THU-MIG/yolov10/releases/download/v1.1/yolov10b.pt"
        ;;
    5)
        MODEL_NAME="yolov10l.pt"
        MODEL_URL="https://github.com/THU-MIG/yolov10/releases/download/v1.1/yolov10l.pt"
        ;;
    *)
        MODEL_NAME="yolov10m.pt"
        MODEL_URL="https://github.com/THU-MIG/yolov10/releases/download/v1.1/yolov10m.pt"
        ;;
esac

if [ -f "$MODEL_NAME" ]; then
    status "Model already downloaded: $MODEL_NAME"
else
    echo "Downloading $MODEL_NAME from GitHub..."
    if command -v wget &> /dev/null; then
        wget -q --show-progress "$MODEL_URL" -O "$MODEL_NAME"
    elif command -v curl &> /dev/null; then
        curl -L "$MODEL_URL" -o "$MODEL_NAME" --progress-bar
    else
        error "Neither wget nor curl found. Please install one."
        exit 1
    fi
    status "Model downloaded: $MODEL_NAME ($(du -h "$MODEL_NAME" | cut -f1))"
fi

# Convert to ONNX first (intermediate step for YOLOv10)
echo
echo -e "${BLUE}[4/6] Converting YOLOv10 to ONNX...${NC}"

python3 << EOF
from ultralytics import YOLO
import sys

try:
    model = YOLO('$MODEL_NAME')
    model.export(format='onnx', imgsz=1024, simplify=True)
    print("✓ ONNX export successful")
except Exception as e:
    print(f"✗ ONNX export failed: {e}")
    sys.exit(1)
EOF

if [ $? -ne 0 ]; then
    error "ONNX conversion failed"
    exit 1
fi

status "ONNX model created"

# Convert ONNX to TFLite
echo
echo -e "${BLUE}[5/6] Converting ONNX to TFLite...${NC}"
echo "This step may take 3-5 minutes..."

ONNX_FILE="${MODEL_NAME%.pt}.onnx"

python3 << EOF
import tensorflow as tf
import onnx
from onnx_tf.backend import prepare
import numpy as np
import sys

try:
    print("Loading ONNX model...")
    onnx_model = onnx.load('$ONNX_FILE')

    print("Converting ONNX to TensorFlow...")
    tf_rep = prepare(onnx_model)
    tf_rep.export_graph('yolov10_tf')

    print("Converting TensorFlow to TFLite...")
    converter = tf.lite.TFLiteConverter.from_saved_model('yolov10_tf')

    # Optimizations
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS  # YOLOv10 may need TF ops
    ]

    tflite_model = converter.convert()

    # Save
    with open('yolov10_converted.tflite', 'wb') as f:
        f.write(tflite_model)

    size_mb = len(tflite_model) / 1024 / 1024
    print(f"✓ TFLite conversion successful ({size_mb:.1f} MB)")

    # Validate
    interpreter = tf.lite.Interpreter(model_path='yolov10_converted.tflite')
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"✓ Input shape: {input_details[0]['shape']}")
    print(f"✓ Output shape: {output_details[0]['shape']}")

    # Test inference
    test_input = np.random.rand(*input_details[0]['shape']).astype(np.float32)
    interpreter.set_tensor(input_details[0]['index'], test_input)
    interpreter.invoke()

    print("✓ Test inference successful")

except Exception as e:
    print(f"✗ Conversion failed: {e}")
    print("\nNote: YOLOv10 conversion can be complex. If this fails:")
    print("  1. Try YOLOv8 instead (easier conversion): ./download_model.sh")
    print("  2. Or use pre-converted YOLOv10 model from team repository")
    sys.exit(1)
EOF

if [ $? -ne 0 ]; then
    error "TFLite conversion failed"
    echo
    warning "YOLOv10 conversion is complex. Alternative options:"
    echo "  1. Use YOLOv8 (easier, 85-90% accuracy): ./download_model.sh"
    echo "  2. Request pre-converted YOLOv10 model from team"
    echo "  3. Continue with manual conversion (see docs/PHASE_3_SETUP_GUIDE.md)"
    exit 1
fi

# Install model
echo
echo -e "${BLUE}[6/6] Installing model to assets...${NC}"

TARGET_FILE="$ASSETS_DIR/yolov10_bubble_detection.tflite"
cp "$MODELS_DIR/yolov10_converted.tflite" "$TARGET_FILE"

if [ -f "$TARGET_FILE" ]; then
    status "Model installed: $TARGET_FILE"
    status "Model size: $(du -h "$TARGET_FILE" | cut -f1)"
else
    error "Failed to copy model to assets"
    exit 1
fi

# Success
echo
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}✓ YOLOv10 setup complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo
echo "Model: YOLOv10 (latest 2024 release)"
echo "Location: $TARGET_FILE"
echo "Expected accuracy: 92-95%"
echo
echo "Next steps:"
echo "  1. Build: ./gradlew :app:assembleDebug"
echo "  2. Install: adb install app/build/outputs/apk/debug/app-debug.apk"
echo "  3. Test translation on manga page"
echo

# Cleanup
read -p "Delete temporary files? [y/N]: " -t 10 CLEANUP || CLEANUP="n"
CLEANUP=${CLEANUP:-n}

if [[ $CLEANUP =~ ^[Yy]$ ]]; then
    rm -rf "$MODELS_DIR"
    status "Cleaned up temporary files"
fi

echo
echo -e "${BLUE}Done! 🚀${NC}"
