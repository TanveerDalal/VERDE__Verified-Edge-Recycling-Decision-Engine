"""
export_vision.py - VERDE
Loads the trained MobileNetV3-Small checkpoint and exports it to an
ExecuTorch .pte file (XNNPACK backend, for the Arm CPU on the Pi 5).
"""

import json
from pathlib import Path

import torch
import torch.nn as nn
from torchvision.models import mobilenet_v3_small
from executorch.exir import to_edge_transform_and_lower
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner

# Load the checkpoint saved by the training notebook.
ckpt = torch.load("best_model.pth", map_location="cpu")
class_names = ckpt["class_names"]
print(f"Checkpoint: epoch {ckpt['epoch']}, val acc {ckpt['val_acc']:.1%}")
print("Classes:", class_names)

# Rebuild the same architecture as training: the 1000-class head becomes 9 classes.
model = mobilenet_v3_small(weights=None)
model.classifier[3] = nn.Linear(model.classifier[3].in_features, len(class_names))
model.load_state_dict(ckpt["model_state"])
model.eval()

# Export with a fixed 1x3x224x224 input, then lower to the XNNPACK backend.
example_input = (torch.randn(1, 3, 224, 224),)
exported = torch.export.export(model, example_input)
program = to_edge_transform_and_lower(
    exported, partitioner=[XnnpackPartitioner()]
).to_executorch()

Path("model.pte").write_bytes(program.buffer)
Path("class_names.json").write_text(json.dumps(class_names, indent=2))
print("Saved model.pte and class_names.json")
