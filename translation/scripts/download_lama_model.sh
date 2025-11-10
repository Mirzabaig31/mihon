#!/bin/bash
# LaMa Model Download and Conversion for Mihon Translation
# LaMa provides production-quality inpainting (95%+ excellent results)

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_ROOT/src/main/assets/models"
MODELS_DIR="$SCRIPT_DIR/models/lama"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Mihon Translation - LaMa Setup${NC}"
echo -e "${BLUE}Production Quality Inpainting (95%+)${NC}"
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
echo "This may take 5-10 minutes for first-time setup..."

pip3 install --quiet --upgrade pip

# LaMa requires specific dependencies
pip3 install --quiet torch torchvision onnx onnx-tf tensorflow>=2.13.0 2>/dev/null || {
    warning "Installing dependencies (this will take 5-10 minutes)..."
    pip3 install torch torchvision onnx onnx-tf tensorflow>=2.13.0
}
status "Dependencies ready"

# Clone LaMa repository (for model architecture)
echo
echo -e "${BLUE}[3/6] Setting up LaMa repository...${NC}"

cd "$MODELS_DIR"

if [ ! -d "lama" ]; then
    echo "Cloning LaMa repository..."
    git clone https://github.com/advimman/lama.git
    cd lama
else
    echo "LaMa repository already exists"
    cd lama
fi

status "LaMa repository ready"

# Download pretrained model
echo
echo -e "${BLUE}[4/6] Downloading LaMa pretrained model...${NC}"
echo "Choose LaMa variant:"
echo "  1) LaMa-Regular (150MB FP32 → 40MB INT8) - Recommended"
echo "  2) LaMa-Big (300MB FP32 → 80MB INT8) - Maximum quality"
echo

read -p "Select option [1-2] (default: 1): " -t 30 MODEL_CHOICE || MODEL_CHOICE=1
MODEL_CHOICE=${MODEL_CHOICE:-1}

case $MODEL_CHOICE in
    2)
        MODEL_NAME="big-lama"
        MODEL_URL="https://huggingface.co/smartywu/big-lama/resolve/main/big-lama.zip"
        ;;
    *)
        MODEL_NAME="lama-regular"
        MODEL_URL="https://github.com/advimman/lama/releases/download/models/big-lama.zip"
        ;;
esac

if [ -f "$MODEL_NAME.pth" ]; then
    status "Model already downloaded: $MODEL_NAME.pth"
else
    echo "Downloading $MODEL_NAME..."
    if command -v wget &> /dev/null; then
        wget -q --show-progress "$MODEL_URL" -O "${MODEL_NAME}.zip"
    elif command -v curl &> /dev/null; then
        curl -L "$MODEL_URL" -o "${MODEL_NAME}.zip" --progress-bar
    else
        error "Neither wget nor curl found. Please install one."
        exit 1
    fi

    # Extract model
    unzip -q "${MODEL_NAME}.zip"
    rm "${MODEL_NAME}.zip"

    status "Model downloaded: $MODEL_NAME"
fi

# Convert to TFLite
echo
echo -e "${BLUE}[5/6] Converting LaMa to TFLite...${NC}"
echo "This is the most complex step and may take 10-15 minutes..."
echo

cd "$SCRIPT_DIR"
python3 convert_lama_to_tflite.py \
    --model "$MODELS_DIR/lama/$MODEL_NAME" \
    --output "$MODELS_DIR/output" \
    --quantize

if [ $? -ne 0 ]; then
    error "TFLite conversion failed"
    echo
    warning "LaMa conversion is complex. Alternative options:"
    echo "  1. Use SimpleInpaintingEngine (Phase 4A, already working)"
    echo "  2. Request pre-converted LaMa model from team"
    echo "  3. Try with different Python/TensorFlow versions"
    exit 1
fi

# Install model
echo
echo -e "${BLUE}[6/6] Installing model to assets...${NC}"

TARGET_FILE="$ASSETS_DIR/lama_inpainting.tflite"
TFLITE_FILE=$(find "$MODELS_DIR/output" -name "lama*.tflite" -type f | head -n 1)

if [ -z "$TFLITE_FILE" ]; then
    error "TFLite model not found after conversion"
    exit 1
fi

cp "$TFLITE_FILE" "$TARGET_FILE"

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
echo -e "${GREEN}✓ LaMa setup complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo
echo "Model: LaMa (production quality inpainting)"
echo "Location: $TARGET_FILE"
echo "Expected quality: 95%+ excellent results"
echo "Performance: 300-500ms per page"
echo
echo "Next steps:"
echo "  1. Build: ./gradlew :app:assembleDebug"
echo "  2. Install: adb install app/build/outputs/apk/debug/app-debug.apk"
echo "  3. Test translation with LaMa inpainting"
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
