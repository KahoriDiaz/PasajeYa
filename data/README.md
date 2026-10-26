# Catálogos reales

`routes.json` debe contener las rutas reales de transporte público que se cargarán en
Firestore. El archivo actual está vacío a propósito: no se inventan rutas ni paraderos.

## Esquema

```json
[
  {
    "id": "identificador-estable",
    "name": "Nombre real de la ruta",
    "active": true,
    "stops": [
      {
        "name": "Nombre real del paradero",
        "lat": -16.39889,
        "lng": -71.535
      }
    ]
  }
]
```

- `id`: identificador estable, sin espacios ni barras. Se usa como id del documento en
  `routes/{routeId}`.
- `name`: nombre real de la ruta.
- `active`: indica si la ruta está disponible.
- `stops`: al menos un paradero real, con coordenadas válidas.

Cuando el archivo tenga datos reales, ejecuta:

```powershell
$env:GCLOUD_PROJECT="pasajeya-f150d"
npm --prefix functions run seed:routes
```

Para sembrar el emulador, usa `firebase emulators:exec --only firestore --project
demo-pasajeya "npm --prefix functions run seed:routes"` o el script de pruebas.
