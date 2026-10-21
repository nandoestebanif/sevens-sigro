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

- **Almacenero** — está en obra. Único que registra entradas y salidas de material desde el dispositivo autorizado. Registra obreros, asistencia, y entrega y devolución de herramientas.
- **Ingeniero SSOMA** — responsable de seguridad y salud ocupacional. Registra y da de baja personal obrero, lleva asistencia y controla herramientas y equipos de protección. No interviene en material, compras ni costos.
- **Residente de obra** — conduce la obra. Configura proyecto, catálogo, requerimiento y parámetros de reposición. No registra movimientos: los consulta y autoriza excepciones.
- **Administrador** — crea usuarios, asigna roles y obras, autoriza dispositivos. Único que cambia quién puede hacer qué.
- **Gerente** — consulta costo, gasto en recursos y estado de las obras. No registra nada operativo.

## Matriz

| Acción | Almacenero | SSOMA | Residente | Admin | Gerente |
|---|:--:|:--:|:--:|:--:|:--:|
| **Preparar la obra** | | | | | |
| Crear el proyecto y su plazo | — | — | R | R | C |
| Ampliar el plazo del proyecto | — | — | R | R | A |
| Definir periodos semanales y día de corte | — | — | R | R | C |
| Registrar los almacenes de la obra | R | — | R | R | C |
| Consultar el estado de los proyectos | C | C | C | C | C |
| **Usuarios y accesos** | | | | | |
| Iniciar sesión | R | R | R | R | R |
| Asignar un rol a una persona | — | — | — | R | — |
| Asignar personas a una obra | — | — | C | R | — |
| Autorizar un dispositivo de registro | — | — | C | R | — |
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
| Consultar y registrar proveedores | — | — | R | R | C |
| Registrar la compra y su comprobante | — | — | R | R | C |
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
| Entregar una herramienta a un trabajador | R | R | C | C | — |
| Registrar la devolución | R | R | C | C | — |
| Consultar quién tiene cada herramienta | C | C | C | C | C |
| Dar de baja una herramienta | R | C | A | C | C |
| **Mano de obra** | | | | | |
| Registrar un trabajador | R | R | C | C | — |
| Dar de baja a un trabajador | R | R | C | C | — |
| Registrar asistencia y horas de la jornada | R | R | C | C | — |
| Consultar las horas acumuladas | C | C | C | C | C |
| **Costo** | | | | | |
| Consultar el inventario valorizado | — | — | C | C | C |
| Comparar el consumo contra lo planificado | — | — | C | C | C |
| Consultar el costo de la mano de obra | — | — | C | C | C |
| Consultar el gasto en recursos de la obra | — | — | C | C | C |

## Pendiente de confirmar con el cliente

- ¿El SSOMA registra también la entrega de los equipos de protección individual?
- ¿Necesita consultar stock de material, o le basta con herramientas y personal?
- ¿Hay más de un ingeniero SSOMA por obra? De eso depende el límite de dos dispositivos.
