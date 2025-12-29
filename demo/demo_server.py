#!/usr/bin/env python3
from http.server import BaseHTTPRequestHandler, HTTPServer
import pathlib
import os
import subprocess
import tempfile

hostName = "localhost"
serverPort = 8080

CONTENT_TYPE_BY_EXTENSION = {
    "html": "text/html",
    "css": "text/css",
    "js": "application/javascript",
    "svg": "image/svg+xml",
}


def docgen(markdown_text):
    with tempfile.TemporaryDirectory() as tmp_dir:
        inputs_dir = pathlib.Path(tmp_dir) / "inputs"
        os.mkdir(inputs_dir)
        with open(inputs_dir / "file.md", "wb") as content_file:
            content_file.write(markdown_text)

        project_dir = pathlib.Path(__file__).resolve().parent.parent
        temper = find_executable(project_dir / "cli/build/install/temper/bin/temper")
        args = [temper, "--working-directory", inputs_dir, "doc"]
        subprocess.run(args=args, check=True)

        output_file_path = pathlib.Path(tmp_dir) / "processed" / "file.md"
        try:
            output_file = open(output_file_path, "rb")
        except FileNotFoundError:
            print("File not found for %r" % markdown_text)
            return None
        with output_file:
            return output_file.read()


def find_executable(path: pathlib.Path) -> pathlib.Path:
    if os.name == "nt":
        for ext in [".bat"]:
            alt = path.with_suffix(ext)
            if alt.exists():
                path = alt
                break
    return path.resolve()


class DemoRequestHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = self.path
        content_type = CONTENT_TYPE_BY_EXTENSION[path[path.rfind(".") + 1 :]]
        with open(os.path.join(os.curdir, *(path.split("/"))), "rb") as f:
            self.send_response(200)
            self.send_header("Content-type", content_type)
            self.end_headers()
            self.wfile.write(f.read())

    def do_POST(self):
        if self.path == "/docgen":
            content_len = int(self.headers.get("Content-Length"))
            content = self.rfile.read(content_len)
            self.rfile.close()

            generated_docs = docgen(content)
            if generated_docs is None:
                self.send_response(500)
                self.send_header("Content-type", "text/plain")
                self.end_headers()
                self.wfile.write(b"Meh")
            else:
                self.send_response(200)
                self.send_header("Content-type", "text/markdown")
                self.end_headers()
                self.wfile.write(generated_docs)


if __name__ == "__main__":
    webServer = HTTPServer((hostName, serverPort), DemoRequestHandler)
    print("Server started http://%s:%s/docgen-testbed.html" % (hostName, serverPort))

    try:
        webServer.serve_forever()
    except KeyboardInterrupt:
        pass

    webServer.server_close()
    print("Server stopped.")
