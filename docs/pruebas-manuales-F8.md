# Pruebas manuales — Funcionalidad 8: Cobro y pago con NFC offline

## 1. Alcance
Validación del registro de Tap In y Tap Out mediante Host Card Emulation (HCE) y almacenamiento de transacciones en cola local cuando no hay conexión.

## 2. Casos de prueba

| ID | Pasos | Resultado esperado |
|---|---|---|
| F8-M01 | Activar NFC en ajustes y abrir la pantalla "Pago NFC" | Se muestra el mensaje "Listo para pagar" y el servicio HCE queda a la escucha |
| F8-M02 | Acercar el dispositivo pasajero al terminal del bus | Se emite el comando APDU y se genera el registro local de la transacción |
| F8-M03 | Realizar pago con el celular en modo avión | La transacción se guarda en la cola local con status "pending" |
| F8-M04 | Desactivar modo avión y conectar a internet | El repositorio sincroniza la cola con Firestore y el status cambia a "synced" |

---

## 3. Flujo de trabajo esperado

### F8-M01: Pantalla inicial
1. Encender el dispositivo Android con la app PasajeYa abierta
2. Verificar que el NFC esté activado en Ajustes > Conexiones > NFC
3. Navegar a la sección "Pago NFC" o iniciar el flujo de Tap In
4. Confirmar que la pantalla muestra: *"Listo para pagar"*
5. Confirmar que el servicio HostApduService está registrado y a la escucha
6. ✅ **Caso aprobado**: Pantalla correcta, HCE listo

### F8-M02: Registro de transacción APDU
1. Desde la pantalla "Pago NFC", acercar la parte trasera del teléfono al terminal del bus (lector NFC)
2. El terminal enviará un comando APDU al dispositivo
3. El servicio HCE responderá con el comando Tap In/Tap Out firmado
4. El sistema generará un registro local de la transacción con:
   - passengerUid (8 dígitos)
   - timestampMs (miliseconds desde epoch)
   - tripId (opcional, identificador del viaje)
   - nonce (token hexadecimal de 16 caracteres)
   - status: "pending" (hasta sincronización)
5. El interfaz mostrará indicador de procesamiento
6. ✅ **Caso aprobado**: Transacción registrada localmente con status pending

### F8-M03: Pago en modo avión
1. Activar el modo avión en el dispositivo Android
2. Desde la pantalla "Pago NFC", acercar el dispositivo al terminal del bus
3. La transacción será procesada sin conexión activa
4. El sistema guardará la transacción en la cola local con:
   - status: "pending"
   - attemptCount: 1
   - timestamp de creación conservado
5. El usuario podrá ver el mensaje de "Transacción guardada localmente"
6. ✅ **Caso aprobado**: Transacción almacenada en cola local sin conexión

### F8-M04: Sincronización cuando hay conexión
1. Desactivar el modo avión
2. Conectar el dispositivo a una red Wi-Fi o datos móviles
3. La app detectará la restauración de la conexión
4. El `NfcTransactionRepository.syncPendingTransactions()` se ejecutará automáticamente o al reintentarse
5. Cada transacción "pending" se sincronizará con Firestore en la colección `nfcTransactions`:
   - document ID: UUID único de la transacción
   - fields: passengerUid, commandType, apduHex, timestampMs, tripId, status="synced", createdAt, attemptCount
6. El UI state cambiará de "pending" a "synced" en el flujo de NFC
7. El historial de viajes se actualizará reflejando el nuevo registro
8. ✅ **Caso aprobado**: Cola sincronizada con Firestore, status cambiado a "synced"

## 4. Verificación técnica

### 4.1. Validación de hash de transacción
Cada transacción incluye un hash generado vía HMAC-SHA256:
- **Datos**: `"$uid:$timestamp:$transactionType"`
- **Clave secreta**: `"PasajeYa-NFC-2024-v1"` (desde Secure Settings)
- **Resultado**: 64 caracteres hexadecimales
- **Verificación**: `verifyTransactionHash(uid, timestamp, type, receivedHash)` debe retornar `true`

### 4.2. Estructura de datos en Firestore (`nfcTransactions/{docId}`)
```json
{
  "passengerUid": "12345678",
  "commandType": "TAP_IN",
  "apduHex": "0001001234567890abcdef1234567890abcd1234567890abcdef1234567890abcd",
  "timestampMs": 1730000000000,
  "tripId": "trip_001",
  "status": "synced",
  "createdAt": 1730000000000,
  "attemptCount": 1
}
```

### 4.3. Estados de la cola
- **pending**: Transacción guardada localmente, awaiting sync
- **synced**: Transacción confirmada en Firestore
- **failed**: Transacción descartada después de 3 intentos fallidos

## 5. Herramientas de diagnóstico

### 5.1. Verificar HCE service
```
adb shell dumpsys package nom.conector | grep -i "HCE\|apdu"
```

### 5.2. Revisar logs de NFC
```
adb logcat -s NFC*:E
```

### 5.3. Consultar cola local (Room - si está integrado)
```
adb shell content query --uri content://com.pasajeya.nfc/transactions
```

### 5.4. Revisar Firestore
Consola Firebase > Firestore > colección `nfcTransactions`