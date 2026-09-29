import contextlib
import io
import logging
import os
import sys

YT_FIRST = ("youtube.com", "youtu.be", "tiktok.com", "vimeo.com",
            "facebook.com", "fb.watch", "twitch.tv", "dailymotion.com")


def _has_files(dest):
    for _root, _dirs, files in os.walk(dest):
        if files:
            return True
    return False


def _gallery(url, dest, cookies, template):
    import gallery_dl
    from gallery_dl import config
    try:
        config.clear()
    except Exception:
        pass
    for name in ("", "gallery-dl"):
        lg = logging.getLogger(name)
        for h in list(lg.handlers):
            lg.removeHandler(h)
    args = ["gallery-dl", "-D", dest]
    if cookies:
        args += ["--cookies", cookies]
    if template:
        args += ["-f", template]
    args.append(url)
    buf = io.StringIO()
    old = sys.argv
    sys.argv = args
    code = 0
    try:
        with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
            code = gallery_dl.main() or 0
    except SystemExit as e:
        code = e.code or 0
    finally:
        sys.argv = old
    return buf.getvalue() + "(çıkış kodu: %s)" % code


class _Log:
    def __init__(self):
        self.lines = []

    def debug(self, msg):
        pass

    def info(self, msg):
        pass

    def warning(self, msg):
        self.lines.append("uyarı: %s" % msg)

    def error(self, msg):
        self.lines.append("hata: %s" % msg)


def _ytdlp(url, dest, cookies, sink):
    import yt_dlp
    last = [-1]

    def hook(d):
        if d.get("status") != "downloading":
            return
        total = d.get("total_bytes") or d.get("total_bytes_estimate")
        if not total:
            return
        pct = int(d.get("downloaded_bytes", 0) * 100 / total)
        if pct != last[0]:
            last[0] = pct
            sink.update(pct)

    log = _Log()
    opts = {
        "outtmpl": os.path.join(dest, "%(title).80s [%(id)s].%(ext)s"),
        "format": "best[ext=mp4]/best",
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "logger": log,
        "progress_hooks": [hook],
    }
    if cookies:
        opts["cookiefile"] = cookies
    with yt_dlp.YoutubeDL(opts) as ydl:
        ydl.download([url])
    return "\n".join(log.lines) or "tamam"


def run(url, dest, cookies, template, sink):
    if any(d in url for d in YT_FIRST):
        order = ["ytdlp", "gallery"]
    else:
        order = ["gallery", "ytdlp"]
    parts = []
    for tool in order:
        try:
            if tool == "gallery":
                out = _gallery(url, dest, cookies, template)
            else:
                out = _ytdlp(url, dest, cookies, sink)
        except Exception as e:
            out = "%s: %s" % (type(e).__name__, e)
        parts.append("[%s]\n%s" % (tool, out.strip()))
        if _has_files(dest):
            break
    return "\n\n".join(parts)
