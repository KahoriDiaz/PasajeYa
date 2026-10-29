# PasajeYa! — Especificaciones completas F2 a F8 y cierre

Este archivo reúne, en orden, las especificaciones de las funcionalidades 2 a 8 y el cierre. Cada bloque contiene: qué debe hacer, el prompt con sus pasos y las pruebas manuales.

Notas de orden: (1) la F3 usa el modelo por distancia y combustible (referencia S/ 1,30, sin zonas, techo de seguridad configurable, universitario al 50 %) y REEMPLAZA cualquier cálculo anterior; (2) la F4 verifica con OCR del carné más código al correo institucional; (3) la F8 (saldo de demostración y NFC) modifica tapIn, tapOut y reclamos: al implementarla se deben actualizar las pruebas de F2, F3, F4 y F7.

---

## 7. Funcionalidad 2 — Tap In / Tap Out con QR y GPS

### 7.1 Qué debe hacer (detalle completo)

**Idea central:** el pasajero muestra un QR que cambia cada 45 segundos. El operador lo escanea al subir y al bajar. El teléfono del operador graba la ruta del vehículo durante todo el turno. Al hacer Tap Out, el servidor mide la distancia recorrida entre ambos escaneos.

**Lado pasajero**

- Pestaña "Mi QR": muestra un QR grande con cuenta regresiva. Cada 45 s la app pide un token nuevo (`issueQrToken`) y regenera el QR. Si no hay internet, muestra "Sin conexión: no se puede generar el QR" (no existe QR sin conexión, y se documenta como limitación).
- El QR contiene solo un token firmado. **Nunca** contiene DNI, nombre ni correo.
- Tarjeta "Viaje en curso" cuando hay un viaje abierto: ruta, hora de inicio y estado. Se alimenta en tiempo real del documento del viaje.

**Lado operador**

- **Turno:** pantalla "Iniciar turno" donde elige su vehículo y su ruta (de las que registró). Al iniciar, arranca un **servicio en primer plano** con notificación permanente "PasajeYa! está registrando tu ruta" que envía puntos GPS. Se pide permiso de ubicación precisa (primero "mientras se usa", luego "siempre" si hace falta), permiso de notificaciones (Android 13+) y se guía al usuario a desactivar la optimización de batería.
- **Escáner:** CameraX + ML Kit para leer el QR. Al leer un QR válido: vibración corta, tarjeta verde "Tap In registrado", y el aviso de voz (funcionalidad 5, aquí solo se deja el gancho).
- **Pasajeros a bordo:** lista de viajes abiertos del turno con botón "Tap Out" por pasajero (además de poder escanear otra vez). El Tap Out por lista existe porque el pasajero puede no tener batería o internet al bajar.
- **Fin de turno:** botón "Terminar turno". Si quedan pasajeros a bordo, advierte que esos viajes pasarán a revisión.

**Registro de la ruta (GPS)**

- Se toma un punto cada 5 s cuando el vehículo se mueve y cada 15 s cuando está detenido.
- Los puntos se agrupan en lotes de hasta 30 y se guardan como documentos en `shifts/{shiftId}/chunks`. Con la persistencia sin conexión de Firestore, si se cae el internet los lotes se suben al volver.
- Cada punto: `lat`, `lng`, `acc` (precisión en metros), `t` (marca de tiempo).

**Servidor**

- `issueQrToken`: genera `{uid, nonce, iat, exp = iat + 60 s}`, lo firma con HMAC-SHA256 (`QR_SECRET`, secreto) y lo devuelve como `payload.firma` en base64url.
- `startShift({vehiclePlate, routeId, sessionId})`: el operador debe tener perfil completo, correo verificado, el vehículo debe ser suyo y la ruta debe estar entre las suyas. No puede haber otro turno activo del mismo operador ni del mismo vehículo.
- `endShift({shiftId})`: cierra el turno y pasa a revisión los viajes abiertos con motivo `shift_ended`.
- `tapIn({qrToken, shiftId, lat, lng, accuracy, sessionId})`:
  1. Verifica la firma y que no haya expirado.
  2. Marca el `nonce` como usado (crea `qrNonces/{nonce}`; si ya existe, rechaza: "QR ya utilizado").
  3. Verifica que el turno es del operador que llama y está activo.
  4. En **transacción** verifica que el pasajero no tenga otro viaje abierto y crea el viaje con `status: "open"`, `startedAt` (hora del servidor), ubicación, `fareVersionId` vigente, `passengerFirstName` y `passengerAge` (calculada en el servidor).
  5. Responde solo `{tripId, firstName, age}`. Nada más del pasajero.
- `tapOut({tripId, lat, lng, accuracy, sessionId})`: ver "Cálculo de distancia" abajo y funcionalidad 3 para la tarifa. Acepta el `tripId` (desde la lista) o un `qrToken` (desde el escaneo; el servidor busca el viaje abierto de ese pasajero en ese turno).
- `closeStaleTrips` (programada cada 15 minutos): todo viaje abierto por más de 3 horas pasa a `requires_review` con motivo `no_tap_out`.

**Cálculo de distancia (servidor)**

1. Toma los puntos del turno entre `startedAt` y `endedAt` del viaje.
2. Descarta puntos con precisión peor que 30 m, con velocidad implícita mayor a 33 m/s (120 km/h) o con saltos de más de 500 m entre puntos consecutivos.
3. Ignora movimientos menores de 5 m (ruido con el vehículo detenido).
4. Suma distancias de Haversine entre puntos válidos consecutivos.
5. Si hay menos de 3 puntos válidos o cubren menos del 60 % de la duración del viaje, no inventa un valor: el viaje pasa a `requires_review` con motivo `insufficient_gps`.
6. Guarda `distanceMeters`, cantidad de puntos usados y porcentaje de cobertura.

Todos los umbrales viven en `functions/src/config/gps.ts`.

**Reglas de Firestore de esta funcionalidad**

- `shifts`: lectura solo del operador dueño. Escritura prohibida (solo funciones).
- `shifts/{id}/chunks`: el operador dueño de un turno **activo** puede crear (no actualizar ni borrar) lotes con máximo 30 puntos, latitud entre -90 y 90, longitud entre -180 y 180, precisión mayor que 0 y marca de tiempo cercana a la hora del servidor.
- `trips`: lectura por `passengerUid` u `operatorUid`; escritura prohibida.
- `qrNonces`: sin acceso de clientes. Configura una política TTL sobre `expiresAt` en la consola de Firestore.

### 7.2 Lo que haces tú

1. Guarda el secreto del QR: `firebase functions:secrets:set QR_SECRET` (cadena aleatoria larga).
2. En la consola de Firestore → TTL, crea una política para la colección `qrNonces` sobre el campo `expiresAt`.
3. Para probar GPS y cámara necesitas **dos celulares físicos** (uno pasajero, uno operador) o uno físico y un emulador.

### 7.3 Prompt para OpenCode

```
Implementa la FUNCIONALIDAD 2 (Tap In / Tap Out con QR y GPS) siguiendo AGENTS.md y lo ya
construido en la funcionalidad 1. Un commit por paso.

PASO 1 - Lógica pura del servidor (TypeScript) con pruebas
- functions/src/config/gps.ts con los umbrales: precisión máxima 30 m, velocidad máxima 33 m/s,
  salto máximo 500 m, movimiento mínimo 5 m, cobertura mínima 60 %, mínimo 3 puntos válidos.
- functions/src/geo/distance.ts: haversine, filtrado de puntos y cálculo de distancia recorrida
  entre dos instantes, devolviendo {distanceMeters, usedPoints, coverage, reviewReason?}.
- functions/src/qr/token.ts: crear y verificar tokens payload.firma con HMAC-SHA256, comparación en
  tiempo constante, expiración de 60 s y rechazo de manipulación.
- Pruebas con trayectos sintéticos de TEST (solo en archivos de prueba): línea recta de longitud
  conocida (error menor al 1 %), trayecto con puntos imprecisos, con saltos imposibles, con huecos
  de GPS, con vehículo detenido y con menos de 3 puntos. Token válido, expirado, manipulado y
  reutilizado.

PASO 2 - Cloud Functions
- issueQrToken, startShift, endShift, tapIn, tapOut (la tarifa se deja como llamada a una función
  computeFare que se implementará en la funcionalidad 3: por ahora tapOut guarda distanceMeters y
  deja el viaje en "closed" sin fareBreakdown, con un TODO claro en el código y en PROGRESO.md),
  closeStaleTrips programada cada 15 minutos.
- Todas verifican autenticación, correo verificado, rol y assertActiveSession.
- tapIn usa transacción para impedir dos viajes abiertos del mismo pasajero. Dos tapIn simultáneos
  con QR distintos del mismo pasajero producen un solo viaje abierto (prueba de concurrencia).
- Responde a tapIn SOLO {tripId, firstName, age}.

PASO 3 - Reglas de Firestore
- shifts, chunks, trips y qrNonces como se describe. Pruebas de reglas: operador crea chunk en su
  turno activo; NO en turno ajeno, NO en turno cerrado, NO con 31 puntos, NO con coordenadas
  fuera de rango; nadie actualiza ni borra chunks; pasajero lee su viaje y no el de otro;
  operador ajeno no lee el viaje.

PASO 4 - Pruebas de Functions en emulador
- Flujo feliz: startShift -> issueQrToken -> tapIn -> chunks -> tapOut.
- Rechazos: QR expirado, QR reutilizado, QR manipulado, turno inexistente, turno de otro operador,
  pasajero con viaje abierto, operador sin turno activo, vehículo ajeno, dos turnos activos del
  mismo operador, tapOut por un operador que no es el del viaje, sessionId viejo.
- endShift pasa los viajes abiertos a requires_review/shift_ended. closeStaleTrips pasa a
  requires_review/no_tap_out los viajes de más de 3 horas (usa un reloj inyectable en la prueba).

PASO 5 - App (pasajero)
- Pestaña "Mi QR": generación con ZXing, renovación cada 45 s, cuenta regresiva, manejo de falta de
  internet y de errores. Tarjeta "Viaje en curso" con listener del viaje abierto.

PASO 6 - App (operador)
- Pantalla de turno (elegir vehículo y ruta reales de Firestore) y servicio en primer plano de
  ubicación (tipo location) que graba puntos y sube lotes de hasta 30. Permisos: ubicación precisa,
  notificaciones, y guía para excluir la app de la optimización de batería.
- Escáner con CameraX + ML Kit: solo acepta códigos QR; antirrebote (no procesar el mismo código
  dos veces en 3 s); feedback visual y vibración; manejo de permiso de cámara denegado.
- Lista de pasajeros a bordo con botón Tap Out y confirmación. Botón "Terminar turno" con aviso si
  hay pasajeros a bordo.
- Deja el gancho onTapInSuccess(firstName, age) para la funcionalidad 5 (voz).

PASO 7 - Pruebas de la app
- Unitarias: ViewModels del QR (renovación, sin conexión), del turno y del escáner (antirrebote);
  agrupador de lotes GPS (tamaño máximo 30, orden por tiempo); reintentos.
- Compose UI: la pantalla del QR muestra la cuenta regresiva; el escáner muestra el estado de
  permiso denegado.

PASO 8 - Documentación
- docs/pruebas-manuales-F2.md con los casos que te doy abajo.
- LIMITACIONES.md: no hay QR sin conexión; la distancia depende del GPS del teléfono del operador.

Al final ejecuta todas las pruebas y muéstrame los resultados. No avances a la funcionalidad 3.
```

### 7.4 Pruebas manuales (dos celulares reales)

| ID | Pasos | Resultado esperado |
|---|---|---|
| F2-M01 | Pasajero abre "Mi QR" | Se ve un QR con cuenta regresiva que se renueva |
| F2-M02 | Operador inicia turno con su placa y ruta | Aparece la notificación permanente y se graban puntos |
| F2-M03 | Operador escanea el QR del pasajero | Tarjeta verde "Tap In registrado"; el pasajero ve "Viaje en curso" |
| F2-M04 | Escanear el mismo QR otra vez | Rechazado: "QR ya utilizado" |
| F2-M05 | Escanear una captura de pantalla del QR pasados 60 s | Rechazado por expirado |
| F2-M06 | Intentar subir otro Tap In del mismo pasajero con viaje abierto | Rechazado: ya tiene un viaje abierto |
| F2-M07 | Recorrer unos 2 km en vehículo con el turno activo y hacer Tap Out | Viaje cerrado con distancia cercana a la real (compáralo con Google Maps) |
| F2-M08 | Tap Out desde la lista, sin QR | Funciona |
| F2-M09 | Cortar internet 2 minutos durante el viaje y reconectar | Los puntos se suben al volver y el viaje se cierra bien |
| F2-M10 | Terminar el turno con un pasajero a bordo | Advertencia; el viaje queda "requiere revisión" |
| F2-M11 | Dejar un viaje abierto 3 horas (o cambiar el umbral para probar) | Pasa a "requiere revisión" por falta de Tap Out |
| F2-M12 | Denegar el permiso de cámara | Mensaje que explica cómo activarlo |
| F2-M13 | Un operador intenta cerrar el viaje de otro | Rechazado |

### 7.5 Criterio de terminado

- Pruebas automáticas en verde (incluida la de concurrencia y la de error de distancia menor al 1 % en trayecto sintético).
- Al menos una prueba real en vehículo con error de distancia documentado.
- `PROGRESO.md` indica que el cálculo de tarifa queda pendiente para la funcionalidad 3.

---

## Funcionalidad 3 — Cálculo de tarifa con desglose (modelo: precio por distancia y combustible)

### 1. Idea del modelo

- Es un proyecto académico. Toma como **referencia lo que cobran hoy las concesionarias**: S/ 1,30 en todas las combis (en la práctica). Los S/ 1,00 que la Municipalidad Provincial de Arequipa (MPA) quiso imponer a seis empresas en agosto de 2026 quedan solo como dato informativo en `data/official-fares.json`; no entran al cálculo.
- **La propuesta:** el precio depende de la **distancia recorrida** y del **precio del combustible**. Si el combustible sube, el precio sube; si baja, baja. Un viaje típico cuesta cerca de S/ 1,30 (así se calibra); uno corto cuesta menos y uno largo, más. No hay tope en S/ 1,30.
- **Techo de seguridad:** solo para evitar errores de datos (por ejemplo, S/ 15 por una distancia mal medida). Vale S/ 3,30 por defecto (330 céntimos) y es configurable en la versión de tarifa (`maxFareCents`). Se toma del tarifario por zonas que el equipo fotografió dentro de una combi de ruta larga, cuyo pasaje máximo es S/ 3,30 (zona Rosario); la foto queda en `data/samples/tarifarios/` (fuera de git) como evidencia, con la fecha y la empresa o ruta. No es una norma legal.
- **Universitario:** siempre el **50 %** del precio regular (Ley N.º 26271 fija el máximo en 50 %). No existe un pasaje universitario fijo.
- **Uso del tarifario observado:** el tarifario por zonas de esa combi (Cercado S/ 1,30; Cural S/ 1,80; Uchumayo S/ 2,30; Rosario S/ 3,30) solo se usa para fijar el techo de S/ 3,30 y se documenta en `docs/TARIFA.md`. **Las zonas no entran al cálculo**: las rutas no usan zonas y, si el CSV trae `zona_id`, se ignora.
- **Aviso sobre el techo:** con un techo de S/ 3,30 ya no detecta por sí solo una distancia mal medida de unos 15 a 25 km. La protección real contra eso son los filtros de GPS de la F2 (puntos imprecisos, saltos, cobertura mínima) y los viajes que pasan a revisión; documéntalo en `docs/TARIFA.md`.
- Combustible: Osinergmin publica precios por grifo en Facilito (facilito.gob.pe). No se asume descarga automática; el equipo ingresa el precio promedio de Arequipa con fecha y fuente.

### 2. Qué debe hacer (detalle completo)

**Datos de referencia (`data/official-fares.json`)**

- Esquema: `{ sources: [{name, url}], checkedAt, concessionaires: [{ id, name, officialFareCents, officialUniversityFareCents, officialSchoolFareCents, chargedFareCents, status }] }`.
- `chargedFareCents` (130) es la **referencia del cálculo**. `officialFareCents` es informativo. `officialUniversityFareCents` ya no se usa en el cálculo.
- Cada ruta (`routes/{id}`) agrega `concessionaireId` y, al cargarse, copia `{chargedFareCents, officialFareCents, fareSourceUrl, fareCheckedAt}`. Sin `zoneId` ni `fareZones`.
- `data/routes.json` se genera con `node data/csv-to-routes.js data/rutas.csv` (ya valida ids de concesionaria y coordenadas, y avisa si el CSV trae `zona_id`).
- Rutas cargadas: R-CAY-01 (Transcayma) y R-GRAU-C (Unión Grau, ex Segrampo).

**Fórmula (servidor, céntimos enteros)**

```
raw       = distancia_km × baseFarePerKmCents × fuelIndex
calculada = redondear(raw, paso)
regular   = min( max(calculada, minFareCents), maxFareCents )
universitario:
  conBeneficio = redondear(regular × 50 / 100, paso)
  descuento    = regular − conBeneficio          // 0 si no hay beneficio
total     = regular − descuento
```

- Redondeo al múltiplo más cercano del paso (5 céntimos por defecto), empate hacia arriba.
- Marcas del desglose: `minApplied` (se aplicó la mínima) y `maxApplied` (se aplicó el techo de seguridad).
- El total nunca es negativo ni mayor que `maxFareCents`; con beneficio, el total nunca es mayor que sin beneficio.

**Versiones del cálculo (transparencia)**

- `fareVersions/{id}`, inmutable: `{baseFarePerKmCents, fuelType, fuelPriceCurrentCents, fuelPriceReferenceCents, fuelPriceSourceName, fuelPriceSourceUrl, fuelPriceDate, fuelIndex, minFareCents, maxFareCents, roundingStepCents, calibrationNote, createdAt}`.
- `fuelIndex = fuelPriceCurrentCents / fuelPriceReferenceCents` con tres decimales. Vale 1,000 el día de la calibración (el precio de referencia es el de ese día).
- `fuelType`: "GNV" | "GLP" | "DIESEL" | "GASOLINA_90" | "GASOLINA_95". Obligatorio; queda marcado "CONFIRMAR" hasta saber qué combustible usan realmente las unidades.
- Valores PROVISIONALES hasta calibrar con viajes reales: base 12 céntimos por km, mínima 50, techo 330, paso 5. Márcalos como "provisional" en la configuración, en `LIMITACIONES.md` y en la app.
- `FuelPriceProvider` (interfaz) con `ManualFuelPriceProvider` (el equipo ingresa el precio por `publishFareVersion.ts`). Una fuente automática futura sería otra implementación.
- `functions/scripts/calibrateFare.ts`: lee viajes `closed` reales con distancia, por ruta, calcula el percentil 75 de la distancia y SUGIERE `base = chargedFareCents / distancia_p75_km`. Exige al menos 30 viajes reales por ruta; con menos, imprime "datos insuficientes" y no sugiere nada. Publicar una versión es una acción manual.
- `publishFareVersion.ts` valida: base > 0; mínima > 0; techo ≥ mínima; paso > 0; `fuelType` válido; precio de combustible positivo; fecha del combustible no mayor a 45 días; índice entre 0,5 y 2,0; no se pueden pisar versiones.
- Cada viaje guarda en el Tap In `fareVersionId` y una foto de la referencia de su ruta (`routeFareSnapshot`), para que un cambio a mitad de viaje no lo altere.

**Desglose guardado en el viaje (`fareBreakdown`):** `distanceMeters`, `baseFarePerKmCents`, `fuelType`, `fuelIndex`, `calculatedCents`, `regularCents`, `benefit {kind, percent, discountCents}`, `totalCents`, `minApplied`, `maxApplied`, `referenceFareCents`, `fareVersionId`, `concessionaireName`.

**Pantalla "Resumen del viaje":** estado, distancia, tarifa por km, combustible ("[tipo] S/ X por [unidad] ([fuente], [fecha])"), índice de combustible, **precio según recorrido y combustible**, comparación con la referencia ("En las combis hoy se cobra S/ 1,30"), beneficio universitario (50 %), **importe final** grande y una tarjeta que explica la fórmula en lenguaje sencillo. Si se aplicó el techo de seguridad, se muestra el aviso correspondiente. Aviso fijo: "El cálculo por distancia y combustible es una propuesta del proyecto; la referencia es el pasaje que cobran hoy las concesionarias".

**Estimar mi pasaje:** `estimateFare({routeId, boardingStopIndex, alightingStopIndex})` usa la distancia entre paraderos reales y la misma fórmula. Sin paraderos con coordenadas devuelve failed-precondition y no inventa nada. Aviso de que es una estimación.

### 3. Lo que haces tú

1. Verifica las coordenadas de `data/rutas.csv` (Google Maps o un recorrido real); la distancia depende de ellas.
2. Ingresa el precio de combustible de Arequipa con fecha y fuente (Facilito) al publicar la primera versión.
3. Con al menos 30 viajes reales por ruta, corre `calibrateFare` y decide si publicas una nueva base.

### 4. Prompt para OpenCode

```
Implementa la FUNCIONALIDAD 3 con el MODELO por distancia y combustible, siguiendo AGENTS.md.
Esta versión reemplaza cualquier cálculo anterior (tope en el pasaje oficial, tarifa máxima
global de 500 céntimos, tarifario por zonas, universitario como el menor entre 50 % y un pasaje
fijo). Un commit por paso.

PASO 0 - Datos de referencia
- Usa data/official-fares.json (con chargedFareCents) y data/routes.json (sin zonas). NO inventes
  nombres ni montos.
- Actualiza AGENTS.md (modelo de datos): routes agrega concessionaireId y el resumen de referencia;
  fareVersions agrega fuelType, precios de combustible, minFareCents y maxFareCents; trips agrega
  routeFareSnapshot.
- Extiende seedRoutes.ts: valida que cada ruta tenga concessionaireId existente, copia la
  referencia y la fuente. Migración para rutas ya cargadas.

PASO 1 - computeFare (TypeScript) y vectores
- functions/src/fare/computeFare.ts con la fórmula de la especificación. Entrada: {distanceMeters,
  version, referenceFareCents, hasUniversityBenefit}. Salida con todo el desglose en céntimos
  enteros. Valida entradas y lanza error si no cumplen.
- shared/fare-vectors.json con al menos 30 casos calculados a mano: 0 m, 1 m, 200 m (mínima),
  empates de redondeo, distancia típica cercana a la referencia, distancia larga que activa el
  techo de 330, combustible +10 % y -10 %, índice 1,000, universitario con regular de 60, 120 y
  330 (30, 60 y 165), universitario con empate de redondeo, distancias grandes.
- Propiedades probadas: total >= 0; total <= maxFareCents; con beneficio, total <= sin beneficio;
  regular - descuento == total; el universitario es el 50 % redondeado.

PASO 2 - Versiones y calibración
- publishFareVersion.ts con las validaciones de la especificación y ManualFuelPriceProvider.
- calibrateFare.ts (percentil 75 por ruta, mínimo 30 viajes reales, solo sugiere). Pruebas con
  viajes sembrados SOLO en el test: datos insuficientes, cálculo correcto del percentil, ruta sin
  viajes.
- Reglas de Firestore: fareVersions y config lectura para autenticados, escritura prohibida.

PASO 3 - Integración con tapIn y tapOut
- tapIn guarda fareVersionId y routeFareSnapshot.
- tapOut llama a computeFare con la versión y la foto del viaje y el beneficio vigente (la
  funcionalidad 4 lo completa; por ahora hasUniversityBenefit = false). Guarda fareBreakdown y
  status "closed". Si la distancia es de revisión (insufficient_gps), no calcula tarifa.
- Quita cualquier TODO previo de la funcionalidad 2 y actualiza PROGRESO.md.

PASO 4 - estimateFare, como en la especificación.

PASO 5 - App
- "Resumen del viaje" con las filas de la especificación y el aviso de que es una propuesta.
  Formato de dinero con una sola función probada ("S/ 1,50"). Se abre al cerrarse el viaje y desde
  el historial.
- Pantalla "Estimar mi pasaje".

PASO 6 - Pruebas
- Unitarias: formateador de dinero (0, 5, 105, 150, 1050), mapeo del desglose a la interfaz.
- Compose UI: el resumen muestra precio calculado, combustible con fuente y fecha, referencia, y el
  aviso del techo cuando maxApplied es true.
- Functions en emulador: viaje completo con tarifa guardada; cambiar la versión a mitad de un viaje
  NO cambia el cobro de ese viaje.

PASO 7 - Documentación: docs/pruebas-manuales-F3.md y docs/TARIFA.md (modelo, fuentes con enlaces,
referencia de S/ 1,30, caso observado de S/ 3,30 como curiosidad, qué es propuesta).

Al final ejecuta todas las pruebas y muéstrame los resultados. No avances a la funcionalidad 4.
```

### 5. Pruebas manuales

| ID | Pasos | Resultado esperado |
|---|---|---|
| F3-M01 | Viaje real de distancia típica en R-CAY-01 | El importe es cercano a S/ 1,30 y coincide con tu cálculo a mano |
| F3-M02 | Viaje de 200 m | Se cobra la mínima (provisional) y el desglose lo indica |
| F3-M03 | Viaje corto y viaje largo en la misma ruta | El corto cuesta menos que el largo |
| F3-M04 | Viaje muy largo (o forzado en emulador) | Se aplica el techo de seguridad y el resumen lo avisa |
| F3-M05 | Cambiar la versión de tarifa durante un viaje abierto | Ese viaje se cobra con la versión del Tap In |
| F3-M06 | Estimar de un paradero a otro y luego hacer el viaje real | El estimado y el real son cercanos y el real manda |
| F3-M07 | Publicar una versión con el combustible 10 % más caro y repetir el viaje | El precio calculado sube cerca de 10 % |
| F3-M08 | Publicar una versión con el combustible más barato | El precio calculado baja |
| F3-M09 | Intentar publicar una versión con combustible de hace 2 meses | Rechazada con mensaje claro |
| F3-M10 | Ejecutar `calibrateFare` con menos de 30 viajes | Imprime "datos insuficientes" y no sugiere |
| F3-M11 | Viaje con beneficio universitario | El descuento es el 50 % del regular, redondeado a 5 céntimos |

### 6. Criterio de terminado

- Los 30 vectores pasan, incluidos mínima, techo, combustible y universitario al 50 %.
- La app muestra el combustible con fuente y fecha, la referencia de S/ 1,30 y que el cálculo es una propuesta.
- No queda ninguna referencia a zonas ni a un tope igual al pasaje oficial.

---

## Funcionalidad 4 — Beneficio universitario (verificación automática con OCR del carné + correo institucional)

### 1. Cómo se valida (sin administrador y sin simulación)

El estudiante pasa **dos verificaciones automáticas**, y el beneficio se aprueba solo si pasa ambas:

1. **Carné:** toma una foto de su carné universitario. El servidor la procesa con OCR (Google Cloud Vision) y comprueba que: (a) es de la universidad autorizada, (b) trae un código de estudiante con formato válido, (c) el nombre coincide con el del perfil de la cuenta, (d) si trae vigencia, no está vencido, y (e) la foto tiene calidad suficiente.
2. **Correo institucional:** recibe un código de 6 dígitos de un solo uso en su correo institucional y lo escribe en la app.

**Honestidad técnica (debe quedar en LIMITACIONES.md y en el informe):**
- El OCR lee el texto del carné; **no prueba que el carné sea auténtico ni que la matrícula esté vigente**. Alguien podría fotografiar un carné ajeno.
- Por eso se exige además el código al correo institucional: para engañar al sistema haría falta tener el carné y el buzón de la misma persona.
- Mitigaciones: foto tomada con la cámara (no de la galería), un código de estudiante por cuenta, límite de intentos, vencimiento del beneficio a los 180 días con nueva verificación.
- La comprobación total de matrícula vigente exigiría una API o convenio con la universidad. Deja la interfaz `EnrollmentVerifier` lista para conectarla.

### 2. Qué debe hacer (detalle completo)

**Descuento aplicado (F3):** el universitario paga el 50 % del precio regular calculado, redondeado al paso de 5 céntimos. La fórmula está en la F3; aquí solo se determina si la persona tiene el beneficio vigente en el momento del Tap Out.

**Flujo en la app (pasajero)**

1. Sección "Mis beneficios", estado "Sin beneficio" y botón "Verificar como universitario".
2. **Paso 1, carné:** pantalla con guía de encuadre y consejos ("buena luz, sin reflejos, carné completo"). Se usa la **cámara** (no galería). Una verificación rápida en el celular (ML Kit Text Recognition, offline y gratuito) indica si el texto se lee y muestra el código detectado, solo para orientar al usuario. **La decisión real la toma el servidor.**
3. La foto se sube a Cloud Storage en `benefitUploads/{uid}/{requestId}.jpg` y se llama a `submitCarne`. Se muestra "Revisando tu carné..." y luego el resultado: aprobado o motivo claro del rechazo ("no se pudo leer el código", "el nombre no coincide con tu cuenta", "carné vencido", "imagen borrosa").
4. **Paso 2, correo:** si el carné pasó, la app pide el correo institucional, el servidor envía el código de 6 dígitos y la app muestra el campo con temporizador de 10 min y reenvío cada 60 s.
5. Al confirmar el código, estado **Aprobado** (tarjeta verde, "Descuento universitario activo hasta [fecha]"). Estados adicionales: Rechazado (con motivo y botón para reintentar), En proceso y Vencido.
6. Tarjeta de comparación del mismo viaje de referencia sin y con beneficio, usando `previewFare` del servidor.

**Servidor**

- **Storage:** reglas que permiten al dueño **subir** (no leer ni borrar) un único archivo por solicitud en su carpeta, solo `image/jpeg`, máximo 5 MB. Política de ciclo de vida que borra todo lo de `benefitUploads/` a las 24 horas por seguridad.
- `submitCarne({requestId, sessionId})` (solo pasajeros con correo de cuenta verificado):
  1. Límite: 3 envíos por día por cuenta.
  2. Descarga la imagen. Comprueba calidad con la librería `sharp`: ancho mínimo 800 px y un umbral de nitidez (varianza del Laplaciano) configurable.
  3. Llama al reconocimiento de texto mediante la interfaz `TextRecognizer`. Implementación real: Google Cloud Vision `documentTextDetection`. Las pruebas usan una implementación de prueba que devuelve textos de ejemplo.
  4. **Análisis del texto (tolerante al diseño del carné, sin depender de posiciones):**
     - Universidad: el texto debe contener todas las palabras clave configuradas (por defecto CATOLICA, SANTA, MARIA), sin tildes y sin distinguir mayúsculas.
     - Código: exactamente un número que cumpla el patrón configurado (por defecto 10 dígitos que empiezan por 19 o 20, como `2023202051`). Si hay varios candidatos, rechaza.
     - Nombre: se normaliza (sin tildes, mayúsculas, solo letras) y se comparan las palabras del nombre y apellido del perfil contra las palabras del texto; coincide si al menos el 75 % de esas palabras (mínimo 2) aparecen, con tolerancia de 1 error de escritura en palabras de 5 o más letras.
     - Vigencia: si el texto contiene una fecha de vigencia reconocible y es anterior a hoy, rechaza ("carné vencido"). Si no trae fecha, se acepta (configurable).
  5. Si todo pasa, guarda en `benefitRequests/{uid}`: `stage: "carne_ok"`, el HMAC del código de estudiante (secreto `CARNE_PEPPER`) y la fecha. **Elimina la imagen de Storage siempre** (en un bloque `finally`), también si falló. No se guarda la imagen ni el texto completo, solo el HMAC del código.
  6. Si ese HMAC ya pertenece a otra cuenta (`carneIndex/{hmac}`), rechaza sin revelar de quién.
  7. Registra el intento (cuenta, resultado, motivo) sin datos personales en el log.
- `requestBenefitCode({institutionalEmail, sessionId})`: exige `stage == "carne_ok"`. Normaliza el correo, valida dominio autorizado (`UNIVERSITY_DOMAINS`) y patrón (`STUDENT_EMAIL_PATTERN`); si la parte local del correo es numérica, debe coincidir con el código del carné (`EMAIL_MUST_MATCH_CODE`, configurable). Límites: 3 códigos por hora, 5 por día. Genera un código de 6 dígitos con generador criptográfico, guarda solo su HMAC con vencimiento a 10 minutos y lo envía por SMTP (secretos `SMTP_*`). Respuesta siempre uniforme.
- `confirmBenefitCode({code, sessionId})`: máximo 5 intentos, comparación en tiempo constante. Si es correcto, en transacción: `users/{uid}.benefit = {status:"approved", kind:"university", percent:50, validUntil, verifiedAt}`, guarda el HMAC del correo institucional, registra el HMAC del código de estudiante en `carneIndex` y borra la solicitud.
- `expireBenefits` (diaria): los vencidos pasan a `expired`.
- `previewFare({routeId})`: devuelve la tarifa de un viaje de referencia con y sin beneficio usando `computeFare` del servidor.
- `tapOut` (F3): `hasUniversityBenefit` es verdadero si `benefit.status == "approved"` y `validUntil` es futuro en el instante del Tap Out.

**Configuración (`functions/src/config/benefit.ts`)**: `UNIVERSITY_DOMAINS` (por defecto `ucsm.edu.pe`, marcado CONFIRMAR), `STUDENT_EMAIL_PATTERN`, `UNIVERSITY_KEYWORDS`, `STUDENT_CODE_PATTERN`, `NAME_MATCH_RATIO = 0.75`, `NAME_MIN_TOKENS = 2`, `MIN_IMAGE_WIDTH = 800`, `BLUR_THRESHOLD`, `REQUIRE_VALIDITY_DATE = false`, `BENEFIT_VALID_DAYS = 180`, `MAX_CARNE_SUBMISSIONS_PER_DAY = 3`.

**Reglas de Firestore y Storage**: `benefitRequests`, `carneIndex` y los índices de HMAC sin acceso de clientes; `users/{uid}.benefit` solo lo escribe el servidor; Storage como se describió.

### 3. Lo que haces tú

1. **Habilita las APIs de Google Cloud** del proyecto: Cloud Vision API (tiene una cuota gratuita mensual; revisa los precios vigentes) y activa Firebase Storage en `southamerica-east1`.
2. **Una muestra de carné:** toma una foto de tu propio carné, tapa los datos que quieras y guárdala en `data/samples/` (NO se sube a git). Sirve para confirmar el texto que aparece (universidad, código, vigencia) y ajustar `UNIVERSITY_KEYWORDS`, `STUDENT_CODE_PATTERN` y `REQUIRE_VALIDITY_DATE`.
3. Define el SMTP y guarda los secretos: `firebase functions:secrets:set SMTP_HOST` (y `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `SMTP_FROM`), más `CARNE_PEPPER`.
4. Confirma el dominio y el formato de los correos de estudiantes.
5. Prueba con tu carné real y tu correo institucional real.

### 4. Prompt para OpenCode

```
Implementa la FUNCIONALIDAD 4 (Beneficio universitario con verificación AUTOMÁTICA: OCR del carné
más código al correo institucional) siguiendo AGENTS.md. Prohibido aprobar manualmente. Esta versión
reemplaza cualquier versión anterior. Un commit por paso.

PASO 1 - Configuración y lógica pura (TypeScript) con pruebas
- functions/src/config/benefit.ts con las constantes de la especificación. Dominio y patrones
  provisionales marcados CONFIRMAR.
- Funciones puras con pruebas: normalizar texto (sin tildes, mayúsculas), comprobar palabras clave
  de la universidad, extraer el código de estudiante (un solo candidato válido; con varios, rechazo),
  comparar el nombre del perfil con el texto OCR (75 %, mínimo 2 palabras, tolerancia de 1 error en
  palabras de 5 o más letras), detectar fecha de vigencia y compararla con hoy, normalizar y validar
  el correo institucional (dominios engañosos como ucsm.edu.pe.evil.com, +alias, mayúsculas), generar
  código de 6 dígitos con crypto.randomInt, HMAC del código, del correo y del código de estudiante,
  comparación en tiempo constante.
- Pruebas del parser con textos OCR de EJEMPLO escritos SOLO en los tests: carné válido, nombre con
  una letra distinta, nombre de otra persona, sin código, dos códigos, universidad distinta, vencido,
  texto con ruido.

PASO 2 - Interfaz TextRecognizer y calidad de imagen
- Interfaz TextRecognizer con implementación real sobre Google Cloud Vision documentTextDetection
  y una implementación de prueba (solo para tests). Comprobación de calidad de imagen con sharp
  (ancho mínimo y nitidez). Pruebas de calidad con imágenes generadas dentro de la prueba
  (nítida, borrosa, pequeña).
- Si la API de Vision no está habilitada o falla, submitCarne devuelve un error claro de servicio
  (nunca aprueba por defecto).

PASO 3 - Storage y Cloud Functions
- storage.rules: el dueño solo puede crear un archivo image/jpeg de máximo 5 MB en
  benefitUploads/{uid}/{requestId}.jpg; sin lectura ni borrado desde clientes. Agrega el emulador
  de Storage y las pruebas de reglas de Storage (permitido y denegado).
- submitCarne, requestBenefitCode, confirmBenefitCode, expireBenefits y previewFare como en la
  especificación. La imagen se borra SIEMPRE en finally. Respuestas uniformes que no revelan cuentas.
  Lifecycle de 24 h para benefitUploads.
- tapOut: usa el beneficio vigente en el instante del Tap Out y computeFare de la funcionalidad 3.

PASO 4 - Pruebas en emulador
- Flujo feliz con TextRecognizer de prueba y transporte de correo de prueba: subir imagen, carné OK,
  pedir código, leer el correo capturado, confirmar, beneficio aprobado y viaje posterior con el
  descuento correcto (50 % del regular, redondeado a 5 céntimos).
- Rechazos: imagen pequeña o borrosa, nombre que no coincide, sin código, código ya usado por otra
  cuenta, carné vencido, 4.º envío del día, intentar pedir el código sin pasar el carné, dominio
  no autorizado, parte local del correo distinta del código del carné, 5 intentos de código erróneos,
  código vencido, 4.º código en una hora, operador intentando verificar, cuenta sin correo verificado.
- La imagen de Storage se elimina tanto en éxito como en error (verificación en la prueba).
- El beneficio que vence entre el Tap In y el Tap Out se decide en el Tap Out.

PASO 5 - App
- "Mis beneficios" con todos los estados; captura con CameraX solo desde la cámara; guía de encuadre;
  verificación previa con ML Kit Text Recognition (solo orientativa); subida a Storage; resultado con
  motivo claro; paso del correo con temporizador y reenvío; tarjeta de comparación con previewFare.
- Listener en tiempo real de users/{uid}.benefit y de benefitRequests (solo el estado público).

PASO 6 - Pruebas de la app
- Unitarias: ViewModel con todos los estados (Turbine), temporizadores, validación de 6 dígitos,
  manejo de errores de subida.
- Compose UI: cada estado muestra lo correcto; el campo del código rechaza letras.

PASO 7 - Documentación
- docs/pruebas-manuales-F4.md.
- LIMITACIONES.md: el OCR lee texto pero no prueba autenticidad ni matrícula vigente; qué mitigaciones
  existen; la interfaz EnrollmentVerifier queda lista para una API o convenio con la universidad.

Al final ejecuta todas las pruebas y muéstrame los resultados. No avances a la funcionalidad 5.
```

### 5. Pruebas manuales

| ID | Pasos | Resultado esperado |
|---|---|---|
| F4-M01 | Fotografiar tu carné real con buena luz | El servidor lo acepta y el estado pasa al paso del correo |
| F4-M02 | Fotografiar tu carné con la cuenta de otra persona (nombre distinto) | Rechazado: "el nombre no coincide con tu cuenta" |
| F4-M03 | Foto muy borrosa o de lejos | Rechazada con consejo de mejorar la imagen |
| F4-M04 | Foto de un documento que no es un carné (por ejemplo, tu DNI) | Rechazada: no se reconoce la universidad ni el código |
| F4-M05 | Subir una foto de la galería | No es posible: la app solo ofrece la cámara |
| F4-M06 | Intentar verificar el mismo carné desde una segunda cuenta | Rechazado sin revelar quién lo usa |
| F4-M07 | Pedir el código de correo con el carné aprobado y usar tu correo institucional | Llega el correo en menos de 1 minuto |
| F4-M08 | Escribir el código correcto | "Descuento universitario activo hasta [fecha]" |
| F4-M09 | Escribir 5 códigos incorrectos | La solicitud se invalida |
| F4-M10 | Usar un correo de otro dominio (por ejemplo gmail) | Rechazado |
| F4-M11 | Hacer un viaje con beneficio aprobado | El resumen muestra el descuento y el descuento es el 50 % del importe regular |
| F4-M12 | Revisar Storage después de un envío | La imagen ya no existe |
| F4-M13 | Intentar 4 envíos de carné en un día | El cuarto es rechazado |
| F4-M14 | Forzar vencimiento del beneficio (emulador) y correr `expireBenefits` | Estado vencido y el siguiente viaje se cobra completo |

### 6. Criterio de terminado

- Se verificó con tu carné real y tu correo institucional real, de principio a fin.
- Pruebas automáticas en verde, incluidas las de abuso y la de borrado de la imagen.
- `LIMITACIONES.md` explica el alcance y los límites del OCR.

---

## 10. Funcionalidad 5 — Aviso de voz

### 10.1 Qué debe hacer (detalle completo)

- Al registrarse un Tap In correcto, el teléfono del operador dice en voz alta: **"Ana, 21 años"** (primer nombre y edad). El texto sale de la respuesta del servidor (`firstName`, `age`), nunca del DNI.
- El aviso se reproduce una sola vez por Tap In. Si llegan dos Tap In seguidos, se encolan y se dicen en orden.
- Idioma: español de Perú (`es-PE`) si el teléfono lo tiene; si no, `es-US` o `es-ES`; si ninguno está instalado, la app muestra un aviso con botón para instalar los datos de voz del motor de síntesis y **muestra el nombre y la edad en pantalla** igualmente.
- Formato del texto: primer nombre limpio (sin números ni símbolos, con mayúscula inicial) y edad con plural correcto: "1 año", "21 años". Si el nombre es muy largo, se recorta al primer nombre.
- Ajustes del operador: interruptor "Aviso de voz" (guardado en DataStore) y botón "Probar voz" que dice "Prueba de voz de PasajeYa".
- Privacidad: el DNI no existe en el cliente del operador. Se verifica con una prueba que la respuesta de `tapIn` solo contiene `tripId`, `firstName` y `age`.
- Accesibilidad: junto al aviso, la pantalla muestra una tarjeta grande con nombre y edad durante unos segundos, para ambientes con ruido.
- El volumen usa el canal de medios del sistema; la app no lo cambia.

### 10.2 Prompt para OpenCode

```
Implementa la FUNCIONALIDAD 5 (Aviso de voz) siguiendo AGENTS.md y conectándola al gancho
onTapInSuccess de la funcionalidad 2. Un commit por paso.

PASO 1 - Lógica pura con pruebas
- VoiceAnnouncementFormatter (feature/voice/domain): construye el texto "Nombre, N años" /
  "Nombre, 1 año". Sanea el nombre (solo letras, tildes, ñ; quita números y símbolos), usa el
  primer nombre con mayúscula inicial, y recorta a 20 caracteres. Casos de prueba: "ana maría" ->
  "Ana", "JOSÉ" -> "José", "a1na" -> "Ana", edad 1, 21, 0, nombre vacío (no se anuncia nada y se
  muestra aviso visual de error).

PASO 2 - Servicio de voz
- Interfaz SpeechAnnouncer (speak, stop, isAvailable) con implementación real sobre el
  TextToSpeech de Android: inicialización asíncrona, selección de idioma es-PE con respaldo es-US y
  es-ES, manejo de LANG_MISSING_DATA y LANG_NOT_SUPPORTED, cola de mensajes (QUEUE_ADD), liberación
  de recursos en onCleared/onDestroy. Si no hay voz disponible, informa al ViewModel para mostrar el
  aviso y el botón que abre ACTION_INSTALL_TTS_DATA.
- Preferencia "Aviso de voz" en DataStore (activada por defecto).

PASO 3 - Integración
- En el flujo del escáner del operador, tras un Tap In correcto llama a SpeechAnnouncer con el
  texto formateado, y muestra la tarjeta grande con nombre y edad durante 4 s.
- Ajustes del operador con el interruptor y el botón "Probar voz".

PASO 4 - Pruebas
- Unitarias: formateador (casos de arriba); ViewModel del escáner con un FakeSpeechAnnouncer de
  prueba: se anuncia una vez por Tap In, se encolan dos seguidos, no se anuncia con el interruptor
  apagado, se muestra el aviso cuando no hay voz.
- Functions (emulador): la respuesta de tapIn tiene EXACTAMENTE las claves tripId, firstName y age.
- Reglas: un operador no puede leer users ni users_private de un pasajero.
- Prueba estática: busca en el código de la app y de functions cualquier uso de la palabra "dni" en
  logs, mensajes de voz o respuestas a clientes; debe fallar la prueba si aparece.

PASO 5 - Documentación: docs/pruebas-manuales-F5.md.

Al final ejecuta todas las pruebas y muéstrame los resultados. No avances a la funcionalidad 6.
```

### 10.3 Pruebas manuales (celular del operador)

| ID | Pasos | Resultado esperado |
|---|---|---|
| F5-M01 | Escanear el QR de una cuenta llamada "Ana", 21 años | El teléfono dice "Ana, 21 años" y la pantalla lo muestra |
| F5-M02 | Cuenta de 1 año de edad (fecha de nacimiento de prueba) | Dice "1 año" |
| F5-M03 | Dos escaneos seguidos de personas distintas | Se dicen en orden, sin cortarse |
| F5-M04 | Apagar el interruptor de voz y escanear | No habla; sí muestra la tarjeta |
| F5-M05 | Botón "Probar voz" | Dice la frase de prueba |
| F5-M06 | Teléfono sin datos de voz en español | Aviso con botón de instalación y datos en pantalla |
| F5-M07 | Revisar que en ningún momento se diga ni se vea el DNI | Confirmado |

### 10.4 Criterio de terminado

- Pruebas en verde, incluida la prueba estática del DNI.
- Probado en al menos dos modelos de teléfono distintos.

---

## 11. Funcionalidad 6 — Historial de viajes y pagos

### 11.1 Qué debe hacer (detalle completo)

**Pasajero**

- Pestaña "Mi historial": lista de viajes cerrados y en revisión, más recientes primero, con fecha y hora, ruta, distancia, importe final y etiqueta de beneficio (por ejemplo, "Univ. 50 %"). Los viajes en revisión muestran "Importe pendiente" y la etiqueta ámbar.
- **Filtros:** "Esta semana", "Este mes" y "Todo". La semana comienza el lunes y se calcula con la zona horaria `America/Lima`.
- **Gasto acumulado** del filtro elegido, calculado con una **consulta de agregación de Firestore** (suma de `totalCents`), no sumando en el celular lo que hay en pantalla. Solo cuenta viajes con estado `closed`.
- **Paginación:** 20 viajes por página con carga al llegar al final (cursor `startAfter`).
- **Detalle:** al tocar un viaje se abre el mismo "Resumen del viaje" de la funcionalidad 3 (solo lectura).
- Estados de carga, lista vacía ("Aún no tienes viajes") y error con reintento.
- Funciona sin conexión con la caché de Firestore: muestra lo ya cargado y un aviso "Sin conexión".

**Operador (turno actual)**

- Sección "Mis viajes del turno": cantidad de viajes cerrados, en revisión y total cobrado del turno actual o del último turno. Solo lectura.

**Índices y reglas**

- Índices compuestos en `firestore.indexes.json`: `passengerUid + startedAt` y `passengerUid + status + startedAt`; para el operador `operatorUid + shiftId + startedAt`.
- Reglas: ya cubiertas (`trips` legible por su pasajero y su operador; nadie escribe).

### 11.2 Prompt para OpenCode

```
Implementa la FUNCIONALIDAD 6 (Historial de viajes y pagos) siguiendo AGENTS.md. Un commit por paso.

PASO 1 - Índices y consultas
- Agrega los índices compuestos necesarios a firestore.indexes.json y verifica que las consultas
  corran en el emulador sin errores de índice.
- HistoryRepository con: página de viajes (limit 20, orden por startedAt descendente, cursor),
  filtro por rango (semana desde el lunes 00:00 hora America/Lima, mes calendario, todo) y suma de
  gasto con AggregateField.sum("fareBreakdown.totalCents") filtrando status == "closed".
- Utilidad de rangos de fechas probada: semana que cruza el cambio de mes, año nuevo, domingo
  23:59 frente a lunes 00:00 en hora de Lima.

PASO 2 - Pruebas de reglas y datos (emulador)
- Un pasajero lee solo sus viajes; consultar viajes de otro es denegado; el operador lee los de su
  turno; nadie escribe trips.
- Con 45 viajes sembrados SOLO en la prueba: la paginación devuelve 20, 20 y 5 sin repetir ni
  omitir; la suma agregada coincide con la suma manual de los viajes cerrados; los viajes en
  revisión no suman.

PASO 3 - App (pasajero)
- Pantalla "Mi historial" con filtros (chips), lista con tarjetas del diseño del informe 6, gasto
  acumulado, paginación infinita, estados de carga/vacío/error y aviso sin conexión.
- Al tocar un viaje, navega al "Resumen del viaje" en modo solo lectura.

PASO 4 - App (operador)
- Sección "Mis viajes del turno" con conteo de cerrados, en revisión y total cobrado.

PASO 5 - Pruebas de la app
- Unitarias: ViewModel (filtros, paginación, errores) con Turbine; formato de fecha en español
  ("Lun 20 oct · 08:15") con zona de Lima.
- Compose UI: lista vacía, lista con viajes en revisión (etiqueta ámbar e "Importe pendiente"),
  cambio de filtro actualiza el gasto.
- Consistencia: una prueba verifica que el gasto agregado mostrado es igual a la suma de los
  importes de los viajes cerrados del mismo filtro.

PASO 6 - Documentación: docs/pruebas-manuales-F6.md.

Al final ejecuta todas las pruebas y muéstrame los resultados. No avances a la funcionalidad 7.
```

### 11.3 Pruebas manuales

| ID | Pasos | Resultado esperado |
|---|---|---|
| F6-M01 | Completar un viaje y abrir el historial | El viaje aparece arriba con fecha, ruta, distancia e importe correctos |
| F6-M02 | Comparar el importe del historial con el resumen del viaje | Idénticos |
| F6-M03 | Cambiar entre "Esta semana", "Este mes" y "Todo" | La lista y el gasto cambian y son coherentes |
| F6-M04 | Sumar a mano los importes de la semana | Igual al gasto mostrado |
| F6-M05 | Tener un viaje en revisión | Aparece con etiqueta ámbar y "Importe pendiente", y no suma al gasto |
| F6-M06 | Poner el teléfono en modo avión | Se ve lo ya cargado con el aviso "Sin conexión" |
| F6-M07 | Cuenta sin viajes | Mensaje "Aún no tienes viajes" |
| F6-M08 | Operador revisa "Mis viajes del turno" | Conteos y total coinciden con los viajes del turno |

### 11.4 Criterio de terminado

- Paginación y suma agregada verificadas con 45 viajes de prueba.
- Las consultas no fallan por falta de índices en el proyecto real (despliega los índices con `firebase deploy --only firestore:indexes`).

---

## 12. Funcionalidad 7 — Viajes en revisión y reclamos

### 12.1 Qué debe hacer (detalle completo)

**Cuándo un viaje pasa a "requiere revisión"** (todo lo decide el servidor, y se guarda `reviewReason`):

| Motivo | Cuándo |
|---|---|
| `no_tap_out` | Viaje abierto por más de 3 horas (funcionalidad 2) |
| `shift_ended` | El operador terminó el turno con el viaje abierto |
| `insufficient_gps` | No hay datos de GPS suficientes para medir la distancia |

**Pasajero**

- En el historial y en la tarjeta del viaje: etiqueta ámbar "Requiere revisión", motivo en palabras simples y botón **"Enviar reclamo"**.
- **Formulario de reclamo:** viaje (fijo), motivo (lista: "Olvidé hacer el Tap Out", "El cobrador no escaneó mi salida", "Me cobraron un importe incorrecto", "Otro"), **paradero donde bajó** (lista real de paraderos de la ruta del viaje, obligatorio para los dos primeros motivos) y comentario de hasta 500 caracteres.
- Estado del reclamo visible: **Enviado → En revisión → Resuelto**, con el resultado y el importe final cuando se resuelve.
- Notificación push al resolverse (FCM). La app pide el permiso de notificaciones (Android 13+) y guarda el token del dispositivo vía función.

**Resolución del reclamo (automática primero, humana solo si hace falta)**

1. **Resolución automática:** si el pasajero indicó el paradero donde bajó, el servidor busca en el recorrido grabado del turno (después del Tap In) el punto más cercano a ese paradero. Si está a **150 m o menos**, calcula la distancia hasta ese punto, la tarifa con la versión y el beneficio de ese viaje, cierra el viaje (`closed`) y resuelve el reclamo (`resolved_auto`).
2. **Confirmación del operador:** si no hay coincidencia automática, el reclamo va a la bandeja del operador del viaje, que ve el viaje, el paradero declarado y dos botones: **"Sí, bajó ahí"** (se calcula y cierra con ese paradero) o **"No coincide"** (se rechaza con un motivo breve obligatorio).
3. **Plazo:** si el operador no responde en **72 horas**, el reclamo se resuelve a favor del pasajero cobrando la tarifa mínima (política configurable; decisión D7).
4. Un reclamo por viaje. Se puede reclamar hasta 7 días después del viaje. Los viajes `closed` pueden reclamarse solo con motivo "importe incorrecto", que entra directo a la bandeja del operador.

**Servidor**

- `createClaim({tripId, reason, stopIndex?, comment, sessionId})`: valida dueño del viaje, estado, plazo de 7 días, un solo reclamo por viaje, longitud del comentario (sin código ni HTML: se escapa), y paradero dentro de la ruta del viaje.
- `resolveClaimByOperator({claimId, decision, note?, sessionId})`: solo el operador del viaje.
- `autoResolveClaim` interno (se llama al crear), `claimsTimeout` programada cada hora, y `registerFcmToken({token})`.
- Mensajes FCM sin datos sensibles: "Tu reclamo fue resuelto".
- Reglas: `claims` legible por el pasajero dueño y por el operador del viaje; escritura prohibida.

**Operador**

- Pestaña "Reclamos" con la bandeja de pendientes y el detalle.

### 12.2 Lo que haces tú

1. Decide la política de los 72 h (decisión D7).
2. Para probar notificaciones, usa un celular físico con Google Play Services.

### 12.3 Prompt para OpenCode

```
Implementa la FUNCIONALIDAD 7 (Viajes en revisión y reclamos) siguiendo AGENTS.md. Un commit por paso.

PASO 1 - Lógica pura con pruebas
- functions/src/claims/matchStop.ts: dado el recorrido del turno (puntos filtrados), el instante de
  Tap In y las coordenadas de un paradero, encuentra el punto más cercano posterior al Tap In y
  decide si está a 150 m o menos (umbral en config). Devuelve también la distancia recorrida hasta
  ese punto. Pruebas: paradero sobre el trayecto, a 140 m, a 160 m, antes del Tap In (no vale),
  recorrido vacío.
- Máquina de estados del reclamo con pruebas: submitted -> in_review -> resolved_auto |
  resolved_operator_accepted | resolved_operator_rejected | resolved_timeout. Transiciones
  inválidas lanzan error.

PASO 2 - Cloud Functions
- createClaim, resolveClaimByOperator, claimsTimeout (cada hora; reloj inyectable),
  registerFcmToken. La resolución que cierra un viaje usa computeFare (funcionalidad 3) con la
  versión de tarifa y el beneficio del viaje, y actualiza trips y claims en una transacción.
- Política de plazo: CLAIM_TIMEOUT_HOURS = 72 y resultado "tarifa mínima" (configurable).
  Pídeme la decisión D7 antes de dejarla fija.
- Validaciones: dueño, estado del viaje, plazo de 7 días, un reclamo por viaje, comentario de
  máximo 500 caracteres y sanitizado, paradero perteneciente a la ruta del viaje, motivo dentro de
  la lista. Notificación FCM al resolver, sin datos sensibles; si el token es inválido, se elimina.

PASO 3 - Reglas de Firestore
- claims legible por el pasajero y el operador del viaje; escritura prohibida. Pruebas de reglas.

PASO 4 - Pruebas en emulador
- Flujo automático: viaje en revisión por no_tap_out -> reclamo con paradero a menos de 150 m ->
  viaje cerrado con tarifa calculada -> reclamo resolved_auto.
- Flujo con operador: paradero lejano -> bandeja del operador -> "Sí, bajó ahí" cierra el viaje;
  "No coincide" rechaza y exige nota.
- Plazo: pasadas 72 h, resolved_timeout con tarifa mínima.
- Rechazos: viaje ajeno, segundo reclamo del mismo viaje, reclamo después de 7 días, comentario de
  501 caracteres, comentario con etiquetas HTML (se escapan), paradero de otra ruta, operador que
  no es el del viaje.

PASO 5 - App (pasajero)
- Etiqueta ámbar y motivo en historial y detalle; formulario de reclamo con lista de motivos,
  selector de paradero real de la ruta del viaje y comentario con contador 0/500; estado del
  reclamo (Enviado, En revisión, Resuelto) con resultado e importe; permiso de notificaciones y
  registro del token FCM; al tocar la notificación abre el viaje.

PASO 6 - App (operador)
- Pestaña "Reclamos": bandeja de pendientes, detalle del viaje y del paradero declarado, botones
  "Sí, bajó ahí" y "No coincide" (nota obligatoria), con confirmación.

PASO 7 - Pruebas de la app
- Unitarias: ViewModels (formulario válido/ inválido, contador, estados del reclamo).
- Compose UI: el formulario exige paradero para los motivos de salida; la bandeja solo muestra
  reclamos del operador; el botón "No coincide" exige nota.

PASO 8 - Documentación: docs/pruebas-manuales-F7.md y la política de reclamos en docs/RECLAMOS.md.

Al final ejecuta todas las pruebas y muéstrame los resultados.
```

### 12.4 Pruebas manuales

| ID | Pasos | Resultado esperado |
|---|---|---|
| F7-M01 | Hacer Tap In y no hacer Tap Out; terminar el turno | El viaje aparece como "Requiere revisión" por turno terminado |
| F7-M02 | Abrir el viaje y tocar "Enviar reclamo" | Formulario con motivos y lista de paraderos de la ruta |
| F7-M03 | Reclamar indicando un paradero que está sobre el recorrido | Se resuelve sola, el viaje se cierra con el importe calculado y llega la notificación |
| F7-M04 | Reclamar con un paradero lejano al recorrido | El reclamo llega a la bandeja del operador |
| F7-M05 | Operador toca "Sí, bajó ahí" | Viaje cerrado y notificación al pasajero |
| F7-M06 | Operador toca "No coincide" sin nota | No deja continuar |
| F7-M07 | Intentar un segundo reclamo del mismo viaje | Rechazado |
| F7-M08 | Escribir un comentario de más de 500 caracteres | El contador lo impide |
| F7-M09 | Simular el paso de 72 horas en emulador | Se resuelve a favor del pasajero con la tarifa mínima |
| F7-M10 | Ver el historial tras resolver | El viaje ya no está ámbar y su importe suma al gasto |

### 12.5 Criterio de terminado

- Los tres caminos de resolución (automático, operador, plazo) están probados.
- Notificación recibida en un celular real.

---

## Funcionalidad 8 — Pago por contacto NFC (saldo de demostración)

### Excepción a la regla "nada simulado"

Esta funcionalidad tiene UNA excepción acordada con el equipo: **el dinero es de demostración**. No se maneja dinero real, no hay pasarela de pago ni recargas reales. Todo lo demás es real: el contacto NFC entre dispositivos, la lectura de tarjetas, el cálculo de la tarifa, los descuentos del saldo y el historial de movimientos.

Reglas de honestidad:
- Toda pantalla que muestre saldo dice "Saldo de demostración: no es dinero real".
- El código de recarga de demostración está protegido por la bandera `DEMO_PAYMENTS`. Si vale `false`, las funciones de recarga rechazan la operación. Esa bandera es lo que habría que cambiar el día que exista una pasarela real.
- Quedan anotados en `docs/LIMITACIONES.md`: pago simulado, tarjeta física clonable y uso solo en Android.

### 1. Qué debe hacer (detalle completo)

**Idea:** el pasajero acerca su celular (o una tarjeta NFC) al celular del conductor-cobrador. Eso hace el Tap In o el Tap Out, igual que el QR, y al bajar se descuenta el pasaje del saldo de demostración.

**A. El celular del pasajero como "tarjeta" (HCE)**

- La app del pasajero implementa un servicio de emulación de tarjeta (Host Card Emulation) con un AID propio: `F0` seguido de los bytes ASCII de `PASAJEYA` (en hexadecimal: `F0504153414A455941`, 9 bytes; OpenCode debe verificarlo y dejarlo como constante probada).
- Cuando el celular del conductor lo lee, responde con el **mismo token firmado y rotativo del QR** (`issueQrToken`, dura 60 s, un solo uso), seguido de `90 00`. El token debe caber en una sola respuesta APDU (máximo 240 bytes): si no cabe, hay que acortar el formato.
- La pantalla "Pagar" del pasajero mantiene el token vigente en memoria y lo renueva cada 45 s. El servicio HCE solo responde si hay un token vigente; si no, responde un código de estado de error.
- Requiere NFC activado y pantalla encendida. Si el NFC está apagado, la pantalla "Pagar" lo avisa y ofrece abrir los ajustes de NFC.
- El QR sigue existiendo como alternativa (iPhone, celulares sin NFC).

**B. El celular del conductor como lector**

- La pantalla de escaneo activa el **modo lector** de NFC (`enableReaderMode`) con soporte para tecnologías NFC-A y NFC-B y sin comprobación de NDEF.
- Si lee un dispositivo con IsoDep: envía el comando SELECT con el AID, lee el token de la respuesta y llama al mismo `tapIn` o `tapOut` que ya existen para el QR.
- Si lee una **tarjeta NFC física** (etiqueta con UID): obtiene el UID y llama a `tapIn` o `tapOut` con ese UID.
- Antirrebote de 3 s para no procesar la misma lectura dos veces. Vibración y tarjeta verde al éxito, mensaje claro en caso de error. Si el NFC está apagado, aviso con botón a ajustes.
- La pantalla mantiene el botón "Usar cámara (QR)".
- El aviso de voz (funcionalidad 5) se dispara igual tras un Tap In por NFC.

**C. Tarjeta NFC física (opcional, pero implementada)**

- El pasajero va a "Mis tarjetas" → "Vincular tarjeta" y acerca una tarjeta NFC (tipo NTAG/MIFARE Ultralight) a su propio celular. La app lee el UID y llama a `linkNfcCard`.
- El servidor guarda **solo el HMAC del UID** (con secreto `NFC_PEPPER`) en `nfcCards/{hmac}` → `{uid, label, createdAt, blocked}`. Máximo 2 tarjetas por cuenta. Una tarjeta solo puede estar vinculada a una cuenta.
- Funciones: `linkNfcCard`, `unlinkNfcCard`, `blockNfcCard` (por si se pierde) y `listNfcCards` (devuelve solo etiqueta, estado y los últimos 4 dígitos del UID).
- Cada Tap In con tarjeta envía una **notificación al pasajero** ("Se usó tu tarjeta en la ruta X"), como mitigación del riesgo de clonación.
- Limitación asumida: el UID de estas tarjetas es fijo y se puede clonar; es aceptable para la demostración y queda documentado.

**D. Saldo de demostración y cobro**

- `wallets/{uid}`: `{balanceCents, heldCents, updatedAt}`. Subcolección `movements/{id}`: `{type, amountCents, tripId?, createdAt}` con tipos `topup_demo`, `hold`, `release`, `charge`, `refund`. Solo el servidor escribe; el dueño solo lee.
- `topUpDemo({amountCents})`: montos permitidos 500, 1000 o 2000 (S/ 5, 10, 20), saldo máximo S/ 50, solo si `DEMO_PAYMENTS` es verdadero, con límite de 10 recargas por día.
- **Tap In:** el servidor exige saldo disponible (saldo menos retenido) mayor o igual al techo de seguridad de la tarifa vigente (`maxFareCents`, el máximo posible del viaje), y la **retiene** (`hold`) dentro de la misma transacción que crea el viaje. Sin saldo suficiente, rechaza con el mensaje "Saldo de demostración insuficiente. Recarga para viajar".
- **Tap Out:** calcula la tarifa (funciones 2, 3 y 4), registra el `charge` por el total, libera la diferencia (`release`) y deja el saldo actualizado, todo en una transacción. El desglose del viaje incluye `paidWith: "demo_balance"` y `balanceAfterCents`.
- **Viaje en revisión:** la retención se mantiene hasta resolverse. Al resolver el reclamo (funcionalidad 7), se cobra el total y se libera el resto; si pasan 7 días sin reclamo, se libera la retención sin cobrar. Las funciones de reclamos deben actualizarse para esto y sus pruebas también.
- Efectivo de respaldo: el operador puede marcar un viaje como "Pagado en efectivo" desde su lista a bordo; en ese caso no se retiene ni se descuenta saldo. Se guarda `paidWith: "cash"`.
- El historial (funcionalidad 6) muestra una etiqueta "Saldo" o "Efectivo" por viaje y una sección "Movimientos" con recargas y cobros.
- Reglas de Firestore: `wallets` y `wallets/*/movements` legibles solo por su dueño; `nfcCards` sin acceso de clientes.

**E. Pantallas**

- Pasajero, pestaña "Pagar": icono NFC con "Acerca tu celular al del conductor", saldo de demostración con aviso, botón "Recargar (demo)", y debajo el QR como alternativa. Sección "Mis tarjetas".
- Operador, escaneo: animación NFC, "Acerca el celular o la tarjeta del pasajero", botón "Usar cámara (QR)", y en la lista a bordo el botón "Pagado en efectivo".
- Resumen del viaje: fila "Pagado con: Saldo de demostración" y "Saldo restante".

**F. Manifiesto y permisos**

- Permiso `NFC`. `uses-feature` de `android.hardware.nfc` y `android.hardware.nfc.hce` con `required="false"` (la app se instala aunque no haya NFC).
- Servicio HCE con `BIND_NFC_SERVICE`, categoría `other` y archivo `apduservice.xml` con el AID.

### 2. Lo que haces tú

1. Guarda el secreto: `firebase functions:secrets:set NFC_PEPPER` (cadena aleatoria larga).
2. Necesitas **dos celulares físicos con NFC** (el emulador no sirve para NFC). Para la tarjeta física, compra 2 o 3 tarjetas o etiquetas NTAG213/215 o MIFARE Ultralight.
3. Para probar en emulador solo se prueba la lógica del servidor y del saldo.
4. Define con tu grupo los montos de la demo si quieres cambiar los de arriba.

### 3. Prompt para OpenCode

```
Implementa la FUNCIONALIDAD 8 (Pago por contacto NFC con saldo de demostración) siguiendo AGENTS.md
y lo ya construido en las funcionalidades 1 a 7. Esta funcionalidad es la ÚNICA excepción a "nada
simulado": el dinero es de demostración, pero el NFC, la lectura de tarjetas y el cobro del saldo
deben ser reales. Un commit por paso.

PASO 1 - Billetera de demostración (TypeScript) con pruebas
- functions/src/wallet: funciones puras y transaccionales para topup, hold, release, charge y
  refund en céntimos enteros, con registro de movimientos. DEMO_PAYMENTS como bandera de entorno
  (si es false, topUpDemo lanza failed-precondition).
- Reglas de negocio de la especificación: montos de recarga permitidos, saldo máximo, límite de
  recargas por día, retención igual al techo de seguridad (`maxFareCents`) en el Tap In, cobro y liberación en el Tap Out.
- Pruebas: recarga válida e inválida; recarga sobre el máximo; DEMO_PAYMENTS=false; dos Tap In
  simultáneos con saldo para uno solo (solo uno crea viaje y retención: prueba de concurrencia);
  Tap Out cobra el total y libera la diferencia; el saldo nunca queda negativo; los movimientos
  suman exactamente el saldo.

PASO 2 - Integración con viajes y reclamos
- tapIn: exige saldo disponible y crea la retención en la misma transacción del viaje.
- tapOut: cobra, libera y escribe paidWith y balanceAfterCents en el desglose.
- Marcar "Pagado en efectivo" (operador): nueva callable, sin retención ni cobro.
- Reclamos (funcionalidad 7): al resolver, cobra el total y libera el resto; a los 7 días sin
  reclamo, libera la retención. Actualiza las pruebas de la funcionalidad 7.
- Reglas de Firestore para wallets y movements (solo lectura del dueño) con pruebas permitidas y
  denegadas.

PASO 3 - Tarjetas NFC
- Callables linkNfcCard, unlinkNfcCard, blockNfcCard, listNfcCards. HMAC del UID con NFC_PEPPER
  (defineSecret). Máximo 2 tarjetas por cuenta, una tarjeta en una sola cuenta, el UID debe ser
  hexadecimal de 4 a 10 bytes. Respuestas que no revelan el UID completo.
- tapIn y tapOut aceptan nfcUidHex además de qrToken; rechazan tarjetas bloqueadas o desconocidas
  con mensajes genéricos; cada Tap In con tarjeta envía una notificación FCM al pasajero.
- Pruebas en emulador: vincular, tarjeta ya usada por otra cuenta, tercera tarjeta, bloqueo, Tap In
  con tarjeta válida, con tarjeta bloqueada y con UID inexistente.

PASO 4 - App del pasajero (HCE)
- Servicio HostApduService con el AID definido (F0 + ASCII "PASAJEYA"; calcula los bytes bien y
  déjalos en una constante probada), apduservice.xml, permisos y uses-feature con required=false.
- Responde al SELECT con el token vigente más 90 00; sin token vigente responde un estado de error.
  Verifica en una prueba que el token cabe en una sola APDU (máximo 240 bytes) y, si no cabe,
  acorta el formato en el servidor y en la app con las pruebas actualizadas.
- Pantalla "Pagar": icono NFC, saldo de demostración con aviso, botón "Recargar (demo)", QR
  alternativo, aviso y botón a ajustes cuando el NFC está apagado. "Mis tarjetas": vincular
  (lectura del UID en modo lector), listar, bloquear y desvincular.

PASO 5 - App del operador (lector)
- En la pantalla de escaneo: enableReaderMode con NFC-A, NFC-B y SKIP_NDEF_CHECK; si es IsoDep,
  envía SELECT del AID y lee la respuesta; si es una etiqueta, usa el UID. Antirrebote de 3 s,
  vibración, tarjeta verde, aviso de NFC apagado con botón a ajustes, y botón "Usar cámara (QR)".
- Respeta el ciclo de vida (activar el modo lector en onResume y desactivarlo en onPause).
- Botón "Pagado en efectivo" en la lista a bordo.
- Conecta el aviso de voz (funcionalidad 5) al Tap In por NFC.

PASO 6 - Historial y resumen
- Etiquetas "Saldo" o "Efectivo" por viaje y sección "Movimientos" en el historial. Resumen del
  viaje con "Pagado con" y "Saldo restante". Todos los textos de saldo llevan la leyenda "Saldo de
  demostración: no es dinero real".

PASO 7 - Pruebas de la app
- Unitarias: construcción y análisis de APDU (SELECT correcto, respuesta con 90 00, estado de
  error, token ausente), HostApduService probado llamando a processCommandApdu directamente (usa
  Robolectric si hace falta), ViewModels de "Pagar" y "Mis tarjetas" (Turbine), antirrebote del
  lector.
- Compose UI: aviso con NFC apagado, saldo con la leyenda de demostración, botón "Recargar (demo)"
  deshabilitado cuando se alcanza el saldo máximo.

PASO 8 - Documentación
- docs/pruebas-manuales-F8.md con la tabla de abajo.
- LIMITACIONES.md: pago simulado por diseño (bandera DEMO_PAYMENTS), tarjeta física clonable,
  HCE solo en Android con NFC y pantalla encendida, requiere dos celulares con NFC para probar.

Al final ejecuta ./gradlew testDebugUnitTest, npm run emulators:test y ./gradlew assembleDebug, y
muéstrame los resultados.
```

### 4. Pruebas manuales (dos celulares con NFC)

| ID | Pasos | Resultado esperado |
|---|---|---|
| F8-M01 | Pasajero abre "Pagar" y recarga S/ 10 (demo) | El saldo muestra S/ 10,00 con la leyenda "no es dinero real" |
| F8-M02 | Operador con turno activo acerca su celular al del pasajero | Tap In registrado, vibración y aviso de voz |
| F8-M03 | Mientras viaja, el pasajero mira su saldo | Se ve la retención aplicada |
| F8-M04 | Operador hace Tap Out acercando de nuevo los celulares | El resumen muestra el importe, "Pagado con: Saldo de demostración" y el saldo restante |
| F8-M05 | Pasajero con saldo menor que el techo de seguridad (`maxFareCents`) intenta Tap In | Rechazado: "Saldo de demostración insuficiente" |
| F8-M06 | Pasajero con NFC apagado abre "Pagar" | Aviso con botón a ajustes de NFC |
| F8-M07 | Vincular una tarjeta NFC física desde "Mis tarjetas" | La tarjeta aparece con sus últimos 4 dígitos |
| F8-M08 | Operador acerca la tarjeta física | Tap In correcto y notificación al pasajero |
| F8-M09 | Bloquear la tarjeta y acercarla otra vez | Rechazada |
| F8-M10 | Intentar vincular la misma tarjeta desde otra cuenta | Rechazado sin revelar quién la usa |
| F8-M11 | Operador marca un viaje como "Pagado en efectivo" | El viaje se cierra sin descontar saldo y el historial muestra "Efectivo" |
| F8-M12 | Pasajero con iPhone o sin NFC | Puede viajar con el QR |
| F8-M13 | Revisar el historial | Aparecen recargas y cobros que suman exactamente el saldo |

### 5. Criterio de terminado

- Pruebas automáticas en verde, incluida la de concurrencia de saldo y la de tamaño del APDU.
- Probado con dos celulares NFC reales: Tap In y Tap Out con celular, y con tarjeta física.
- `LIMITACIONES.md` explica que el pago es de demostración y por qué.

---

## 13. Cierre — Prueba de punta a punta, endurecimiento y entrega

### 13.1 Qué debe hacer

1. **Prueba de punta a punta automática** en emuladores con el recorrido completo del informe: registrar pasajero y operador, verificar correos, verificar beneficio universitario, iniciar turno, Tap In, grabar ruta, Tap Out, tarifa con desglose, historial, viaje sin Tap Out, reclamo y resolución.
2. **Endurecimiento de seguridad:** Firebase **App Check** con Play Integrity (en la app y exigido en las funciones con `enforceAppCheck: true`), revisión de reglas de Firestore, límites de frecuencia en funciones sensibles y auditoría de dependencias.
3. **Auditoría de "nada simulado"** sobre el código.
4. **Compilación de release** firmada, con R8 activado, y lista de entrega.

### 13.2 Lo que haces tú

1. En la consola de Firebase, registra App Check con **Play Integrity** para la app y registra el SHA-256 de la clave de firma de release.
2. Crea tu keystore de release y guárdalo fuera del repositorio.
3. Despliega: `firebase deploy --only firestore:rules,firestore:indexes,functions`.
4. Carga las rutas reales con el script `seedRoutes` y publica la primera versión de tarifa con `publishFareVersion`.

### 13.3 Prompt para OpenCode

```
Haz el CIERRE del proyecto siguiendo AGENTS.md. Un commit por paso.

PASO 1 - Prueba de punta a punta
- Script o prueba instrumentada que, contra los emuladores, ejecute el recorrido completo: registro
  de pasajero y operador, verificación de correo, beneficio universitario (con el transporte de
  correo de prueba), turno, Tap In, lotes de GPS sembrados SOLO en la prueba, Tap Out con tarifa y
  beneficio, historial con suma agregada, viaje sin Tap Out, reclamo con resolución automática y
  con resolución por operador. Debe fallar si cualquier importe difiere de los vectores esperados.

PASO 2 - Endurecimiento
- App Check con Play Integrity en la app (proveedor de depuración solo en debug) y enforceAppCheck
  en todas las funciones callable.
- Revisa las reglas de Firestore: cada colección tiene prueba de caso permitido y de caso denegado.
  Agrega un test que recorra firestore.rules y falle si existe algún "allow ... if true".
- Límites de frecuencia en funciones sensibles (registro, envío de código, creación de reclamos) con
  contadores en Firestore con TTL.
- npm audit y revisión de dependencias de Gradle; actualiza lo que tenga vulnerabilidades conocidas.

PASO 3 - Auditoría automática
- Un script npm run audit:code que falle si encuentra en código de producción: "mock", "fake",
  "simulad", "TODO", "FIXME", datos de ejemplo cargados a mano, la palabra "dni" dentro de logs,
  mensajes de voz o respuestas a clientes, claves o contraseñas escritas en el código.
- Revisa los logs de Functions de una corrida completa y confirma que no hay DNI, correo completo
  ni coordenadas exactas.

PASO 4 - Release
- Configura el firmado de release con variables de entorno o local.properties (nunca en git),
  activa R8/ProGuard con reglas para Firebase, ML Kit y Hilt, y genera ./gradlew assembleRelease.
- Confirma que el release apunta a Firebase real y no a emuladores.

PASO 5 - Documentación final
- docs/ENTREGA.md: cómo instalar, cómo desplegar, cómo publicar una versión de tarifa, cómo cargar
  rutas, cómo correr todas las pruebas, y la lista de limitaciones (resumen de LIMITACIONES.md).
- docs/MATRIZ_PRUEBAS.md: para cada una de las 7 funcionalidades, la lista de pruebas automáticas
  y manuales con su resultado.
- Actualiza docs/PROGRESO.md con el estado final de las 7 funcionalidades.

Al final ejecuta ./gradlew testDebugUnitTest, npm run emulators:test, npm run audit:code y
./gradlew assembleRelease, y muéstrame los resultados.
```

### 13.4 Criterio de terminado

- Prueba de punta a punta en verde y auditoría de código sin hallazgos.
- Release instalada en un celular real funcionando contra Firebase real.
- Capturas reales de las 7 funcionalidades tomadas de esa release (para reemplazar las maquetas del informe 6).

---
