# Pruebas manuales — F2 Tap In / Tap Out mediante QR

Casos para validar F2 en dos celulares reales con Google Play Services y GPS. Usa datos
sintéticos y una ruta de prueba creada por el script de administración; no inventes rutas
ni uses un DNI real.

## Preparación

1. Compila e instala la app debug en ambos celulares:

   ```powershell
   .\gradlew assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

2. Levanta los emuladores y confirma que la app debug apunta a ellos:

   ```powershell
   npm run emulators:start
   ```

3. Crea una ruta activa con el script de administración. En el emulador se puede sembrar
   una ruta de prueba; en producción solo se cargan rutas reales.
4. Registra un operador aprobado y asígnale la ruta. Verifica que `operators/{uid}` tenga
   `status: "approved"` y una placa.
5. Registra un pasajero con correo verificado y perfil completo.
6. Activa ubicación de alta precisión y desactiva la optimización de batería para
   PasajeYa! en el celular del operador.
7. Para revisar datos, usa la consola del emulador o `firebase emulators:start`; no
   publiques capturas con DNI.

## Casos

### F2-01 — QR del pasajero y renovación

1. Inicia sesión como pasajero y abre la pantalla principal.
2. Observa el QR y la cuenta regresiva.
3. Espera sin cerrar la pantalla hasta que la cuenta llegue a cero.

**Resultado esperado:** el QR se muestra nítido, la cuenta regresiva baja desde 60 s y el
QR se renueva automáticamente antes de expirar. No se muestran datos personales ni el DNI
en la pantalla.

### F2-02 — Inicio de turno del operador

1. Inicia sesión como operador aprobado.
2. Toca **Iniciar turno**, elige la ruta asignada y confirma.
3. Acepta los permisos de ubicación y notificaciones si se solicitan.
4. Verifica que aparezca la notificación persistente de ubicación.
5. Intenta iniciar otro turno desde el mismo operador o desde otro operador con la misma
   placa.

**Resultado esperado:** se crea `shifts/{shiftId}` con `status: "active"`, la ruta y la
placa. La notificación de ubicación permanece visible. El segundo intento se rechaza con
un mensaje claro: el operador ya tiene un turno activo o la placa ya está en uso.

### F2-03 — Tap In correcto

1. Con el turno activo, toca **Escanear pasajero**.
2. Apunta la cámara al QR del pasajero.
3. Acepta el permiso de cámara si se solicita.

**Resultado esperado:** se crea `trips/{tripId}` con `status: "open"`, `passengerUid`,
`operatorUid`, `shiftId` y `routeId`. El operador ve al pasajero en la lista **A bordo**.
El pasajero ve la tarjeta de viaje en curso con el nombre de la ruta. No aparece el DNI.

### F2-04 — Tap Out correcto

1. Con un pasajero a bordo, deja que el vehículo avance al menos 100 metros.
2. En la lista **A bordo**, toca el pasajero y confirma **Tap Out**.

**Resultado esperado:** el viaje pasa a `status: "closed"` con `distanceMeters`, `usedPoints`
y `coverage`. El pasajero desaparece de la lista a bordo y el viaje deja de mostrarse como
en curso. En esta etapa la tarifa puede quedar pendiente de F3.

### F2-05 — QR vencido, reutilizado o de otro turno

1. Captura el QR del pasajero y espera más de 60 s.
2. Intenta usarlo con el operador.
3. Genera un QR nuevo, úsalo una vez y vuelve a escanear la misma imagen.
4. Intenta escanear un QR de un pasajero con un turno que ya fue cerrado.

**Resultado esperado:** los tres intentos se rechazan con un mensaje claro. No se crea un
viaje duplicado ni se reutiliza el nonce. El operador puede seguir escaneando.

### F2-06 — Viaje sin Tap Out y cierre de turno

1. Haz Tap In a un pasajero y no hagas Tap Out.
2. Toca **Terminar turno** y confirma.

**Resultado esperado:** el turno queda `ended`, el viaje abierto pasa a
`status: "requires_review"` con `reviewReason: "shift_ended"` y el operador recibe el aviso
de cuántos viajes quedaron para revisión. La ubicación deja de recolectarse al cerrar el
turno.

### F2-07 — GPS insuficiente o sin movimiento

1. Haz Tap In dentro de un lugar cerrado o con el GPS desactivado.
2. Haz Tap Out sin recorrer distancia suficiente o con menos de tres puntos válidos.

**Resultado esperado:** el viaje queda `requires_review` con
`reviewReason: "insufficient_gps"`, `distanceMeters` en 0 y `coverage` menor al mínimo. La
app informa que el viaje será revisado; no inventa una distancia.

### F2-08 — Un solo viaje abierto por pasajero

1. Haz Tap In a un pasajero.
2. Sin hacer Tap Out, intenta hacer Tap In al mismo pasajero en el mismo turno o en otro
   turno activo.

**Resultado esperado:** el segundo Tap In se rechaza; no se crean dos viajes abiertos para
el mismo pasajero.

### F2-09 — Sin conexión

1. Activa modo avión en el celular del pasajero y observa el QR.
2. Desactiva el modo avión y espera unos segundos.
3. Repite la prueba en el celular del operador al intentar escanear o cerrar turno.

**Resultado esperado:** el pasajero ve un aviso offline y la app reintenta sola; al volver
la conexión el QR se renueva. El operador ve un error claro y puede reintentar sin que se
cree un viaje a medias.

### F2-10 — Permisos y servicio en segundo plano

1. Deniega el permiso de cámara y luego habilítalo desde ajustes.
2. Deniega la ubicación y verifica el mensaje de la app.
3. Con el turno activo, apaga la pantalla o cambia a otra app durante cinco minutos.
4. Vuelve a PasajeYa! y revisa el estado del turno y los chunks GPS.

**Resultado esperado:** la app explica cómo habilitar cada permiso y no se cierra. Mientras
el turno está activo, el servicio sigue registrando ubicación con la pantalla apagada y los
chunks se guardan sin ediciones desde el cliente.

### F2-11 — Operador pendiente o sin ruta

1. Inicia sesión con un operador cuyo `status` no sea `approved` o sin rutas asignadas.

**Resultado esperado:** la app muestra el estado pendiente o el mensaje de que no hay rutas
asignadas. No permite iniciar turno ni escanear pasajeros.

## Revisión de datos

En Firestore comprueba:

- `shifts/{shiftId}`: operador, placa, ruta, `startedAt`, `endedAt` y `status`.
- `shifts/{shiftId}/chunks/{chunkId}`: puntos con `lat`, `lng`, `acc` y `t`, sin edición
  posterior.
- `trips/{tripId}`: estado, turno, ruta, pasajero, operador, distancias y revisión.
- `qrNonces/{nonce}`: uso único y `expiresAt`.
- En ningún documento de cliente aparece el DNI.
