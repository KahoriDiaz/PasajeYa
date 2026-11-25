#!/usr/bin/env bash
set -u
UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
mkdir -p out/fuel3
P="https://www.osinergmin.gob.pe/empresas/hidrocarburos/Paginas/SCOP-DOCS/scop_docs.htm"
curl -sL -m 40 -A "$UA" -o out/fuel3/scop.htm -w "scop HTTP %{http_code} %{size_download}B\n" "$P"
grep -oiE 'href="[^"]+"' out/fuel3/scop.htm | sed 's/href="//I; s/"$//' | sort -u > out/fuel3/enlaces.txt
wc -l out/fuel3/enlaces.txt; grep -iE "xls|csv|zip|precio|price|txt|json" out/fuel3/enlaces.txt | head -40
# baja los dos primeros archivos de datos que encuentre
n=0
for u in $(grep -iE "\.(xlsx?|csv|zip|txt)$" out/fuel3/enlaces.txt | head -3); do
  case "$u" in http*) url="$u";; /*) url="https://www.osinergmin.gob.pe$u";; *) url="https://www.osinergmin.gob.pe/empresas/hidrocarburos/Paginas/SCOP-DOCS/$u";; esac
  n=$((n+1)); curl -sL -m 120 -A "$UA" -o "out/fuel3/dato_$n.$(echo ${u##*.} | tr A-Z a-z)" -w "dato $n $url HTTP %{http_code} %{size_download}B\n" "$url"
done
ls -la out/fuel3
