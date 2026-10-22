# ADR-07 · Segundo microservicio (Notificaciones) con patrón outbox — amplía el ADR-01

Estado: **propuesto** (amplía [ADR-01](decisiones-arquitectura.md): monolito modular + integraciones), para ratificar en equipo · Autor: Adiel (Product Owner) · SIGRO · SEVENS INGENIEROS SAC

## Contexto
Tres personas, un semestre, una obra real que registra horas, compras e inventario desde celulares con mala señal.
Las reglas más importantes del negocio (nada se borra, el saldo se calcula, un reenvío no duplica, un rol vive junto a la
obra) son **reglas de datos**: necesitan transacciones y restricciones de PostgreSQL, no mensajes entre servicios.

## Decisión
1. **Un monolito modular** (`api/`): módulos `obra`, `catalogo`, `compras`, `tareo` (y `almacen`, `costo` de Henry) en
   un mismo proceso y una misma base PostgreSQL, **un esquema por módulo**. Los módulos solo dependen hacia `obra` y
   `catalogo`; una prueba falla si aparece otra dependencia.
2. **Dos microservicios**, solo donde hay una razón técnica real:
   - **Integraciones** (Fernando): habla con terceros (SUNAT/Factiliza, S10). Se cae, cambia de proveedor y tiene
     secretos propios; la API tolera su caída (el proveedor queda «sin validar»).
   - **Notificaciones**: debe seguir vivo y recibir eventos aunque la API se reinicie, y tiene su propio almacenamiento.
3. **Frontera dura (ADR-01):** un microservicio no lee ni escribe la base de la API. Se comunican por HTTP con
   token de servicio.

## Patrón outbox (API → Notificaciones)
- El evento se inserta en `outbox.evento` **en la misma transacción** que el hecho (anular una compra, pedir un
  dispositivo…). Si la transacción se revierte, el evento no existe; si se confirma, no se pierde.
- El relevo (`python -m app.relay`) entrega **al menos una vez**, con espera exponencial (5 s → 1 h) y
  `FOR UPDATE SKIP LOCKED`, de modo que varios relevos no entregan el mismo evento (probado con hilos reales).
- El receptor **deduplica** por `evento_id`; por eso «al menos una vez» no duplica la campana de nadie.
- La API resuelve los **destinatarios** al publicar (solo ella sabe quién tiene qué rol en cada obra).
- El motor protege la bandeja: el evento es inmutable, una entrega confirmada no se desmarca, nada se borra.

## Por qué NO seis microservicios
| Argumento | Consecuencia |
|---|---|
| Una compra y su detalle, un movimiento y su saldo deben ser **atómicos** | Entre servicios eso es una saga con compensaciones; en un monolito es `BEGIN … COMMIT` |
| Las reglas R1–R10 viven en el motor | Repartidas en bases distintas, dejan de poder garantizarse |
| 3 personas | Cada servicio extra es un despliegue, un pipeline, una base y un contrato que mantener |
| Se puede extraer después | Los módulos ya tienen esquema propio y dependencias de un solo sentido: sacar uno es mecánico |

## Consecuencias
- (+) Transacciones reales, una sola migración coordinada, pruebas rápidas sobre PostgreSQL real.
- (−) Un despliegue conjunto de los módulos; si un módulo se vuelve cuello de botella, se extrae con su esquema.
- (−) Notificaciones es consistente **eventualmente**: la campana puede tardar segundos si hubo fallos.

## Alternativas descartadas
- *Broker de mensajes (RabbitMQ/Kafka):* una pieza más que operar; el outbox sobre PostgreSQL cubre el volumen de una obra.
- *Notificaciones como módulo del monolito:* viable, pero se pierde la demostración de un servicio con su propio ciclo de vida.
