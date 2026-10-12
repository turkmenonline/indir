import ctypes
import html as _html
import re
import socket
import threading
import weakref
import contextlib
import importlib
import io
import json
import logging
import os
import shutil
import ssl
import subprocess
import sys
import tarfile
import time
import urllib.parse
import urllib.request
import zipfile

try:
    import certifi
    ca_bundle = certifi.where()
    os.environ["SSL_CERT_FILE"] = ca_bundle
    os.environ["REQUESTS_CA_BUNDLE"] = ca_bundle
    ssl._create_default_https_context = lambda: ssl.create_default_context(cafile=ca_bundle)
except Exception:
    try:
        ssl._create_default_https_context = ssl._create_unverified_context
    except Exception:
        pass

YT_FIRST = ("youtube.com", "youtu.be", "tiktok.com", "vimeo.com",
            "facebook.com", "fb.watch", "twitch.tv", "dailymotion.com")


class CancelledDownload(BaseException):
    pass


active_threads = {}  # task_id -> worker thread id
cancelled_ids = set()

_tl = threading.local()          # worker thread'in task_id'si
_socks = {}                      # task_id -> [weakref(socket), ...]
_socks_lock = threading.Lock()
_OrigSocket = socket.socket
_orig_wrap_socket = ssl.SSLContext.wrap_socket


def _register_sock(s):
    tid = getattr(_tl, "task_id", None)
    if not tid:
        return
    with _socks_lock:
        lst = _socks.setdefault(tid, [])
        if len(lst) > 64:
            lst[:] = [r for r in lst if r() is not None]
        lst.append(weakref.ref(s))


class _TrackedSocket(_OrigSocket):
    def __init__(self, *a, **kw):
        tid = getattr(_tl, "task_id", None)
        if tid and tid in cancelled_ids:
            raise CancelledDownload("Iptal edildi")
        _OrigSocket.__init__(self, *a, **kw)
        _register_sock(self)


def _tracked_wrap_socket(self, *a, **kw):
    tid = getattr(_tl, "task_id", None)
    if tid and tid in cancelled_ids:
        raise CancelledDownload("Iptal edildi")
    s = _orig_wrap_socket(self, *a, **kw)
    _register_sock(s)
    return s


socket.socket = _TrackedSocket
ssl.SSLContext.wrap_socket = _tracked_wrap_socket


def _kill_sockets(tid_str):
    """Bloklu recv() anında hata versin diye task'in soketlerini kapatır."""
    n = 0
    with _socks_lock:
        refs = list(_socks.get(tid_str, []))
    for r in refs:
        s = r()
        if s is None:
            continue
        try:
            if s.fileno() != -1:
                _OrigSocket.shutdown(s, socket.SHUT_RDWR)
                n += 1
        except Exception:
            pass
    return n


def _async_raise(tid):
    n = ctypes.pythonapi.PyThreadState_SetAsyncExc(
        ctypes.c_ulong(tid), ctypes.py_object(CancelledDownload))
    if n > 1:
        ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_ulong(tid), None)
    return n


def cancel_task(task_id):
    tid_str = str(task_id)
    cancelled_ids.add(tid_str)
    killed = _kill_sockets(tid_str)
    tid = active_threads.get(tid_str)
    raised = _async_raise(tid) if tid else -1
    msg = "cancel_task id=%s sockets=%d async_exc=%s" % (tid_str, killed, raised)
    print(msg)
    return msg


class RateLimiter:
    def __init__(self, interval=0.5):
        self.interval = interval
        self.last_time = 0

    def check(self):
        now = time.time()
        if now - self.last_time >= self.interval:
            self.last_time = now
            return True
        return False


def _unload_module(mod_name):
    if mod_name in sys.modules:
        del sys.modules[mod_name]
    for k in list(sys.modules.keys()):
        if k.startswith(f"{mod_name}."):
            del sys.modules[k]


def setup_updates_path(updates_dir):
    if updates_dir and os.path.exists(updates_dir):
        if updates_dir in sys.path:
            sys.path.remove(updates_dir)
        sys.path.insert(0, updates_dir)


def get_yt_dlp_version(updates_dir=None):
    if updates_dir:
        setup_updates_path(updates_dir)
    try:
        import yt_dlp
        v = getattr(yt_dlp, "__version__", "bilinmiyor")
        if v == "bilinmiyor":
            try:
                from yt_dlp.version import __version__ as v2
                return v2
            except Exception:
                pass
        return v
    except Exception:
        return "bilinmiyor"


def get_gallery_dl_version(updates_dir=None):
    if updates_dir:
        setup_updates_path(updates_dir)
    try:
        import gallery_dl
        return getattr(gallery_dl, "__version__", "bilinmiyor")
    except Exception:
        return "bilinmiyor"


def get_versions(updates_dir=None):
    yt_ver = get_yt_dlp_version(updates_dir)
    gdl_ver = get_gallery_dl_version(updates_dir)
    return f"yt-dlp {yt_ver} • gallery-dl {gdl_ver}"


def get_versions_json(updates_dir=None):
    yt_ver = get_yt_dlp_version(updates_dir)
    gdl_ver = get_gallery_dl_version(updates_dir)
    return json.dumps({"yt_dlp": yt_ver, "gallery_dl": gdl_ver})


def _ffmpeg_usable(path, lib_dir=""):
    """ffmpeg gerçekten çalışıyor mu? (dosya var diye çalışır demek değil)"""
    if not path or not os.path.exists(path):
        return False, "dosya yok"
    try:
        os.chmod(path, 0o755)
    except Exception:
        pass
    try:
        # ffmpeg'in yanındaki .so kütüphanelerini (libavcodec vb.) bulabilsin
        dirs = [os.path.dirname(os.path.abspath(path))]
        if lib_dir and os.path.isdir(lib_dir):
            dirs.insert(0, lib_dir)
        old_ld = os.environ.get("LD_LIBRARY_PATH", "")
        cur = old_ld.split(":") if old_ld else []
        for d in reversed(dirs):
            if d not in cur:
                cur.insert(0, d)
        os.environ["LD_LIBRARY_PATH"] = ":".join(cur)
        r = subprocess.run([path, "-version"], capture_output=True, timeout=10,
                           env=os.environ.copy())
        if r.returncode == 0:
            return True, ""
        err = (r.stderr or b"").decode(errors="ignore")[:200]
        return False, "çıkış kodu %s: %s" % (r.returncode, err)
    except Exception as e:
        return False, "%s: %s" % (type(e).__name__, e)


def _create_ssl_context():
    ctx = ssl.create_default_context()
    try:
        import certifi
        ctx.load_verify_locations(cafile=certifi.where())
    except Exception:
        pass
    return ctx


def _download_file(url, target_path):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    ctx = _create_ssl_context()
    with urllib.request.urlopen(req, context=ctx, timeout=60) as resp, open(target_path, "wb") as out:
        shutil.copyfileobj(resp, out)


def _get_pypi_url(pkg_name):
    url = f"https://pypi.org/pypi/{pkg_name}/json"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    ctx = _create_ssl_context()
    with urllib.request.urlopen(req, context=ctx, timeout=30) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    urls = data.get("urls", [])
    for u in urls:
        fn = u.get("filename", "").lower()
        if fn.endswith(".whl"):
            return u["url"]
    for u in urls:
        fn = u.get("filename", "").lower()
        if fn.endswith(".zip") or fn.endswith(".tar.gz"):
            return u["url"]
    if urls:
        return urls[0]["url"]
    raise RuntimeError(f"{pkg_name} için PyPI indirme adresi bulunamadı")


def _download_and_extract_pkg(url, updates_dir, pkg_folder):
    os.makedirs(updates_dir, exist_ok=True)
    is_tar_url = ("tar.gz" in url.lower()) or ("tgz" in url.lower()) or ("tar" in url.lower())
    temp_file = os.path.join(updates_dir, f"_temp_{pkg_folder}." + ("tar.gz" if is_tar_url else "zip"))

    try:
        _download_file(url, temp_file)
        target_pkg_dir = os.path.join(updates_dir, pkg_folder)

        if os.path.exists(target_pkg_dir):
            shutil.rmtree(target_pkg_dir, ignore_errors=True)

        extracted_any = False

        if is_tar_url or tarfile.is_tarfile(temp_file):
            with tarfile.open(temp_file, "r:*") as tf:
                for member in tf.getmembers():
                    filename = member.name
                    parts = filename.replace("\\", "/").split("/")
                    if pkg_folder in parts:
                        idx = parts.index(pkg_folder)
                        rel_path = "/".join(parts[idx:])
                        if not rel_path:
                            continue
                        dest_file = os.path.join(updates_dir, rel_path)
                        if member.isdir():
                            os.makedirs(dest_file, exist_ok=True)
                        else:
                            os.makedirs(os.path.dirname(dest_file), exist_ok=True)
                            f = tf.extractfile(member)
                            if f:
                                with f, open(dest_file, "wb") as target:
                                    shutil.copyfileobj(f, target)
                                extracted_any = True
        else:
            with zipfile.ZipFile(temp_file, "r") as zf:
                for member in zf.infolist():
                    filename = member.filename
                    parts = filename.replace("\\", "/").split("/")
                    if pkg_folder in parts:
                        idx = parts.index(pkg_folder)
                        rel_path = "/".join(parts[idx:])
                        if not rel_path:
                            continue
                        dest_file = os.path.join(updates_dir, rel_path)
                        if member.is_dir():
                            os.makedirs(dest_file, exist_ok=True)
                        else:
                            os.makedirs(os.path.dirname(dest_file), exist_ok=True)
                            with zf.open(member) as source, open(dest_file, "wb") as target:
                                shutil.copyfileobj(source, target)
                                extracted_any = True

        if not extracted_any and not os.path.exists(target_pkg_dir):
            raise RuntimeError(f"{pkg_folder} paketi arşiv içerisinden çıkarılamadı!")
    finally:
        if os.path.exists(temp_file):
            try:
                os.remove(temp_file)
            except Exception:
                pass


def update_yt_dlp(updates_dir, channel="Stable"):
    os.makedirs(updates_dir, exist_ok=True)
    target_pkg_dir = os.path.join(updates_dir, "yt_dlp")

    if os.path.exists(target_pkg_dir):
        shutil.rmtree(target_pkg_dir, ignore_errors=True)
    _unload_module("yt_dlp")

    try:
        if channel == "Nightly":
            yt_url = "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/latest/download/yt-dlp.tar.gz"
        else:
            yt_url = _get_pypi_url("yt-dlp")

        _download_and_extract_pkg(yt_url, updates_dir, "yt_dlp")

        setup_updates_path(updates_dir)
        _unload_module("yt_dlp")

        import yt_dlp
        importlib.reload(yt_dlp)

        v = get_yt_dlp_version(updates_dir)
        if v == "bilinmiyor":
            raise RuntimeError("yt-dlp sürümü doğrulanamadı")
        return v
    except Exception as e:
        if os.path.exists(target_pkg_dir):
            shutil.rmtree(target_pkg_dir, ignore_errors=True)
        _unload_module("yt_dlp")
        raise e


def update_gallery_dl(updates_dir):
    os.makedirs(updates_dir, exist_ok=True)
    target_pkg_dir = os.path.join(updates_dir, "gallery_dl")

    if os.path.exists(target_pkg_dir):
        shutil.rmtree(target_pkg_dir, ignore_errors=True)
    _unload_module("gallery_dl")

    try:
        gdl_url = _get_pypi_url("gallery-dl")
        _download_and_extract_pkg(gdl_url, updates_dir, "gallery_dl")

        setup_updates_path(updates_dir)
        _unload_module("gallery_dl")

        import gallery_dl
        importlib.reload(gallery_dl)

        v = get_gallery_dl_version(updates_dir)
        if v == "bilinmiyor":
            raise RuntimeError("gallery-dl sürümü doğrulanamadı")
        return v
    except Exception as e:
        if os.path.exists(target_pkg_dir):
            shutil.rmtree(target_pkg_dir, ignore_errors=True)
        _unload_module("gallery_dl")
        raise e


def update_engines(updates_dir, channel="Stable"):
    yt_v = update_yt_dlp(updates_dir, channel)
    gdl_v = get_gallery_dl_version(updates_dir)
    return f"yt-dlp {yt_v} • gallery-dl {gdl_v}"


def _has_files(dest):
    for _root, _dirs, files in os.walk(dest):
        if files:
            return True
    return False


def _sanitize_filenames(dest):
    if not os.path.exists(dest):
        return
    for root, dirs, files in os.walk(dest):
        for filename in files:
            if "None" in filename:
                new_name = filename
                for pattern in ["None_", "_None_", "-None-", "_None", "-None", "None"]:
                    if pattern in new_name:
                        new_name = new_name.replace(pattern, "" if pattern != "None" else "")
                while "__" in new_name:
                    new_name = new_name.replace("__", "_")
                while "--" in new_name:
                    new_name = new_name.replace("--", "-")
                while "_-" in new_name:
                    new_name = new_name.replace("_-", "-")
                while "-_" in new_name:
                    new_name = new_name.replace("-_", "-")
                base, ext = os.path.splitext(new_name)
                base = base.strip("_.-")
                if not base:
                    base = "download"
                final_name = base + ext
                if final_name != filename:
                    old_path = os.path.join(root, filename)
                    new_path = os.path.join(root, final_name)
                    try:
                        os.rename(old_path, new_path)
                    except Exception:
                        pass


def _gallery(task_id, url, dest, cookies, template, sink=None, ffmpeg_path="", ffmpeg_lib_dir=""):
    import gallery_dl
    from gallery_dl import config
    ffmpeg_ok, _ffmpeg_why = _ffmpeg_usable(ffmpeg_path, ffmpeg_lib_dir)
    try:
        config.clear()
        config.load()
        config.set(('extractor',), 'timeout', 5)
        config.set(('downloader',), 'timeout', 5)
        config.set(('extractor', 'instagram'), 'ytdl', False)
        if ffmpeg_ok:
            config.set(('downloader', 'ytdl'), 'raw-options', {'ffmpeg_location': ffmpeg_path})
            config.set(('downloader', 'ytdl'), 'format', 'bestvideo+bestaudio/best')
            config.set(('downloader', 'ytdl'), 'merge-output-format', 'mp4')
        else:
            config.set(('downloader', 'ytdl'), 'format', 'b[acodec!=none][vcodec!=none]/b')
        config.set(('extractor', 'instagram'), 'videos', True)
    except Exception:
        pass
    for name in ("", "gallery-dl", "gallery_dl"):
        lg = logging.getLogger(name)
        for h in list(lg.handlers):
            lg.removeHandler(h)
    if "instagram.com/s/" in url and "?" in url:
        parts = url.split("?", 1)
        base = parts[0]
        query = parts[1]
        if "%3D" in query or "%26" in query or "%3d" in query or "%25" in query:
            url = base + "?" + urllib.parse.unquote(query)
    args = ["gallery-dl", "-D", dest, "-o", "instagram.ytdl=false", "-o", "ytdl=false"]
    if cookies:
        args += ["--cookies", cookies]
    if template:
        args += ["-f", template]
    args.append(url)
    buf = io.StringIO()
    old = sys.argv
    sys.argv = args
    code = 0

    tid_str = str(task_id)
    stop_event = threading.Event()

    def monitor_gallery():
        limiter = RateLimiter(0.5)
        while not stop_event.is_set():
            if tid_str in cancelled_ids or (sink and hasattr(sink, "isCancelled") and sink.isCancelled()):
                return  # not: bu ayrı thread; burada raise etmek indirmeyi durdurmaz
            try:
                total_bytes = 0
                if os.path.exists(dest):
                    for root, dirs, files in os.walk(dest):
                        for f in files:
                            try:
                                total_bytes += os.path.getsize(os.path.join(root, f))
                            except Exception:
                                pass
                if total_bytes > 0 and sink and limiter.check():
                    mb = total_bytes / (1024 * 1024)
                    detail = f"{mb:.1f} MB indirildi"
                    sink.updateProgress(-1, detail)
            except Exception:
                pass
            time.sleep(0.5)

    t = threading.Thread(target=monitor_gallery, daemon=True)
    if sink:
        t.start()

    try:
        with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
            code = gallery_dl.main() or 0
    except SystemExit as e:
        code = e.code or 0
    except Exception as e:
        buf.write("\n[Hata]: %s: %s\n" % (type(e).__name__, e))
        code = 1
    finally:
        stop_event.set()
        sys.argv = old
    return buf.getvalue() + "(çıkış kodu: %s)" % code


def scan_media(url, cookie_path=""):
    return "[]"


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


def _ytdlp(task_id, url, dest, cookies, template, sink, quality="best", ffmpeg_path="", ffmpeg_lib_dir="",
           subtitle_lang="off", subtitle_embed=True):
    import yt_dlp
    limiter = RateLimiter(0.5)
    tid_str = str(task_id)

    def ytdlp_hook(d):
        if tid_str in cancelled_ids or (sink and hasattr(sink, "isCancelled") and sink.isCancelled()):
            raise CancelledDownload("İptal edildi")
        if d.get('status') == 'downloading':
            total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
            downloaded = d.get('downloaded_bytes', 0)
            if total > 0:
                pct = int(downloaded * 100 / total)
            else:
                frag_idx = d.get('fragment_index')
                frag_count = d.get('fragment_count')
                if frag_idx is not None and frag_count and frag_count > 0:
                    pct = int(frag_idx * 100 / frag_count)
                else:
                    pct = -1

            speed = d.get('speed') or 0
            eta = int(d.get('eta') or 0)

            speed_str = f"{speed / (1024 * 1024):.1f} MB/s" if speed > 0 else ""
            eta_str = f"{eta} sn" if eta > 0 else ""

            if pct >= 0:
                detail = f"%{pct} • {speed_str} ({eta_str})".replace(" ()", "").strip()
            else:
                detail = f"{speed_str} ({eta_str})".replace(" ()", "").strip()
                if not detail:
                    detail = "İndiriliyor..."

            if sink and limiter.check():
                sink.updateProgress(pct, detail)

    log = _Log()
    if quality == "audio_only":
        fmt = 'bestaudio[ext=m4a]/bestaudio/best'
    elif quality and quality != "best" and quality.rstrip("p").isdigit():
        # "2160p", "1440p", "1080p", "720p", "360p" gibi belirli bir
        # çözünürlük tavanı istenmiş. O tavanın altındaki en iyi video+ses
        # denenir; o çözünürlük hiç yoksa yt-dlp otomatik olarak var olan
        # en yakın altı seçer (best[height<=N] her zaman bir sonuç bulur).
        h = int(quality.rstrip("p"))
        fmt = f'bestvideo[height<={h}]+bestaudio/best[height<={h}]/best'
    else:
        fmt = 'bestvideo+bestaudio/best'

    outtmpl = os.path.join(dest, template) if template else os.path.join(dest, "%(title)s_%(id)s.%(ext)s")

    ydl_opts = {
        "outtmpl": outtmpl,
        "format": fmt,
        "merge_output_format": "mp4",
        "format_sort": ['res', 'fps', 'codec:h264'],
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "socket_timeout": 5,
        "logger": log,
        "progress_hooks": [ytdlp_hook],
        # Video genelde küçük parçalar (fragment) halinde geliyor; bunları
        # tek tek sırayla değil, aynı anda birden fazla bağlantıyla çekmek
        # gerçek bir hız artışı sağlıyor (CLI'daki -N/--concurrent-fragments
        # ile aynı şey). Sunucunun izin verdiği gerçek üst sınırı aşamaz,
        # yalnızca mevcut bant genişliğini daha iyi kullanır.
        "concurrent_fragment_downloads": 8,
    }
    if cookies:
        ydl_opts["cookiefile"] = cookies
    ffmpeg_ok, ffmpeg_why = _ffmpeg_usable(ffmpeg_path, ffmpeg_lib_dir)
    single_fmt = 'best[vcodec!=none][acodec!=none]/best'
    if ffmpeg_ok:
        ydl_opts['ffmpeg_location'] = ffmpeg_path
        log.lines.append("bilgi: ffmpeg aktif")
    else:
        # ffmpeg yok ya da çalışmıyor: birleştirme gerektirmeyen tek parça formata düş
        log.warning("ffmpeg kullanılamıyor (%s), tek parça formata düşülüyor" % (ffmpeg_why if ffmpeg_path else "yol boş"))
        ydl_opts['format'] = single_fmt
        ydl_opts.pop("merge_output_format", None)

    if subtitle_lang and subtitle_lang != "off":
        langs = [x.strip() for x in subtitle_lang.split(",") if x.strip()]
        ydl_opts["writesubtitles"] = True
        # İstenen dilde elle hazırlanmış altyazı yoksa YouTube'un otomatik
        # (makine) altyazısına düşülür; hiç yoksa sessizce atlanır, indirme
        # bu yüzden başarısız olmaz.
        ydl_opts["writeautomaticsub"] = True
        ydl_opts["subtitleslangs"] = langs
        if subtitle_embed and ffmpeg_ok:
            ydl_opts["embedsubtitles"] = True
            ydl_opts.setdefault("postprocessors", []).append({"key": "FFmpegEmbedSubtitle"})
        else:
            if subtitle_embed and not ffmpeg_ok:
                log.lines.append("uyarı: ffmpeg yok, altyazı videoya gömülemiyor; ayrı dosya olarak inecek")
            ydl_opts["subtitlesformat"] = "srt/best"
            if ffmpeg_ok:
                ydl_opts.setdefault("postprocessors", []).append(
                    {"key": "FFmpegSubtitlesConvertor", "format": "srt"}
                )

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])
    except Exception as e:
        err_msg = str(e)
        if "ffmpeg is not installed" in err_msg.lower() or "ffmpeg not found" in err_msg.lower():
            log.warning("ffmpeg hatası, tek parça formatla yeniden deneniyor...")
            ydl_opts["format"] = single_fmt
            ydl_opts.pop("merge_output_format", None)
            ydl_opts.pop("ffmpeg_location", None)
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.download([url])
        elif "output template" in err_msg.lower() or "template" in err_msg.lower() or "invalid" in err_msg.lower() or "outtmpl" in err_msg.lower():
            log.warning(f"Şablon hatası ({err_msg}), varsayılan şablon ile yeniden deneniyor...")
            ydl_opts["outtmpl"] = os.path.join(dest, "%(title).80B [%(id)s].%(ext)s")
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.download([url])
        else:
            raise e
    return "\n".join(log.lines) or "tamam"


_IG_UA = ("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
          "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
_IG_CODE_RE = re.compile(r"instagram\.com/(?:[A-Za-z0-9_.]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)")


def _ig_unescape(s):
    s = s.replace("\\u0026", "&").replace("\\/", "/")
    s = s.replace("\\", "")
    return _html.unescape(s)


_IG_BOT_UA = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"


def _ig_fetch(page_url, referer="https://www.instagram.com/", ua=None):
    req = urllib.request.Request(page_url, headers={
        "User-Agent": ua or _IG_UA,
        "Accept": "text/html,application/xhtml+xml,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
        "Referer": referer,
    })
    with urllib.request.urlopen(req, context=_create_ssl_context(), timeout=20) as resp:
        final = resp.geturl()
        body = resp.read().decode("utf-8", errors="ignore")
    return final, body


def _ig_image_urls(page):
    """Sayfadan fotoğraf adreslerini çıkarır (tam boy öncelikli)."""
    found = []

    def add(u):
        u = _ig_unescape(u).strip()
        if not u.startswith("http"):
            return
        host = urllib.parse.urlparse(u).netloc
        if "cdninstagram" not in host and "fbcdn" not in host:
            return
        key = urllib.parse.urlparse(u).path
        if all(urllib.parse.urlparse(x).path != key for x in found):
            found.append(u)

    # 1) JSON içindeki tam boy adresler
    for m in re.finditer(r'display_url\\*"\s*:\s*\\*"(.+?)\\*"', page):
        add(m.group(1))
    # 2) embed sayfasındaki <img class="EmbeddedMediaImage">
    #    (JSON'dan tam boy geldiyse küçük sürümü ikinci dosya olarak ekleme)
    if not found:
        for tag in re.findall(r"<img\b[^>]*>", page):
            if "EmbeddedMediaImage" in tag:
                m = re.search(r'\bsrc="([^"]+)"', tag)
                if m:
                    add(m.group(1))
    # 3) son çare: og:image
    if not found:
        for m in re.finditer(r'<meta[^>]+property="og:image"[^>]+content="([^"]+)"', page):
            add(m.group(1))
    return found


def _ig_og_image(page):
    """<meta property="og:image" content="..."> adresini döndürür (öznitelik sırası fark etmez)."""
    for tag in re.findall(r"<meta\b[^>]*>", page):
        if re.search(r'property\s*=\s*["\']og:image["\']', tag):
            m = re.search(r'content\s*=\s*["\']([^"\']+)["\']', tag)
            if m:
                u = _ig_unescape(m.group(1)).strip()
                host = urllib.parse.urlparse(u).netloc
                if u.startswith("http") and ("cdninstagram" in host or "fbcdn" in host):
                    return u
    return None


_IG_RESERVED = {"p", "reel", "reels", "tv", "s", "stories", "explore", "accounts", "embed", "web", "direct"}
_IG_USER_OK = re.compile(r"^[A-Za-z0-9_.]{1,30}$")


def _ig_username_from_url(u):
    """instagram.com/<kullanici>/p/<kod>/ biçimli adreslerden kullanıcı adını alır."""
    try:
        parts = [x for x in urllib.parse.urlparse(u).path.split("/") if x]
    except Exception:
        return None
    if len(parts) >= 2 and parts[1] in ("p", "reel", "reels", "tv"):
        cand = parts[0]
        if cand.lower() not in _IG_RESERVED and _IG_USER_OK.match(cand):
            return cand
    return None


def _ig_username(page, final_url=""):
    """Gönderi sayfasından (embed ya da önizleme) paylaşan hesabın kullanıcı adını bulur.
    Giriş gerektirmez; bulamazsa None döner."""
    # 1) yönlendirilmiş/kanonik adres: instagram.com/<kullanici>/p/<kod>/
    u = _ig_username_from_url(final_url)
    if u:
        return u
    for m in re.finditer(r'(?:canonical"[^>]*href|og:url"[^>]*content|href)=["\']([^"\']*instagram\.com/[^"\']+/(?:p|reel|reels|tv)/[^"\']+)', page):
        u = _ig_username_from_url(_html.unescape(m.group(1)))
        if u:
            return u
    # 2) embed sayfasındaki kullanıcı adı alanı
    m = re.search(r'class=["\'][^"\']*UsernameText[^"\']*["\'][^>]*>\s*([A-Za-z0-9_.]{1,30})\s*<', page)
    if m:
        return m.group(1)
    # 3) sayfadaki JSON: "owner":{..."username":"..."} ya da düz "username":"..."
    txt = page.replace('\\"', '"')
    m = re.search(r'"owner"\s*:\s*\{[^{}]*?"username"\s*:\s*"([A-Za-z0-9_.]{1,30})"', txt)
    if m:
        return m.group(1)
    m = re.search(r'"username"\s*:\s*"([A-Za-z0-9_.]{1,30})"', txt)
    if m:
        return m.group(1)
    # 4) og:description: '12 likes, 3 comments - kullanici on March 1, 2025: "..."'
    m = re.search(r'-\s+([A-Za-z0-9_.]{1,30})\s+on\s+[A-Z][a-z]+ \d{1,2}, \d{4}', _html.unescape(page))
    if m:
        return m.group(1)
    return None


def _ig_scan_cdn(page, log=None):
    """Sayfadaki TÜM cdninstagram/fbcdn görsel adreslerini tarar.
    Profil resimlerini eler, aynı fotoğrafın en büyük sürümünü seçer."""
    txt = _ig_unescape(page)
    cands = []
    for m in re.finditer(r"https?://[^\s\"'<>()]+", txt):
        u = m.group(0).rstrip(",;")
        host = urllib.parse.urlparse(u).netloc
        if "cdninstagram" not in host and "fbcdn" not in host:
            continue
        path = urllib.parse.urlparse(u).path
        low = path.lower()
        if not (low.endswith((".jpg", ".jpeg", ".webp", ".png")) or ".jpg" in low):
            continue
        if "2885-19" in path or "/t51.2885-19/" in path:  # profil resmi
            continue
        if "static" in host or "/rsrc.php" in path:
            continue
        cands.append(u)

    def dim(u):
        mm = re.search(r"[sp](\d{2,4})x\d{2,4}", u)
        return int(mm.group(1)) if mm else 99999  # boyut belirtilmemiş = orijinal

    best = {}
    order = []
    for u in cands:
        key = os.path.basename(urllib.parse.urlparse(u).path)
        if key not in best:
            best[key] = u
            order.append(key)
        elif dim(u) > dim(best[key]):
            best[key] = u
    res = [best[k] for k in order]
    if log is not None:
        log.append("  CDN adayı: %d, benzersiz: %d" % (len(cands), len(res)))
        for u in res[:4]:
            log.append("  aday: %s (boyut %s)" % (u[:110], dim(u)))
        if not res:
            log.append("  sayfa ipuçları: img=%d cdninstagram=%d display_url=%d video=%d Embedded=%d" % (
                page.count("<img"), page.count("cdninstagram"), page.count("display_url"),
                page.count("video"), page.count("Embedded")))
            log.append("  anahtarlar: og:image=%d scontent=%d image_versions=%d shortcode=%d graphql=%d data-sjs=%d" % (
                page.count("og:image"), page.count("scontent"), page.count("image_versions"),
                page.count("shortcode"), page.count("graphql"), page.count("data-sjs")))
            t = re.search(r"<title>(.{0,80})", page)
            if t:
                log.append("  başlık: %s" % t.group(1).replace("\n", " "))
            k = page.find("og:image")
            if k >= 0:
                log.append("  og:image çevresi: %s" % page[max(0, k - 20):k + 220].replace("\n", " "))
            k = page.find("scontent")
            if k >= 0:
                log.append("  ilk scontent: %s" % page[max(0, k - 60):k + 200].replace("\n", " "))
    return res


def _ig_embed(task_id, url, dest, sink=None):
    """Giriş/çerez olmadan, herkese açık Instagram gönderisinin fotoğrafını embed sayfasından alır."""
    m = _IG_CODE_RE.search(url)
    if not m:
        return "gönderi kodu bulunamadı"
    code = m.group(1)
    log = ["gönderi kodu: %s" % code]
    username = _ig_username_from_url(url)
    images = []
    attempts = [("embed/captioned/", None), ("embed/", None), ("", _IG_BOT_UA)]
    for suffix, ua in attempts:
        page_url = "https://www.instagram.com/p/%s/%s" % (code, suffix)
        suffix = (suffix or "ana sayfa (önizleme botu)")
        try:
            final, page = _ig_fetch(page_url, ua=ua)
        except Exception as e:
            log.append("%s -> %s: %s" % (suffix, type(e).__name__, e))
            continue
        log.append("%s -> %d bayt, son adres: %s" % (suffix, len(page), final[:80]))
        if "accounts/login" in final:
            log.append("  giriş sayfasına yönlendirildi")
            continue
        if not username:
            username = _ig_username(page, final)
            if username:
                log.append("  kullanıcı adı: %s" % username)
        if ua:
            # Önizleme botu sayfası: gönderinin kendi görseli og:image'dır.
            # Sayfadaki diğer görseller "benzer gönderiler" küçük resimleridir, alınmaz.
            # Öncelik: boyut parametresi olmayan (orijinal çözünürlüklü) ilk adres.
            # og:image yalnızca 640x640 kırpılmış önizlemedir, en son çare.
            cands = _ig_scan_cdn(page, log)
            full = [u for u in cands if not re.search(r"[sp]\d{2,4}x\d{2,4}", u)]
            log.append("  orijinal boyutlu aday: %d / %d" % (len(full), len(cands)))
            if full:
                images = full[:20]  # carousel: sayfadaki tüm orijinal boyutlu görseller (Instagram üst sınırı 20)
            else:
                og = _ig_og_image(page)
                images = [og] if og else cands[:1]
                if images:
                    log.append("  uyarı: orijinal bulunamadı, düşük çözünürlüklü önizleme alınacak")
        else:
            images = _ig_scan_cdn(page, log) or _ig_image_urls(page)
        log.append("  bulunan görsel: %d" % len(images))
        if images:
            break
    if not images:
        return "\n".join(log) + "\nfotoğraf adresi bulunamadı"

    os.makedirs(dest, exist_ok=True)
    saved = 0
    for i, img in enumerate(images, 1):
        if str(task_id) in cancelled_ids:
            raise CancelledDownload("İptal edildi")
        path = urllib.parse.urlparse(img).path
        ext = os.path.splitext(path)[1].lower()
        if ext not in (".jpg", ".jpeg", ".png", ".webp"):
            ext = ".jpg"
        prefix = username if username else "instagram"
        name = "%s_%s%s%s" % (prefix, code, ("_%d" % i) if len(images) > 1 else "", ext)
        target = os.path.join(dest, name)
        try:
            if sink:
                sink.updateProgress(-1, "Fotoğraf indiriliyor (%d/%d)" % (i, len(images)))
            req = urllib.request.Request(img, headers={"User-Agent": _IG_UA, "Referer": "https://www.instagram.com/"})
            with urllib.request.urlopen(req, context=_create_ssl_context(), timeout=30) as resp, open(target, "wb") as out:
                shutil.copyfileobj(resp, out)
            size = os.path.getsize(target)
            if size < 2048:  # hata sayfası / boş cevap
                os.remove(target)
                log.append("%s çok küçük (%d bayt), atlandı" % (name, size))
            else:
                saved += 1
                log.append("%s kaydedildi (%.0f KB)" % (name, size / 1024.0))
        except Exception as e:
            log.append("%s indirilemedi: %s: %s" % (name, type(e).__name__, e))
            try:
                if os.path.exists(target):
                    os.remove(target)
            except Exception:
                pass
    log.append("toplam %d/%d fotoğraf" % (saved, len(images)))
    return "\n".join(log)


def _run_tools(task_id, url, out_dir, cookies, gallery_template, ytdlp_template, sink, quality, ffmpeg_path="", ffmpeg_lib_dir="",
               subtitle_lang="off", subtitle_embed=True):
    tid_str = str(task_id)
    _tl.task_id = tid_str
    is_ig_video = "instagram.com" in url and any(p in url for p in ["/reel/", "/reels/", "/p/", "/tv/"])
    if any(d in url for d in YT_FIRST) or is_ig_video:
        order = ["ytdlp", "gallery"]
    else:
        order = ["gallery", "ytdlp"]
    parts = []
    for tool in order:
        if tid_str in cancelled_ids:
            raise CancelledDownload("İptal edildi")
        try:
            if tool == "gallery":
                out = _gallery(task_id, url, out_dir, cookies, gallery_template, sink, ffmpeg_path, ffmpeg_lib_dir)
            else:
                out = _ytdlp(task_id, url, out_dir, cookies, ytdlp_template, sink, quality, ffmpeg_path, ffmpeg_lib_dir,
                             subtitle_lang, subtitle_embed)
        except Exception as e:
            out = "%s: %s" % (type(e).__name__, e)
        parts.append("[%s]\n%s" % (tool, out.strip()))
        if _has_files(out_dir):
            break
    # Yedek: Instagram gönderisi iki araçla da inmediyse (genelde giriş isteyen fotoğraf),
    # çerezsiz embed sayfasından fotoğrafı almayı dene.
    if (not _has_files(out_dir) and "instagram.com" in url and _IG_CODE_RE.search(url)
            and tid_str not in cancelled_ids):
        try:
            out = _ig_embed(task_id, url, out_dir, sink)
        except CancelledDownload:
            raise
        except Exception as e:
            out = "%s: %s" % (type(e).__name__, e)
        parts.append("[embed]\n%s" % out.strip())
    _sanitize_filenames(out_dir)
    return "\n\n".join(parts)


def _hard_stop(tid_str, w):
    for _ in range(16):  # en fazla ~4 sn
        _kill_sockets(tid_str)
        if w.ident:
            _async_raise(w.ident)
        w.join(0.25)
        if not w.is_alive():
            return True
    print("UYARI: iptal edilen worker thread hala calisiyor id=%s" % tid_str)
    return False


CANCEL_RESULT = "[iptal]\nKullanıcı tarafından iptal edildi"


def run(task_id, url, out_dir, cookies, gallery_template, ytdlp_template, sink, updates_dir=None, quality="best", ffmpeg_path="", ffmpeg_lib_dir="",
        subtitle_lang="off", subtitle_embed=True):
    tid_str = str(task_id)
    if updates_dir:
        setup_updates_path(updates_dir)
    result = {}

    def work():
        try:
            result["out"] = _run_tools(task_id, url, out_dir, cookies, gallery_template, ytdlp_template, sink, quality, ffmpeg_path, ffmpeg_lib_dir,
                                        subtitle_lang, subtitle_embed)
        except BaseException as e:
            result["err"] = e

    w = threading.Thread(target=work, name="dl-" + tid_str, daemon=True)
    w.start()
    active_threads[tid_str] = w.ident
    try:
        while w.is_alive():
            w.join(0.25)
            if tid_str in cancelled_ids:
                _hard_stop(tid_str, w)
                return CANCEL_RESULT
        if tid_str in cancelled_ids:
            return CANCEL_RESULT
        err = result.get("err")
        if err is not None and not isinstance(err, CancelledDownload):
            raise err
        return result.get("out", "")
    finally:
        active_threads.pop(tid_str, None)
        if not w.is_alive():
            cancelled_ids.discard(tid_str)
            with _socks_lock:
                _socks.pop(tid_str, None)
