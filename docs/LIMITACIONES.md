# Limitaciones conocidas

Registro de limitaciones de F1, F2, F5 y del entorno. Cada una indica qué falta y qué
interfaz queda preparada para la fuente real.

## Datos y verificación

- **Rutas reales:** `data/routes.json` está vacío a propósito. Sin rutas reales cargadas
  con el script de administración no se puede completar el alta de un operador. Para las
  pruebas de emulador se creó `routes/TEST-01` directamente en Firestore; no está en el
  repositorio y no debe considerarse una ruta real.
- **DNI:** se valida formato, que no sea una repetición y que sea único mediante
  `dniIndex` con HMAC-SHA256. No se valida la autenticidad del DNI contra RENIEC ni otro
  registro oficial. La interfaz para una futura verificación oficial queda pendiente.
- **Licencia y placa:** se validan formato y unicidad de la placa, pero no se verifica la
  licencia ni la propiedad del vehículo contra el MTC. El operador queda en estado
  `unverified` y la revisión es manual desde el servidor. No existe una pantalla de
  administración en la app.
- **Beneficio universitario:** F1 solo deja `benefit.status = none`. La verificación
  institucional y el descuento del 50 % pertenecen a F4.

## Seguridad y sesiones

- **Política de contraseña en el servidor:** la app valida la política completa, pero la
  contraseña se envía directamente a Firebase Auth. Con la configuración estándar,
  Firebase Auth solo exige 6 caracteres; no hay una política de contraseñas de Identity
  Platform configurada. Un cliente manipulado podría crear una cuenta con una contraseña
  más débil. Para cerrar esta brecha hay que configurar la política de Identity Platform
  o mover la creación de cuentas a un flujo con validación en servidor.
- **Sesión única:** se implementa con `sessions/{uid}` y un listener de Firestore. No se
  revocan refresh tokens de Firebase Auth. Un cliente modificado con credenciales
  válidas puede volver a registrar su dispositivo y recuperar la sesión. Las Functions de
  F2 en adelante exigen `sessionId` activo mediante `assertActiveSession`; las callables
  de F1 no lo exigen porque una de ellas (`registerDevice`) es la que crea la sesión.
  Si la app está en segundo plano o sin red, el aviso de sesión reemplazada puede
  aparecer al reconectar o al volver a abrir la app.
- **Bloqueo de intentos:** el bloqueo de cinco intentos durante 30 s es local y se pierde
  al borrar los datos de la app. No sustituye el rate limiting del servidor ni las
  protecciones propias de Firebase Auth.
- **Operador en el cliente:** el cliente no puede leer `operators/{uid}`. El inicio del
  operador muestra un estado genérico de revisión y no refleja cambios posteriores de
  estado hasta que una funcionalidad futura agregue una callable de consulta.

## Voz (F5)

- **Motor y datos del sistema:** la voz depende de un motor TextToSpeech y de datos en
  español instalados en el celular. Si no están disponibles, la app mantiene el nombre y
  la edad en pantalla, muestra un aviso y ofrece abrir la instalación de datos de voz.
- **Audio no garantizado:** el aviso usa el canal multimedia y no modifica el volumen ni
  el modo No molestar. El operador debe configurar ambos; la app no puede confirmar que el
  sonido se haya escuchado.
- **Contenido limitado:** solo se anuncia el primer nombre y la edad. Si el servidor no
  entrega una edad o el nombre queda vacío tras el saneo, se omite la voz y se muestra el
  aviso visual; no se usa el DNI como respaldo.

## Operación y emuladores

- **Cold start de Functions:** el primer callable tras arrancar los emuladores puede
  tardar más de 30 s. La app no aplica timeout a las callables, por lo que puede quedar
  cargando. Las pruebas usan un precalentamiento; en producción conviene medir el arranque
  en frío y definir un timeout y reintento.
- **Correo:** en el emulador la verificación se obtiene de `oobCodes`; el envío real de
  correos y el enlace desde la app de correo deben probarse en un celular real.
- **Rutas y datos de prueba:** las cuentas y la ruta de prueba del emulador no forman
  parte del repositorio. Las pruebas automáticas usan el proyecto `demo-pasajeya`.
- **Auditoría de dependencias:** `npm audit` reporta 5 vulnerabilidades moderadas en
  dependencias de desarrollo de la raíz y 5 altas en `firebase-tests`
  (`@grpc/grpc-js` transitivo de `@firebase/rules-unit-testing`). Las dependencias de
  producción de `functions/` no reportan vulnerabilidades. La corrección disponible
  implica un cambio mayor de Firebase en las pruebas; se debe revisar y actualizar cuando
  el ecosistema lo permita.
- **Despliegue:** no se han desplegado Functions, reglas, índices ni el secreto
  `DNI_PEPPER` a producción. El valor de producción lo configura el responsable del
  proyecto en Secret Manager.

## Fuera de alcance de F1

- Eliminación de cuenta, cambio de contraseña autenticado y reautenticación.
- Verificación automática de licencias, placas, rutas o DNI contra fuentes oficiales.
- Panel de administración para aprobar operadores y resolver reclamos.
- QR, GPS, tarifas, voz, historial y reclamos (F2–F7).


## Billetera demo (F8) y mapa

- **Saldo de demostración:** la billetera de `feature/wallet` vive solo en el dispositivo
  (DataStore, por cuenta). No es dinero real, no se sincroniza con Firestore ni pasa por Cloud
  Functions; por eso la pantalla muestra "Saldo de demostración: no es dinero real". Conectar una
  pasarela de pago real requiere un proveedor y un backend que hoy no existen. La interfaz
  `WalletRepository` queda lista para sustituir `DataStoreWalletRepository`.
- **Costo en vivo del pasajero:** se calcula en el teléfono con el GPS del pasajero y es solo una
  estimación. El importe que se cobra y se guarda lo calcula el servidor en el Tap Out con el GPS
  del vehículo.
- **Mapa:** usa teselas públicas de OpenStreetMap (osmdroid) sin clave de API. Para producción con
  mucho tráfico conviene un proveedor de teselas propio o con contrato.
- **Verificación del carné en el teléfono:** ML Kit solo comprueba que el texto parezca un carné de
  la UCSM para repetir la foto a tiempo; la validación que manda es la del servidor.
