# Historias del Sprint 1

29 historias · 152 puntos

## HU-01 · Crear proyecto

> Como residente de obra, quiero crear el proyecto con su nombre y su plazo, para acotar todo el movimiento a ese periodo.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Proyectos / Configuración · **Prioridad**: Crítica · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: ninguna

**Criterios de aceptación**

- [ ] Nombre, fecha de inicio y fecha de fin son obligatorios
- [ ] La fecha de fin no puede ser anterior a la de inicio
- [ ] El proyecto nace en estado "en ejecución"
- [ ] Ningún movimiento puede registrarse con fecha fuera del plazo vigente

## HU-03 · Dividir la obra en periodos semanales

> Como residente de obra, quiero dividir el plazo de la obra en periodos semanales de control, para comparar lo consumido de un periodo con otro sin armar el corte a mano.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Proyectos / Configuración · **Prioridad**: Crítica · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-01

**Criterios de aceptación**

- [ ] Los periodos se generan solos al crear el proyecto, desde la fecha de inicio hasta la de fin
- [ ] El último periodo puede ser más corto que una semana
- [ ] Cada movimiento queda asociado al periodo en que ocurrió
- [ ] Un periodo ya cerrado no admite movimientos nuevos con esa fecha

## HU-07 · Importar catálogo de insumos

> Como residente de obra, quiero importar el catálogo de insumos del S10, para evitar cargar a mano cientos de insumos.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Catálogo de insumos · **Prioridad**: Crítica · **Puntos**: 8
- **Responsable**: Integraciones · Fernando Istaña
- **Depende de**: ninguna

**Criterios de aceptación**

- [ ] La importación acepta el archivo de recursos exportado del S10
- [ ] Un insumo ya existente se actualiza, no se duplica
- [ ] El resultado informa cuántos se crearon, cuántos se actualizaron y cuántos fallaron
- [ ] La importación puede repetirse sin alterar los saldos

## HU-08 · Registrar insumo estandarizado

> Como almacenero, quiero registrar un insumo con información estandarizada, para identificarlo de la misma manera en todas las obras.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Catálogo de insumos · **Prioridad**: Crítica · **Puntos**: 5
- **Responsable**: Backend · Henry Ticona
- **Depende de**: ninguna

**Criterios de aceptación**

- [ ] Material y unidad de medida son obligatorios
- [ ] Medida y norma se exigen cuando el tipo de insumo las tiene
- [ ] El sistema genera un código único e irrepetible
- [ ] La marca es opcional y se exige solo en los tipos que la requieren
- [ ] No se admite un insumo con el mismo material, medida y marca que otro

## HU-12 · Iniciar sesión

> Como administrador, quiero entrar al sistema con mi usuario y contraseña, para que mis registros queden atribuidos a mí.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Usuarios / Seguridad · **Prioridad**: Crítica · **Puntos**: 5
- **Responsable**: Backend · Henry Ticona
- **Depende de**: ninguna

**Criterios de aceptación**

- [ ] El acceso exige usuario y contraseña
- [ ] La contraseña se almacena cifrada, nunca en claro
- [ ] La sesión caduca por inactividad y se renueva sin volver a escribir la contraseña
- [ ] Todo movimiento guarda el usuario que lo registró

## HU-13 · Asignar rol a usuario

> Como administrador, quiero asignar a cada persona un usuario y un rol, para limitar lo que cada quien ve y registra.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Usuarios / Seguridad · **Prioridad**: Crítica · **Puntos**: 5
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-12

**Criterios de aceptación**

- [ ] Los roles son almacenero, residente de obra, administrador y gerente
- [ ] Cada rol tiene permisos declarados de consulta y de registro
- [ ] Un usuario se da de baja cambiando su estado, nunca borrándolo

## HU-14 · Asignar usuarios a proyectos

> Como administrador, quiero asignar usuarios a los proyectos, para que cada quien opere solo en sus obras.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Usuarios / Seguridad · **Prioridad**: Crítica · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-13

**Criterios de aceptación**

- [ ] Un usuario solo ve los proyectos que tiene asignados
- [ ] Un usuario puede estar en varios proyectos
- [ ] Retirar la asignación no borra los movimientos que registró

## HU-16 · Importar análisis de precios unitarios

> Como residente de obra, quiero importar el análisis de precios unitarios del S10, para conocer cuánto material exige cada partida.

- **Épica**: E2. Planificar el requerimiento y la reposición de recursos
- **Módulo**: S10 / Requerimientos · **Prioridad**: Crítica · **Puntos**: 8
- **Responsable**: Integraciones · Fernando Istaña
- **Depende de**: HU-07

**Criterios de aceptación**

- [ ] La importación trae partida, recurso, cantidad por unidad y unidad
- [ ] Las partidas se identifican por código y descripción, porque el reporte trunca el código
- [ ] Un recurso no reconocido se reporta y no detiene la importación

## HU-17 · Registrar factor de conversión

> Como almacenero, quiero registrar el factor entre la unidad del S10 y la unidad de compra, para comparar en una misma unidad lo presupuestado, lo comprado y lo consumido.

- **Épica**: E2. Planificar el requerimiento y la reposición de recursos
- **Módulo**: S10 / Requerimientos · **Prioridad**: Crítica · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-08

**Criterios de aceptación**

- [ ] Aplica solo cuando el recurso del S10 corresponde a un único insumo de compra
- [ ] Sin factor declarado, el sistema asume que ambas unidades son la misma
- [ ] Toda comparación entre requerido, comprado y consumido se hace en la unidad de compra
- [ ] El factor queda registrado con su fecha y el usuario que lo declaró

## HU-19 · Importar requerimiento total de obra

> Como residente de obra, quiero importar el requerimiento total de la obra desde el reporte de recursos del S10, para fijar el cien por ciento contra el que se controla todo.

- **Épica**: E2. Planificar el requerimiento y la reposición de recursos
- **Módulo**: S10 / Requerimientos · **Prioridad**: Crítica · **Puntos**: 5
- **Responsable**: Integraciones · Fernando Istaña
- **Depende de**: HU-07, HU-17

**Criterios de aceptación**

- [ ] El total por insumo se toma del reporte de recursos, no se recalcula
- [ ] Las cantidades se convierten a unidad de compra con el factor o el reparto declarado
- [ ] El residente puede ajustar una cantidad dejando registro del motivo
- [ ] Un insumo que no venga en el reporte puede agregarse manualmente, marcado como tal

## HU-25 · Definir parámetros de reposición

> Como residente de obra, quiero definir para cada insumo su stock de seguridad y su punto de reposición, para saber cuándo reponer y con cuánto colchón frente a la demora del proveedor.

- **Épica**: E2. Planificar el requerimiento y la reposición de recursos
- **Módulo**: Reposición / Planificación · **Prioridad**: Crítica · **Puntos**: 5
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-08

**Criterios de aceptación**

- [ ] El stock de seguridad es el colchón frente a la variabilidad del plazo de entrega
- [ ] El punto de reposición es el saldo que dispara la alerta e incluye el stock de seguridad
- [ ] El nivel máximo se calcula aparte y marca por encima de qué saldo hay sobre-stock
- [ ] Los tres se expresan en unidad de compra y pueden modificarse durante la obra, con histórico
- [ ] Los cuatro conceptos se muestran por separado en pantalla, nunca como un solo "mínimo"

## HU-26 · Avisar punto de reposición

> Como residente de obra, quiero recibir un aviso cuando un insumo baje de su punto de reposición, para reponer antes de que la obra se detenga.

- **Épica**: E2. Planificar el requerimiento y la reposición de recursos
- **Módulo**: Reposición / Planificación · **Prioridad**: Crítica · **Puntos**: 5
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-25, HU-43

**Criterios de aceptación**

- [ ] El punto de reposición es el mínimo absoluto o un porcentaje del requerimiento, según se configure
- [ ] El aviso indica insumo, saldo actual, punto de reposición y cuánto falta por comprar
- [ ] Un mismo insumo no genera avisos repetidos mientras siga bajo el punto
- [ ] El aviso se marca como atendido al registrarse una compra de ese insumo

## HU-27 · Sugerir cantidad a comprar

> Como residente de obra, quiero consultar cuánto conviene comprar de cada insumo, para tener una referencia para armar el pedido.

- **Épica**: E2. Planificar el requerimiento y la reposición de recursos
- **Módulo**: Reposición / Planificación · **Prioridad**: Crítica · **Puntos**: 5
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-24, HU-26

**Criterios de aceptación**

- [ ] La sugerencia es el menor valor entre lo que falta por comprar y lo que exige la fase en curso
- [ ] Se presenta como referencia editable, nunca como pedido automático
- [ ] Se muestra el criterio con el que se calculó

## HU-32 · Registrar compra

> Como almacenero, quiero registrar la compra con proveedor, comprobante y productos, para dejar constancia de lo adquirido.

- **Épica**: E3. Registrar las compras y sus recepciones
- **Módulo**: Compras / Recepciones · **Prioridad**: Crítica · **Puntos**: 8
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-29, HU-30

**Criterios de aceptación**

- [ ] Exige proveedor, fecha, tipo y número de comprobante, y al menos un insumo con cantidad y precio
- [ ] Registra base imponible, impuesto y total
- [ ] No se admiten dos comprobantes con el mismo tipo y número del mismo proveedor
- [ ] La compra no modifica el stock por sí sola: el stock se mueve con la recepción

## HU-35 · Registrar recepción de compra

> Como almacenero, quiero registrar la recepción total o parcial de una compra, para actualizar el stock solo con lo realmente recibido.

- **Épica**: E3. Registrar las compras y sus recepciones
- **Módulo**: Compras / Recepciones · **Prioridad**: Crítica · **Puntos**: 8
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-32

**Criterios de aceptación**

- [ ] Una compra admite varias recepciones hasta completar lo comprado
- [ ] No se puede recibir más de lo comprado de cada insumo
- [ ] Cada recepción genera el ingreso al almacén por la cantidad recibida
- [ ] La compra muestra pedido, recibido y pendiente por insumo
- [ ] Por defecto se propone la recepción total, que es el caso habitual en SEVENS

## HU-37 · Registrar movimientos sin conexión

> Como almacenero, quiero registrar un movimiento aunque el celular no tenga señal, para no interrumpir el trabajo en obra, donde la señal falla.

- **Épica**: E4. Controlar el movimiento de materiales en almacén
- **Módulo**: Offline / Sincronización · **Prioridad**: Crítica · **Puntos**: 8
- **Responsable**: Android · Nicolás Pachamora
- **Depende de**: HU-15

**Criterios de aceptación**

- [ ] El movimiento se guarda en el dispositivo y queda en cola de envío
- [ ] Cada movimiento lleva un identificador único generado en el dispositivo
- [ ] La pantalla indica que el movimiento está pendiente de enviar
- [ ] Se guarda la hora del dispositivo junto con el movimiento

## HU-38 · Sincronizar movimientos pendientes

> Como almacenero, quiero enviar al servidor los movimientos que quedaron en cola, para que el saldo del sistema refleje lo que ya ocurrió en obra.

- **Épica**: E4. Controlar el movimiento de materiales en almacén
- **Módulo**: Offline / Sincronización · **Prioridad**: Crítica · **Puntos**: 5
- **Responsable**: Android · Nicolás Pachamora
- **Depende de**: HU-37

**Criterios de aceptación**

- [ ] El envío se reintenta solo cuando vuelve la señal
- [ ] Un reenvío con el mismo identificador no duplica el movimiento
- [ ] El saldo lo recalcula el servidor al recibir, nunca el dispositivo
- [ ] Un movimiento rechazado queda con su motivo y no se pierde
- [ ] La hora que vale para auditar es la del servidor

## HU-40 · Registrar salida de material

> Como almacenero, quiero registrar la salida con su motivo y quién retira el material, para saber en qué se usó y quién responde por él.

- **Épica**: E4. Controlar el movimiento de materiales en almacén
- **Módulo**: Almacén / Kardex · **Prioridad**: Crítica · **Puntos**: 8
- **Responsable**: Android · Nicolás Pachamora
- **Depende de**: HU-35

**Criterios de aceptación**

- [ ] Exige almacén, insumo, cantidad, motivo y persona que retira
- [ ] El frente o la cuadrilla de destino es opcional y sirve para ubicar el consumo sin exigir la partida
- [ ] No se puede sacar más de lo disponible en ese almacén
- [ ] La salida queda valorizada al costo promedio ponderado vigente en ese instante
- [ ] El control del costo se hace por insumo (HU-55), no por partida: el cliente pidió postergar la imputación a partidas

## HU-43 · Consultar stock de insumos

> Como residente de obra, quiero consultar el stock de cada insumo en cualquier momento, para programar la jornada sabiendo con qué cuento.

- **Épica**: E4. Controlar el movimiento de materiales en almacén
- **Módulo**: Almacén / Kardex · **Prioridad**: Crítica · **Puntos**: 5
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-40

**Criterios de aceptación**

- [ ] Muestra saldo por almacén y consolidado por proyecto
- [ ] Indica la fecha y hora del último movimiento considerado
- [ ] Señala los insumos por debajo de su punto de reposición

## HU-45 · Corregir movimiento con reverso

> Como almacenero, quiero corregir un movimiento sin borrar el original, para conservar el rastro de lo registrado y lo corregido.

- **Épica**: E4. Controlar el movimiento de materiales en almacén
- **Módulo**: Almacén / Kardex · **Prioridad**: Crítica · **Puntos**: 5
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-40

**Criterios de aceptación**

- [ ] La corrección genera un movimiento de reverso que apunta al original
- [ ] El original nunca se modifica ni se elimina
- [ ] El saldo se recalcula considerando ambos movimientos
- [ ] La consulta permite ver el movimiento anulado y el que lo anuló

## HU-55 · Valorizar salida de material

> Como gerente, quiero consultar cada salida valorizada al costo promedio ponderado, para conocer el costo de lo que se consumió.

- **Épica**: E6. Valorizar el consumo e informar el costo de obra
- **Módulo**: Valorización / Costos · **Prioridad**: Crítica · **Puntos**: 8
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-35, HU-40

**Criterios de aceptación**

- [ ] Cada recepción recalcula el promedio ponderado del insumo en la obra
- [ ] Cada salida congela el costo unitario vigente en ese instante y no cambia después
- [ ] El criterio aplicado se declara en el reporte, conforme a la NIC 2

## HU-57 · Comparar consumo contra lo planificado

> Como gerente, quiero comparar el consumo valorizado de cada insumo contra lo que se planificó para la obra, para detectar la desviación de costo mientras la obra corre.

- **Épica**: E6. Valorizar el consumo e informar el costo de obra
- **Módulo**: Valorización / Costos · **Prioridad**: Crítica · **Puntos**: 8
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-19, HU-55

**Criterios de aceptación**

- [ ] Se compara el consumo real contra la cantidad y el costo que el reporte de recursos del S10 planificó para ese insumo
- [ ] La desviación se muestra en cantidad, en importe y en porcentaje
- [ ] Los insumos no planificados se informan aparte y no distorsionan la desviación
- [ ] El reporte advierte que lo planificado incluye desperdicio y reserva, de modo que una desviación moderada es esperable
- [ ] Permite ordenar por mayor desviación en importe

## HU-63 · Registrar asistencia de la jornada

> Como almacenero, quiero registrar la asistencia y las horas trabajadas de cada jornada, para tener el tareo de la obra en el sistema y no en un cuaderno.

- **Épica**: E7. Controlar la asistencia de la mano de obra
- **Módulo**: Mano de obra · **Prioridad**: Crítica · **Puntos**: 5
- **Responsable**: Android · Nicolás Pachamora
- **Depende de**: HU-62

**Criterios de aceptación**

- [ ] Se registra por obra y por día: presente, ausente o permiso
- [ ] Al presente se le anotan las horas normales y, por separado, las horas extra
- [ ] Una jornada ya registrada se corrige con un registro nuevo que deja constancia del anterior
- [ ] No se puede registrar asistencia con fecha fuera del plazo vigente de la obra
- [ ] El registro del día queda cerrado y no admite dos asistencias para el mismo trabajador

## HU-15 · Autorizar dispositivo de registro

> Como administrador, quiero autorizar el dispositivo que registra movimientos en cada obra, para impedir que un equipo ajeno opere en nombre de la obra.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Usuarios / Seguridad · **Prioridad**: Alta · **Puntos**: 5
- **Responsable**: Android · Nicolás Pachamora
- **Depende de**: HU-13

**Criterios de aceptación**

- [ ] El dispositivo se identifica por un código que la aplicación genera en la primera instalación
- [ ] Cada obra admite un dispositivo activo a la vez
- [ ] Un registro que llegue de un dispositivo no autorizado se rechaza y se informa
- [ ] Cambiar de dispositivo exige autorización del administrador y deja registro

## HU-23 · Consultar avance de compra y consumo

> Como residente de obra, quiero consultar qué porcentaje de cada insumo se compró y se consumió, para distinguir lo que ingresó de lo que se usó.

- **Épica**: E2. Planificar el requerimiento y la reposición de recursos
- **Módulo**: Reposición / Planificación · **Prioridad**: Alta · **Puntos**: 5
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-19, HU-35, HU-40

**Criterios de aceptación**

- [ ] Se muestran requerido, comprado, recibido, consumido y saldo por comprar
- [ ] Los porcentajes se calculan sobre el requerimiento total de la obra
- [ ] Todo se expresa en la unidad de compra

## HU-24 · Consultar saldo por comprar

> Como residente de obra, quiero consultar cuánto falta comprar de cada insumo, para formular el siguiente requerimiento sobre una base cierta.

- **Épica**: E2. Planificar el requerimiento y la reposición de recursos
- **Módulo**: Reposición / Planificación · **Prioridad**: Alta · **Puntos**: 3
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-23

**Criterios de aceptación**

- [ ] Falta por comprar = requerido − comprado
- [ ] Se puede filtrar por fase y por insumo crítico
- [ ] La consulta se exporta para armar el pedido

## HU-29 · Consultar proveedores

> Como almacenero, quiero consultar la lista de proveedores, para elegir uno sin volver a escribir sus datos.

- **Épica**: E3. Registrar las compras y sus recepciones
- **Módulo**: Proveedores · **Prioridad**: Alta · **Puntos**: 2
- **Responsable**: Web · Víctor Luján
- **Depende de**: ninguna

**Criterios de aceptación**

- [ ] Se busca por razón social o RUC
- [ ] Se muestran razón social, RUC y estado
- [ ] Un proveedor dado de baja no aparece al registrar compras nuevas

## HU-62 · Registrar trabajador de la obra

> Como almacenero, quiero registrar a cada trabajador de la obra con su categoría y su cuadrilla, para saber quiénes componen la mano de obra de esa obra.

- **Épica**: E7. Controlar la asistencia de la mano de obra
- **Módulo**: Mano de obra · **Prioridad**: Alta · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: ninguna

**Criterios de aceptación**

- [ ] Exige nombre, documento, categoría (operario, oficial o peón) y cuadrilla
- [ ] La lista se arma al iniciar la obra y se puede modificar mientras la obra corre
- [ ] Un trabajador nuevo se incorpora en cualquier momento del plazo, con su fecha de ingreso
- [ ] Un trabajador que se retira se da de baja con su fecha de salida: no se borra, y sus jornadas ya registradas se conservan
- [ ] Un trabajador dado de baja no aparece en la asistencia de las jornadas siguientes
- [ ] Un mismo trabajador puede estar en más de una obra, pero no dos veces en la misma
- [ ] La categoría determina el costo por hora que se aplica después

## HU-30 · Registrar proveedor

> Como almacenero, quiero crear un proveedor desde el formulario de compra, para no interrumpir el registro cuando aún no existe.

- **Épica**: E3. Registrar las compras y sus recepciones
- **Módulo**: Proveedores · **Prioridad**: Media · **Puntos**: 3
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-29

**Criterios de aceptación**

- [ ] Exige RUC y razón social como mínimo
- [ ] Al guardarlo queda seleccionado en la compra en curso
- [ ] No se admiten dos proveedores con el mismo RUC
