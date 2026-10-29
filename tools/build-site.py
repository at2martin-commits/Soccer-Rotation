"""Builds a GitHub Pages copy of the app.

    python3 tools/build-site.py [SOURCE_HTML] [OUTPUT_DIR] [--beta]

Defaults to web/index.html -> site/. The page itself is written without a doctype or <head>
(the claude.ai viewer adds its own). For the standalone copy this adds a doctype, so browsers
use standards mode, and a Content Security Policy that lets the page run only its own script
(pinned by hash) and load only its fonts. Nothing else can load, and the page can't send
data anywhere.

It also makes the copy installable on a phone's home screen: a web app manifest, the icons in
web/icons/, and a small service worker (sw.js) so the installed app opens without internet.

--beta marks the copy as a beta: a BETA tag in the header, and its own saved data, so testing
a beta never touches the teams saved by the regular version. It installs as a separate app.
"""
import base64
import hashlib
import json
import pathlib
import re
import shutil
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

src = swap(src, '<meta name="theme-color" content="#1f7a3f">',
           '<meta name="theme-color" content="#1f7a3f">\n<link rel="manifest" href="manifest.webmanifest">')

scripts = re.findall(r"<script>(.*?)</script>", src, re.S)
assert len(scripts) == 1, "expected exactly one inline script"
digest = base64.b64encode(hashlib.sha256(scripts[0].encode("utf-8")).digest()).decode()

csp = "; ".join([
    "default-src 'none'",
    f"script-src 'sha256-{digest}'",
    "style-src 'unsafe-inline' https://fonts.googleapis.com",
    "font-src https://fonts.gstatic.com",
    "img-src data: blob:",
    "manifest-src 'self'",
    "worker-src 'self'",
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
# Home screen app: manifest, icons and the offline helper. Paths are relative, so the regular
# copy (site root) and the beta (/beta/) are separate apps with separate scopes.
icons = src_path.parent / "icons"
shutil.copytree(icons, out / "icons", dirs_exist_ok=True)
manifest = {
    "name": "Soccer Rotation (beta)" if beta else "Soccer Rotation",
    "short_name": "Soccer Beta" if beta else "Soccer Rotation",
    "description": "Fair playing-time lineups for youth soccer.",
    "id": "./",
    "start_url": "./",
    "scope": "./",
    "display": "standalone",
    "background_color": "#eef2ec",
    "theme_color": "#1f7a3f",
    "icons": [
        {"src": "icons/icon-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any"},
        {"src": "icons/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any"},
        {"src": "icons/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable"},
    ],
}
(out / "manifest.webmanifest").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
# The service worker always asks the network first, so updates show up right away; the saved
# copy is only used when there's no connection. Fonts are kept too, so the app looks the same
# offline. The regular copy's worker also covers /beta/ by path, so it leaves that alone.
sw = """const CACHE = %s;
const FONTS = ["https://fonts.googleapis.com", "https://fonts.gstatic.com"];
const SKIP = %s;
self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(["./", "manifest.webmanifest", "icons/icon-192.png"])));
  self.skipWaiting();
});
self.addEventListener("activate", (e) => e.waitUntil(self.clients.claim()));
self.addEventListener("fetch", (e) => {
  const req = e.request, url = new URL(req.url);
  if (req.method !== "GET") return;
  if (FONTS.includes(url.origin)) {
    e.respondWith(caches.match(req).then((hit) => hit || fetch(req).then((res) => {
      const copy = res.clone();
      caches.open(CACHE).then((c) => c.put(req, copy));
      return res;
    })));
    return;
  }
  if (url.origin !== location.origin || (SKIP && url.pathname.startsWith(new URL(SKIP, self.registration.scope).pathname))) return;
  e.respondWith(fetch(req).then((res) => {
    if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(req, copy)); }
    return res;
  }).catch(() => caches.match(req, { ignoreSearch: true }).then((hit) => hit || caches.match("./"))));
});
""" % (('"soccer-rotation-beta"', '""') if beta else ('"soccer-rotation"', '"beta/"'))
(out / "sw.js").write_text(sw, encoding="utf-8")
print(f"{out / 'index.html'} written{' (beta)' if beta else ''}; script sha256-{digest}")
