#!/usr/bin/env python3
"""
FlipOff Display Server — Jarvis edition
Serves the flipboard display and accepts messages via API.
Messages auto-expire after 30 minutes.
"""

import json
import os
import time
import base64
from http.server import HTTPServer, SimpleHTTPRequestHandler
from urllib.parse import urlparse
import socketserver

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
FLIPOFF_DIR = os.path.join(BASE_DIR, "flipoff-display")
MESSAGES_FILE = os.path.join(BASE_DIR, "messages.json")
PORT = 8765

# --- Message Storage ---
def load_messages():
    if not os.path.exists(MESSAGES_FILE):
        return {"messages": []}
    try:
        with open(MESSAGES_FILE, "r") as f:
            data = json.load(f)
        now = time.time()
        data["messages"] = [
            m for m in data.get("messages", [])
            if m.get("expires_at", 0) > now
        ]
        if not data["messages"]:
            data["messages"] = [{"id": str(now), "text": ["","JARVIS","ONLINE","",""], "sender": "system", "created_at": now, "expires_at": now + 3600}]
        return data
    except (json.JSONDecodeError, IOError):
        return {"messages": [{"id": "0", "text": ["","JARVIS","ONLINE","",""], "sender": "system", "created_at": 0, "expires_at": 9999999999}]}

def save_messages(data):
    with open(MESSAGES_FILE, "w") as f:
        json.dump(data, f, ensure_ascii=False)

def add_message(text_lines, sender="jarvis", ttl_minutes=30):
    data = load_messages()
    now = time.time()
    msg = {"id": f"{now}", "text": text_lines, "sender": sender, "created_at": now, "expires_at": now + ttl_minutes * 60}
    data["messages"].append(msg)
    save_messages(data)
    return msg

def format_text(text, cols=22, rows=5):
    parts = text.replace("\n", " | ").split(" | ")
    lines = [""] * rows
    for i, word in enumerate(parts):
        if i >= rows: break
        lines[i] = word.strip()[:cols].center(cols)
    return lines

# --- HTTP Handler ---
class FlipHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=FLIPOFF_DIR, **kwargs)

    def send_cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_cors_headers()
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        # Handle API endpoints before parent serves static files
        if parsed.path == "/api/messages":
            self.send_json_response(load_messages())
        elif parsed.path == "/api/status":
            self.send_json_response({"status": "ok", "time": time.time()})
        elif parsed.path == "/api/audio":
            audio_path = os.path.join(FLIPOFF_DIR, "audio.wav")
            if os.path.exists(audio_path):
                with open(audio_path, "rb") as f:
                    data = f.read()
                self.send_response(200)
                self.send_header("Content-Type", "audio/wav")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Content-Length", len(data))
                self.end_headers()
                self.wfile.write(data)
            else:
                self.send_error(404, "Audio not found")
            return  # <-- IMPORTANT: don't fall through to parent
        else:
            super().do_GET()

    def do_POST(self):
        parsed = urlparse(self.path)
        if parsed.path == "/api/display":
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length).decode("utf-8")
            try:
                payload = json.loads(body)
                text = payload.get("text", "")
                sender = payload.get("sender", "jarvis")
                ttl = float(payload.get("ttl", 30))
            except json.JSONDecodeError:
                text = body.strip()
                sender = "jarvis"
                ttl = 30

            if text:
                lines = format_text(text)
                add_message(lines, sender=sender, ttl_minutes=ttl)
                self.send_json_response({"ok": True, "queued": lines})
            else:
                self.send_json_response({"ok": False, "error": "no text"}, status=400)
        elif parsed.path == "/api/clear":
            save_messages({"messages": []})
            self.send_json_response({"ok": True})
        else:
            self.send_json_response({"error": "not found"}, status=404)

    def send_json_response(self, data, status=200):
        response = json.dumps(data, ensure_ascii=False)
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_cors_headers()
        self.send_header("Content-Length", len(response))
        self.end_headers()
        self.wfile.write(response.encode("utf-8"))

    def log_message(self, format, *args):
        pass

class ThreadedHTTPServer(socketserver.ThreadingMixIn, HTTPServer):
    daemon_threads = True

def run_server():
    server = ThreadedHTTPServer(("", PORT), FlipHandler)
    print(f"FlipOff server running on http://0.0.0.0:{PORT}")
    server.serve_forever()

if __name__ == "__main__":
    run_server()
