# Modelo de datos

Punto de partida para el diagrama físico. No es una propuesta libre: cada decisión de aquí
viene de una regla del `CLAUDE.md`, y cambiar una obliga a revisar la otra.

## Lo que decide todo

1. **`movimiento` es un libro contable.** No hay `UPDATE` ni `DELETE`. Corregir es insertar
   un movimiento inverso que apunta al original.
2. **No existe una columna de saldo.** El stock se deriva sumando movimientos.
3. **`uuid_cliente` es único.** Es la clave de idempotencia del registro sin señal.
4. **El costo se congela** en el movimiento de salida, no se recalcula nunca.

## Tablas

### Obra y periodos

| Tabla | Campos principales | Notas |
|---|---|---|
| `proyecto` | id · nombre · fecha_inicio · fecha_fin · dia_corte · estado · creado_por · creado_en | El plazo acota todo. Ampliarlo inserta en `proyecto_plazo_historial`, no pisa el dato |
| `proyecto_plazo_historial` | id · proyecto_id · fecha_fin_anterior · fecha_fin_nueva · motivo · autor · fecha | HU-02 exige dejar rastro |
| `periodo` | id · proyecto_id · numero · fecha_inicio · fecha_fin · estado | Se generan solos al crear la obra. Único por (proyecto, numero) |
| `almacen` | id · proyecto_id · nombre · tipo | central o frente de obra |

### Usuarios y accesos

| Tabla | Campos principales | Notas |
|---|---|---|
| `usuario` | id · nombre · usuario · hash_clave · estado | La contraseña nunca en claro |
| `asignacion` | id · usuario_id · proyecto_id · rol · asignado_por · fecha | **El rol vive aquí, no en `usuario`**: la misma persona puede ser almacenero en una obra y nada en otra |
| `dispositivo` | id · proyecto_id · usuario_id · identificador · estado · autorizado_por · fecha | Máximo dos activos por proyecto |

`rol` es un enum: `almacenero`, `ssoma`, `residente`, `administrador`, `gerente`.

### Catálogo

| Tabla | Campos principales | Notas |
|---|---|---|
| `insumo` | id · **codigo** · descripcion · unidad_s10 · unidad_compra · factor_conversion · tipo · activo | `codigo` **único global**, no por obra. `tipo`: material, mano_obra, equipo, subcontrato |
| `insumo_nombre_comercial` | id · insumo_id · nombre | Un insumo, varios nombres de proveedor |
| `parametro_reposicion` | id · proyecto_id · insumo_id · stock_seguridad · punto_reposicion · nivel_maximo · vigente_desde | Con histórico: nunca se pisa, se inserta una vigencia nueva |
| `requerimiento` | id · proyecto_id · insumo_id · cantidad_planificada · costo_unitario_planificado · fase · origen | Lo que el S10 planificó |

### Compras

| Tabla | Campos principales | Notas |
|---|---|---|
| `proveedor` | id · ruc · razon_social · validado_en | RUC único |
| `compra` | id · proyecto_id · proveedor_id · comprobante · fecha · estado · registrado_por | **Registrar la compra no mueve stock** |
| `compra_detalle` | id · compra_id · insumo_id · cantidad · precio_unitario | |
| `compra_adjunto` | id · compra_id · archivo · subido_por | |

### El corazón: movimiento

| Campo | Para qué |
|---|---|
| `id` | |
| `uuid_cliente` | **UNIQUE.** Generado en el dispositivo. Un reenvío repetido no inserta |
| `proyecto_id` · `almacen_id` · `insumo_id` | |
| `tipo` | ingreso · salida · traslado · reverso |
| `cantidad` | Siempre positiva. El signo lo da el tipo |
| `costo_unitario` | **Congelado** al momento del movimiento |
| `motivo` · `persona` · `frente` | `frente` es opcional |
| `periodo_id` | El periodo en que ocurrió |
| `compra_id` | Solo si es recepción de una compra |
| `movimiento_origen_id` | Solo si es un reverso: apunta al corregido |
| `registrado_por` · `dispositivo_id` | |
| `fecha_dispositivo` | La que dio el celular |
| `fecha_servidor` | **La que vale para auditar** |

Restricciones que el motor debe garantizar, no la aplicación:

- `UNIQUE (uuid_cliente)`
- `CHECK (cantidad > 0)`
- `CHECK (fecha dentro del plazo del proyecto)` — o trigger
- Sin permisos de `UPDATE`/`DELETE` sobre la tabla para el usuario de la aplicación
- Índice por `(proyecto_id, almacen_id, insumo_id)` — es la consulta más frecuente

**El saldo** se calcula: `SUM(cantidad × signo(tipo))` filtrando por almacén e insumo. Si
hiciera falta por rendimiento, una vista materializada que **se reconstruye desde los
movimientos** y nunca se edita a mano.

### Herramientas

| Tabla | Campos principales |
|---|---|
| `herramienta` | id · proyecto_id · codigo · descripcion · estado_actual · fecha_ingreso |
| `herramienta_evento` | id · herramienta_id · tipo (ingreso, entrega, devolucion, mantenimiento, baja) · trabajador_id · estado · motivo · fecha · registrado_por |

Mismo principio: el estado actual se deriva del último evento; los eventos no se borran.

### Mano de obra

| Tabla | Campos principales | Notas |
|---|---|---|
| `trabajador` | id · proyecto_id · nombre · documento · categoria · cuadrilla · fecha_ingreso · fecha_salida | `categoria`: operario, oficial, peon. La baja es `fecha_salida`, no un borrado |
| `jornada` | id · proyecto_id · trabajador_id · fecha · estado · horas_normales · horas_extra · periodo_id · registrado_por · corrige_a_id | `UNIQUE (trabajador_id, fecha)` entre las vigentes. Corregir inserta una nueva que apunta a la anterior |

El costo por hora **no se guarda en la jornada**: sale de la categoría contra el precio del
S10 en `requerimiento`, para que un cambio de precio no reescriba el pasado. Si el cliente
pide congelarlo, se agrega `costo_hora` a la jornada y se declara como decisión.

## Los dos servicios

```
api/            monolito modular · seis módulos, un esquema por módulo
  obra · catalogo · almacen · compras · costo · tareo

integraciones/  microservicio independiente
  importar S10 (relación de insumos, APU, requerimiento)
  validar RUC en SUNAT
```

Por qué separado y no un tercer servicio por dominio: está en `docs/decisiones-arquitectura.md`,
ADR-01. En resumen: aislar lo que depende de terceros para que una importación de 237
insumos no deje lenta la app del almacenero, sin pagar el costo de desplegar seis servicios
con cuatro personas y cuatro semanas.

**Cómo se hablan**: la API llama al microservicio por HTTP y recibe el resultado ya
normalizado. El microservicio **no escribe en la base de datos de la API**: devuelve datos,
la API decide qué guardar. Así el día que el formato del S10 cambie, se toca un solo lugar.

## Lo que el diagrama físico tiene que mostrar

1. Las tablas con sus claves primarias y foráneas.
2. Los **índices únicos** que sostienen las reglas: `uuid_cliente`, `insumo.codigo`,
   `(trabajador_id, fecha)` en jornada, `proveedor.ruc`.
3. Una nota en `movimiento` diciendo que es append-only y que no hay columna de saldo.
4. La frontera entre los dos servicios.
