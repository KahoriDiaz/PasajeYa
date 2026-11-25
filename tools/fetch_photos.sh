#!/usr/bin/env bash
# Descarga fotos de licencia libre de Unsplash (no Unsplash+) para la presentacion.
set -u
UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
mkdir -p out/fotos
: > out/fotos/fuentes.txt
for q in "people-waiting-bus" "bus-stop-crowd" "crowded-bus" "commuters" "peru-bus" "lima-traffic" "arequipa" "woman-smiling-phone" "student-phone-smile" "happy-commuter" "latina-woman-smiling" "team-students-laptop"; do
  html=$(curl -sL -A "$UA" "https://unsplash.com/s/photos/$q?license=free" || true)
  echo "$q: $(echo "$html" | wc -c) bytes"
  ids=$(echo "$html" | grep -oE 'https://images\.unsplash\.com/photo-[A-Za-z0-9_-]+' | sort -u | head -8)
  n=0
  for u in $ids; do
    n=$((n+1))
    curl -sL -A "$UA" "$u?w=1600&q=80&fm=jpg" -o "out/fotos/${q}_${n}.jpg" || true
    echo "${q}_${n}.jpg $u" >> out/fotos/fuentes.txt
  done
done
ls -la out/fotos | head -120
