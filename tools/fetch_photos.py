"""Busca fotos con licencia Creative Commons para la presentacion (Commons y Openverse)."""
import json, os, subprocess, urllib.parse, urllib.request

UA = "PasajeYa-presentacion/1.0 (proyecto universitario UCSM; contacto via GitHub KahoriDiaz)"
OUT = "out/fotos"
os.makedirs(OUT, exist_ok=True)
meta = []

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=40) as r:
        return r.read()

def save(img_url, name, info):
    try:
        data = get(img_url)
        path = f"{OUT}/{name}"
        open(path, "wb").write(data)
        subprocess.run(["convert", path, "-resize", "1600x1600>", "-quality", "82", path], check=False)
        info["file"] = name
        meta.append(info)
        print("ok", name, info.get("title", "")[:70])
    except Exception as e:
        print("fallo", name, e)

commons = ["combi Arequipa", "Arequipa transporte publico", "Arequipa bus urbano", "Arequipa calle trafico",
           "Lima combi pasajeros", "paradero Lima gente", "transporte publico Peru"]
for qi, q in enumerate(commons):
    api = ("https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6"
           f"&gsrlimit=8&gsrsearch={urllib.parse.quote(q)}&prop=imageinfo&iiprop=url|extmetadata|size&iiurlwidth=1600")
    try:
        pages = json.loads(get(api)).get("query", {}).get("pages", {})
    except Exception as e:
        print("busqueda fallo", q, e); continue
    for n, p in enumerate(pages.values()):
        ii = (p.get("imageinfo") or [{}])[0]
        url = ii.get("thumburl") or ii.get("url")
        if not url or not url.lower().split("?")[0].endswith((".jpg", ".jpeg", ".png")):
            continue
        em = ii.get("extmetadata", {})
        save(url, f"c{qi}_{n}.jpg", {
            "fuente": "Wikimedia Commons", "consulta": q, "title": p.get("title"),
            "autor": (em.get("Artist", {}).get("value") or "")[:200],
            "licencia": em.get("LicenseShortName", {}).get("value"),
            "pagina": ii.get("descriptionurl")})

openverse = ["people waiting bus stop", "crowded bus passengers", "woman smiling smartphone",
             "student smiling phone", "latina woman smiling phone", "bus passenger smartphone smiling",
             "young people smiling phone street"]
for qi, q in enumerate(openverse):
    api = f"https://api.openverse.org/v1/images/?q={urllib.parse.quote(q)}&page_size=8&license=by,by-sa,cc0,pdm&mature=false"
    try:
        res = json.loads(get(api)).get("results", [])
    except Exception as e:
        print("busqueda fallo", q, e); continue
    for n, r in enumerate(res):
        save(r["url"], f"o{qi}_{n}.jpg", {
            "fuente": r.get("source"), "consulta": q, "title": r.get("title"),
            "autor": r.get("creator"), "licencia": f'{r.get("license")} {r.get("license_version") or ""}',
            "pagina": r.get("foreign_landing_url")})

json.dump(meta, open(f"{OUT}/fotos.json", "w"), ensure_ascii=False, indent=1)
print(len(meta), "fotos")
