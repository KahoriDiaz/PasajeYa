# Progreso — PasajeYa!

Estado de las 7 funcionalidades. Se actualiza al terminar cada paso.

| ID | Funcionalidad | Estado |
|---|---|---|
| F1 | Autenticación y cuentas (pasajero/operador, DNI, contraseña segura, consentimiento, sesión única) | Completado en emulador; pendiente celular real |
| F2 | Tap In / Tap Out mediante QR | Completado en emulador; pendiente celular real |
| F3 | Cálculo de tarifa (distancia GPS, versión tarifaria, redondeo, mínimo/máximo, desglose) | Pendiente |
| F4 | Beneficio universitario del 50 % | Pendiente |
| F5 | Voz del operador (nombre y edad, nunca DNI) | En progreso (paso 1 de 5) |
| F6 | Historial de viajes | Pendiente |
| F7 | Reclamos por viajes sin Tap Out | Pendiente |

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

F1 y F2 cumplen los cuatro criterios. La ejecución en celular real queda pendiente y sus
casos están en `docs/pruebas-manuales-F1.md` y `docs/pruebas-manuales-F2.md`; las
limitaciones conocidas se registran en `docs/LIMITACIONES.md` y el estado general en
`docs/ESTADO.md`.

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
