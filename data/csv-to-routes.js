// Uso (desde la raiz del proyecto):
//   node data/csv-to-routes.js data/rutas.csv                    (tarifa plana por empresa)
//   node data/csv-to-routes.js data/rutas.csv data/zonas.csv     (con tarifario por zonas)
// Convierte las hojas en data/routes.json y valida los datos.
const fs = require('fs');
const path = require('path');

const csvPath = process.argv[2];
const zonesPath = process.argv[3];
if (!csvPath) { console.error('Falta el CSV de paraderos. Ej: node data/csv-to-routes.js data/rutas.csv [data/zonas.csv]'); process.exit(1); }
const faresPath = path.join(__dirname, 'official-fares.json');
const outPath = path.join(__dirname, 'routes.json');

function readCsv(file) {
  const raw = fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '');
  const lines = raw.split(/\r?\n/).filter(l => l.trim() !== '');
  if (lines.length < 2) { console.error(`El CSV ${file} no tiene filas de datos.`); process.exit(1); }
  const delim = (lines[0].match(/;/g) || []).length > (lines[0].match(/,/g) || []).length ? ';' : ',';
  const split = line => {
    const out = []; let cur = ''; let q = false;
    for (const ch of line) {
      if (ch === '"') q = !q;
      else if (ch === delim && !q) { out.push(cur.trim()); cur = ''; }
      else cur += ch;
    }
    out.push(cur.trim());
    return out;
  };
  const header = split(lines[0]).map(h => h.toLowerCase());
  return { header, rows: lines.slice(1).map(split) };
}
const num = s => parseFloat(String(s).replace(',', '.'));
const errors = [], warnings = [];
// Reglas del proyecto: el universitario es siempre el 50 % del pasaje (Ley 26271), redondeado a 5 centimos.
// No hay tope fijo de S/ 1,30: el pasaje depende de la distancia y el combustible, pero parte del pasaje vigente.
const uniFare = fare => Math.round(fare * 0.5 / 5) * 5;
const ZONE_MAX_MULTIPLIER = 3;      // una zona no puede costar mas de 3 veces el pasaje vigente de la empresa (aviso)
const ABSOLUTE_MAX_CENTS = 500;     // techo duro: nada por encima de S/ 5,00 (evita errores como S/ 15)

const fares = JSON.parse(fs.readFileSync(faresPath, 'utf8'));
const ids = new Set(fares.concessionaires.map(c => c.id));

// --- zonas (opcional)
const zonesByRoute = new Map();
if (zonesPath) {
  const z = readCsv(zonesPath);
  for (const n of ['ruta_id', 'zona_id', 'zona_nombre', 'orden_zona', 'tarifa_centimos'])
    if (!z.header.includes(n)) { console.error('Falta la columna en zonas: ' + n); process.exit(1); }
  const c = n => z.header.indexOf(n);
  z.rows.forEach((f, i) => {
    const row = i + 2, rid = f[c('ruta_id')];
    const fare = parseInt(f[c('tarifa_centimos')], 10);
    const uniRaw = z.header.includes('tarifa_universitaria_centimos') ? f[c('tarifa_universitaria_centimos')] : '';
    if (!rid) return errors.push(`Zonas fila ${row}: falta ruta_id`);
    if (!Number.isInteger(fare) || fare <= 0) return errors.push(`Zonas fila ${row}: tarifa_centimos debe ser un entero positivo (ej. 130 para S/ 1,30)`);
    if (fare > ABSOLUTE_MAX_CENTS) errors.push(`Zonas fila ${row}: tarifa ${fare} centimos supera el techo de ${ABSOLUTE_MAX_CENTS} (S/ ${(ABSOLUTE_MAX_CENTS / 100).toFixed(2)}). Revisa si pusiste soles en vez de centimos.`);
    const rank = parseInt(f[c('orden_zona')], 10);
    const uni = uniFare(fare); // siempre 50 %; la columna del CSV ya no manda
    if (uniRaw !== '' && uniRaw !== undefined && parseInt(uniRaw, 10) !== uni)
      warnings.push(`Zonas fila ${row}: tarifa_universitaria_centimos (${uniRaw}) se ignora; el universitario del proyecto es el 50 % = ${uni}.`);
    if (!zonesByRoute.has(rid)) zonesByRoute.set(rid, []);
    zonesByRoute.get(rid).push({ id: f[c('zona_id')], name: f[c('zona_nombre')], rank, fareCents: fare, universityFareCents: uni });
  });
}

// --- paraderos
const s = readCsv(csvPath);
const need = ['ruta_id', 'ruta_nombre', 'concesionaria_id', 'orden', 'paradero', 'lat', 'lng'];
for (const n of need) if (!s.header.includes(n)) { console.error('Falta la columna: ' + n); process.exit(1); }
const col = n => s.header.indexOf(n);
const hasZoneCol = s.header.includes('zona_id');
if (hasZoneCol && !zonesPath) warnings.push('El CSV trae zona_id pero no se pasó zonas.csv: se ignoran las zonas y se usa tarifa plana (modelo por distancia).');
const routes = new Map();

s.rows.forEach((f, i) => {
  const row = i + 2;
  const id = f[col('ruta_id')], conc = f[col('concesionaria_id')];
  const lat = num(f[col('lat')]), lng = num(f[col('lng')]), orden = parseInt(f[col('orden')], 10);
  const zona = hasZoneCol ? f[col('zona_id')] : '';
  if (!id) return errors.push(`Fila ${row}: falta ruta_id`);
  if (!ids.has(conc)) errors.push(`Fila ${row}: concesionaria_id "${conc}" no existe en official-fares.json`);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return errors.push(`Fila ${row}: lat/lng no son numeros`);
  if (!Number.isInteger(orden)) errors.push(`Fila ${row}: orden debe ser un numero entero`);
  if (lat < -16.8 || lat > -16.0 || lng < -71.9 || lng > -71.2)
    warnings.push(`Fila ${row}: (${lat}, ${lng}) parece estar fuera de Arequipa. Revisa si invertiste lat y lng.`);
  if (!routes.has(id)) routes.set(id, { id, name: f[col('ruta_nombre')], active: true, concessionaireId: conc, stops: [] });
  const r = routes.get(id);
  if (r.concessionaireId !== conc) errors.push(`Ruta ${id}: tiene dos concesionarias distintas`);
  r.stops.push({ order: orden, name: f[col('paradero')], lat, lng, zoneId: zona || null });
});

for (const r of routes.values()) {
  r.stops.sort((a, b) => a.order - b.order);
  if (r.stops.length < 2) errors.push(`Ruta ${r.id}: necesita al menos 2 paraderos`);
  const seen = new Set();
  r.stops.forEach(st => { if (seen.has(st.order)) errors.push(`Ruta ${r.id}: el orden ${st.order} esta repetido`); seen.add(st.order); });

  const zones = zonesByRoute.get(r.id);
  if (zones) {
    zones.sort((a, b) => a.rank - b.rank);
    const ref = fares.concessionaires.find(c => c.id === r.concessionaireId);
    if (ref) zones.forEach(z => { if (z.fareCents > ref.officialFareCents * ZONE_MAX_MULTIPLIER) warnings.push(`Ruta ${r.id}: la zona "${z.name}" (${z.fareCents}) supera ${ZONE_MAX_MULTIPLIER}x el pasaje vigente de ${ref.name} (${ref.officialFareCents}). Confirma con la foto del tarifario.`); });
    const zid = new Set(zones.map(z => z.id));
    if (zid.size !== zones.length) errors.push(`Ruta ${r.id}: hay zonas con el mismo zona_id`);
    zones.forEach((z, k) => { if (k > 0 && z.fareCents < zones[k - 1].fareCents) warnings.push(`Ruta ${r.id}: la zona "${z.name}" cuesta menos que la anterior; revisa orden_zona.`); });
    let lastRank = -1;
    r.stops.forEach(st => {
      if (!st.zoneId) return errors.push(`Ruta ${r.id}, paradero "${st.name}": falta zona_id (la ruta tiene tarifario por zonas)`);
      const z = zones.find(x => x.id === st.zoneId);
      if (!z) return errors.push(`Ruta ${r.id}, paradero "${st.name}": zona_id "${st.zoneId}" no existe en zonas.csv`);
      const rk = zones.indexOf(z);
      if (rk < lastRank) warnings.push(`Ruta ${r.id}, paradero "${st.name}": su zona vuelve a una zona anterior. Revisa el orden de los paraderos.`);
      lastRank = Math.max(lastRank, rk);
    });
    r.fareZones = zones.map(({ id, name, fareCents, universityFareCents }) => ({ id, name, fareCents, universityFareCents }));
    r.fareSource = 'tarifario_en_unidad';
  }
  r.stops = r.stops.map(({ name, lat, lng, zoneId }) => (r.fareZones && zoneId ? { name, lat, lng, zoneId } : { name, lat, lng }));
}
for (const rid of zonesByRoute.keys()) if (!routes.has(rid)) errors.push(`zonas.csv menciona la ruta "${rid}" que no esta en rutas.csv`);

warnings.forEach(w => console.warn('AVISO  ' + w));
if (errors.length) { errors.forEach(e => console.error('ERROR  ' + e)); console.error('\nNo se genero routes.json. Corrige los errores y repite.'); process.exit(1); }
fs.writeFileSync(outPath, JSON.stringify([...routes.values()], null, 2) + '\n', 'utf8');
console.log(`OK: ${routes.size} ruta(s) escritas en data/routes.json`);
for (const r of routes.values()) console.log(` - ${r.id}: ${r.stops.length} paraderos, ${r.fareZones ? r.fareZones.length + ' zonas' : 'tarifa plana por empresa'} (${r.concessionaireId})`);
