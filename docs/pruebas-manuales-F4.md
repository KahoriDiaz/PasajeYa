# Pruebas manuales — Funcionalidad 4: Beneficio universitario del 50 %

## 1. Alcance
Validación del registro y aplicación del descuento legal del 50 % para estudiantes universitarios.

## 2. Casos de prueba

| ID | Pasos | Resultado esperado |
|---|---|---|
| F4-M01 | Ingresar a la sección "Beneficio Universitario" | Se muestra el formulario para registrar universidad y código |
| F4-M02 | Intentar enviar formulario con campos vacíos | El botón permanece deshabilitado |
| F4-M03 | Enviar formulario con datos válidos | La solicitud se guarda con estado "pending" |
| F4-M04 | Realizar viaje con beneficio aprobado | La tarifa total calculada refleja el 50% de descuento |
| F4-M05 | Verificar cobro del operador | La pantalla y feedback anuncian tarifa universitaria aplicada |

## 3. Pasos para ejecutar en un celular

1. Iniciar la app y navegar a la sección "Beneficio Universitario".
2. Completar los campos Universidad / Instituto y Código de estudiante / Carné.
3. Presionar el botón "Solicitar beneficio del 50%".
4. Verificar que el formulario se envía y el estado cambia a "pending".
5. Realizar un viaje; la tarifa total debe reflejar el 50% de descuento.
6. El operador debe ver en su pantalla el beneficio aplicado y el feedback de voz indicar "Medio pasaje".

## 4. Resultados esperados

- El formulario se envía correctamente con datos válidos y el estado cambia a "pending".
- Al realizar un viaje con beneficio aprobado, la tarifa total calculada refleja el 50% de descuento sobre la tarifa base.
- El feedback de voz y la pantalla del operador indican "Medio pasaje" o el monto con descuento.
- Los datos del beneficio se almacenan correctamente en Firestore y el pasajero solo puede ver/solicitar su propio beneficio.