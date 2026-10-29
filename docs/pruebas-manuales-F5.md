# Pruebas manuales — F5 Voz del operador

Casos para validar F5 en un celular real con Google Play Services y un motor de
TextToSpeech con voz en español. Usa cuentas y nombres sintéticos; nunca datos personales
reales ni un DNI real. La app nunca debe decir ni mostrar el DNI.

## Preparación

1. Compila e instala la app debug en el celular del operador:

   ```powershell
   .\gradlew assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

2. Levanta los emuladores y confirma que la app debug apunta a ellos:

   ```powershell
   npm run emulators:start
   ```

3. Crea una ruta activa, un operador aprobado con esa ruta y un pasajero con perfil
   completo. El pasajero debe tener fecha de nacimiento para que el servidor calcule su
   edad.
4. En el celular del operador, configura una voz en español:
   **Ajustes > Sistema > Idiomas e introducción de texto > Salida de texto a voz**.
   Selecciona un motor y una voz en español; descarga los datos si es necesario.
5. Sube el volumen multimedia y desactiva el modo silencio o No molestar para escuchar
   el aviso.
6. Inicia sesión como operador e inicia un turno antes del primer caso.
7. Limpia los datos de la app solo cuando el caso lo requiera:
   `adb shell pm clear com.pasajeya.app`. Esto restablece la preferencia de voz al valor
   por defecto.

## Casos

### F5-01 — Anuncio en el Tap In

**Precondiciones:** preferencia de voz activada e interruptor en **Activar aviso de voz**.

1. Toca **Escanear pasajero** y apunta la cámara al QR del pasajero.
2. Espera a que el Tap In termine.

**Resultado esperado:** el teléfono dice el primer nombre y la edad del pasajero, por
ejemplo **“Ana, 22 años”**. La tarjeta **Tap In registrado** muestra el nombre y la edad
durante unos 4 segundos. No se dice el DNI, el apellido, el correo ni la ruta.

### F5-02 — Dos Tap In consecutivos

**Precondiciones:** dos pasajeros distintos con QR vigente y el turno activo.

1. Escanea el QR del primer pasajero.
2. Sin esperar a que termine el aviso, escanea el QR del segundo pasajero.

**Resultado esperado:** se escuchan los dos avisos en el orden de los Tap In, sin que uno
corte al otro. La tarjeta visual puede mostrar al último pasajero; el segundo aviso no
reemplaza al primero en la cola de audio.

### F5-03 — Preferencia de voz apagada

1. Desactiva **Activar aviso de voz**.
2. Escanea el QR de un pasajero.
3. Cierra y vuelve a abrir la app; revisa que el interruptor siga apagado.
4. Activa el interruptor y escanea otro pasajero.

**Resultado esperado:** con el interruptor apagado no se reproduce ningún aviso, pero la
tarjeta con nombre y edad sigue apareciendo. La preferencia se conserva tras reiniciar la
app. Al activarla, el siguiente Tap In vuelve a sonar.

### F5-04 — Botón Probar voz

1. En la tarjeta **Aviso de voz**, toca **Probar voz**.
2. Repite con el interruptor apagado.

**Resultado esperado:** se escucha la frase de prueba **“Prueba de voz de PasajeYa”** sin
crear un viaje ni modificar datos. El botón sirve para comprobar el motor incluso con el
aviso automático apagado.

### F5-05 — Motor o datos de voz no disponibles

1. Desactiva o desinstala el motor de TextToSpeech, o borra sus datos de voz en español.
2. Toca **Probar voz** o realiza un Tap In.
3. Toca **Instalar datos de voz** si aparece el aviso.
4. Vuelve a probar después de instalar los datos.

**Resultado esperado:** la app no se cierra y mantiene visible la tarjeta con el nombre y
la edad. Muestra el aviso de que no hay datos de voz y el botón de instalación abre la
pantalla del sistema correspondiente. Después de instalar los datos, vuelve a probar; si
el motor no se recupera en esa sesión, reinicia la app para que se inicialice de nuevo.

### F5-06 — Volumen y idioma del sistema

1. Baja el volumen multimedia a cero y realiza un Tap In.
2. Sube el volumen, cambia la voz del sistema a otro idioma y realiza otro Tap In.
3. Vuelve a seleccionar una voz en español.

**Resultado esperado:** la app no modifica los ajustes del sistema ni fuerza el volumen.
Con volumen cero no se garantiza audio, pero la información visual sigue disponible. La
app selecciona español cuando el motor lo ofrece; si no hay ninguna voz en español,
muestra el aviso de voz no disponible en lugar de reproducir una voz incorrecta.

### F5-07 — Privacidad en pantalla, voz y logs

1. Realiza un Tap In y observa la tarjeta y el aviso de voz.
2. Revisa los logs de la app en el celular:

   ```powershell
   adb logcat -d | Select-String -Pattern "pasajeya|DNI|dni"
   ```

3. En el emulador, revisa la respuesta de `tapIn` y el documento creado en `trips`.

**Resultado esperado:** la interfaz y la voz usan solo el primer nombre y la edad. La
respuesta de `tapIn` contiene únicamente `tripId`, `firstName` y `age`. No aparece el DNI
en la pantalla, en los logs de la app ni en la respuesta al operador. No publiques capturas
que incluyan datos de cuentas.

### F5-08 — Accesibilidad de los controles

1. Activa TalkBack y recorre la tarjeta **Aviso de voz**.
2. Enfoca el interruptor, **Probar voz** y **Instalar datos de voz** cuando aparezca.

**Resultado esperado:** TalkBack anuncia el nombre y el estado del interruptor, y los
botones tienen etiquetas comprensibles. Todos los controles se pueden activar con un toque
y tienen un área táctil de al menos 48 dp.

## Revisión de datos

En Firestore comprueba:

- `trips/{tripId}` contiene `passengerFirstName` y `passengerAge`, pero nunca el DNI.
- `users_private` y `dniIndex` siguen siendo inaccesibles desde los clientes.
- La preferencia local `voice_enabled` está en DataStore; no se envía a Firestore.
- La voz se genera en el celular del operador y no requiere una llamada adicional al
  servidor.
