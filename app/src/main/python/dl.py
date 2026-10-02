import ctypes
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


def _gallery(task_id, url, dest, cookies, template, sink=None):
    import gallery_dl
    from gallery_dl import config
    try:
        config.clear()
        config.load()
        config.set(('extractor',), 'timeout', 5)
        config.set(('downloader',), 'timeout', 5)
        # ffmpeg yok: ses+video ayri inip birlesmiyor -> sesli tek dosya iste
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


def _ytdlp(task_id, url, dest, cookies, template, sink, quality="best"):
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
            eta = d.get('eta') or 0

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
    if quality == "720p":
        fmt = 'best[height<=720][ext=mp4]/best[height<=720]/best'
    elif quality == "audio_only":
        fmt = 'bestaudio[ext=m4a]/bestaudio/best'
    else:
        fmt = 'best[ext=mp4]/best'

    outtmpl = os.path.join(dest, template) if template else os.path.join(dest, "%(title).80B [%(id)s].%(ext)s")

    ydl_opts = {
        "outtmpl": outtmpl,
        "format": fmt,
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "socket_timeout": 5,
        "logger": log,
        "progress_hooks": [ytdlp_hook],
    }
    if cookies:
        ydl_opts["cookiefile"] = cookies

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])
    except Exception as e:
        err_msg = str(e)
        if "output template" in err_msg.lower() or "template" in err_msg.lower() or "invalid" in err_msg.lower() or "outtmpl" in err_msg.lower():
            log.warning(f"Şablon hatası ({err_msg}), varsayılan şablon ile yeniden deneniyor...")
            ydl_opts["outtmpl"] = os.path.join(dest, "%(title).80B [%(id)s].%(ext)s")
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.download([url])
        else:
            raise e
    return "\n".join(log.lines) or "tamam"


def _run_tools(task_id, url, out_dir, cookies, gallery_template, ytdlp_template, sink, quality):
    tid_str = str(task_id)
    _tl.task_id = tid_str
    if any(d in url for d in YT_FIRST):
        order = ["ytdlp", "gallery"]
    else:
        order = ["gallery", "ytdlp"]
    parts = []
    for tool in order:
        if tid_str in cancelled_ids:
            raise CancelledDownload("İptal edildi")
        try:
            if tool == "gallery":
                out = _gallery(task_id, url, out_dir, cookies, gallery_template, sink)
            else:
                out = _ytdlp(task_id, url, out_dir, cookies, ytdlp_template, sink, quality)
        except Exception as e:
            out = "%s: %s" % (type(e).__name__, e)
        parts.append("[%s]\n%s" % (tool, out.strip()))
        if _has_files(out_dir):
            break
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


def run(task_id, url, out_dir, cookies, gallery_template, ytdlp_template, sink, updates_dir=None, quality="best"):
    tid_str = str(task_id)
    if updates_dir:
        setup_updates_path(updates_dir)
    result = {}

    def work():
        try:
            result["out"] = _run_tools(task_id, url, out_dir, cookies, gallery_template, ytdlp_template, sink, quality)
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
