"""
server.py - VERDE Pi backend
  GET  /health    - is the server up?
  GET  /stream    - live MJPEG preview from the Pi camera
  POST /scan      - capture a photo with the Pi camera, classify it, return the result
  POST /classify  - classify a photo uploaded by the phone app, return the result
"""

import io
import threading
import time
from datetime import datetime

from flask import Flask, Response, jsonify, request
from PIL import Image, ImageOps

import advice
import classifier
from camera import VerdeCamera, to_model_input
from config import HOST, PORT, SAVE_DIR, SAVE_FULL_PHOTO

app = Flask(__name__)
camera = VerdeCamera()

# Phone uploads are kept apart from the Pi's own scans.
PHONE_DIR = SAVE_DIR / "phone"

# WHY: requests can run on several threads at once (threaded=True below).
# The lock makes a second request wait instead of calling the model at the same time.
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
        "inference": round(inference_ms, 1),
    }

    top3_text = ", ".join(f"{name} {prob:.0%}" for name, prob in top3)
    print(f"[scan] {label} {confidence:.1%} | top 3: {top3_text} | "
          f"inference {inference_ms:.1f} ms", flush=True)
    return jsonify(result)


@app.post("/classify")
def classify_upload():
    """Classify a photo taken by the phone. Send it as multipart field 'image'."""
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")[:-3]

    upload = request.files.get("image")
    data = upload.read() if upload else request.get_data()
    if not data:
        return jsonify(error="no image received"), 400

    # In this response, "capture" is the time to decode the upload; the phone
    # took the photo itself, so there is no Pi capture stage.
    t0 = time.perf_counter()
    try:
        # exif_transpose: phone photos are often stored sideways with a rotation tag
        photo = ImageOps.exif_transpose(Image.open(io.BytesIO(data))).convert("RGB")
    except Exception:
        return jsonify(error="could not read image"), 400
    t1 = time.perf_counter()
    model_input = to_model_input(photo)
    t2 = time.perf_counter()
    model_input.save(PHONE_DIR / f"phone_{stamp}_224.png")
    t3 = time.perf_counter()

    with classify_lock:
        label, confidence, top3, inference_ms = classifier.classify(model_input)

    # Keep the original upload untouched (no re-compression) for the notebook
    (PHONE_DIR / f"phone_{stamp}.jpg").write_bytes(data)

    result = advice.build_result(label, confidence)
    result["timings_ms"] = {
        "capture": round((t1 - t0) * 1000, 1),
        "preprocess": round((t2 - t1) * 1000, 1),
        "save": round((t3 - t2) * 1000, 1),
        "inference": round(inference_ms, 1),
    }

    top3_text = ", ".join(f"{name} {prob:.0%}" for name, prob in top3)
    print(f"[phone] {label} {confidence:.1%} | top 3: {top3_text} | "
          f"inference {inference_ms:.1f} ms", flush=True)
    return jsonify(result)


if __name__ == "__main__":
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    PHONE_DIR.mkdir(parents=True, exist_ok=True)
    camera.start()
    try:
        # threaded=True: the endless /stream must not block /scan
        app.run(host=HOST, port=PORT, threaded=True)
    finally:
        camera.stop()  # release the camera cleanly on Ctrl + C
