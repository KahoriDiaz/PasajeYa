# Pruebas manuales de la Funcionalidad 6 – Historial de viajes y pagos

| ID | Pasos | Resultado esperado |
|--|-------|-------------------|
| **F6‑M01** | Completar un viaje y abrir el historial | El viaje aparece arriba con fecha, ruta, distancia e importe correctos |
| **F6‑M02** | Comparar el importe del historial con el resumen del viaje | Idénticos |
| **F6‑M03** | Cambiar entre **"Esta semana"**, **"Este mes"** y **"Todo"** | La lista y el gasto cambian y son coherentes |
| **F6‑M04** | Sumar a mano los importes de la semana | Igual al gasto mostrado |
| **F6‑M05** | Tener un viaje en **"revisión"** | Aparece con etiqueta **ámbar** y **"Importe pendiente"**, y no suma al gasto |
| **F6‑M06** | Poner el teléfono en **modo avión** | Se ve lo ya cargado con el aviso **"Sin conexión"** |
| **F6‑M07** | Cuenta sin viajes | Mensaje **"Aún no tienes viajes"** |
| **F6‑M08** | Operador revisa **"Mis viajes del turno"** | Conteos y total coinciden con los viajes del turno |

### Índices y reglas
- Los índices compuestos están definidos en `firestore.indexes.json` (`passengerUid + startedAt`, `passengerUid + status + startedAt`, `operatorUid + shiftId + startedAt`).
- Las reglas de Firestore ya cubren la lectura por pasajero y operador, y deniegan todas las escrituras directas desde el cliente.

### Criterio de terminado
- Paginación y suma agregada verificadas con **45** viajes de prueba.
- Las consultas no fallan por falta de índices en el proyecto real (despliega los índices con `firebase deploy --only firestore:indexes`).