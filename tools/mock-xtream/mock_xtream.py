"""Servidor Xtream Codes de demostración para capturas de Lumen (datos ficticios)."""
import base64, json, os, random, time, colorsys
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
IMG = os.path.join(HERE, "img")
SAMPLE = os.path.join(HERE, "sample.ts")
FONT = os.environ.get("LUMEN_FONT_DIR", os.path.join(HERE, "..", "..", "app", "src", "main", "res", "font"))
BASE = "http://10.0.2.2:8099"
random.seed(7)

LIVE_CATS = ["España", "Deportes", "Cine y series", "Noticias", "Infantil", "Música", "Documentales", "Adultos"]
CHANNELS = [
    ("ES: La 1 HD", 0), ("ES: La 2 HD", 0), ("ES: Antena 3 FHD", 0), ("ES: Cuatro HD", 0), ("ES: Telecinco FHD", 0),
    ("ES: laSexta HD", 0), ("ES: Energy HD", 2), ("ES: DMAX FHD", 6), ("ES: Neox HD", 2), ("ES: Nova HD", 2),
    ("ES: Mega HD", 2), ("ES: FDF HD", 2), ("ES: Paramount HD", 2), ("ES: TRECE HD", 0),
    ("DEP: Liga 1 FHD", 1), ("DEP: Liga 2 HD", 1), ("DEP: Champions FHD", 1), ("DEP: Golf HD", 1), ("DEP: Tenis 4K", 1),
    ("DEP: Motor HD", 1), ("DEP: Baloncesto HD", 1), ("DEP: NBA HD", 1), ("DEP: UFC PPV", 1),
    ("CINE: Estrenos HD", 2), ("CINE: Acción FHD", 2), ("CINE: Comedia HD", 2), ("CINE: Clásicos HD", 2),
    ("NOT: 24 Horas HD", 3), ("NOT: Euronews ES", 3), ("NOT: CNN Int", 3), ("NOT: BBC World", 3),
    ("INF: Clan HD", 4), ("INF: Boing HD", 4), ("INF: Disney Jr", 4),
    ("MUS: Hits FM TV", 5), ("MUS: Sol Música", 5), ("DOC: Historia HD", 6), ("DOC: Naturaleza 4K", 6), ("DOC: Odisea HD", 6),
    ("XXX: Canal privado", 7),
]
PROGRAMS = ["Telediario", "FBI: Most Wanted", "Mentes criminales", "Pesca radical", "El tiempo", "Fútbol: Jornada 9",
            "Documental: Océanos", "Cine: Horizonte rojo", "Informe semanal", "La ruleta", "Noticias 24h", "Concierto en vivo",
            "Los Simpson", "MasterChef", "Tenis: Final ATP", "Fórmula 1: Clasificación", "NBA: Lakers vs Celtics", "Zapeando",
            "Callejeros viajeros", "Pasapalabra", "Supervivientes", "El hormiguero", "Planeta Tierra", "Dibujos animados"]
MOVIE_CATS = ["Estrenos", "Acción", "Comedia", "Drama", "Ciencia ficción", "Infantil", "4K"]
MOVIES = ["Horizonte rojo", "La última estación", "Sombras de neón", "El faro del norte", "Código Aurora", "Marea alta",
          "Ciudad de cristal", "El jardín de invierno", "Vuelo nocturno", "Los cartógrafos", "Polvo de estrellas", "Frontera sur",
          "La casa de las cerezas", "Órbita", "Sin retorno", "El último verano", "Tormenta de arena", "Ecos del pasado",
          "Pequeños gigantes", "Doble juego", "Medianoche en Lisboa", "El silencio del lobo", "Raíces", "Luz de agosto",
          "Kilómetro cero", "La isla de los pájaros", "Nebulosa", "Contrarreloj", "El reloj de arena", "Viento del este"]
SERIES_CATS = ["Drama", "Comedia", "Thriller", "Ciencia ficción", "Documental"]
SERIES = ["Distrito Norte", "La familia Ortega", "Señales", "Puerto Viejo", "Estación 7", "Los herederos",
          "Código rojo", "Mar de fondo", "Nómadas", "El internado del lago", "Proyecto Atlas", "Tierra firme"]

def hue_color(i, s=0.65, v=0.85):
    r, g, b = colorsys.hsv_to_rgb((i * 0.137) % 1.0, s, v)
    return int(r * 255), int(g * 255), int(b * 255)

def font(size, bold=True):
    return ImageFont.truetype(os.path.join(FONT, "inter_bold.ttf" if bold else "inter_medium.ttf"), size)

def make_images():
    os.makedirs(IMG, exist_ok=True)
    for i, (name, _) in enumerate(CHANNELS):
        p = os.path.join(IMG, f"logo_{i}.png")
        if os.path.exists(p): continue
        im = Image.new("RGBA", (320, 200), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
        d.rounded_rectangle([20, 30, 300, 170], 28, fill=hue_color(i))
        label = name.split(": ", 1)[-1].replace(" HD", "").replace(" FHD", "").replace(" 4K", "")
        f = font(40 if len(label) < 10 else 30)
        w = d.textlength(label, font=f); d.text(((320 - w) / 2, 75), label, font=f, fill="white")
        im.save(p)
    for kind, names in (("movie", MOVIES), ("series", SERIES)):
        for i, title in enumerate(names):
            p = os.path.join(IMG, f"{kind}_{i}.png")
            b = os.path.join(IMG, f"{kind}_bd_{i}.png")
            if os.path.exists(p) and os.path.exists(b): continue
            c1 = hue_color(i + (3 if kind == "series" else 0), 0.7, 0.55); c2 = hue_color(i + 5, 0.8, 0.2)
            im = Image.new("RGB", (400, 600)); d = ImageDraw.Draw(im)
            for y in range(600):
                t = y / 600; d.line([(0, y), (400, y)], fill=tuple(int(c1[k] * (1 - t) + c2[k] * t) for k in range(3)))
            d.ellipse([60 + i * 7 % 120, 120, 340 - i * 5 % 80, 400], fill=tuple(min(255, c + 40) for c in c1))
            words = title.split(); lines, cur = [], ""
            for wd in words:
                if len(cur + " " + wd) > 12: lines.append(cur.strip()); cur = wd
                else: cur += " " + wd
            lines.append(cur.strip())
            f = font(46)
            for k, ln in enumerate(lines):
                w = d.textlength(ln, font=f); d.text(((400 - w) / 2, 430 + k * 54), ln, font=f, fill="white")
            im.save(p)
            bd = im.resize((400, 600)).crop((0, 100, 400, 325)).resize((1280, 720)); bd.save(b)

def b64(s): return base64.b64encode(s.encode()).decode()

def epg_for(i, start_offset_h=-6, hours=30, step_min=None):
    rnd = random.Random(i)
    now = int(time.time()); t = now - (now % 1800) + start_offset_h * 3600
    out = []
    while t < now + hours * 3600:
        dur = (step_min or rnd.choice([30, 45, 60, 90, 120])) * 60
        out.append((t, t + dur, rnd.choice(PROGRAMS)))
        t += dur
    return out

def live_streams():
    res = []
    for i, (name, cat) in enumerate(CHANNELS):
        res.append({"num": i + 1, "name": name, "stream_type": "live", "stream_id": 1000 + i,
                    "stream_icon": f"{BASE}/img/logo_{i}.png", "epg_channel_id": f"ch{i}.es",
                    "category_id": str(cat + 1), "tv_archive": 1 if i % 3 == 0 else 0, "tv_archive_duration": "7" if i % 3 == 0 else 0})
    return res

class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, *a): pass

    def send(self, code, body, ctype="application/json"):
        data = body if isinstance(body, bytes) else body.encode()
        self.send_response(code); self.send_header("Content-Type", ctype); self.send_header("Content-Length", str(len(data))); self.end_headers()
        self.wfile.write(data)

    def do_HEAD(self): self.send(200, b"")

    def do_GET(self):
        u = urlparse(self.path); q = {k: v[0] for k, v in parse_qs(u.query).items()}
        path = u.path
        if path.startswith("/img/"):
            f = os.path.join(IMG, os.path.basename(path))
            return self.send(200, open(f, "rb").read(), "image/png") if os.path.exists(f) else self.send(404, "")
        if path.startswith(("/live/", "/movie/", "/series/", "/timeshift/")):
            return self.send(200, open(SAMPLE, "rb").read(), "video/mp2t")
        if path == "/xmltv.php":
            out = ['<?xml version="1.0" encoding="UTF-8"?><tv>']
            for i in range(len(CHANNELS)):
                for s, e, title in epg_for(i):
                    fs = time.strftime("%Y%m%d%H%M%S +0000", time.gmtime(s)); fe = time.strftime("%Y%m%d%H%M%S +0000", time.gmtime(e))
                    out.append(f'<programme start="{fs}" stop="{fe}" channel="ch{i}.es"><title>{title}</title><desc>Episodio de {title.lower()} con todos los detalles de la emisión de hoy y un resumen de lo que verás.</desc></programme>')
            out.append("</tv>")
            return self.send(200, "".join(out), "application/xml")
        if path != "/player_api.php":
            return self.send(404, "")
        a = q.get("action")
        if not a:
            return self.send(200, json.dumps({
                "user_info": {"username": q.get("username"), "auth": 1, "status": "Active", "exp_date": str(int(time.time()) + 365 * 86400),
                              "max_connections": "2", "active_cons": "0", "message": ""},
                "server_info": {"url": "10.0.2.2", "port": "8099", "timezone": "Europe/Madrid", "timestamp_now": int(time.time())}}))
        if a == "get_live_categories":
            return self.send(200, json.dumps([{"category_id": str(i + 1), "category_name": n, "parent_id": 0} for i, n in enumerate(LIVE_CATS)]))
        if a == "get_vod_categories":
            return self.send(200, json.dumps([{"category_id": str(i + 1), "category_name": n, "parent_id": 0} for i, n in enumerate(MOVIE_CATS)]))
        if a == "get_series_categories":
            return self.send(200, json.dumps([{"category_id": str(i + 1), "category_name": n, "parent_id": 0} for i, n in enumerate(SERIES_CATS)]))
        if a == "get_live_streams":
            return self.send(200, json.dumps(live_streams()))
        if a == "get_vod_streams":
            return self.send(200, json.dumps([{"num": i + 1, "name": t, "stream_id": 5000 + i, "stream_icon": f"{BASE}/img/movie_{i}.png",
                                                "rating": f"{6 + (i * 37 % 35) / 10:.1f}", "added": str(int(time.time()) - i * 86400),
                                                "category_id": str(i % len(MOVIE_CATS) + 1), "container_extension": "mp4"} for i, t in enumerate(MOVIES)]))
        if a == "get_series":
            return self.send(200, json.dumps([{"num": i + 1, "name": t, "series_id": 8000 + i, "cover": f"{BASE}/img/series_{i}.png",
                                                "plot": f"{t} sigue a un grupo de personajes cuyas vidas se cruzan cuando un secreto del pasado sale a la luz.",
                                                "genre": SERIES_CATS[i % len(SERIES_CATS)], "releaseDate": f"{2018 + i % 7}-03-01", "rating": f"{7 + i % 20 / 10:.1f}",
                                                "backdrop_path": [f"{BASE}/img/series_bd_{i}.png"], "category_id": str(i % len(SERIES_CATS) + 1)} for i, t in enumerate(SERIES)]))
        if a == "get_vod_info":
            i = int(q.get("vod_id", "5000")) - 5000
            return self.send(200, json.dumps({"info": {"movie_image": f"{BASE}/img/movie_{i}.png", "backdrop_path": [f"{BASE}/img/movie_bd_{i}.png"],
                                                        "plot": f"Cuando una tormenta deja aislado el pueblo, una ingeniera descubre que «{MOVIES[i]}» esconde algo más que una leyenda local. Un thriller elegante sobre la memoria y las segundas oportunidades.",
                                                        "cast": "Lucía Martín, Daniel Ruiz, Carmen Vidal", "director": "Ana Ferrer", "genre": MOVIE_CATS[i % len(MOVIE_CATS)],
                                                        "releasedate": f"{2019 + i % 6}-05-12", "duration_secs": 6000 + i * 120, "rating": "7.8"},
                                               "movie_data": {"stream_id": 5000 + i, "name": MOVIES[i], "container_extension": "mp4"}}))
        if a == "get_series_info":
            i = int(q.get("series_id", "8000")) - 8000
            eps = {str(s): [{"id": str(90000 + i * 100 + s * 10 + e), "episode_num": e, "title": f"Capítulo {e}", "container_extension": "mp4", "season": s,
                             "info": {"movie_image": f"{BASE}/img/series_bd_{i}.png", "plot": "La investigación avanza mientras surgen nuevas sospechas en el grupo.", "duration_secs": 2700}}
                            for e in range(1, 9)] for s in range(1, 4)}
            return self.send(200, json.dumps({"seasons": [], "info": {"name": SERIES[i], "plot": f"{SERIES[i]}: una serie sobre lealtades, secretos y segundas oportunidades.",
                                                                      "cast": "Reparto coral", "genre": SERIES_CATS[i % len(SERIES_CATS)], "releaseDate": f"{2018 + i % 7}-03-01",
                                                                      "rating": "8.1", "cover": f"{BASE}/img/series_{i}.png", "backdrop_path": [f"{BASE}/img/series_bd_{i}.png"]},
                                              "episodes": eps}))
        if a in ("get_short_epg", "get_simple_data_table"):
            i = int(q.get("stream_id", "1000")) - 1000
            rows = epg_for(i, -72 if a == "get_simple_data_table" else 0, 30 if a == "get_short_epg" else 0)
            now = time.time()
            return self.send(200, json.dumps({"epg_listings": [{"title": b64(t), "description": b64("Programa emitido. Disponible desde el principio."),
                                                                 "start_timestamp": str(s), "stop_timestamp": str(e), "has_archive": 1 if e < now else 0} for s, e, t in rows]}))
        return self.send(200, "[]")

def ensure_sample():
    """Descarga ~60 s de Big Buck Bunny (CC-BY, Blender Foundation) en MPEG-TS para las pruebas de vídeo."""
    if os.path.exists(SAMPLE):
        return
    import urllib.request
    url = "https://test-streams.mux.dev/x36xhzz/url_6/url_{}/193039199_mp4_h264_aac_hq_7.ts"
    with open(SAMPLE, "wb") as out:
        for i in range(846, 852):
            out.write(urllib.request.urlopen(url.format(i), timeout=30).read())

if __name__ == "__main__":
    ensure_sample()
    make_images()
    print("mock xtream ready on :8099", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8099), H).serve_forever()
