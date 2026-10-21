# Pantallas diseñadas

Las doce pantallas del Sprint 1 están diseñadas en un lienzo de Claude, con los datos
reales del presupuesto 2701006 «Multifamiliar Flores». **Pídele el enlace a Fernando**: es
privado y hay que compartirlo uno por uno.

No son bocetos sueltos: cada una sostiene una regla de dominio, y esa regla es lo que no
se puede cambiar al programarla, por mucho que la interfaz cambie.

## App del almacenero · Android · Nicolás

| | Pantalla | Qué tiene que respetar al construirla |
|---|---|---|
| 1 | Iniciar sesión | Avisa que el equipo está autorizado para esa obra; un dispositivo distinto no registra |
| 2 | Mis obras | Solo las obras asignadas. Muestra cuántos movimientos quedan sin enviar |
| 3 | Registrar salida | Exige almacén, insumo, cantidad, motivo y quién retira. El frente es opcional. Muestra el saldo que queda y el valor al costo promedio. Avisa que, una vez registrada, se corrige con un movimiento inverso |
| 4 | Registrar ingreso | Dos caminos: con orden de compra o sin ella. El ingreso sin compra lleva su costo unitario, que es lo que permite valorizar después |
| 5 | Consultar stock | Saldo por almacén, fecha del último movimiento considerado, y marca los insumos bajo su punto de reposición. Dice en pantalla que el saldo lo calcula el servidor |
| 6 | Registrar trabajador | Alta y baja durante la obra, con fecha de ingreso y de salida. Dar de baja no borra |
| 7 | Asistencia de la jornada | Presente, ausente o permiso; horas normales y extra por separado; costo de la jornada por categoría |

La banda amarilla de «sin señal» aparece en las pantallas de registro: es el estado normal
en obra, no un error.

## Web de oficina · Víctor

| | Pantalla | Qué tiene que respetar al construirla |
|---|---|---|
| 8 | Reposición y sugerencia de compra | La alerta nunca va sola: muestra saldo, punto de reposición y demora del proveedor. La cantidad sugerida parte del saldo por comprar más el stock de seguridad |
| 9 | Crear la obra | El plazo acota todo lo que venga después. Explica qué pasa al crearla: los periodos se generan solos |
| 10 | Usuarios, roles y dispositivos | Los cinco roles, las obras asignadas y la autorización de dispositivos, hasta dos por obra |
| 11 | Importar el catálogo del S10 | Resultado con creados, actualizados y rechazados, y el detalle de cada fila rechazada con su número y su motivo |
| 12 | Registrar la compra | Dice en pantalla que **registrar la compra no mueve el stock**. La referencia del S10 es informativa, no un tope |

## Cómo usarlas

El diseño es una guía, no un contrato. Si al construir encuentras algo mejor, cámbialo —
pero avísalo en el Pull Request, porque Víctor acepta la historia contra sus criterios y
conviene que no se lleve una sorpresa.

Lo que **no** se cambia sin hablarlo es lo de la columna derecha: ahí está el dominio, no
la estética.
