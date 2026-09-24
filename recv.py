from http.server import BaseHTTPRequestHandler, HTTPServer

class H(BaseHTTPRequestHandler):
    def do_POST(self):
        n = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(n)
        with open('got.zip', 'wb') as f:
            f.write(body)
        print(f"[+] {n} bytes -> got.zip")
        self.send_response(200); self.end_headers()
    def log_message(self, *a): pass

HTTPServer(('0.0.0.0', 8080), H).serve_forever()
