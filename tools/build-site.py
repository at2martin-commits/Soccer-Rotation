"""Builds a GitHub Pages copy of the app.

    python3 tools/build-site.py [SOURCE_HTML] [OUTPUT_DIR] [--beta]

Defaults to web/index.html -> site/. The page itself is written without a doctype or <head>
(the claude.ai viewer adds its own). For the standalone copy this adds a doctype, so browsers
use standards mode, and a Content Security Policy that lets the page run only its own script
(pinned by hash) and load only its fonts. Nothing else can load, and the page can't send
data anywhere.

--beta marks the copy as a beta: a BETA tag in the header, and its own saved data, so testing
a beta never touches the teams saved by the regular version.
"""
import base64
import hashlib
import pathlib
import re
import sys

args = [a for a in sys.argv[1:] if not a.startswith("--")]
beta = "--beta" in sys.argv
src_path = pathlib.Path(args[0] if args else "web/index.html")
out = pathlib.Path(args[1] if len(args) > 1 else "site")

src = src_path.read_text(encoding="utf-8")


def swap(text, old, new):
    assert text.count(old) == 1, f"expected exactly one {old!r}"
    return text.replace(old, new)


if beta:
    src = swap(src, 'const LOCAL_KEY = "soccer-rotation";', 'const LOCAL_KEY = "soccer-rotation-beta";')
    src = re.sub(r'const APP_VERSION = "([^"]+)";', r'const APP_VERSION = "\1 beta";', src, count=1)
    src = swap(src, "<title>Soccer Rotation</title>", "<title>Soccer Rotation (beta)</title>")
    src = swap(src, '<span class="sync" id="sync" hidden></span>',
               '<span class="beta-tag" title="Test version. Its data is kept separate.">BETA</span>\n'
               '    <span class="sync" id="sync" hidden></span>')
    src = swap(src, "</style>",
               ".beta-tag { flex: none; padding: 2px 8px; border-radius: 999px; background: var(--select);"
               " color: #2b2000; font-weight: 700; font-size: 0.75rem; letter-spacing: 0.06em; }\n</style>")

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

charset, rest = src.split("\n", 1)
assert charset.startswith("<meta charset"), "page should start with its charset"
out.mkdir(parents=True, exist_ok=True)
(out / "index.html").write_text(
    '<!doctype html>\n<html lang="en">\n' + charset + "\n"
    + f'<meta http-equiv="Content-Security-Policy" content="{csp}">\n' + rest,
    encoding="utf-8",
)
print(f"{out / 'index.html'} written{' (beta)' if beta else ''}; script sha256-{digest}")
