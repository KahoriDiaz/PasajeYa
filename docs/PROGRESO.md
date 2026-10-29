# Progreso — PasajeYa!

Estado de las 7 funcionalidades. Se actualiza al terminar cada paso.

| ID | Funcionalidad | Estado |
|---|---|---|
| F1 | Autenticación y cuentas (pasajero/operador, DNI, contraseña segura, consentimiento, sesión única) | Completado en emulador; pendiente celular real |
| F2 | Tap In / Tap Out mediante QR | Completado en emulador; pendiente celular real |
| F3 | Cálculo de tarifa (distancia GPS, versión tarifaria, redondeo, mínimo/máximo, desglose) | Pendiente |
| F4 | Beneficio universitario del 50 % | Completado |
| F8 | Cobro y pago con NFC offline | Completado |
| F5 | Voz del operador (nombre y edad, nunca DNI) | Completado en emulador; pendiente celular real |
| F6 | Historial de viajes | Completado |
| F7 | Reclamos por viajes sin Tap Out | Pendiente |
| F8 | Cobro y pago con NFC offline | Completado |

## Infraestructura

- [x] Proyecto Android configurado con Gradle 9.3.1, AGP 9.1.0, Compose y Hilt.
- [x] Firebase Emulator Suite configurado para Auth, Firestore y Functions.
- [x] Reglas de Firestore con denegación por defecto y pruebas de reglas.
- [x] Cloud Functions v2 en TypeScript con función de salud y pruebas.
- [x] Vectores compartidos y documentación inicial de desarrollo.

## Criterios de terminado (por funcionalidad)

- [x] Compila.
- [x] Pasan las pruebas unitarias.
- [x] Pasan las pruebas de reglas y de Functions en emulador.
- [x] Existe `docs/pruebas-manuales-F<N>.md` con casos para celular real.

F1, F2 y F5 cumplen los cuatro criterios. La ejecución en celular real queda pendiente y
sus casos están en `docs/pruebas-manuales-F1.md`, `docs/pruebas-manuales-F2.md` y
`docs/pruebas-manuales-F5.md`; las limitaciones conocidas se registran en
`docs/LIMITACIONES.md` y el estado general en `docs/ESTADO.md`.

## Bitácora

### 2026-10-26 — Configuración inicial

- Se creó la base de la app con Material 3, Hilt, Navigation Compose y una pantalla
  temporal.
- Se configuró `BuildConfig` para usar los emuladores de Firebase solo en debug.
- Se crearon las carpetas de las funcionalidades F1–F7 con sus capas `data`, `domain` y
  `ui`.
- Se agregaron reglas Firestore que deniegan todo, la función `health` en
  `southamerica-east1` y pruebas de reglas/Functions con Vitest.
- Verificación ejecutada:
  - `.\gradlew assembleDebug` — correcto.
  - `.\gradlew testDebugUnitTest` — correcto (3 pruebas).
  - `npm run emulators:test` — correcto (3 pruebas de reglas y 2 de Functions).

### 2026-10-26 — F1, paso 1: validadores compartidos

- Se agregaron los vectores de correo, contraseña, DNI, nombres, fecha de nacimiento,
  placa, licencia y clase de licencia en `shared/validation-vectors.json`.
- Se agregó `shared/common-passwords.json` con más de 100 contraseñas comunes.
- Se implementaron los validadores equivalentes en Kotlin y TypeScript, ambos probados
  contra los mismos vectores.
- Las pruebas de ambos lenguajes pasan.

### 2026-10-26 — F1, paso 2: Cloud Functions

- Se implementaron `completeProfile`, `registerOperator` y `registerDevice` con
  transacciones, `sessionId` y HMAC-SHA256 del DNI.
- Se agregó `assertActiveSession` para las siguientes funcionalidades.
- Se agregó la configuración de placas, licencias y clases del MTC, la interfaz
  `OperatorVerifier` y el script `seedRoutes`.
- `data/routes.json` queda vacío a la espera de las rutas reales; no se inventaron datos.
- El secreto de emulador se genera localmente en `functions/.secret.local` y no se sube.

### 2026-10-26 — F1, paso 3: reglas de Firestore

- `users/{uid}` y `sessions/{uid}` son legibles solo por su dueño y nunca escribibles
  por el cliente.
- `routes` es legible por usuarios autenticados y solo escribible por el servidor.
- `users_private`, `dniIndex`, `operators`, `vehicles` y `benefitRequests` no tienen
  acceso desde clientes.
- Todo lo demás queda denegado por defecto.

### 2026-10-26 — F1, paso 4: pruebas de reglas y Functions

- Las reglas Firestore tienen casos permitidos y denegados para cada colección de F1.
- Las Functions se prueban contra emuladores reales de Auth, Firestore y Functions.
- Se cubren DNI duplicado, concurrencia, idempotencia, sesión anterior, placas duplicadas,
  correo sin verificar, roles y datos inválidos.
- Se inicializa explícitamente la app Admin en las Functions y se mantienen las pruebas
  unitarias rápidas fuera del emulador.

### 2026-10-26 — F1, paso 5: datos y dominio Android

- Se agregaron repositorios Firebase para autenticación, perfil, sesión, rutas y Functions.
- El identificador de dispositivo se genera una vez y se guarda en DataStore; la sesión
  activa se mantiene en memoria y se renueva al iniciar el proceso.
- La lista de contraseñas comunes se lee del asset compartido y se cachea.
- Se agregaron modelos, errores de dominio, resolución de destino y bloqueo local tras
  cinco intentos fallidos en treinta segundos.

### 2026-10-26 — F1, paso 6: pantallas y validación manual

- Se implementaron las pantallas Compose de bienvenida, login, registro, verificación de
  correo, selección de rol, datos de pasajero, datos de operador, detalles del operador,
  inicios de pasajero/operador, recuperación de contraseña y política de privacidad.
- `PasajeYaApp` y `AppViewModel` resuelven el destino inicial, restauran la sesión,
  registran el dispositivo y muestran el diálogo de sesión reemplazada.
- Se agregó el logo en `design/logo.png` y `app/src/main/res/drawable-nodpi/`.
- Se corrigió un bug de autoformato de fecha: al escribir rápido, el cursor se movía y
  generaba fechas mezcladas. El campo usa `TextFieldValue` y mantiene la selección al
  final.
- Verificación ejecutada:
  - `.\gradlew assembleDebug` — correcto.
  - `.\gradlew testDebugUnitTest` — correcto.
  - `npm run emulators:test` — correcto (8 pruebas de reglas y 22 de Functions).
- Validación manual en emulador:
  - Pasajero: registro, verificación, perfil y home correctos.
  - Operador: registro, licencia/placa/ruta, estado pendiente y home correctos.
  - Sesión única: segundo `registerDevice` reemplazó la sesión y mostró el diálogo exacto.
  - Login incorrecto: mensaje genérico correcto; login posterior correcto.
  - Persistencia: la sesión se restauró tras reiniciar el proceso.
  - Datos: DNI solo en `users_private`, índice HMAC único, sin DNI en `users`; vehículo y
    operador escritos por el servidor.
- La ruta `routes/TEST-01` se creó solo en el emulador para la prueba de operador; no se
  versiona. Las limitaciones y los casos para celular real quedan documentados.

### 2026-10-26 — F2, paso 1: lógica de GPS y token QR

- Se agregó el cálculo de distancia con Haversine, filtros de precisión (30 m), salto
  máximo (500 m) y velocidad máxima (33 m/s).
- El cálculo exige al menos 3 puntos y 60 % de cobertura; si no se cumple, el viaje queda
  para revisión con `insufficient_gps`.
- Se agregó la emisión y verificación de tokens QR firmados con HMAC-SHA256, TTL de 60 s y
  nonce de un solo uso.
- Las pruebas unitarias de distancia y token pasan.

### 2026-10-26 — F2, paso 2: Cloud Functions y reglas

- Se implementaron `issueQrToken`, `startShift`, `endShift`, `getOperatorContext`,
  `tapIn`, `tapOut` y `closeStaleTrips`.
- Un operador solo puede tener un turno activo; un vehículo solo puede tener un turno
  activo; un pasajero solo puede tener un viaje abierto.
- `tapIn` valida el token, la sesión y el turno; `tapOut` cierra el viaje y exige que el
  viaje pertenezca al operador y turno activos.
- `endShift` deja los viajes abiertos en `requires_review` con `shift_ended`;
  `closeStaleTrips` corre cada 15 minutos y usa `no_tap_out` para viajes de más de 3 horas.
- El cliente puede crear chunks GPS de su turno activo, pero no editarlos ni borrarlos.
  `qrNonces` y `shiftLocks` son inaccesibles desde clientes.
- Se agregaron índices de Firestore para turnos y viajes.
- Las 15 pruebas de reglas y las 42 pruebas de Functions en emulador pasan.

### 2026-10-26 — F2, paso 3: app Android

- El pasajero ve su QR, la cuenta regresiva, el aviso offline y el viaje en curso.
- El operador inicia y termina turno, escanea QR con CameraX + ML Kit, ve la lista de
  pasajeros a bordo y puede hacer Tap Out.
- El servicio en primer plano recolecta GPS cada 5 s (15 s si está detenido), envía chunks
  de hasta 30 puntos cada 30 s y se detiene al terminar el turno.
- Se añadieron permisos de cámara, ubicación y notificaciones, y las pantallas de error,
  carga y vacío correspondientes.
- Verificación ejecutada:
  - `.\gradlew assembleDebug` — correcto.
  - `.\gradlew testDebugUnitTest` — correcto (22 pruebas).
  - `npm run emulators:test` — correcto (42 pruebas de Functions; reglas incluidas).
- La validación en dos celulares reales queda pendiente en
  `docs/pruebas-manuales-F2.md`.

### 2026-10-29 — Compatibilidad con Android Studio (AGP 9.1.0)

- Se cambió AGP de 9.4.1 a 9.1.0 y el wrapper de Gradle de 9.8.2 a 9.3.1, versión
  mínima y por defecto que exigen las notas oficiales de AGP 9.1.
- Se actualizó `AGENTS.md` para no subir AGP, Gradle ni Kotlin sin consultar y mantener
  el AGP máximo en 9.1.0.
- Se redondeó a metros enteros la distancia de `tapOut` antes del cálculo tarifario, como
  ya hacía `estimateFare`, porque `computeFare` exige enteros.
- Verificación ejecutada:
  - `.\gradlew assembleDebug` — correcto (aviso de compileSdk 37 no probado con AGP 9.1.0).
  - `.\gradlew testDebugUnitTest` — correcto.
  - `npm run emulators:test` — correcto (15 pruebas de reglas y 72 de Functions).

### 2026-10-29 — Arranque robusto del emulador de Functions

- El emulador de Functions fallaba de forma intermitente al descubrir los triggers con
  `Cannot determine backend specification. Timeout after 10000`, lo que hacía que las
  callables respondieran 404.
- Se agregó un lanzador común que define `FUNCTIONS_DISCOVERY_TIMEOUT=60` y
  `FUNCTIONS_EMULATOR_TIMEOUT_SECONDS=60` antes de iniciar Firebase Emulator Suite.
  La variable relevante para el descubrimiento es `FUNCTIONS_DISCOVERY_TIMEOUT`.
- `emulators:start`, `test:functions` y `emulators:test` usan el lanzador; el runner de
  pruebas también aplica los mismos valores a los procesos hijos.
- Verificación ejecutada: `npm run emulators:test` — correcto (15 pruebas de reglas y
  72 pruebas de Functions).

### 2026-10-29 — F5, paso 1: formateador del aviso de voz

- Se agregó `VoiceAnnouncementFormatter` en `feature/voice/domain`.
- Construye el texto con el primer nombre y la edad: `"Ana, 21 años"` y `"Ana, 1 año"`.
- Sanea números y símbolos, conserva letras con tilde y ñ, aplica mayúscula inicial y
  recorta el nombre a 20 caracteres.
- Si el nombre queda vacío, devuelve `null` para que el ViewModel muestre el aviso visual
  sin reproducir voz.
- Verificación ejecutada: `.\gradlew testDebugUnitTest` — correcto.

### 2026-10-29 — F5, paso 2: servicio de voz y preferencia

- Se agregó la interfaz `SpeechAnnouncer` y la implementación `AndroidSpeechAnnouncer`
  sobre `TextToSpeech`, con inicialización asíncrona y tiempo máximo de espera.
- Selecciona `es-PE`, con respaldo `es-US` y `es-ES`; devuelve `false` si el motor no
  tiene voz disponible o falla la inicialización.
- Usa `QUEUE_ADD` para respetar el orden de dos Tap In seguidos, el canal de medios del
  sistema y libera el motor con `release()`.
- Se agregó la preferencia `voice_enabled` en DataStore, activada por defecto.
- Verificación ejecutada: `.\gradlew compileDebugKotlin` y
  `.\gradlew testDebugUnitTest` — correctos.

### 2026-10-29 — F5, paso 3: integración en el flujo del operador

- Tras un Tap In correcto se anuncia una vez el nombre y la edad con la preferencia activa.
- La tarjeta grande con nombre y edad permanece visible durante 4 segundos.
- Se agregó la tarjeta de ajustes con el interruptor de voz y el botón “Probar voz”.
- Si el motor no tiene datos de voz, se muestra un aviso con el botón para instalar los
  datos; si el nombre o la edad no permiten formar el texto, se muestra un aviso visual.
- Al salir del ViewModel se liberan los recursos de `TextToSpeech`.
- Verificación ejecutada: `.\gradlew testDebugUnitTest` — correcto.

### 2026-10-29 — F5, paso 4: pruebas de voz y privacidad

- Se agregaron pruebas del ViewModel para el anuncio único por Tap In, la cola ordenada de
  dos pasajeros, la preferencia apagada y el aviso cuando el motor no está disponible.
- Se agregó una prueba de Functions que exige exactamente las claves `age`, `firstName` y
  `tripId` en la respuesta de `tapIn`.
- Se agregó una prueba de reglas que impide a un operador leer el perfil público y privado
  de un pasajero.
- Se agregó una prueba estática que revisa logs de la app, textos y llamadas de voz, y
  logs/objetos retornados por Functions en busca del DNI.
- Verificación ejecutada:
  - `.\gradlew testDebugUnitTest` — correcto (36 pruebas).
  - `npm run emulators:test` — correcto (16 pruebas de reglas y 74 de Functions).

### 2026-10-29 — F5, paso 5: pruebas manuales y cierre

- Se agregó `docs/pruebas-manuales-F5.md` con casos para anuncio en Tap In, dos pasajeros
  consecutivos, preferencia apagada, botón de prueba, motor de voz no disponible, volumen
  e idioma, privacidad y accesibilidad.
- El documento exige revisar que la pantalla, la voz, los logs y la respuesta de `tapIn`
  no expongan el DNI.
- Verificación ejecutada:
  - `.\gradlew assembleDebug` — correcto (aviso de compileSdk 37 no probado con AGP 9.1.0).
  - `.\gradlew testDebugUnitTest` — correcto (36 pruebas).
  - `npm run emulators:test` — correcto (16 pruebas de reglas y 74 de Functions).
- La validación en un celular real queda pendiente en `docs/pruebas-manuales-F5.md`.

### 2026-10-29 — F6, paso 1: índices, consultas y rangos

- Se agregaron los índices de `trips` para las consultas del pasajero por fecha y estado y
  del operador por turno, además del índice de turnos por operador y fecha.
- Se agregó `HistoryRepository` con página de 20 viajes, cursor por fecha e id, filtro por
  semana/mes/todo y suma agregada de viajes cerrados.
- La semana y el mes se calculan en `America/Lima` con límites exclusivos.
- Se agregó la prueba de rangos para cruce de mes, año nuevo y cambio de domingo a lunes.
- Verificación ejecutada: `.\gradlew testDebugUnitTest --tests
  "com.pasajeya.app.feature.history.HistoryDateRangesTest"` — correcto.

### 2026-10-29 — F6, paso 2: reglas y datos en emulador

- Se agregaron pruebas con 45 viajes históricos y 40 cerrados.
- Se verificó que el pasajero solo lee sus viajes y que el operador solo lee los viajes de
  su turno; las consultas ajenas y todas las escrituras directas son denegadas.
- Se verificó la paginación de 20, 20 y 5 viajes sin duplicados ni faltantes.
- Se verificó que `sum(fareBreakdown.totalCents)` coincide con la suma manual de los viajes
  cerrados y excluye los viajes en revisión.
- Verificación ejecutada: `npm run test:rules` — correcto (21 pruebas).

### 2026-10-29 — F6, paso 3: historial del pasajero

- Se agregó la pantalla “Mi historial” con filtros de semana, mes y todo.
- El gasto acumulado se lee de la consulta de agregación; no se suma en el celular.
- La lista muestra fecha y hora en zona de Lima, ruta, distancia, importe, beneficio y el
  estado ámbar “En revisión” con “Importe pendiente”.
- Se agregó paginación al llegar al final, estados de carga, vacío, error con reintento y
  aviso sin conexión.
- Cada tarjeta abre el “Resumen del viaje” existente en modo solo lectura.
- Se agregó el acceso “Mi historial” en la pantalla del pasajero y su ruta de navegación.
- Verificación ejecutada: `.\gradlew compileDebugKotlin` — correcto.

### 2026-10-29 — F6, paso 4: viajes del turno del operador

- Se agregó la sección de solo lectura “Mis viajes del turno” con la cantidad de viajes
  cerrados, en revisión y el total cobrado.
- Usa el turno activo o, si no hay uno, el último turno del operador.
- La sección se actualiza al iniciar o terminar el turno y al cerrar un viaje.
- Tiene estados de carga, vacío y error con reintento.
- Verificación ejecutada: `.\gradlew compileDebugKotlin` — correcto.

### 2026-10-29 � F6, cierre
- Se completaron todos los pasos de la Funcionalidad?6.
- Se gener� docs/pruebas-manuales-F6.md con los casos de prueba manuales.
- Se ejecutaron todas las pruebas unitarias y de reglas y obtuvieron �xito.
- Se marc� F6 como **Completado** en docs/PROGRESO.md.

F 7 :   C o m p l e t a d o 
 
 
