"""
classifier.py - VERDE Pi backend
Loads the exported ExecuTorch vision model once and classifies a 224x224 image.
Run directly to test on the newest saved scan and measure inference time.
"""

import json
import re
import statistics
import sys
import time
from pathlib import Path

import numpy as np
import torch
from PIL import Image
from executorch.runtime import Runtime

MODELS_DIR = Path.home() / "projects" / "verde" / "models"
CAPTURES_DIR = Path.home() / "projects" / "verde" / "captures"

# WHY: these must match training exactly (ImageNet normalisation),
# or accuracy drops for reasons unrelated to the model.
MEAN = torch.tensor([0.485, 0.456, 0.406]).view(1, 3, 1, 1)
STD = torch.tensor([0.229, 0.224, 0.225]).view(1, 3, 1, 1)

# Loaded once when the file is imported, so each scan only pays for inference.
CLASS_NAMES = json.loads((MODELS_DIR / "class_names.json").read_text())
_method = Runtime.get().load_program(MODELS_DIR / "model.pte").load_method("forward")


def clean_label(name):
    """Turn a dataset folder name such as '6-Paper' into 'Paper'."""
    return re.sub(r"^\d+-", "", name)


def preprocess(image):
    """PIL image (224x224) -> normalised float tensor of shape (1, 3, 224, 224)."""
    array = np.asarray(image.convert("RGB"), dtype=np.float32) / 255.0
    tensor = torch.from_numpy(array).permute(2, 0, 1).unsqueeze(0)
    return ((tensor - MEAN) / STD).contiguous()


def classify(image):
    """Return (label, confidence, top3, inference_ms) for a 224x224 PIL image."""
    x = preprocess(image)
    start = time.perf_counter()
    logits = _method.execute([x])[0]
    inference_ms = (time.perf_counter() - start) * 1000

    probs = torch.softmax(logits.flatten(), dim=0)
    top = torch.topk(probs, 3)
    top3 = [(clean_label(CLASS_NAMES[int(i)]), p.item())
            for p, i in zip(top.values, top.indices)]
    return top3[0][0], top3[0][1], top3, inference_ms


if __name__ == "__main__":
    if len(sys.argv) > 1:
        path = Path(sys.argv[1])
    else:
        path = max(CAPTURES_DIR.glob("*_224.*"), key=lambda p: p.stat().st_mtime)

    image = Image.open(path)
    print(f"Image: {path.name}, size {image.size}")
    if image.size != (224, 224):
        print("Warning: the model expects 224x224 input")

    classify(image)  # warm-up run, not timed
    results = [classify(image) for _ in range(20)]
    times = [r[3] for r in results]

    print("Top 3 predictions:")
    for name, prob in results[0][2]:
        print(f"  {name:<20} {prob:.1%}")
    print(f"Inference over 20 runs: median {statistics.median(times):.1f} ms, "
          f"mean {statistics.mean(times):.1f} ms, "
          f"min {min(times):.1f} ms, max {max(times):.1f} ms")
