# Pruebas manuales — F1 Autenticación y cuentas

Casos para validar F1 en un celular real con Google Play Services. No uses datos
personales reales: emplea correos, nombres y DNI sintéticos. Nunca escribas el DNI en
capturas, logs ni reportes.

## Preparación

1. Compila e instala la app debug:

   ```powershell
   .\gradlew assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

2. Levanta los emuladores de Firebase y confirma que la app debug apunta a ellos:

   ```powershell
   npm run emulators:start
   ```

3. Para el alta de operador debe existir al menos una ruta activa. En el emulador se
   puede sembrar una ruta de prueba; en producción solo con rutas reales cargadas por el
   script de administración. No inventes rutas.
4. Para verificar el correo en el emulador, consulta
   `http://127.0.0.1:9099/emulator/v1/projects/<project-id>/oobCodes` y abre el enlace.
   En un celular real revisa la bandeja del correo.
5. Borra los datos de la app entre casos que requieran empezar desde cero:
   `adb shell pm clear com.pasajeya.app`.

## Casos

### F1-01 — Registro completo de pasajero

**Precondiciones:** correo nuevo y verificado, DNI sintético válido no usado.

1. Abre la app y toca **Crear cuenta**.
2. Ingresa correo y una contraseña que cumpla la política; confirma la contraseña.
3. Abre el enlace de verificación y vuelve a la app; toca **Ya verifiqué**.
4. Elige **Pasajero**.
5. Completa nombres, apellidos, DNI y fecha de nacimiento; acepta la política.
6. Toca **Guardar datos**.

**Resultado esperado:** se muestra el inicio de pasajero con el saludo, el correo, la
tarjeta de pasajero activo y el mensaje de perfil listo. En Firestore, `users/{uid}`
contiene rol, nombres, fecha, beneficio y consentimiento, sin DNI; `users_private/{uid}`
contiene solo el DNI; `dniIndex` tiene una entrada para el uid; `sessions/{uid}` existe.

### F1-02 — Validación de contraseña

1. En **Crear cuenta**, prueba contraseñas cortas, sin mayúscula, sin minúscula, sin
   número, sin símbolo, con espacios, con la parte local del correo y una contraseña
   común.
2. Observa la lista de requisitos y el indicador de seguridad.

**Resultado esperado:** la app explica cada requisito incumplido y no permite crear la
cuenta hasta que la contraseña sea válida y ambas coincidan.

### F1-03 — Correo ya registrado

1. Intenta crear una cuenta con un correo ya existente.

**Resultado esperado:** mensaje **Ya existe una cuenta con ese correo.** No se crea una
cuenta duplicada.

### F1-04 — Verificación y reenvío de correo

1. Crea una cuenta y no verifiques el correo.
2. Toca **Reenviar correo** dos veces seguidas.
3. Toca **Ya verifiqué** sin abrir el enlace.

**Resultado esperado:** el reenvío respeta el tiempo de espera indicado; sin verificar,
la app muestra que aún no detecta la verificación y no deja continuar. Con el enlace
abierto, **Ya verifiqué** avanza a la selección de rol.

### F1-05 — DNI inválido y DNI duplicado

1. En los datos personales, prueba DNI con letras, con menos o más de 8 dígitos, con
   espacios y con dígitos repetidos (`00000000`, `11111111`, etc.).
2. Con una cuenta válida, usa el DNI de otra cuenta ya registrada.

**Resultado esperado:** los formatos inválidos se rechazan en la app. El duplicado se
rechaza con **Ese DNI ya está registrado.** y no se escriben documentos parciales.

### F1-06 — Registro completo de operador

**Precondiciones:** ruta activa existente, licencia y placa sintéticas no usadas.

1. Crea y verifica una cuenta; elige **Operador**.
2. Completa los datos personales y acepta la política.
3. Ingresa número de licencia, clase de licencia, placa y selecciona al menos una ruta.
4. Toca **Registrar operador**.

**Resultado esperado:** se muestra **Registro de operador enviado**, el aviso de revisión
por un administrador y **Estado: pendiente de verificación**. En Firestore,
`operators/{uid}` queda con estado `unverified`, clase, licencia y rutas;
`vehicles/{placa}` queda con `ownerUid` del operador. El cliente no puede leer
`operators/{uid}`.

### F1-07 — Placa duplicada

1. Intenta registrar un operador con una placa ya asociada a otro operador.

**Resultado esperado:** mensaje **Esa placa ya está registrada.** El vehículo y el
operador no se duplican.

### F1-08 — Licencia, clase y ruta obligatorias

1. Intenta registrar un operador con licencia inválida, sin clase o sin ruta.

**Resultado esperado:** la app impide el envío y señala el campo faltante. El servidor
también rechaza una solicitud manipulada.

### F1-09 — Inicio de sesión y persistencia

1. Cierra la app y vuelve a abrirla con una cuenta que ya completó su perfil.
2. Fuerza la detención (`adb shell am force-stop com.pasajeya.app`) y ábrela de nuevo.

**Resultado esperado:** la app restaura la sesión y llega directamente al inicio del rol
correspondiente, sin pedir la selección de rol otra vez.

### F1-10 — Credenciales incorrectas

1. En **Iniciar sesión**, ingresa un correo válido con contraseña incorrecta.

**Resultado esperado:** mensaje genérico **Correo o contraseña incorrectos.** No se revela
si el correo existe.

### F1-11 — Bloqueo tras cinco intentos

1. Falla el inicio de sesión cinco veces en menos de treinta segundos.
2. Intenta de nuevo de inmediato.

**Resultado esperado:** la app bloquea el botón y muestra una cuenta regresiva de 30 s;
al terminar permite reintentar. El bloqueo es local y no sustituye la protección del
servidor.

### F1-12 — Recuperar contraseña

1. Toca **Olvidé mi contraseña**.
2. Envía un correo existente y luego uno inexistente.

**Resultado esperado:** en ambos casos se muestra el mismo mensaje: si la cuenta existe,
se envió un enlace. No se revela la existencia del correo.

### F1-13 — Sesión única

1. Inicia sesión en el dispositivo A y deja la app abierta.
2. Inicia sesión con la misma cuenta en el dispositivo B (o llama a `registerDevice`
   con otro `deviceId`).

**Resultado esperado:** el dispositivo A muestra **Sesión cerrada** con el mensaje
**Tu cuenta se inició en otro dispositivo. Por seguridad, esta sesión se cerró.** Al
tocar **Entendido** vuelve a **Iniciar sesión**.

### F1-14 — Cierre de sesión

1. Desde el inicio, toca **Cerrar sesión**.

**Resultado esperado:** vuelve a la bienvenida y no queda sesión ni rol en memoria. Al
reabrir la app se muestra la bienvenida.

### F1-15 — Privacidad y consentimiento

1. En los datos personales, toca **Leer la política de privacidad**.
2. Vuelve e intenta guardar sin aceptar el consentimiento.

**Resultado esperado:** la política muestra versión y fecha; sin consentimiento no se
guarda el perfil y se muestra el mensaje correspondiente. El consentimiento guardado
incluye versión y fecha de aceptación.

### F1-16 — Revisión de datos y seguridad

Con Admin SDK o la consola del emulador, verifica:

- `users/{uid}` no contiene DNI.
- `users_private/{uid}` no es legible ni escribible por clientes.
- `dniIndex` y `operators` no son legibles ni escribibles por clientes.
- `vehicles/{placa}` solo lo escribe el servidor.
- Los logs de Functions no contienen DNI, correo completo ni coordenadas.

### F1-17 — Accesibilidad básica

1. Activa TalkBack y recorre bienvenida, login, registro, perfiles e inicio.
2. Verifica que los botones tengan descripción, que los campos tengan etiqueta y que los
   objetivos táctiles midan al menos 48 dp.

**Resultado esperado:** todos los controles son anunciados y accionables; no hay texto
truncado en tamaño de fuente grande.

### F1-18 — Sin conexión y errores de red

1. Activa modo avión y prueba iniciar sesión, cargar rutas y guardar un perfil.
2. Reactiva la red y reintenta.

**Resultado esperado:** mensajes claros de conexión, sin bloqueos permanentes; al volver
la red la operación puede reintentarse.

## Ejecución en emulador (2026-10-26)

| Caso | Resultado |
|---|---|
| F1-01 | Correcto. Pasajero creado, correo verificado, perfil y home validados. |
| F1-05 (fecha) | Se encontró un bug de cursor en la fecha; corregido y revalidado. |
| F1-06 | Correcto. Operador creado, licencia/placa/ruta guardadas, home pendiente. |
| F1-09 | Correcto. La sesión persiste tras reiniciar el proceso. |
| F1-10 | Correcto. Mensaje genérico mostrado. |
| F1-13 | Correcto. Diálogo de sesión reemplazada mostrado y vuelta al login. |
| F1-16 | Correcto. Datos y aislamiento verificados con Admin SDK. |

Pruebas automáticas de la misma fecha: reglas Firestore 8/8, Functions 22/22 y pruebas
unitarias Android correctas.

## Pendiente en celular real

- Teclado real y autoformato de fecha con entrada rápida.
- Apertura del enlace de verificación desde la app de correo.
- Envío real de correos de verificación y recuperación.
- Tiempo real del bloqueo de 30 s y de la sesión única en segundo plano.
- TalkBack, fuente grande y orientación.
- Red móvil lenta, modo avión y reconexión.
