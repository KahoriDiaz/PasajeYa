# Pruebas manuales — mapa, tarifa en vivo, beneficio, billetera demo e historial

Para probar en un celular real, con internet y conectado a la nube de Firebase (sin emuladores).

## Arranque y verificación de correo

- **M01 — Sin "cargando…" infinito.** Abre la app con internet. Debe llegar a Bienvenida o a tu
  inicio. Con el modo avión activado y una sesión iniciada debe mostrar el error de arranque con
  "Reintentar" en menos de 25 s.
- **M02 — Sondeo de correo.** Regístrate, deja la pantalla de verificación abierta, abre el correo y
  pulsa el enlace. En unos 3 s la app avanza sola, sin pulsar "Ya verifiqué".

## Estimar pasaje (mapa)

- **M03 — Mapa de la ruta.** Inicio de pasajero → "Estimar mi pasaje". Se ve el mapa con la línea
  de la ruta y los paraderos; el origen en verde y el destino en rojo.
- **M04 — Origen y destino.** Cambia el paradero de origen y el de destino y pulsa "Calcular".
  El importe cambia con la distancia por la ruta. Con el mismo paradero en ambos aparece
  "Elige paraderos distintos".
- **M05 — Tope y mínimo.** En una ruta larga el importe nunca pasa de S/ 3.30 ni del pasaje
  oficial de la ruta; en un tramo muy corto no baja de la tarifa mínima.
- **M06 — Universitario.** Con el beneficio aprobado aparece "Descuento universitario 50 %
  aplicado" y el total es la mitad (redondeada al paso) de la tarifa regular.

## Viaje en vivo

- **M07 — Costo en vivo.** Haz un Tap In con el QR del pasajero. En el inicio del pasajero aparece
  el mapa con la tarjeta flotante. Acepta el permiso de ubicación. Camina o viaja: la distancia y
  el costo suben con el movimiento.
- **M08 — Sin permiso.** Niega la ubicación: el mapa muestra el aviso y el botón "Activar
  ubicación"; el costo queda en la tarifa mínima.
- **M09 — Tap Out.** Al bajar, el viaje se cierra, la tarjeta desaparece y se abre el resumen con el
  desglose cobrado por el servidor (puede diferir de la estimación en vivo).

## Beneficio universitario

- **M10 — Carné.** Inicio → "Beneficio universitario" → "Verificar como universitario". Solo hay
  cámara (no hay galería). Foto de un papel cualquiera: avisa que no se pudo leer el texto del
  carné. Foto del carné real: se sube y la pantalla se cierra.
- **M11 — Correo y código.** Tras el carné aparece el campo de correo institucional; envía el código,
  escribe los 6 dígitos y confirma. El estado pasa a "Descuento universitario activo".

## Voz del operador

- **M12 — Aviso de voz.** Como operador, escanea el QR de un pasajero: se dice "Nombre, N años" y
  se ve la tarjeta de confirmación. Nunca se dice el DNI. Escanear a dos pasajeros seguidos: los
  avisos se encolan sin pisarse.

## Billetera demo

- **M13 — Aviso.** "Pagar" muestra siempre "Saldo de demostración: no es dinero real".
- **M14 — Recarga.** Recarga S/ 5, 10, 20 y 50 (demo). El saldo suma; pasar de S/ 200 se rechaza.
- **M15 — Retención y cobro.** "Retener tarifa máxima" reserva S/ 3.30 (saldo disponible baja). Con un
  viaje cerrado, "Cobrar último viaje" descuenta el total real de ese viaje y libera el resto.
  "Liberar retención" devuelve la reserva. Sin saldo suficiente muestra "Saldo insuficiente".
- **M16 — Movimientos.** Cada operación aparece en "Movimientos" con fecha y monto.

## Historial

- **M17 — Lista y filtros.** Inicio → "Mi historial". Filtra por semana, mes y todos; cada viaje con
  beneficio muestra la etiqueta del descuento.
- **M18 — Desglose.** Tocar un viaje abre el resumen con el desglose de tarifa.
