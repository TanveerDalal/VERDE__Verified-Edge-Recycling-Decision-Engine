"""
config.py - VERDE Pi backend settings
All settings live here so they can be changed in one place.
"""

from pathlib import Path

# Server: 0.0.0.0 means "listen on every network connection",
# which lets your phone reach the Pi.
HOST = "0.0.0.0"
PORT = 5000

# Camera and model input
MODEL_SIZE = 224
CAPTURE_SIZE = (2304, 1296)
SAVE_DIR = Path.home() / "projects" / "verde" / "captures"
