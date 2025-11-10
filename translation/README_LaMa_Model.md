# LaMa-Dilated Model Setup

## Model Information
- **Model:** Qualcomm LaMa-Dilated (Production-Quality Inpainting)
- **Format:** TensorFlow Lite (FP32)
- **Size:** 174MB
- **Quality:** 95%+ excellent inpainting results
- **Performance:** 86-429ms inference on Qualcomm devices

## Download Instructions

The LaMa model is too large for GitHub (exceeds 100MB limit). Download it manually:

### Method 1: Using Hugging Face Hub
```bash
cd translation/scripts
pip3 install huggingface_hub
python3 -c "
from huggingface_hub import hf_hub_download
hf_hub_download(
    repo_id='qualcomm/LaMa-Dilated', 
    filename='LaMa-Dilated_float.tflite',
    local_dir='../src/main/assets/models'
)
"
mv ../src/main/assets/models/LaMa-Dilated_float.tflite ../src/main/assets/models/lama_inpainting.tflite
```

### Method 2: Direct Download
```bash
cd translation/scripts
./lama_env/bin/python3 -c "
from huggingface_hub import hf_hub_download
hf_hub_download(
    repo_id='qualcomm/LaMa-Dilated', 
    filename='LaMa-Dilated_float.tflite',
    local_dir='../src/main/assets/models'
)
"
mv ../src/main/assets/models/LaMa-Dilated_float.tflite ../src/main/assets/models/lama_inpainting.tflite
```

### Method 3: Manual Download
1. Visit: https://huggingface.co/qualcomm/LaMa-Dilated
2. Download `LaMa-Dilated_float.tflite` (174MB)
3. Place in: `translation/src/main/assets/models/lama_inpainting.tflite`

## Usage
After installation, the model will be automatically detected and used by Phase 4B LaMa inpainting engine.

## Expected Performance
- **Inference Time:** 86-429ms (varies by device)
- **Quality:** Production-quality (95%+ excellent results)
- **Memory:** 3-332MB peak usage
- **Compatibility:** Works with most Android devices

## Model Optimization
For production deployment, consider:
1. **FP16 conversion** (~87MB, minimal quality loss)
2. **INT8 quantization** (~70MB, slight quality reduction)
3. **Dynamic loading** for memory-constrained devices

## Troubleshooting
If model doesn't load:
1. Verify file path: `translation/src/main/assets/models/lama_inpainting.tflite`
2. Check file integrity (174MB expected size)
3. Ensure Android assets are properly included in build
