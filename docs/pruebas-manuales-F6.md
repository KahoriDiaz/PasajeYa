# Pruebas manuales — Funcionalidad 6: Historial de viajes y pagos

## 1. Alcance
Validación del historial para pasajero (paginación, filtros de semana/mes, suma acumulada en America/Lima) y resumen del turno para operador (conteo de cerrados, en revisión y total cobrado).

## 2. Casos de prueba

### CP-F6-01: Visualización del historial del pasajero
- **Precondición:** Pasajero autenticado con viajes registrados.
- **Paso:** Abrir la pestaña "Mi historial".
- **Resultado esperado:** Se muestran los viajes ordenados descendentemente por `startedAt` con fecha en español (ej. "Lun 20 oct · 08:15").

### CP-F6-02: Filtros de fecha (Semana / Mes / Todo)
- **Precondición:** Viajes realizados en distintas semanas y meses.
- **Paso:** Cambiar el filtro a "Esta semana" y luego a "Este mes".
- **Resultado esperado:** El cálculo toma como referencia lunes 00:00 hora de Lima (`America/Lima`) y el total acumulado refleja únicamente la suma de los viajes cerrados del periodo.

### CP-F6-03: Paginación infinita (20 en 20)
- **Precondición:** Usuario con más de 25 viajes.
- **Paso:** Hacer scroll hasta el final de la lista.
- **Resultado esperado:** Se carga el segundo bloque de resultados mediante cursor sin duplicados ni parpadeos.

### CP-F6-04: Navegación al detalle
- **Precondición:** Tarjeta de viaje visible.
- **Paso:** Tocar cualquier viaje de la lista.
- **Resultado esperado:** Abre la pantalla de Resumen del viaje en modo solo lectura.

### CP-F6-05: Resumen de turno del operador
- **Precondición:** Operador con turno activo.
- **Paso:** Abrir la vista del operador.
- **Resultado esperado:** La tarjeta `OperatorShiftSummaryCard` muestra el conteo exacto de viajes cerrados, en revisión y la suma total recaudada en soles.
