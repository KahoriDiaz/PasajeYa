# Estado — PasajeYa!

Actualizado: 2026-10-26.

## Funcionalidades

| ID | Estado automático | Validación en celular real |
|---|---|---|
| F1 | Completado en emulador | Pendiente |
| F2 | Completado en emulador | Pendiente |
| F3–F7 | Pendientes | — |

## Verificado automáticamente

- `.\gradlew assembleDebug` compila.
- `.\gradlew testDebugUnitTest` pasa con 22 pruebas.
- `npm run emulators:test` pasa con 42 pruebas de Functions y las pruebas de reglas de
  Firestore.
- F1: registro de pasajero y operador, validaciones, sesión única, persistencia y reglas.
- F2: GPS, token QR, turnos, Tap In/Tap Out, concurrencia, viajes sin cierre y reglas.
- La app debug usa los emuladores de Firebase; en release se usa el proyecto real.

## Pendiente de probar en celular real

- Cámara y permisos: escaneo del QR del pasajero.
- GPS real: precisión, túneles, interiores, batería y servicio en segundo plano.
- Notificaciones: permiso y visibilidad de la notificación del turno.
- Dos celulares simultáneos: operador y pasajero.
- Voz (F5), NFC (F8), notificaciones push de F5/F7 y correo real de F4.
- Pruebas manuales: `docs/pruebas-manuales-F1.md` y `docs/pruebas-manuales-F2.md`.

## Datos y secretos que faltan

- Rutas reales en `data/routes.json` y tarifas oficiales en `data/official-fares.json`.
- Muestra de carné universitario para calibrar el OCR de F4.
- Secretos reales en Secret Manager: `DNI_PEPPER`, `QR_SECRET`, `NFC_PEPPER`,
  `CARNE_PEPPER`, `SMTP_*`.
- Configuración SMTP y dominio de correo.
- Deploy de reglas, índices y Function subsequent a la validación.

## Limitaciones conocidas

- Cloud Vision no tiene emulador; el OCR de F4 se probará con el carné real.
- El programador de `closeStaleTrips` no se ejecuta en el emulador sin Pub/Sub; la prueba
  invoca el handler directamente.
- Las tarifas de F3 son provisionales hasta cargar los valores oficiales.
- La validación en dispositivo real de F1 y F2 queda pendiente.
