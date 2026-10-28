"""Builds the GitHub Pages copy of the app: web/index.html -> site/index.html.

The page itself is written without a doctype or <head> (the claude.ai viewer adds its own).
For the standalone copy this adds a doctype, so browsers use standards mode, and a Content
Security Policy that lets the page run only its own script (pinned by hash) and load only
its fonts. Nothing else can load, and the page can't send data anywhere.
"""
import base64
import hashlib
import pathlib
import re

src = pathlib.Path("web/index.html").read_text(encoding="utf-8")
scripts = re.findall(r"<script>(.*?)</script>", src, re.S)
assert len(scripts) == 1, "expected exactly one inline script"
digest = base64.b64encode(hashlib.sha256(scripts[0].encode("utf-8")).digest()).decode()

csp = "; ".join([
    "default-src 'none'",
    f"script-src 'sha256-{digest}'",
    "style-src 'unsafe-inline' https://fonts.googleapis.com",
    "font-src https://fonts.gstatic.com",
    "img-src data: blob:",
    "connect-src 'none'",
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'none'",
])

out = pathlib.Path("site")
out.mkdir(exist_ok=True)
charset, rest = src.split("\n", 1)
assert charset.startswith("<meta charset"), "page should start with its charset"
(out / "index.html").write_text(
    '<!doctype html>\n<html lang="en">\n' + charset + "\n"
    + f'<meta http-equiv="Content-Security-Policy" content="{csp}">\n' + rest,
    encoding="utf-8",
)
print("site/index.html written; script sha256-" + digest)
