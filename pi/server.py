"""
server.py - VERDE Pi backend
Receives scan requests from the app, takes a photo and returns the result as JSON.
Version 2: takes a real photo on every scan, but still returns dummy data
until the classifier and Llama are ready.
"""

import threading
from datetime import datetime

from flask import Flask, jsonify

import camera
import config

app = Flask(__name__)

# WHY: the camera can only be used by one request at a time. The lock makes
# a second scan wait for the first instead of crashing with "camera in use".
camera_lock = threading.Lock()


@app.get("/health")
def health():
    """Quick check that the server is running."""
    return jsonify({"status": "ok"})


@app.post("/scan")
def scan():
    """Take a photo, then return a scan result (dummy values for now)."""
    config.SAVE_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    with camera_lock:
        photo = camera.capture_photo()
    model_image = camera.to_model_input(photo)

    model_image.save(config.SAVE_DIR / f"scan_{stamp}_224.jpg")

    # TODO (weeks 3-5): pass model_image to the classifier and Llama.
    # The field names below are the contract with the app and must not change.
    result = {
        "label": "Plastic",
        "confidence": 0.93,
        "uncertain": False,
        "bin": "Mixed recycling",
        "steps": [
            "Empty any remaining liquid",
            "Rinse the item quickly",
            "Put the lid back on",
            "Place it in the mixed recycling bin",
        ],
        "warnings": [],
        "why_it_matters": "Recycling plastic bottles saves energy and keeps plastic out of landfill.",
    }
    return jsonify(result)


if __name__ == "__main__":
    app.run(host=config.HOST, port=config.PORT)
