# PasajeYa! — Guía de desarrollo

PasajeYa! es una app Android para el transporte público de Arequipa. Este documento
explica cómo compilar, probar y ejecutar el proyecto con los emuladores de Firebase.

## Requisitos

- JDK 17 o superior.
- Android SDK con la plataforma Android 37 y Build Tools compatibles.
- Node.js 24 LTS.
- npm.
- Firebase CLI (`firebase-tools`).
- Un emulador o dispositivo Android con Google Play Services.

Las dependencias y versiones están declaradas en `gradle/libs.versions.toml`.

## Configuración local

1. Copia `local.properties.example` a `local.properties` si el archivo no existe y ajusta
   la ruta del SDK de Android.
2. El host de los emuladores de Firebase se configura en `local.properties`:

   ```properties
   firebase.emulator.host=10.0.2.2
   ```

   Usa `10.0.2.2` desde el emulador Android, `127.0.0.1` con `adb reverse` o la IP local
   del equipo desde un celular físico.

3. Solicita el archivo `app/google-services.json` al responsable del proyecto Firebase.
   No inventes valores ni uses un proyecto de prueba distinto para la app.
4. Instala las dependencias de Node:

   ```powershell
   npm install
   npm --prefix functions install
   npm --prefix firebase-tests install
   ```

## Compilar y probar

```powershell
.\gradlew assembleDebug
.\gradlew testDebugUnitTest
```

Las pruebas instrumentadas requieren un emulador o dispositivo:

```powershell
.\gradlew connectedDebugAndroidTest
```

## Emuladores de Firebase

El proyecto de pruebas es `demo-pasajeya`, que no requiere credenciales reales.

```powershell
npm run build:functions
npm run emulators:start
```

Servicios disponibles:

- Auth: `http://127.0.0.1:9099`
- Firestore: `http://127.0.0.1:8080`
- Functions: `http://127.0.0.1:5001`
- Emulator UI: `http://127.0.0.1:4000`

Para ejecutar todas las pruebas de reglas y Functions contra los emuladores:

```powershell
npm run emulators:test
```

En `local.properties`, configura `firebase.emulator.host=10.0.2.2` para que la app debug
se conecte a esos emuladores desde el emulador Android.

## Estructura

| Ruta | Contenido |
|---|---|
| `app/` | Aplicación Android en un solo módulo |
| `functions/` | Cloud Functions v2 en TypeScript |
| `firebase-tests/` | Pruebas de reglas de Firestore |
| `shared/` | Vectores JSON compartidos entre Kotlin y TypeScript |
| `data/` | Catálogos reales, como rutas |
| `docs/` | Documentación y pruebas manuales |
| `design/` | Logo y recursos de diseño |

Las funcionalidades viven en `app/src/main/java/com/pasajeya/app/feature/` y se dividen
en `data`, `domain` y `ui`. La región de Functions es `southamerica-east1`.

## Seguridad

- Las reglas de Firestore empiezan denegando todo.
- El cliente nunca escribe tarifas, roles, estados de viaje ni beneficios.
- El DNI no se registra en logs, no se muestra y nunca se pronuncia.
- Los secretos van en Secret Manager, no en el repositorio.
- `local.properties`, las claves de firma y los archivos `.env` no se suben a git.

Antes de probar en un celular real, revisa la lista manual de la funcionalidad
correspondiente en `docs/pruebas-manuales-F<N>.md`.
