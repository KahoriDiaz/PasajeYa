# Pruebas manuales — Funcionalidad 7: Reclamos por viajes sin Tap Out

## 1. Alcance
Validación del flujo de reclamos cuando un pasajero olvida o no puede realizar el Tap Out y el viaje queda en estado de revisión (`review`).

## 2. Casos de prueba

| ID | Pasos | Resultado esperado |
|---|---|---|
| F7-M01 | Abrir un viaje en revisión y presionar "Reclamar viaje" | Se despliega el diálogo solicitando motivo y distancia estimada |
| F7-M02 | Dejar el campo motivo vacío | El botón de confirmación permanece deshabilitado o muestra error de validación |
| F7-M03 | Ingresar motivo válido y enviar | El reclamo se registra en Firestore con status "submitted" y el diálogo se cierra |
| F7-M04 | Verificar estado del reclamo | La tarjeta muestra el badge de estado ("Pendiente", "Aprobado", "Rechazado") |
| F7-M05 | Verificar reglas de seguridad | El pasajero solo puede consultar sus propios reclamos |

## 3. Pasos para ejecutar en un celular

1. Iniciar un viaje sin realizar el Tap Out al bajar.
2. Esperar a que el viaje quede en estado `review`.
3. Desde el resumen del viaje, seleccionar la opción "Reclamar viaje".
4. En el diálogo, completar los campos obligatorios (motivo) y opcionales (distancia).
5. Presionar "Enviar reclamo".
6. Verificar que el reclamo aparece en la lista de reclamos del pasajero con el badge correspondiente.
7. Probar los casos de borde: motivo vacío, distancia inválida, y verificación que el pasajero ajeno no puede ver el reclamo.

## 4. Resultados esperados

- El diálogo se cierra correctamente tras enviar un reclamo válido.
- El estado del reclamo se refleja en la UI con el badge adecuado.
- Los mensajes de error son claros y no exponen información sensible (ej. DNI).
- El pasajero solo puede ver sus propios reclamos; las consultas a otros usuarios están denegadas por las reglas de Firestore.