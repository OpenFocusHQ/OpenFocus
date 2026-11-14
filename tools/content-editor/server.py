#!/usr/bin/env python3
"""
OpenFocus content editor: a local, developer-only tool. Not shipped in the app or deployed anywhere.

    python3 tools/content-editor/server.py        # then open http://127.0.0.1:8765

Serves the editor page, reads/writes app/src/main/resources/content/content.json, previews sprites
from app/src/main/res/drawable-nodpi, and can re-run art/tools/build_placeholders.py.
Standard library only; binds to 127.0.0.1.
"""
import argparse
import json
import os
import struct
import subprocess
import sys
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
CONTENT = os.path.join(ROOT, "app", "src", "main", "resources", "content", "content.json")
SCHEMA = os.path.join(HERE, "content.schema.json")
SPRITES = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
GENERATOR = os.path.join(ROOT, "art", "tools", "build_placeholders.py")


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    return struct.unpack(">II", head[16:24])


class Handler(BaseHTTPRequestHandler):
    def _send(self, status, body, ctype="application/json; charset=utf-8"):
        data = body if isinstance(body, bytes) else body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _file(self, path, ctype):
        with open(path, "rb") as f:
            self._send(HTTPStatus.OK, f.read(), ctype)

    def log_message(self, fmt, *args):  # quieter console
        sys.stderr.write("  %s\n" % (fmt % args))

    def do_GET(self):
        p = self.path.split("?")[0]
        if p in ("/", "/index.html"):
            return self._file(os.path.join(HERE, "index.html"), "text/html; charset=utf-8")
        if p == "/api/content":
            return self._file(CONTENT, "application/json; charset=utf-8")
        if p == "/api/schema":
            return self._file(SCHEMA, "application/json; charset=utf-8")
        if p == "/api/sprites":
            out = {}
            for n in sorted(os.listdir(SPRITES)):
                if n.startswith("px_") and n.endswith(".png"):
                    w, h = png_size(os.path.join(SPRITES, n))
                    out[n[:-4]] = {"width": w, "height": h}
            return self._send(HTTPStatus.OK, json.dumps(out))
        if p.startswith("/sprites/"):
            name = os.path.basename(p)  # no path traversal
            path = os.path.join(SPRITES, name)
            if name.endswith(".png") and os.path.isfile(path):
                return self._file(path, "image/png")
        self._send(HTTPStatus.NOT_FOUND, json.dumps({"error": "not found"}))

    def do_PUT(self):
        if self.path != "/api/content":
            return self._send(HTTPStatus.NOT_FOUND, json.dumps({"error": "not found"}))
        length = int(self.headers.get("Content-Length", 0))
        try:
            data = json.loads(self.rfile.read(length).decode("utf-8"))
        except ValueError as e:
            return self._send(HTTPStatus.BAD_REQUEST, json.dumps({"error": f"invalid JSON: {e}"}))
        if not isinstance(data, dict) or "items" not in data:
            return self._send(HTTPStatus.BAD_REQUEST, json.dumps({"error": "not a content document"}))
        tmp = CONTENT + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            f.write(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
        os.replace(tmp, CONTENT)
        self._send(HTTPStatus.OK, json.dumps({"saved": os.path.relpath(CONTENT, ROOT)}))

    def do_POST(self):
        if self.path != "/api/generate":
            return self._send(HTTPStatus.NOT_FOUND, json.dumps({"error": "not found"}))
        r = subprocess.run([sys.executable, GENERATOR], cwd=ROOT, capture_output=True, text=True, timeout=300)
        self._send(HTTPStatus.OK, json.dumps({"ok": r.returncode == 0, "output": r.stdout + r.stderr}))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", type=int, default=8765)
    args = ap.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"OpenFocus content editor on http://127.0.0.1:{args.port}  (Ctrl+C to stop)")
    print(f"editing {os.path.relpath(CONTENT, ROOT)}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
