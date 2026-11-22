"""
llama_server.py - VERDE Pi backend, port 5001
Turns a scan result (class + confidence) into a short plain-English explanation written by
Llama 3.1 8B Instruct, using ONLY the checked facts from advice.py.

  POST /explain   {"label": "plastic", "confidence": 0.92}   header: X-Token: <token>
  POST /ask       {"question": "Can I recycle pizza boxes?", "fact": "..."}  (fact optional)
  GET  /health    (no token) - is the model there, is Llama busy

The model is run with ExecuTorch's llama_main, one question at a time (a second request
while Llama is busy gets HTTP 429, so scans on port 5000 stay responsive).
Start:  export VERDE_LLAMA_TOKEN=<secret>  then  python pi/llama_server.py
"""
import hmac
import json
import os
import re
import subprocess
import threading
import time

from flask import Flask, jsonify, request

from advice import ADVICE, UNCERTAIN_BELOW

HOME = os.path.expanduser("~")
LLAMA_MAIN = os.environ.get("LLAMA_MAIN", f"{HOME}/et-rel1/executorch/cmake-out/examples/models/llama/llama_main")
MODEL = os.environ.get("LLAMA_MODEL", f"{HOME}/llama3-files/llama31_instruct_ctx1024.pte")
TOKENIZER = os.environ.get("LLAMA_TOKENIZER", f"{HOME}/llama3-files/tokenizer.model")
THREADS = os.environ.get("LLAMA_THREADS", "4")
SEQ_LEN = os.environ.get("LLAMA_SEQ_LEN", "260")      # prompt + answer, in tokens
TIMEOUT_S = int(os.environ.get("LLAMA_TIMEOUT_S", "240"))
TOKEN = os.environ.get("VERDE_LLAMA_TOKEN", "")

if not TOKEN:
    raise SystemExit("Set VERDE_LLAMA_TOKEN first, for example: export VERDE_LLAMA_TOKEN=$(python3 -c 'import secrets;print(secrets.token_urlsafe(16))')")

app = Flask(__name__)
busy = threading.Lock()          # one Llama question at a time

SYSTEM = (
    "You are VERDE, a friendly UK recycling assistant. Use ONLY the facts you are given. "
    "Do not add other rules, bin colours or council names. Answer in 2 or 3 short sentences "
    "in plain English. If the facts say the result is uncertain, say you are not sure and "
    "suggest checking with the local council."
)


def find_key(label):
    """Accept 'plastic', 'food_garden', or the display name such as 'Food or garden waste'."""
    text = str(label or "").strip().lower()
    if text in ADVICE:
        return text
    text_us = text.replace(" ", "_").replace("-", "_")
    if text_us in ADVICE:
        return text_us
    for key, item in ADVICE.items():
        if item["name"].lower() == text:
            return key
    return None


def build_prompt(key, confidence):
    item = ADVICE[key]
    uncertain = confidence is not None and confidence < UNCERTAIN_BELOW
    facts = [
        f"Item type found by the camera: {item['name']}",
        f"Bin: {item['bin']}",
        "Steps: " + "; ".join(item["steps"]),
        "Warnings: " + "; ".join(item["warnings"]),
        "Why it matters: " + item["why"],
    ]
    if confidence is not None:
        facts.append(f"Camera confidence: {round(confidence * 100)}%")
    if uncertain:
        facts.append("The camera is NOT sure about this result.")
    user = "Facts:\n" + "\n".join(facts) + "\n\nExplain to the user where this item goes and what to do first."
    return wrap(user)


def build_ask_prompt(question, fact):
    """Free question from the Ask tab. 'fact' is the checked UK advice the app found, if any."""
    if fact:
        user = (f"Facts: {fact}\n\nUser question: {question}\n\n"
                "Answer the question using ONLY these facts.")
    else:
        user = (f"User question: {question}\n\n"
                "Answer briefly. If you are not sure, say so and suggest checking with the local council.")
    return wrap(user)


def wrap(user):
    return (
        f"<|start_header_id|>system<|end_header_id|>\n\n{SYSTEM}<|eot_id|>"
        f"<|start_header_id|>user<|end_header_id|>\n\n{user}<|eot_id|>"
        f"<|start_header_id|>assistant<|end_header_id|>\n\n"
    )


def run_llama(prompt):
    """Run llama_main once and return (answer text, stats dict)."""
    cmd = [LLAMA_MAIN, f"--model_path={MODEL}", f"--tokenizer_path={TOKENIZER}",
           f"--cpu_threads={THREADS}", f"--seq_len={SEQ_LEN}", "--temperature=0", f"--prompt={prompt}"]
    out = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=TIMEOUT_S)
    text = out.stdout
    marker = "assistant<|end_header_id|>"
    if marker not in text:
        raise RuntimeError("llama_main gave no answer (exit code %s): %s" % (out.returncode, text[-600:]))
    answer = text.rsplit(marker, 1)[1]
    answer = re.split(r"<\|eot_id\||<\|end_of_text\||PyTorchObserver", answer)[0].strip()
    stats = {}
    found = re.search(r"PyTorchObserver (\{.*\})", text)
    if found:
        s = json.loads(found.group(1))
        gen_ms = s["inference_end_ms"] - s["first_token_ms"]
        stats = {
            "load_s": round((s["model_load_end_ms"] - s["model_load_start_ms"]) / 1000, 1),
            "first_token_s": round((s["first_token_ms"] - s["inference_start_ms"]) / 1000, 2),
            "generated_tokens": s["generated_tokens"],
            "tokens_per_s": round(max(s["generated_tokens"] - 1, 0) / (gen_ms / 1000), 2) if gen_ms > 0 else None,
        }
    return answer, stats


def authorised():
    return hmac.compare_digest(request.headers.get("X-Token", ""), TOKEN)


@app.get("/health")
def health():
    return jsonify(ok=True, model_found=os.path.exists(MODEL), llama_found=os.path.exists(LLAMA_MAIN),
                   busy=busy.locked())


@app.post("/ask")
def ask():
    if not authorised():
        return jsonify(error="bad or missing X-Token"), 401
    body = request.get_json(silent=True) or {}
    question = str(body.get("question") or "").strip()[:300]
    if not question:
        return jsonify(error="question is empty"), 400
    fact = str(body.get("fact") or "").strip()[:600] or None
    if not busy.acquire(blocking=False):
        return jsonify(error="Llama is busy, try again in a moment", busy=True), 429
    started = time.time()
    try:
        answer, stats = run_llama(build_ask_prompt(question, fact))
    except subprocess.TimeoutExpired:
        return jsonify(error="Llama took too long"), 504
    except Exception as err:
        return jsonify(error=str(err)), 500
    finally:
        busy.release()
    return jsonify(answer=answer, engine="ExecuTorch", device="Raspberry Pi 5 · Llama 3.1 8B",
                   seconds=round(time.time() - started, 1), **stats)


@app.post("/explain")
def explain():
    if not authorised():
        return jsonify(error="bad or missing X-Token"), 401
    body = request.get_json(silent=True) or {}
    key = find_key(body.get("label"))
    if key is None:
        return jsonify(error="unknown label", known=sorted(ADVICE)), 400
    try:
        confidence = None if body.get("confidence") is None else float(body["confidence"])
    except (TypeError, ValueError):
        return jsonify(error="confidence must be a number from 0 to 1"), 400
    if not busy.acquire(blocking=False):
        return jsonify(error="Llama is busy, try again in a moment", busy=True), 429
    started = time.time()
    try:
        answer, stats = run_llama(build_prompt(key, confidence))
    except subprocess.TimeoutExpired:
        return jsonify(error="Llama took too long"), 504
    except Exception as err:                       # keep the server alive whatever happens
        return jsonify(error=str(err)), 500
    finally:
        busy.release()
    return jsonify(label=key, explanation=answer, seconds=round(time.time() - started, 1), **stats)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, threaded=True)
