#!/bin/bash
# Quick test script - Downloads smallest YOLOv8n model for immediate testing
# Run this on your local machine for fastest setup

set -e

echo "🚀 Quick Test Setup (YOLOv8n - Fastest)"
echo "========================================"
echo

# Install minimal dependencies
echo "[1/3] Installing dependencies..."
pip3 install --quiet ultralytics tensorflow

# Download smallest model (6MB - very fast)
echo "[2/3] Downloading YOLOv8n (6MB)..."
cd "$(dirname "$0")/models" || mkdir -p "$(dirname "$0")/models" && cd "$(dirname "$0")/models"

wget -q --show-progress https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8n.pt

# Convert
echo "[3/3] Converting to TFLite..."
python3 << 'EOF'
from ultralytics import YOLO
model = YOLO('yolov8n.pt')
model.export(format='tflite', imgsz=640, int8=True)
print("✓ Conversion complete!")
EOF

# Copy to assets
SCRIPT_DIR="$(dirname "$0")"
ASSETS_DIR="$SCRIPT_DIR/../src/main/assets/models"
mkdir -p "$ASSETS_DIR"

TFLITE=$(find . -name "*.tflite" -type f | head -n 1)
cp "$TFLITE" "$ASSETS_DIR/yolov10_bubble_detection.tflite"

echo
echo "✓ Setup complete! Model ready at:"
echo "  $ASSETS_DIR/yolov10_bubble_detection.tflite"
echo
echo "Next: ./gradlew :app:assembleDebug"
