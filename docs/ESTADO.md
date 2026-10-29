# Estado — PasajeYa!

Actualizado: 2026-10-29.

## Funcionalidades

| ID | Estado automático | Validación en celular real |
|---|---|---|
| F1 | Completado en emulador | Pendiente |
| F2 | Completado en emulador | Pendiente |
| F5 | Completado en emulador | Pendiente |
| F3–F4, F6–F7 | Pendientes | — |

## Verificado automáticamente

- `.\gradlew assembleDebug` compila.
- `.\gradlew testDebugUnitTest` pasa con 36 pruebas.
- `npm run emulators:test` pasa con 74 pruebas de Functions y 16 pruebas de reglas de
  Firestore.
- F1: registro de pasajero y operador, validaciones, sesión única, persistencia y reglas.
- F2: GPS, token QR, turnos, Tap In/Tap Out, concurrencia, viajes sin cierre y reglas.
- F5: formateo del aviso, TextToSpeech en español, preferencia local, cola de avisos,
  respuesta mínima de `tapIn` y pruebas estáticas de privacidad.
- La app debug usa los emuladores de Firebase; en release se usa el proyecto real.

## Pendiente de probar en celular real

- Cámara y permisos: escaneo del QR del pasajero.
- GPS real: precisión, túneles, interiores, batería y servicio en segundo plano.
- Notificaciones: permiso y visibilidad de la notificación del turno.
- Dos celulares simultáneos: operador y pasajero.
- Voz (F5): motor TTS, voz en español, volumen, cola de dos avisos y accesibilidad.
- Notificaciones push de F7 y correo real de F4.
- Pruebas manuales: `docs/pruebas-manuales-F1.md`, `docs/pruebas-manuales-F2.md` y
  `docs/pruebas-manuales-F5.md`.

## Datos y secretos que faltan

- Rutas reales en `data/routes.json` y tarifas oficiales en `data/official-fares.json`.
- Muestra de carné universitario para calibrar el OCR de F4.
- Secretos reales en Secret Manager: `DNI_PEPPER`, `QR_SECRET`, `NFC_PEPPER`,
  `CARNE_PEPPER`, `SMTP_*`.
- Configuración SMTP y dominio de correo.
- Deploy de reglas, índices y Functions posterior a la validación.

## Limitaciones conocidas

- Cloud Vision no tiene emulador; el OCR de F4 se probará con el carné real.
- El programador de `closeStaleTrips` no se ejecuta en el emulador sin Pub/Sub; la prueba
  invoca el handler directamente.
- Las tarifas de F3 son provisionales hasta cargar los valores oficiales.
- La voz depende de un motor TextToSpeech y de datos en español instalados en el celular;
  si faltan, la app mantiene el aviso visual y ofrece instalarlos.
- La validación en dispositivo real de F1, F2 y F5 queda pendiente.
