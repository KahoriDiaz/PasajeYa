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
