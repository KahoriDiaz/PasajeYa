# Progreso — PasajeYa!

Estado de las 7 funcionalidades. Se actualiza al terminar cada paso.

| ID | Funcionalidad | Estado |
|---|---|---|
| F1 | Autenticación y cuentas (pasajero/operador, DNI, contraseña segura, consentimiento, sesión única) | Pendiente |
| F2 | Tap In / Tap Out mediante QR | Pendiente |
| F3 | Cálculo de tarifa (distancia GPS, versión tarifaria, redondeo, mínimo/máximo, desglose) | Pendiente |
| F4 | Beneficio universitario del 50 % | Pendiente |
| F5 | Voz del operador (nombre y edad, nunca DNI) | Pendiente |
| F6 | Historial de viajes | Pendiente |
| F7 | Reclamos por viajes sin Tap Out | Pendiente |

## Infraestructura

- [x] Proyecto Android configurado con Gradle 9.8.2, AGP 9.4.1, Compose y Hilt.
- [x] Firebase Emulator Suite configurado para Auth, Firestore y Functions.
- [x] Reglas de Firestore con denegación por defecto y pruebas de reglas.
- [x] Cloud Functions v2 en TypeScript con función de salud y pruebas.
- [x] Vectores compartidos y documentación inicial de desarrollo.

## Criterios de terminado (por funcionalidad)

- [ ] Compila.
- [ ] Pasan las pruebas unitarias.
- [ ] Pasan las pruebas de reglas y de Functions en emulador.
- [ ] Existe `docs/pruebas-manuales-F<N>.md` con casos para celular real.

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
