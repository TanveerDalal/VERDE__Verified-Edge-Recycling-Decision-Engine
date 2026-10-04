"""
camera.py - VERDE Pi backend
Keeps the Camera Module 3 running so that:
  - a small live preview can be streamed to the phone (lores stream)
  - scans grab a full-size frame instantly (main stream)
"""

import io
import threading
import time

from libcamera import controls
from picamera2 import Picamera2
from picamera2.encoders import JpegEncoder
from picamera2.outputs import FileOutput

from config import CAPTURE_SIZE, MODEL_SIZE, PREVIEW_QUALITY, PREVIEW_SIZE, SAVE_DIR


class PreviewBuffer(io.BufferedIOBase):
    """Holds the latest preview JPEG and wakes up anyone waiting for a new one."""

    def __init__(self):
        self.frame = None
        self.condition = threading.Condition()

    def write(self, buf):
        with self.condition:
            self.frame = buf
            self.condition.notify_all()


class VerdeCamera:
    """One camera, started once, shared by the preview stream and scans."""

    def __init__(self):
        self.picam2 = None
        self.preview = PreviewBuffer()
        self.lock = threading.Lock()

    def start(self):
        self.picam2 = Picamera2()
        # WHY two streams: "main" is full size for scans,
        # "lores" is small for the live preview.
        config = self.picam2.create_video_configuration(
            main={"size": CAPTURE_SIZE, "format": "RGB888"},
            lores={"size": PREVIEW_SIZE},
        )
        self.picam2.configure(config)
        self.picam2.start()
        # Camera Module 3 has autofocus: keep refocusing as items move
        self.picam2.set_controls({"AfMode": controls.AfModeEnum.Continuous})
        # Encode the small stream as JPEG frames for the phone
        self.picam2.start_encoder(
            JpegEncoder(q=PREVIEW_QUALITY), FileOutput(self.preview), name="lores"
        )
        time.sleep(2)  # let focus and exposure settle ONCE, at startup

    def capture(self):
        """Grab the current full-size frame from the running camera."""
        with self.lock:  # one scan at a time
            return self.picam2.capture_image("main")

    def wait_for_preview_frame(self):
        """Block until the next preview JPEG is ready, then return it."""
        with self.preview.condition:
            self.preview.condition.wait()
            return self.preview.frame

    def stop(self):
        if self.picam2:
            self.picam2.stop_encoder()
            self.picam2.stop()
            self.picam2.close()


def to_model_input(image, size=MODEL_SIZE):
    """Centre-crop to a square, then shrink to size x size."""
    # WHY crop first: the camera is 16:9 and the model input is square.
    width, height = image.size
    side = min(width, height)
    left = (width - side) // 2
    top = (height - side) // 2
    square = image.crop((left, top, left + side, top + side))
    return square.resize((size, size))


if __name__ == "__main__":
    # Quick test: start the camera, capture once, report the time
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    cam = VerdeCamera()
    cam.start()
    t = time.perf_counter()
    photo = cam.capture()
    ms = (time.perf_counter() - t) * 1000
    to_model_input(photo).save(SAVE_DIR / "test_224.png")
    print(f"Captured {photo.size} in {ms:.0f} ms (camera already running)")
    cam.stop()
