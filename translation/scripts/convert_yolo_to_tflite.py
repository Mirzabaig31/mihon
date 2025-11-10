#!/usr/bin/env python3
"""
YOLOv8/v10 to TFLite Conversion Script for Mihon Translation Feature
Converts YOLO models to optimized TFLite format for Android bubble detection

Usage:
    python convert_yolo_to_tflite.py yolov8m.pt
    python convert_yolo_to_tflite.py yolov8m.pt --img-size 640 --no-quantize
    python convert_yolo_to_tflite.py comic_trained.pt --output ./models
"""

import argparse
import sys
from pathlib import Path


def convert_yolo_to_tflite(
    model_path: str,
    output_dir: str = './output',
    img_size: int = 1024,
    quantize: bool = True,
    copy_to_project: bool = True,
):
    """
    Convert YOLOv8/v10 model to TFLite format

    Args:
        model_path: Path to YOLO .pt model file
        output_dir: Directory to save TFLite model
        img_size: Input image size (1024 recommended for comics)
        quantize: Apply INT8 quantization (reduces size 4x)
        copy_to_project: Automatically copy to Mihon assets directory
    """

    # Check if ultralytics is installed
    try:
        from ultralytics import YOLO
    except ImportError:
        print("ERROR: ultralytics package not installed")
        print("\nInstall with:")
        print("  pip install ultralytics>=8.0.0")
        print("\nFor full setup, also install:")
        print("  pip install tensorflow>=2.13.0")
        sys.exit(1)

    # Validate model file exists
    model_file = Path(model_path)
    if not model_file.exists():
        print(f"ERROR: Model file not found: {model_path}")
        sys.exit(1)

    # Create output directory
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    print("="*60)
    print("YOLO to TFLite Conversion for Mihon")
    print("="*60)
    print(f"Model: {model_path}")
    print(f"Output: {output_dir}")
    print(f"Input size: {img_size}x{img_size}")
    print(f"Quantization: {'INT8' if quantize else 'FP32'}")
    print("="*60)

    # Load YOLO model
    print("\n[1/4] Loading YOLO model...")
    try:
        model = YOLO(str(model_file))
        print(f"✓ Model loaded: {model.task} task")
    except Exception as e:
        print(f"✗ Failed to load model: {e}")
        sys.exit(1)

    # Export to TFLite
    print(f"\n[2/4] Exporting to TFLite (this may take 2-5 minutes)...")
    try:
        export_path = model.export(
            format='tflite',
            imgsz=img_size,
            int8=quantize,
            optimize=True,
            simplify=True,
        )
        print(f"✓ Export successful: {export_path}")
    except Exception as e:
        print(f"✗ Export failed: {e}")
        print("\nTroubleshooting:")
        print("  - Try without quantization: --no-quantize")
        print("  - Try smaller input size: --img-size 640")
        print("  - Ensure tensorflow is installed: pip install tensorflow")
        sys.exit(1)

    # Validate converted model
    print("\n[3/4] Validating TFLite model...")
    try:
        import tensorflow as tf
        import numpy as np

        # Find the TFLite file
        export_dir = Path(export_path).parent
        tflite_files = list(export_dir.glob('*.tflite'))

        if not tflite_files:
            print("✗ No TFLite file found in export directory")
            sys.exit(1)

        tflite_path = tflite_files[0]
        size_mb = tflite_path.stat().st_size / (1024 * 1024)

        print(f"✓ TFLite file: {tflite_path.name}")
        print(f"✓ Model size: {size_mb:.2f} MB")

        # Load and validate
        interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
        interpreter.allocate_tensors()

        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()

        print(f"✓ Input shape: {input_details[0]['shape']}")
        print(f"✓ Input type: {input_details[0]['dtype']}")
        print(f"✓ Output shape: {output_details[0]['shape']}")
        print(f"✓ Output type: {output_details[0]['dtype']}")

        # Test inference
        test_input = np.random.rand(*input_details[0]['shape']).astype(np.float32)
        interpreter.set_tensor(input_details[0]['index'], test_input)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]['index'])

        print(f"✓ Test inference successful! Output shape: {output.shape}")

    except ImportError:
        print("⚠ TensorFlow not installed, skipping validation")
        print("  Install with: pip install tensorflow")
        # Still continue without validation
        tflite_files = list(Path(export_path).parent.glob('*.tflite'))
        if tflite_files:
            tflite_path = tflite_files[0]
            size_mb = tflite_path.stat().st_size / (1024 * 1024)
            print(f"✓ TFLite file: {tflite_path.name} ({size_mb:.2f} MB)")
        else:
            print("✗ No TFLite file found")
            sys.exit(1)
    except Exception as e:
        print(f"⚠ Validation failed: {e}")
        print("  Model may still work, proceeding...")

    # Copy to Mihon project
    if copy_to_project:
        print("\n[4/4] Copying to Mihon project...")

        # Try to find Mihon project assets directory
        possible_paths = [
            Path(__file__).parent.parent / 'src' / 'main' / 'assets' / 'models',
            Path.cwd() / 'translation' / 'src' / 'main' / 'assets' / 'models',
            Path.cwd() / 'src' / 'main' / 'assets' / 'models',
        ]

        mihon_assets = None
        for path in possible_paths:
            if path.exists():
                mihon_assets = path
                break

        if mihon_assets:
            mihon_assets.mkdir(parents=True, exist_ok=True)
            target_path = mihon_assets / 'yolov10_bubble_detection.tflite'

            import shutil
            shutil.copy(tflite_path, target_path)

            print(f"✓ Copied to: {target_path}")
            print(f"✓ Integration complete!")
        else:
            print("⚠ Mihon assets directory not found")
            print(f"  Manually copy {tflite_path} to:")
            print(f"  translation/src/main/assets/models/yolov10_bubble_detection.tflite")
    else:
        print(f"\n[4/4] Skipping copy to project")
        print(f"  Model saved at: {tflite_path}")

    print("\n" + "="*60)
    print("CONVERSION COMPLETE! ✓")
    print("="*60)
    print("\nNext steps:")
    if not copy_to_project or not mihon_assets:
        print("1. Copy the TFLite model to:")
        print("   translation/src/main/assets/models/yolov10_bubble_detection.tflite")
    print("2. Build and run the Mihon app")
    print("3. Enable translation in reader settings")
    print("4. Test on a manga page")
    print("\nFor more details, see:")
    print("  translation/docs/PHASE_3_BUBBLE_DETECTION.md")
    print("="*60)


def main():
    parser = argparse.ArgumentParser(
        description='Convert YOLO model to TFLite for Mihon Translation',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Convert YOLOv8 medium model with INT8 quantization
  python convert_yolo_to_tflite.py yolov8m.pt

  # Convert with custom input size
  python convert_yolo_to_tflite.py yolov8m.pt --img-size 640

  # Convert without quantization (larger but potentially more accurate)
  python convert_yolo_to_tflite.py yolov8m.pt --no-quantize

  # Convert comic-trained model
  python convert_yolo_to_tflite.py comic_trained.pt --output ./models

Pre-trained models:
  - YOLOv8n: https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8n.pt
  - YOLOv8m: https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8m.pt
  - Comic detector: https://huggingface.co/ogkalu/comic-speech-bubble-detector-yolov8m
        """
    )

    parser.add_argument(
        'model',
        help='Path to YOLO .pt model file'
    )
    parser.add_argument(
        '--img-size',
        type=int,
        default=1024,
        help='Input image size (default: 1024, recommended for comics)'
    )
    parser.add_argument(
        '--no-quantize',
        action='store_true',
        help='Disable INT8 quantization (results in larger model)'
    )
    parser.add_argument(
        '--output',
        default='./output',
        help='Output directory (default: ./output)'
    )
    parser.add_argument(
        '--no-copy',
        action='store_true',
        help='Do not copy model to Mihon project automatically'
    )

    args = parser.parse_args()

    convert_yolo_to_tflite(
        model_path=args.model,
        output_dir=args.output,
        img_size=args.img_size,
        quantize=not args.no_quantize,
        copy_to_project=not args.no_copy,
    )


if __name__ == '__main__':
    main()
