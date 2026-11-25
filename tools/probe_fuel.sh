#!/usr/bin/env bash
set -u
UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
mkdir -p out/fuel
B="https://www.facilito.gob.pe"
J=out/fuel/cookies.txt
curl -sL -m 40 -A "$UA" -c $J -b $J -o out/fuel/buscador.html "$B/facilito/pages/facilito/buscadorEESS.jsp"
curl -sL -m 40 -A "$UA" -c $J -b $J -o out/fuel/inicio.html "$B/facilito/actions/PreciosCombustibleAutomotorAction.do?method=inicio"
for js in $(grep -oE 'src="[^"]+\.js[^"]*"' out/fuel/buscador.html out/fuel/inicio.html | sed 's/.*src="//; s/"$//' | sort -u | grep -v googletag); do
  case "$js" in http*) u="$js";; /*) u="$B$js";; *) u="$B/facilito/pages/facilito/$js";; esac
  f="out/fuel/js_$(basename "${js%%\?*}")"
  curl -sL -m 30 -A "$UA" -o "$f" "$u"; echo "js $u -> $(wc -c < "$f")"
done
ls -la out/fuel
