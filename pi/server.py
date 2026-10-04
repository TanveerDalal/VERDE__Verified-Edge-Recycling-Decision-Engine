"""
server.py - VERDE Pi backend
  GET  /health  - is the server up?
  GET  /stream  - live MJPEG preview from the Pi camera
  POST /scan    - capture a photo and return the result, with stage timings
"""

import time
from datetime import datetime

from flask import Flask, Response, jsonify

from camera import VerdeCamera, to_model_input
from config import HOST, PORT, SAVE_DIR, SAVE_FULL_PHOTO

app = Flask(__name__)
camera = VerdeCamera()

# Placeholder until the classifier and Llama are connected
DUMMY_RESULT = {
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


@app.get("/health")
def health():
    return jsonify(status="ok")


@app.get("/stream")
def stream():
    # WHY multipart: the browser or app receives an endless series of JPEG
    # frames over one connection and shows each as it arrives (MJPEG).
    def frames():
        while True:
            frame = camera.wait_for_preview_frame()
            yield (
                b"--frame\r\n"
                b"Content-Type: image/jpeg\r\n"
                b"Content-Length: " + str(len(frame)).encode() + b"\r\n\r\n"
                + frame + b"\r\n"
            )

    return Response(frames(), mimetype="multipart/x-mixed-replace; boundary=frame")


@app.post("/scan")
def scan():
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")[:-3]

    # Timed pipeline (same stages as the phone, for the benchmark)
    t0 = time.perf_counter()
    photo = camera.capture()
    t1 = time.perf_counter()
    model_input = to_model_input(photo)
    t2 = time.perf_counter()
    model_input.save(SAVE_DIR / f"pi_{stamp}_224.png")
    t3 = time.perf_counter()

    # Full-size photo for the notebook, saved after timing stops
    if SAVE_FULL_PHOTO:
        photo.save(SAVE_DIR / f"pi_{stamp}.jpg", quality=95)

    result = dict(DUMMY_RESULT)
    result["timings_ms"] = {
        "capture": round((t1 - t0) * 1000, 1),
        "preprocess": round((t2 - t1) * 1000, 1),
        "save": round((t3 - t2) * 1000, 1),
    }
    return jsonify(result)


if __name__ == "__main__":
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    camera.start()
    try:
        # threaded=True: the endless /stream must not block /scan
        app.run(host=HOST, port=PORT, threaded=True)
    finally:
        camera.stop()  # release the camera cleanly on Ctrl + C
