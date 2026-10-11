"""
server.py - VERDE Pi backend
  GET  /health  - is the server up?
  GET  /stream  - live MJPEG preview from the Pi camera
  POST /scan    - capture a photo, classify it and return the result, with stage timings
"""

import threading
import time
from datetime import datetime

from flask import Flask, Response, jsonify

import advice
import classifier
from camera import VerdeCamera, to_model_input
from config import HOST, PORT, SAVE_DIR, SAVE_FULL_PHOTO

app = Flask(__name__)
camera = VerdeCamera()

# WHY: /scan can run on several threads at once (threaded=True below).
# The lock makes a second scan wait instead of calling the model at the same time.
classify_lock = threading.Lock()


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

    # Classify the same 224x224 image that was just saved
    with classify_lock:
        label, confidence, top3, inference_ms = classifier.classify(model_input)

    # Full-size photo for the notebook, saved after timing stops
    if SAVE_FULL_PHOTO:
        photo.save(SAVE_DIR / f"pi_{stamp}.jpg", quality=95)

    result = advice.build_result(label, confidence)
    result["timings_ms"] = {
        "capture": round((t1 - t0) * 1000, 1),
        "preprocess": round((t2 - t1) * 1000, 1),
        "save": round((t3 - t2) * 1000, 1),
    }

    top3_text = ", ".join(f"{name} {prob:.0%}" for name, prob in top3)
    print(f"[scan] {label} {confidence:.1%} | top 3: {top3_text} | "
          f"inference {inference_ms:.1f} ms", flush=True)
    return jsonify(result)


if __name__ == "__main__":
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    camera.start()
    try:
        # threaded=True: the endless /stream must not block /scan
        app.run(host=HOST, port=PORT, threaded=True)
    finally:
        camera.stop()  # release the camera cleanly on Ctrl + C
