#!/usr/bin/env python3
"""
frame_server.py — Simple API server for JarvisDisplay photo frame
Run: python3 frame_server.py
Listens on port 8765
"""

import json
import os
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime

MESSAGES_FILE = os.path.expanduser("~/.openclaw/workspace/frame_messages.json")
PORT = 8765

def load_messages():
    if os.path.exists(MESSAGES_FILE):
        try:
            with open(MESSAGES_FILE, "r") as f:
                data = json.load(f)
                return data.get("messages", [])
        except:
            return []
    return []

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/api/messages":
            messages = load_messages()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            response = {"messages": messages, "count": len(messages)}
            self.wfile.write(json.dumps(response).encode())
        elif self.path == "/api/status":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "time": datetime.now().isoformat()}).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        print(f"[{datetime.now().strftime('%H:%M:%S')}] {args[0]}")

if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", PORT), Handler)
    print(f"Frame API server running on port {PORT}")
    server.serve_forever()
