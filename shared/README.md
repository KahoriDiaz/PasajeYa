# Vectores de prueba compartidos

Esta carpeta contiene vectores JSON que deben usar **las mismas pruebas** en Kotlin y en
TypeScript. Su objetivo es evitar que la validación de la app y la del servidor se
desincronicen.

## Reglas

- Cada archivo describe un caso de validación con una entrada y el resultado esperado.
- Los vectores **no contienen datos reales** de personas: sin DNI real, sin correos reales,
  sin coordenadas exactas ni secretos.
- La app y las Cloud Functions leen el mismo archivo; no se duplican casos por lenguaje.
- Si una regla cambia, primero se actualiza el vector y luego ambas implementaciones.
- Un vector debe cubrir tanto casos permitidos como casos denegados.

## Formato sugerido

```json
{
  "name": "dni",
  "cases": [
    {
      "input": "12345678",
      "valid": true,
      "reason": "ocho dígitos no repetidos"
    },
    {
      "input": "00000000",
      "valid": false,
      "reason": "dígitos repetidos"
    }
  ]
}
```

Los nombres de archivo y las claves se definen al implementar cada funcionalidad. No se
crean vectores "de ejemplo" con datos inventados que parezcan reales.
