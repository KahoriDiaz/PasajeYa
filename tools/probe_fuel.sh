#!/usr/bin/env bash
# Explora de donde sacar el precio del diesel en Arequipa.
set -u
UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
mkdir -p out/fuel
probe() { echo "=== $1"; curl -sL -m 30 -A "$UA" -o "out/fuel/$2" -w "HTTP %{http_code} %{size_download}B %{content_type}\n" "$1" || echo "fallo"; head -c 600 "out/fuel/$2" | tr '\n' ' '; echo; }
probe "https://api.osinergmin.gob.pe/hidrocarburos/precios/arequipa/diesel" api.txt
probe "https://www.facilito.gob.pe/facilito/pages/facilito/buscadorEESS.jsp" buscador.html
probe "https://www.facilito.gob.pe/facilito/pages/facilito/menuPrecios.jsp" menu.html
probe "https://www.facilito.gob.pe/" home.html
probe "https://www.datosabiertos.gob.pe/api/3/action/package_search?q=precios%20combustibles%20osinergmin" datos.json
probe "https://www.infobae.com/peru/" infobae.html
for f in out/fuel/*.html; do echo "--- enlaces en $f"; grep -oE '(src|href|action|url)\s*[:=]\s*["'"'"'][^"'"'"']+' "$f" | sort -u | head -60; done
echo "--- datos abiertos"; python3 -c "
import json;d=json.load(open('out/fuel/datos.json'))
for r in d['result']['results'][:10]:
  print(r['title']); [print('   ',x.get('format'),x.get('url')) for x in r.get('resources',[])[:6]]" 2>&1 | head -80
