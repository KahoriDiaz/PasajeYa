#!/usr/bin/env bash
set -u
UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
mkdir -p out/fuel4
get() { curl -sL -m 60 -A "$UA" -o "out/fuel4/$2" -w "$2 HTTP %{http_code} %{size_download}B %{url_effective}\n" "$1"; }
get "https://www.osinergmin.gob.pe/empresas/hidrocarburos/scop/documentos-scop" scop.html
grep -oiE 'href="[^"]+"' out/fuel4/scop.html | sed 's/href="//I; s/"$//' | sort -u > out/fuel4/enlaces.txt
wc -l < out/fuel4/enlaces.txt
grep -iE "precio|price|xls|csv|zip|pdf|scop|txt" out/fuel4/enlaces.txt | head -60
