#!/usr/bin/env bash
set -u
UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
mkdir -p out/fuel5
U="https://www.osinergmin.gob.pe/seccion/centro_documental/hidrocarburos/SCOP/SCOP-DOCS/2026/Registro-precios/Ultimos-Precios-Registrados-EVPC.xlsx"
curl -sL -m 120 -A "$UA" -D out/fuel5/headers.txt -o /tmp/evpc.xlsx -w "HTTP %{http_code} %{size_download}B\n" "$U"
grep -iE "last-modified|content-length|date:" out/fuel5/headers.txt
pip install -q openpyxl
python3 - <<'PY' | tee out/fuel5/resumen.txt
import openpyxl, statistics, collections
wb = openpyxl.load_workbook('/tmp/evpc.xlsx', read_only=True, data_only=True)
for ws in wb.worksheets:
    print("HOJA", ws.title, ws.max_row, ws.max_column)
    rows = ws.iter_rows(values_only=True)
    head = None
    sample = []
    for i, r in enumerate(rows):
        if i < 12: print(i, r)
        if head is None and r and any(isinstance(c, str) and "DEPART" in c.upper() for c in r if c):
            head = [str(c).strip().upper() if c else "" for c in r]; print("CABECERA", head); continue
        if head:
            sample.append(r)
    if not head: continue
    def col(name):
        for j, h in enumerate(head):
            if name in h: return j
    jd, jp, jprod, jprice = col("DEPART"), col("PROVINC"), col("PRODUCTO"), col("PRECIO")
    print("columnas", jd, jp, jprod, jprice)
    prods = collections.Counter(r[jprod] for r in sample if jprod is not None and r[jprod])
    print("productos", prods.most_common(25))
    aqp = [r for r in sample if r[jd] and "AREQUIPA" in str(r[jd]).upper() and (jp is None or "AREQUIPA" in str(r[jp]).upper())]
    print("filas Arequipa/Arequipa", len(aqp))
    for p in sorted(set(r[jprod] for r in aqp if r[jprod])):
        vals = [float(r[jprice]) for r in aqp if r[jprod] == p and r[jprice] not in (None, "")]
        if vals: print(f"{p}: n={len(vals)} mediana={statistics.median(vals):.2f} prom={statistics.mean(vals):.2f} min={min(vals):.2f} max={max(vals):.2f}")
    for r in aqp[:5]: print("ejemplo", r)
PY
