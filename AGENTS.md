# AGENTS.md — PasajeYa!

Guía de trabajo para el desarrollo de **PasajeYa!**. Este documento es la fuente de verdad
del proyecto. Léelo completo antes de escribir código.

---

## 1. PRODUCTO

**PasajeYa!** es una app móvil para el transporte público (combis y buses) de **Arequipa, Perú**.

- Existen dos perfiles: **PASAJERO** y **OPERADOR** (conductor-cobrador).
- El pasajero tiene un **QR personal**.
- El operador escanea ese QR al subir (**Tap In**) y al bajar (**Tap Out**).
- El servidor calcula la distancia usando el **GPS del vehículo**.
- La tarifa se calcula como:

  `tarifa = distancia (km) × tarifa base por km × índice de combustible`

  aplicando **redondeo** y **tarifa mínima/máxima**.
- El pasajero ve un **desglose transparente** del cobro.
- Los **universitarios verificados** obtienen **50 % de descuento**.
- El teléfono del operador **dice por voz el nombre y la edad** del pasajero.
  **Nunca dice el DNI.**
- Existe **historial de viajes**.
- Existe un **flujo de reclamos** para viajes que quedaron sin Tap Out.

---

## 2. STACK

- **Kotlin**, **Jetpack Compose**, **Material 3**, **MVVM con StateFlow**.
- **Hilt** (inyección de dependencias).
- **Navigation Compose**.
- **Coroutines**.
- **Firebase**:
  - Authentication (correo/contraseña).
  - Firestore.
  - Cloud Functions v2 (TypeScript, Node LTS, región `southamerica-east1`).
  - Cloud Messaging (FCM).
- **CameraX + ML Kit Barcode Scanning** para leer QR.
- **ZXing core** para generar QR.
- **FusedLocationProvider** para GPS.
- **TextToSpeech** nativo de Android para la voz.
- **DataStore** para preferencias locales.
- **Pruebas**:
  - JUnit4.
  - MockK.
  - Turbine.
  - kotlinx-coroutines-test.
  - Compose UI tests.
  - Reglas de Firestore y Cloud Functions con **Firebase Emulator Suite** y
    **@firebase/rules-unit-testing**.

> No actualices AGP, Gradle ni Kotlin sin preguntarme; el AGP debe ser compatible con mi
> Android Studio (máximo 9.1.0). Verifica las versiones en la documentación oficial y que
> compilen.

---

## 3. ARQUITECTURA

### 3.1 Módulo y paquetes

- Un **solo módulo `app`**.
- Paquetes organizados **por funcionalidad**:
  - `core` — tema, navegación, utilidades.
  - `feature/auth`
  - `feature/trip`
  - `feature/fare`
  - `feature/benefit`
  - `feature/voice`
  - `feature/history`
  - `feature/claims`
- Dentro de cada funcionalidad: `data`, `domain`, `ui`.

### 3.2 Carpetas del repositorio

| Carpeta | Contenido |
|---|---|
| `functions/` | Cloud Functions |
| `firebase-tests/` | Pruebas de reglas de Firestore |
| `shared/` | Vectores de prueba JSON compartidos entre Kotlin y TypeScript |
| `data/` | Catálogos reales, por ejemplo `routes.json` |
| `docs/` | Documentación y listas de pruebas manuales |
| `design/` | Logo y recursos de diseño |

### 3.3 Idioma

- Textos de la interfaz en **`strings.xml`**, en **español**.
- Código e identificadores en **inglés**.

---

## 4. MODELO DE DATOS (Firestore)

> El dinero va **SIEMPRE en céntimos enteros**.

### 4.1 `users/{uid}`
```text
role: "passenger" | "operator"
firstName
lastName
birthDate            // yyyy-MM-dd
createdAt
privacyConsent: { version, acceptedAt }
benefit: { status, kind, validUntil }
```
- El cliente **solo puede LEER su propio documento**.
- El cliente **nunca lo escribe**.

### 4.2 `users_private/{uid}`
```text
dni                  // 8 dígitos
institutionalEmail
```
- **Ningún cliente** puede leerlo ni escribirlo.

### 4.3 `dniIndex/{hmacDni}`
```text
uid
```
- Garantiza **un DNI por cuenta**.
- **Solo servidor**.

### 4.4 `sessions/{uid}`
```text
sessionId
deviceId
updatedAt
```
- **Una sola sesión activa por cuenta**.
- **Solo el servidor escribe**.
- El dueño **puede leer**.

### 4.5 `operators/{uid}`
```text
licenseNumber
licenseClass
plate
routeIds[]
status
```
- **Solo servidor escribe**.

### 4.6 `vehicles/{plate}`
```text
ownerUid
```
- El **id es la placa normalizada**.
- **Solo servidor**.

### 4.7 `routes/{routeId}`
```text
name
active
concessionaireId
stops: [{ name, lat, lng }]
officialFareCents
officialUniversityFareCents
fareSourceUrl
fareCheckedAt
```
- **Lectura para autenticados**.
- **Escritura solo con script de administración** (Admin SDK).
- El resumen oficial se copia desde `data/official-fares.json` al cargar la ruta.
- **La F3 usa el pasaje oficial de la concesionaria como tope del cálculo.**

### 4.8 `fareVersions/{versionId}`
```text
baseFarePerKmCents
fuelIndex
fuelPriceCurrent
fuelPriceReference
minFareCents
roundingStepCents
calibrationNote
createdAt
```
- **Inmutable**.
- `config/currentFare`: `{ versionId }`.
- **No existe `maxFareCents` ni `isOfficial`.** El tope sale de la ruta.
- Valores iniciales provisionales: base 12 céntimos/km, índice 1,00, mínima 50
  céntimos, paso 5. Se calibran con viajes reales.

### 4.9 `shifts/{shiftId}`
```text
operatorUid
plate
routeId
startedAt
endedAt
status
```
- Subcolección `chunks/{chunkId}`:
  ```text
  points: [{ lat, lng, acc, t }]
  from
  to
  ```
- El operador puede **CREAR chunks en su turno activo** (con validación de forma en reglas).
- **No puede editarlos ni borrarlos**.
- `shiftLocks/{lockId}` es interno del servidor para impedir dos turnos activos del mismo
  operador o vehículo. El cliente no tiene acceso.

### 4.10 `qrNonces/{nonce}`
```text
uid
expiresAt
```
- **Uso único**.
- **TTL sobre `expiresAt`**.

### 4.11 `trips/{tripId}`
```text
passengerUid
operatorUid
shiftId
routeId
status               // "open" | "closed" | "requires_review"
reviewReason
startedAt
endedAt
startLocation
endLocation
fareVersionId
routeFareSnapshot: {
  officialFareCents
  officialUniversityFareCents
  fareSourceUrl
  fareCheckedAt
  concessionaireName
}
distanceMeters
usedPoints
coverage
fareBreakdown: {
  distanceMeters
  baseFarePerKmCents
  fuelIndex
  calculatedCents
  officialFareCents
  regularCents
  benefit: {
    kind
    percent
    officialUniversityFareCents
    discountCents
  }
  totalCents
  capped
  minApplied
  fareVersionId
  concessionaireName
}
passengerFirstName
passengerAge
```
- **Solo servidor escribe**.
- **Pasajero y operador del viaje pueden leerlo**.
- La foto de tarifas se guarda en el Tap In y no cambia si luego se actualiza la ruta.

### 4.12 `claims/{claimId}`
```text
tripId
passengerUid
reason
comment
status
resolution
createdAt
```
- Se **crea y resuelve solo por Cloud Functions**.

### 4.13 `benefitRequests/{uid}`
```text
stage                // "carne_ok" | "code_sent"
carneCodeHash
carneVerifiedAt
emailHash
codeHash
expiresAt
attempts
updatedAt
```
- **Solo servidor**.
- Guárdense únicamente HMAC; nunca la imagen, el texto OCR ni los códigos en claro.

### 4.14 `carneIndex/{hmacCarne}`
```text
uid
```
- Garantiza **un carné por cuenta**.
- **Solo servidor**.

### 4.15 Storage `benefitUploads/{uid}/{requestId}.jpg`
- El dueño puede **subir** un JPEG de máximo 5 MB para su solicitud.
- **Sin lectura ni borrado desde clientes**.
- Ciclo de vida de **24 horas**; el servidor borra la imagen en `finally`.

---

## 5. REGLAS NO NEGOCIABLES

1. **NADA SIMULADO**
   - No uses mocks, datos falsos ni `TODO: simular` en código de producción.
   - Todo lo que se muestra viene de **Firebase** o de **sensores reales**.
   - Los mocks y fakes **solo van en pruebas**.

2. **Seguridad**
   - El cliente **NUNCA** escribe importes, tarifas, estados de viaje, estados de
     beneficio ni roles.
   - Eso lo hacen las **Cloud Functions**.
   - Las reglas de Firestore **deniegan todo por defecto**.
   - Cada regla se prueba con **@firebase/rules-unit-testing**:
     **casos permitidos y casos denegados**.

3. **Validaciones**
   - **DNI**:
     - Exactamente **8 dígitos numéricos** (`^\d{8}$`).
     - **Sin letras ni espacios**.
     - **No** puede ser una repetición (`00000000`, `11111111`, ...).
   - **Contraseña segura**:
     - **10 a 64 caracteres**.
     - Debe tener **mayúscula, minúscula, número y símbolo**.
     - **Sin espacios**.
     - **No** puede contener la parte local del correo.
     - **No** puede estar en la lista de contraseñas comunes.
     - Se valida en la app **Y** la exige Firebase Auth.
   - Toda validación existe en la app (para dar mensajes claros) y **se repite en el
     servidor** (que es la que manda).
   - Ambas usan los **mismos vectores de prueba** en `shared/*.json`.

4. **Privacidad**
   - El **DNI jamás** se muestra, se registra en logs, viaja a otro cliente ni se dice
     en voz alta.
   - Los logs **no** deben contener DNI, correo completo ni coordenadas exactas.

5. **Definición de terminado por funcionalidad** — una funcionalidad termina **SOLO** cuando:
   - compila;
   - pasan las pruebas unitarias;
   - pasan las pruebas de reglas y de Functions en emulador;
   - existe `docs/pruebas-manuales-F<N>.md` con los casos para probar en un celular real.

6. **Forma de trabajo**
   - Trabaja en **pasos pequeños**.
   - **Ejecuta tú mismo** los tests y corrige antes de reportar.
   - Haz **un commit de git por paso** con mensaje claro.
   - Actualiza `docs/PROGRESO.md` al terminar cada paso.

7. **Secretos**
   - **Nunca** subas secretos (claves, contraseñas SMTP, el pepper del HMAC).
   - Van en **Secret Manager** con `defineSecret` de Firebase Functions.
   - Si falta un dato que **solo el usuario** puede dar, **dímelo y espera**.

8. **Fuentes no verificables**
   - Si algo no se puede validar automáticamente por falta de una fuente oficial,
     **NO lo simules**.
   - Déjalo documentado en `docs/LIMITACIONES.md`.
   - Deja una **interfaz lista** para conectar la fuente real.

---

## 6. DISEÑO

Diseño simple, igual al de las capturas del **informe 6**.

### 6.1 Paleta

| Uso | Color |
|---|---|
| Primario | `#1F6FB2` (azul) |
| Éxito | `#2E9E5B` (verde) |
| Revisión | `#E0A100` (ámbar) |
| Error | `#D64545` (rojo) |
| Fondo | `#F3F5F8` |
| Texto | `#1B2A3A` |
| Gris | `#6E7A87` |
| Bordes | `#DBE0E6` |

### 6.2 Componentes

- **Barra superior azul** con título blanco.
- **Tarjetas blancas** con borde suave y esquinas de **16 dp**.
- **Botones grandes redondeados** (**14 dp**).

### 6.3 Estados y accesibilidad

- **Estados de carga, vacío y error** en **todas** las pantallas.
- **Accesibilidad básica**:
  - `contentDescription`.
  - Tamaños táctiles de **48 dp**.

---

## 7. FUNCIONALIDADES

| ID | Funcionalidad |
|---|---|
| F1 | Autenticación y cuentas (pasajero/operador, DNI, contraseña segura, consentimiento, sesión única) |
| F2 | Tap In / Tap Out mediante QR |
| F3 | Cálculo de tarifa (distancia GPS, versión tarifaria, redondeo, mínimo/máximo, desglose) |
| F4 | Beneficio universitario del 50 % |
| F5 | Voz del operador (nombre y edad, nunca DNI) |
| F6 | Historial de viajes |
| F7 | Reclamos por viajes sin Tap Out |

El estado de cada funcionalidad se mantiene en **`docs/PROGRESO.md`**.
