"""
camera.py - VERDE Pi backend
Captures one photo with the Camera Module 3 and prepares a 224x224 square
copy for the vision model.
"""

import time
from datetime import datetime
from pathlib import Path

from libcamera import controls
from picamera2 import Picamera2

# WHAT: Settings in one place.
# WHY:  The model expects 224x224 (see Section 10 of the data analysis).
#       Photos are saved outside the code folder so they never reach GitHub.
MODEL_SIZE = 224
CAPTURE_SIZE = (2304, 1296)  # 16:9 mode that the camera supports
SAVE_DIR = Path.home() / "projects" / "verde" / "captures"


def capture_photo():
    """Take one full-size photo and return it as a PIL image."""
    picam2 = Picamera2()

    # HOW: configure for a still photo, start the camera, then let
    # autofocus and exposure settle for 2 seconds before capturing.
    picam2.configure(picam2.create_still_configuration(main={"size": CAPTURE_SIZE}))
    picam2.start()
    picam2.set_controls({"AfMode": controls.AfModeEnum.Continuous})
    time.sleep(2)

    image = picam2.capture_image("main")
    picam2.stop()
    picam2.close()
    return image


def to_model_input(image, size=MODEL_SIZE):
    """Centre-crop to a square, then shrink to size x size."""
    # WHY crop first: the camera is 16:9 and the model input is square.
    # Resizing directly would stretch the item and distort its shape.
    width, height = image.size
    side = min(width, height)
    left = (width - side) // 2
    top = (height - side) // 2
    square = image.crop((left, top, left + side, top + side))
    return square.resize((size, size))


if __name__ == "__main__":
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    photo = capture_photo()
    photo.save(SAVE_DIR / f"verde_{stamp}_full.jpg")
    to_model_input(photo).save(SAVE_DIR / f"verde_{stamp}_224.jpg")

    print(f"Saved to {SAVE_DIR}: full size {photo.size} and a {MODEL_SIZE}x{MODEL_SIZE} copy")
