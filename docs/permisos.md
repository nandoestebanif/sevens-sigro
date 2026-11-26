# Matriz de roles y permisos

El control de acceso se verifica **en el servidor**, nunca en la pantalla. Cada endpoint
declara el rol y el alcance que exige. Todo permiso aplica solo a las obras donde la
persona está asignada.

| Marca | Significado |
|---|---|
| **R** | Registra. Crea el dato y responde por su exactitud. |
| **C** | Consulta. Lo ve, no lo modifica. |
| **A** | Autoriza. Otro ejecuta, pero no surte efecto sin su aprobación. |
| **—** | Sin acceso. El servidor rechaza la operación aunque llegue la petición. |

## Los cinco roles

- **Almacenero** — está en obra. Registra entradas, salidas, traslados, conteos y el préstamo y devolución de herramientas desde el dispositivo autorizado. **Desde el 25/11 ya no registra personal ni asistencia** (decisión del PO): eso es del SSOMA.
- **Ingeniero SSOMA** — responsable de seguridad y salud ocupacional. Registra y da de baja personal obrero y lleva la asistencia y las horas. Consulta herramientas y stock. No interviene en material, compras ni costos.
- **Residente de obra** — conduce la obra. Configura proyecto, catálogo, requerimiento y parámetros de reposición. No registra movimientos: los consulta y autoriza excepciones.
- **Administrador** — crea usuarios, asigna roles y obras, autoriza dispositivos y consulta la bitácora. **Desde el 25/11 puede hacer todas las acciones** (decisión del PO); lo de administrar (usuarios, roles, celulares, bitácora) le pide además su **PIN** de 6 dígitos, que valida el servidor.
- **Gerente** — consulta costo, gasto en recursos y estado de las obras. No registra nada operativo.

## Matriz

| Acción | Almacenero | SSOMA | Residente | Admin | Gerente |
|---|:--:|:--:|:--:|:--:|:--:|
| **Preparar la obra** | | | | | |
| Crear el proyecto y su plazo | — | — | R | R | C |
| Ampliar el plazo del proyecto | — | — | R | R | A |
| Cerrar la obra sin borrar nada (25/11; queda «cerrada», al final de la lista, y no se reabre) | — | — | C | R | C |
| Definir periodos semanales y día de corte | — | — | R | R | C |
| Registrar los almacenes de la obra | R | — | R | R | C |
| Consultar el estado de los proyectos | C | C | C | C | C |
| **Usuarios y accesos** | | | | | |
| Iniciar sesión | R | R | R | R | R |
| Asignar un rol a una persona | — | — | — | R | — |
| Asignar personas a una obra | — | — | C | R | — |
| Autorizar un dispositivo de registro | — | — | C | R | — |
| Ver el estado de su propio celular | C | C | — | — | — |
| **Catálogo de insumos** | | | | | |
| Importar el catálogo desde el S10 | — | — | R | R | — |
| Registrar un insumo estandarizado | R | — | R | R | — |
| Nombres comerciales y búsqueda | R | C | R | R | C |
| Registrar el factor de conversión | R | — | R | R | — |
| **Requerimiento y reposición** | | | | | |
| Importar el requerimiento total | — | — | R | R | — |
| Definir parámetros de reposición | — | — | R | R | C |
| Recibir el aviso de punto de reposición | C | — | R | C | C |
| Consultar la sugerencia de compra | — | — | R | C | C |
| Agregar un insumo no planificado | C | — | R | R | — |
| **Compras y recepciones** | | | | | |
| Consultar y registrar proveedores | R | — | R | R | C |
| Registrar la compra y su comprobante (CR-05) | R | — | R | R | C |
| Consultar las compras para recibirlas | C | — | R | R | C |
| Registrar la recepción de la compra | R | — | C | C | C |
| **Movimiento de almacén** | | | | | |
| Registrar ingreso de material sin compra | R | — | C | C | C |
| Registrar la salida de material | R | — | C | C | C |
| Consultar el stock de la obra | C | C | C | C | C |
| Corregir un movimiento con su reverso | R | — | A | C | C |
| Trasladar material entre obras | R | — | A | C | C |
| Registrar el conteo físico | R | — | C | C | C |
| Cerrar la obra y transferir el saldo | — | — | R | A | C |
| **Herramientas y equipos** | | | | | |
| Registrar el ingreso de herramientas | R | R | C | C | C |
| Entregar una herramienta a un trabajador (préstamo, 25/11) | R | C | C | R | — |
| Registrar la devolución | R | C | C | R | — |
| Consultar quién tiene cada herramienta | C | C | C | C | C |
| Dar de baja una herramienta | R | C | A | C | C |
| **Mano de obra** | | | | | |
| Registrar un trabajador | — | R | C | R | — |
| Dar de baja a un trabajador | — | R | C | R | — |
| Corregir nombres, apellidos, categoría o cuadrilla de un trabajador (25/11) | — | R | C | R | — |
| Registrar asistencia y horas de la jornada | — | R | C | R | — |
| Consultar las horas acumuladas | C | C | C | C | C |
| **Auditoría (25/11)** | | | | | |
| Consultar la bitácora de operaciones (con PIN) | — | — | — | C | — |
| **Costo** | | | | | |
| Consultar el inventario valorizado | — | — | C | C | C |
| Comparar el consumo contra lo planificado | — | — | C | C | C |
| Consultar el costo de la mano de obra | — | — | C | C | C |
| Consultar el gasto en recursos de la obra | — | — | C | C | C |


## Matriz que aplica el servidor (fuente única: `backend-spring/…/seguridad/Permisos.java`, 26/11)

La tabla de arriba describe la responsabilidad de cada rol en el negocio. La que **decide** es esta: cada endpoint llama a
`Acceso.exigir(acción, obra)` y responde 403 si el rol de la persona **en esa obra** no está. La web tiene una copia
(`web/src/api/permisos.ts`) solo para ocultar botones, y `permisos.test.ts` falla si se desfasan. Con ✓ = permitido;
🔒 = además pide el PIN del administrador (`Acceso.CON_PIN`).

| Acción | Almacenero | SSOMA | Residente | Admin | Gerente |
|---|:--:|:--:|:--:|:--:|:--:|
| `proyecto.crear` | — | — | ✓ | ✓ | — |
| `proyecto.ampliar_plazo` | — | — | ✓ | ✓ | — |
| `proyecto.cerrar` | — | — | — | ✓ | — |
| `proyecto.consultar` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `periodo.consultar` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `periodo.cerrar` | — | — | ✓ | ✓ | — |
| `almacen.registrar` | ✓ | — | ✓ | ✓ | — |
| `almacen.consultar` | ✓ | — | ✓ | ✓ | ✓ |
| `usuario.crear` | — | — | — | ✓🔒 | — |
| `usuario.consultar` | — | — | — | ✓🔒 | — |
| `asignacion.escribir` | — | — | — | ✓🔒 | — |
| `asignacion.consultar` | — | — | ✓ | ✓ | — |
| `dispositivo.solicitar` | ✓ | ✓ | — | ✓ | — |
| `dispositivo.autorizar` | — | — | — | ✓🔒 | — |
| `dispositivo.consultar` | — | — | ✓ | ✓ | — |
| `trabajador.registrar` | — | ✓ | — | ✓ | — |
| `trabajador.consultar` | ✓ | ✓ | ✓ | ✓ | — |
| `jornada.registrar` | — | ✓ | — | ✓ | — |
| `horas.consultar` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `proveedor.registrar` | ✓ | — | ✓ | ✓ | — |
| `proveedor.consultar` | ✓ | — | ✓ | ✓ | ✓ |
| `compra.registrar` | ✓ | — | ✓ | ✓ | — |
| `compra.consultar` | ✓ | — | ✓ | ✓ | ✓ |
| `solicitud.emitir` | — | — | ✓ | ✓ | — |
| `solicitud.consultar` | — | — | ✓ | ✓ | ✓ |
| `requerimiento.importar` | — | — | ✓ | ✓ | — |
| `requerimiento.ajustar` | — | — | ✓ | ✓ | — |
| `requerimiento.agregar` | — | — | ✓ | ✓ | — |
| `requerimiento.consultar` | — | — | ✓ | ✓ | ✓ |
| `partida.importar` | — | — | ✓ | ✓ | — |
| `partida.consultar` | — | — | ✓ | ✓ | ✓ |
| `reposicion.registrar` | — | — | ✓ | ✓ | — |
| `reposicion.consultar` | — | — | ✓ | ✓ | ✓ |
| `critico.marcar` | — | — | ✓ | ✓ | — |
| `avance.consultar` | — | — | ✓ | ✓ | ✓ |
| `salida.registrar` | ✓ | — | — | ✓ | — |
| `ingreso.registrar` | ✓ | — | — | ✓ | — |
| `recepcion.registrar` | ✓ | — | — | ✓ | — |
| `traslado.registrar` | ✓ | — | — | ✓ | — |
| `conteo.registrar` | ✓ | — | — | ✓ | — |
| `movimiento.revertir` | ✓ | — | ✓ | ✓ | — |
| `stock.consultar` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `herramienta.prestar` | ✓ | — | — | ✓ | — |
| `herramienta.consultar` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `costo.consultar` | — | — | ✓ | ✓ | ✓ |
| `insumo.consultar` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `insumo.registrar` | ✓ | — | ✓ | ✓ | — |
| `insumo.verificar` | — | — | ✓ | ✓ | — |
| `insumo.factor` | — | — | ✓ | ✓ | — |
| `bitacora.consultar` | — | — | — | ✓🔒 | — |
| `insumo.importar` | — | — | ✓ | ✓ | — |

51 acciones. Pruebas: `api/pruebas/test_api_acceso.py` recorre la matriz completa contra el servidor.

## Pendiente de confirmar con el cliente

- ¿El SSOMA registra también la entrega de los equipos de protección individual?
- ¿Necesita consultar stock de material, o le basta con herramientas y personal?
- ¿Hay más de un ingeniero SSOMA por obra? De eso depende el límite de dos dispositivos.
