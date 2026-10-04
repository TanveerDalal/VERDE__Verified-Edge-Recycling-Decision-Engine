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

# Live preview streamed to the phone (small, so it's light on the Pi's CPU)
PREVIEW_SIZE = (640, 360)   # 16:9, same shape as the camera
PREVIEW_QUALITY = 70        # JPEG quality for preview frames (0-100)

# Also save the full-size photo for preprocessing in the notebook later
SAVE_FULL_PHOTO = True
