import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        return

    def do_GET(self):
        if self.path == "/healthz":
            self.respond(200, {"status": "ok"})
        else:
            self.respond(404, {"error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")
        credential = self.headers.get("Authorization") or self.headers.get("x-api-key") or self.headers.get("x-goog-api-key")
        if not credential or "bad-key" in credential:
            self.respond(401, {"error": {"message": "invalid credential"}})
            return
        if body.get("model") == "gpt-fail":
            self.respond(503, {"error": {"message": "forced provider failure"}})
            return
        if self.path == "/v1/chat/completions":
            self.respond(200, {"choices": [{"message": {"content": "mock OpenAI response"}}], "usage": {"prompt_tokens": 12, "completion_tokens": 6}})
        elif self.path == "/v1/messages":
            self.respond(200, {"content": [{"text": "mock Anthropic response"}], "usage": {"input_tokens": 14, "output_tokens": 7}})
        elif self.path.startswith("/v1beta/models/"):
            self.respond(200, {"candidates": [{"content": {"parts": [{"text": "mock Gemini response"}]}}], "usageMetadata": {"promptTokenCount": 10, "candidatesTokenCount": 5}})
        else:
            self.respond(404, {"error": {"message": "unknown endpoint"}})

    def respond(self, status, body):
        encoded = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


ThreadingHTTPServer(("0.0.0.0", 9000), Handler).serve_forever()
