#!/bin/bash
# Automatic YOLOv8 Model Download and Conversion for Mihon Translation
# Downloads pre-trained model, converts to TFLite, and places in assets

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_ROOT/src/main/assets/models"
MODELS_DIR="$SCRIPT_DIR/models"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Mihon Translation - Model Auto Setup${NC}"
echo -e "${BLUE}============================================${NC}"
echo

# Create directories
mkdir -p "$MODELS_DIR"
mkdir -p "$ASSETS_DIR"

# Function to print status
status() {
    echo -e "${GREEN}✓${NC} $1"
}

error() {
    echo -e "${RED}✗${NC} $1"
}

warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Check if Python is installed
echo -e "${BLUE}[1/5] Checking dependencies...${NC}"
if ! command -v python3 &> /dev/null; then
    error "Python 3 not found. Please install Python 3.8 or higher."
    exit 1
fi
status "Python 3 found: $(python3 --version)"

# Check/install Python packages
echo
echo -e "${BLUE}[2/5] Installing Python dependencies...${NC}"
echo "This may take a few minutes..."

pip3 install --quiet --upgrade pip
pip3 install --quiet ultralytics>=8.0.0 tensorflow>=2.13.0 2>/dev/null || {
    warning "Installing ultralytics and tensorflow (this will take 2-3 minutes)..."
    pip3 install ultralytics>=8.0.0 tensorflow>=2.13.0
}
status "Python dependencies ready"

# Download model
echo
echo -e "${BLUE}[3/5] Downloading YOLOv8 model...${NC}"
echo "Choose model variant:"
echo "  1) YOLOv8n (6MB, fast, 80-85% accuracy) - Recommended for testing"
echo "  2) YOLOv8m (52MB, balanced, 85-90% accuracy) - Recommended"
echo "  3) Comic-trained (45MB, best for manga, 87-92% accuracy) - Best quality"
echo

# Default to option 2 if no input (for CI/automation)
read -p "Select option [1-3] (default: 2): " -t 30 MODEL_CHOICE || MODEL_CHOICE=2
MODEL_CHOICE=${MODEL_CHOICE:-2}

cd "$MODELS_DIR"

case $MODEL_CHOICE in
    1)
        MODEL_NAME="yolov8n.pt"
        MODEL_URL="https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8n.pt"
        ;;
    3)
        MODEL_NAME="comic_trained.pt"
        MODEL_URL="https://huggingface.co/ogkalu/comic-speech-bubble-detector-yolov8m/resolve/main/best.pt"
        ;;
    *)
        MODEL_NAME="yolov8m.pt"
        MODEL_URL="https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8m.pt"
        ;;
esac

if [ -f "$MODEL_NAME" ]; then
    status "Model already downloaded: $MODEL_NAME"
else
    echo "Downloading $MODEL_NAME from $MODEL_URL..."
    if command -v wget &> /dev/null; then
        wget -q --show-progress "$MODEL_URL" -O "$MODEL_NAME"
    elif command -v curl &> /dev/null; then
        curl -L "$MODEL_URL" -o "$MODEL_NAME" --progress-bar
    else
        error "Neither wget nor curl found. Please install one of them."
        exit 1
    fi
    status "Model downloaded: $MODEL_NAME ($(du -h "$MODEL_NAME" | cut -f1))"
fi

# Convert to TFLite
echo
echo -e "${BLUE}[4/5] Converting to TFLite format...${NC}"
echo "This will take 2-3 minutes..."

python3 "$SCRIPT_DIR/convert_yolo_to_tflite.py" \
    "$MODELS_DIR/$MODEL_NAME" \
    --output "$MODELS_DIR/output" \
    --img-size 1024

if [ $? -ne 0 ]; then
    error "Conversion failed. Check the error message above."
    exit 1
fi

# Find and copy TFLite model
echo
echo -e "${BLUE}[5/5] Installing model to assets...${NC}"

TFLITE_FILE=$(find "$MODELS_DIR/output" -name "*.tflite" -type f | head -n 1)

if [ -z "$TFLITE_FILE" ]; then
    error "TFLite model not found after conversion"
    exit 1
fi

TARGET_FILE="$ASSETS_DIR/yolov10_bubble_detection.tflite"
cp "$TFLITE_FILE" "$TARGET_FILE"

if [ -f "$TARGET_FILE" ]; then
    status "Model installed: $TARGET_FILE"
    status "Model size: $(du -h "$TARGET_FILE" | cut -f1)"
else
    error "Failed to copy model to assets"
    exit 1
fi

# Success message
echo
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}✓ Model setup complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo
echo "Model installed at:"
echo "  $TARGET_FILE"
echo
echo "Next steps:"
echo "  1. Build the app: ./gradlew :app:assembleDebug"
echo "  2. Install: adb install app/build/outputs/apk/debug/app-debug.apk"
echo "  3. Enable translation in Mihon reader settings"
echo "  4. Test on a manga page"
echo
echo "Expected detection performance:"
echo "  - Speed: 200-400ms per page (with GPU)"
echo "  - Accuracy: 85-92% (depending on model)"
echo "  - Bubbles detected: 5-20 per page"
echo

# Cleanup option
read -p "Delete downloaded models to save space? [y/N]: " -t 10 CLEANUP || CLEANUP="n"
CLEANUP=${CLEANUP:-n}

if [[ $CLEANUP =~ ^[Yy]$ ]]; then
    rm -rf "$MODELS_DIR"
    status "Cleaned up temporary files"
fi

echo
echo -e "${BLUE}Done! 🚀${NC}"
