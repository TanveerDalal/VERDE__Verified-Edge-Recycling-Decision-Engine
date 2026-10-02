"""
server.py - VERDE Pi backend
Receives scan requests from the app and returns the result as JSON.
Version 1: returns fixed dummy data in the final format, so the app
can be built and tested before the classifier and Llama are ready.
"""

from flask import Flask, jsonify

import config

app = Flask(__name__)


@app.get("/health")
def health():
    """Quick check that the server is running."""
    return jsonify({"status": "ok"})


@app.post("/scan")
def scan():
    """Return a scan result. Dummy values for now."""
    # WHY: this is the agreed JSON contract between the Pi and the app.
    # Later steps replace the dummy values with real ones, but the
    # field names stay the same, so the app never needs to change.
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
