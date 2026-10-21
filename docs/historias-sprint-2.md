# Historias del Sprint 2

31 historias · 107 puntos

## HU-02 · Ampliar plazo del proyecto

> Como residente de obra, quiero ampliar el plazo del proyecto dejando registro de quién y cuándo, para que la obra pueda seguir registrando movimientos después de la fecha original.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Proyectos / Configuración · **Prioridad**: Alta · **Puntos**: 2
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-01

**Criterios de aceptación**

- [ ] La nueva fecha de fin debe ser posterior a la vigente
- [ ] Se conserva el histórico de todas las ampliaciones con usuario y fecha
- [ ] Las semanas de control se extienden automáticamente

## HU-05 · Registrar almacenes de la obra

> Como almacenero, quiero registrar más de un almacén en un mismo proyecto, para separar los frentes de obra distantes.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Proyectos / Configuración · **Prioridad**: Alta · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-01

**Criterios de aceptación**

- [ ] Un proyecto admite uno o varios almacenes
- [ ] Todo movimiento indica en qué almacén ocurre
- [ ] El saldo puede consultarse por almacén y consolidado por proyecto

## HU-18 · Repartir acero por diámetro

> Como residente de obra, quiero declarar cómo se reparte un recurso genérico del S10 entre los insumos con que se compra, para convertir a varillas por diámetro el acero que el presupuesto entrega como kilos totales.

- **Épica**: E2. Planificar el requerimiento y la reposición de recursos
- **Módulo**: S10 / Requerimientos · **Prioridad**: Alta · **Puntos**: 5
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-17

**Criterios de aceptación**

- [ ] Aplica cuando un recurso del S10 corresponde a varios insumos de compra
- [ ] El reparto se declara una vez por obra, con el dato de la hoja de metrados
- [ ] La suma del reparto debe igualar el total del recurso genérico
- [ ] Cada insumo resultante conserva su propio factor de conversión
- [ ] El reparto queda registrado con su fecha y el usuario que lo declaró

## HU-20 · Registrar requerimiento por fase

> Como residente de obra, quiero registrar requerimientos parciales por fase, para pedir el material por etapas.

- **Épica**: E2. Planificar el requerimiento y la reposición de recursos
- **Módulo**: Reposición / Planificación · **Prioridad**: Alta · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-19

**Criterios de aceptación**

- [ ] Cada requerimiento parcial indica fase, insumo y cantidad
- [ ] La suma de los parciales se muestra contra el total de la obra
- [ ] Un parcial puede corregirse mientras no tenga compras asociadas

## HU-21 · Avisar exceso del requerimiento

> Como residente de obra, quiero recibir un aviso cuando los parciales superen el total, para frenar un pedido que excede el presupuesto.

- **Épica**: E2. Planificar el requerimiento y la reposición de recursos
- **Módulo**: Reposición / Planificación · **Prioridad**: Alta · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-20

**Criterios de aceptación**

- [ ] El aviso aparece al guardar el parcial que provoca el exceso
- [ ] Indica insumo, total presupuestado, acumulado de parciales y exceso
- [ ] El aviso no bloquea: el residente puede continuar dejando registro del motivo

## HU-31 · Validar RUC en SUNAT

> Como almacenero, quiero validar el RUC del proveedor contra el padrón de SUNAT, para asegurar que el comprobante corresponda a quien dice.

- **Épica**: E3. Registrar las compras y sus recepciones
- **Módulo**: Proveedores · **Prioridad**: Alta · **Puntos**: 5
- **Responsable**: Integraciones · Fernando Istaña
- **Depende de**: HU-30

**Criterios de aceptación**

- [ ] El dígito verificador se valida siempre, incluso sin conexión
- [ ] Con conexión se consulta SUNAT y se autocompleta la razón social
- [ ] Si el servicio no responde, el proveedor se guarda marcado como "por validar" y el registro continúa

## HU-34 · Adjuntar comprobante de compra

> Como almacenero, quiero adjuntar la factura en foto o PDF, para guardar el respaldo junto a la operación.

- **Épica**: E3. Registrar las compras y sus recepciones
- **Módulo**: Compras / Recepciones · **Prioridad**: Alta · **Puntos**: 3
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-32

**Criterios de aceptación**

- [ ] Se admiten JPG, PNG y PDF
- [ ] Una compra admite más de un archivo
- [ ] El archivo puede descargarse después desde la compra

## HU-39 · Mostrar estado de sincronización

> Como almacenero, quiero distinguir cuáles de mis registros ya se sincronizaron, para confiar en que nada quedó sin enviar.

- **Épica**: E4. Controlar el movimiento de materiales en almacén
- **Módulo**: Offline / Sincronización · **Prioridad**: Alta · **Puntos**: 5
- **Responsable**: Android · Nicolás Pachamora
- **Depende de**: HU-38

**Criterios de aceptación**

- [ ] Cada movimiento muestra si está pendiente, enviado o rechazado
- [ ] Un movimiento rechazado indica el motivo y puede corregirse
- [ ] El contador de pendientes es visible en todo momento

## HU-42 · Trasladar material entre obras

> Como almacenero, quiero trasladar material de una obra a otra, para aprovechar el stock sin descuadrar ningún saldo.

- **Épica**: E4. Controlar el movimiento de materiales en almacén
- **Módulo**: Almacén / Kardex · **Prioridad**: Alta · **Puntos**: 5
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-40

**Criterios de aceptación**

- [ ] Solo se traslada hasta el stock disponible en el almacén de origen
- [ ] Genera una salida en origen y un ingreso en destino
- [ ] Ambos movimientos comparten un identificador de traslado
- [ ] El costo unitario viaja con el material al almacén de destino
- [ ] Anular el traslado genera dos movimientos de reverso, nunca un borrado

## HU-46 · Registrar conteo físico

> Como almacenero, quiero registrar el conteo físico y justificar cada diferencia, para cuadrar el saldo del sistema con el almacén.

- **Épica**: E4. Controlar el movimiento de materiales en almacén
- **Módulo**: Almacén / Kardex · **Prioridad**: Alta · **Puntos**: 5
- **Responsable**: Android · Nicolás Pachamora
- **Depende de**: HU-43

**Criterios de aceptación**

- [ ] El conteo se registra por almacén y fecha
- [ ] El sistema muestra saldo teórico, contado y diferencia por insumo
- [ ] Toda diferencia exige un motivo antes de confirmarse
- [ ] Al confirmar, se genera un movimiento de ajuste por cada diferencia
- [ ] El ajuste se valoriza al costo promedio ponderado vigente

## HU-47 · Cerrar obra y transferir saldo

> Como almacenero, quiero registrar el inventario final de la obra y transferir el saldo al almacén central, para que el material sobrante quede disponible para la siguiente obra.

- **Épica**: E4. Controlar el movimiento de materiales en almacén
- **Módulo**: Almacén / Kardex · **Prioridad**: Alta · **Puntos**: 5
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-46, HU-42

**Criterios de aceptación**

- [ ] Al cerrar la obra se lista el saldo de cada insumo con su costo promedio ponderado
- [ ] El almacenero confirma el conteo físico de ese saldo antes de cerrar
- [ ] El saldo confirmado genera un traslado al almacén central, conservando el costo unitario
- [ ] La obra no puede cerrarse con saldos sin confirmar
- [ ] El cierre produce un acta con lo comprado, lo consumido y lo transferido

## HU-48 · Registrar ingreso de herramientas

> Como almacenero, quiero registrar las herramientas y equipos que entran a obra con su estado inicial, para saber con qué se cuenta y en qué condición llegó.

- **Épica**: E5. Controlar las existencias de herramientas y equipos
- **Módulo**: Herramientas / Equipos · **Prioridad**: Alta · **Puntos**: 5
- **Responsable**: Android · Nicolás Pachamora
- **Depende de**: HU-01

**Criterios de aceptación**

- [ ] Cada herramienta se identifica individualmente, no por cantidad
- [ ] Exige descripción, código y estado de ingreso
- [ ] El estado se elige de una lista: nueva, buena, regular, deteriorada
- [ ] Admite fotos como respaldo del estado declarado

## HU-49 · Entregar herramienta a trabajador

> Como almacenero, quiero registrar a quién se entrega una herramienta, para conocer quién es responsable de ella durante su uso.

- **Épica**: E5. Controlar las existencias de herramientas y equipos
- **Módulo**: Herramientas / Equipos · **Prioridad**: Alta · **Puntos**: 3
- **Responsable**: Android · Nicolás Pachamora
- **Depende de**: HU-48

**Criterios de aceptación**

- [ ] Exige herramienta, persona que recibe y fecha
- [ ] Una herramienta entregada no puede volver a entregarse sin devolverse antes
- [ ] La herramienta queda en estado "en uso" con su responsable visible

## HU-50 · Registrar devolución de herramienta

> Como almacenero, quiero registrar la devolución de una herramienta y el estado en que vuelve, para conocer su disponibilidad y su condición actual.

- **Épica**: E5. Controlar las existencias de herramientas y equipos
- **Módulo**: Herramientas / Equipos · **Prioridad**: Alta · **Puntos**: 3
- **Responsable**: Android · Nicolás Pachamora
- **Depende de**: HU-49

**Criterios de aceptación**

- [ ] Exige fecha, estado de devolución y quién la devuelve
- [ ] Si el estado empeoró respecto a la entrega, se exige observación
- [ ] La herramienta vuelve a estado "disponible" y puede entregarse de nuevo
- [ ] Admite fotos como respaldo

## HU-51 · Consultar responsable de herramienta

> Como residente de obra, quiero consultar dónde está y quién tiene cada herramienta, para ubicarla sin preguntar de persona en persona.

- **Épica**: E5. Controlar las existencias de herramientas y equipos
- **Módulo**: Herramientas / Equipos · **Prioridad**: Alta · **Puntos**: 3
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-49

**Criterios de aceptación**

- [ ] Lista las herramientas de la obra con su estado y su responsable actual
- [ ] Permite filtrar por disponible, en uso, en mantenimiento o dada de baja
- [ ] Muestra el historial de entregas y devoluciones de cada una

## HU-54 · Declarar estado de herramientas al cierre

> Como almacenero, quiero declarar el estado de cada herramienta al cerrar la obra, para sustentar el desgaste frente a la gerencia.

- **Épica**: E5. Controlar las existencias de herramientas y equipos
- **Módulo**: Herramientas / Equipos · **Prioridad**: Alta · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-48, HU-50

**Criterios de aceptación**

- [ ] Al cerrar la obra se exige estado final de cada herramienta ingresada
- [ ] Las que no fueron devueltas aparecen con su responsable
- [ ] El cierre produce un acta con ingresadas, devueltas, dadas de baja y faltantes

## HU-56 · Consultar inventario valorizado

> Como administrador, quiero consultar el inventario valorizado por obra, para saber cuánto capital está detenido en almacén.

- **Épica**: E6. Valorizar el consumo e informar el costo de obra
- **Módulo**: Valorización / Costos · **Prioridad**: Alta · **Puntos**: 3
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-55

**Criterios de aceptación**

- [ ] Muestra cantidad, costo unitario promedio y valor por insumo
- [ ] Se consulta por almacén y consolidado por proyecto
- [ ] Indica la fecha de corte del cálculo

## HU-58 · Consultar el gasto en recursos de la obra

> Como gerente, quiero consultar cuánto se gastó en los recursos de la obra: material, herramientas y horas hombre, para saber en qué estado está el dinero puesto en los recursos.

- **Épica**: E6. Valorizar el consumo e informar el costo de obra
- **Módulo**: Reportes / Administración · **Prioridad**: Alta · **Puntos**: 5
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-32, HU-35, HU-40

**Criterios de aceptación**

- [ ] Comprado: valor de las compras registradas
- [ ] Recibido: valor de lo efectivamente ingresado al almacén
- [ ] Consumido: valor de las salidas de material, valorizadas al costo promedio ponderado
- [ ] En stock: valor del inventario que queda
- [ ] Horas hombre: valor de las jornadas registradas, con sus horas extra aparte
- [ ] El tablero declara que informa el gasto en recursos y no el costo total de la obra: quedan fuera los gastos generales, los subcontratos, los servicios y la planilla
- [ ] Cada cifra indica su definición y su fecha de corte, para no confundirse entre sí

## HU-64 · Consultar horas acumuladas

> Como residente de obra, quiero consultar las horas acumuladas por trabajador y por cuadrilla, para saber cuánta mano de obra lleva consumida la obra.

- **Épica**: E7. Controlar la asistencia de la mano de obra
- **Módulo**: Mano de obra · **Prioridad**: Alta · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-63

**Criterios de aceptación**

- [ ] Muestra horas normales y horas extra por separado
- [ ] Se puede consultar por trabajador, por cuadrilla y por periodo
- [ ] Indica la fecha de la última jornada considerada

## HU-65 · Consultar costo de la mano de obra

> Como gerente, quiero consultar el costo de las horas trabajadas junto al costo del material, para conocer cuánto lleva gastado la obra en sus recursos.

- **Épica**: E7. Controlar la asistencia de la mano de obra
- **Módulo**: Mano de obra · **Prioridad**: Alta · **Puntos**: 5
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-64

**Criterios de aceptación**

- [ ] Las horas se valorizan al costo por hora de la categoría, tomado del reporte de recursos del S10
- [ ] Las horas acumuladas se comparan contra las horas hombre que el S10 planificó para cada categoría
- [ ] La desviación se muestra en horas y en importe, por categoría y para toda la obra
- [ ] Las horas extra se valorizan con su recargo y se informan por separado
- [ ] El costo de la mano de obra se muestra junto al del material y al de las herramientas
- [ ] El reporte advierte que el costo es referencial y no reemplaza a la planilla

## HU-04 · Definir el día de corte del periodo

> Como residente de obra, quiero definir en qué día de la semana cierra cada periodo de control, para que el corte coincida con el que exige el contratante de la obra.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Proyectos / Configuración · **Prioridad**: Media · **Puntos**: 2
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-03

**Criterios de aceptación**

- [ ] El día de corte se elige al crear el proyecto y por defecto es el domingo
- [ ] Cambiarlo vuelve a generar los periodos sin alterar los movimientos ya registrados
- [ ] Cada movimiento se reubica en el periodo que le corresponde con el nuevo corte

## HU-06 · Mostrar estado de proyectos

> Como gerente, quiero consultar los proyectos con su estado y su plazo, para entrar directo al que necesito revisar.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Proyectos / Configuración · **Prioridad**: Media · **Puntos**: 2
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-01

**Criterios de aceptación**

- [ ] Se listan solo los proyectos a los que el usuario está asignado
- [ ] Se muestran nombre, estado, fecha de inicio y de fin

## HU-09 · Registrar nombres comerciales del insumo

> Como almacenero, quiero registrar nombres comerciales alternativos de un insumo, para encontrarlo aunque el proveedor use otra denominación.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Catálogo de insumos · **Prioridad**: Media · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-08

**Criterios de aceptación**

- [ ] Un insumo admite varios nombres alternativos
- [ ] Un mismo nombre alternativo no puede apuntar a dos insumos
- [ ] Al escribir un nombre alternativo ya registrado, el sistema advierte antes de crear uno nuevo

## HU-10 · Buscar insumo

> Como almacenero, quiero buscar un insumo por cualquiera de sus nombres, para no crear un duplicado por error.

- **Épica**: E1. Preparar la obra y su control
- **Módulo**: Catálogo de insumos · **Prioridad**: Media · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-09

**Criterios de aceptación**

- [ ] La búsqueda abarca el nombre estandarizado, el código y los nombres comerciales
- [ ] Ignora mayúsculas, tildes y espacios de más
- [ ] Al crear un insumo, si hay coincidencias se muestran antes de confirmar

## HU-22 · Agregar insumo no presupuestado

> Como residente de obra, quiero agregar insumos que el presupuesto no contempló, para cubrir lo que las instalaciones exigen en obra.

- **Épica**: E2. Planificar el requerimiento y la reposición de recursos
- **Módulo**: Reposición / Planificación · **Prioridad**: Media · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-19

**Criterios de aceptación**

- [ ] El insumo queda marcado como "no presupuestado"
- [ ] Exige motivo y queda registrado quién lo agregó
- [ ] Se informa aparte del consumo contra presupuesto, para no distorsionar la desviación

## HU-28 · Marcar insumos críticos

> Como residente de obra, quiero marcar los insumos críticos de la obra, para concentrar el control en los que concentran el valor.

- **Épica**: E2. Planificar el requerimiento y la reposición de recursos
- **Módulo**: Reposición / Planificación · **Prioridad**: Media · **Puntos**: 3
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-19

**Criterios de aceptación**

- [ ] El residente marca y desmarca el insumo como crítico
- [ ] El sistema propone candidatos ordenados por valor presupuestado, sin decidir por él
- [ ] Las consultas y avisos permiten filtrar solo los críticos

## HU-33 · Registrar precio pagado

> Como almacenero, quiero registrar el precio pagado viendo propuesto el promedio ya pagado, para reconocer cuándo el precio se salió de lo habitual.

- **Épica**: E3. Registrar las compras y sus recepciones
- **Módulo**: Compras / Recepciones · **Prioridad**: Media · **Puntos**: 3
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-32

**Criterios de aceptación**

- [ ] El campo se precarga con el promedio ponderado histórico del insumo en la obra
- [ ] El almacenero escribe el precio realmente pagado, que es el que se guarda
- [ ] Si el precio se aparta del promedio más de un porcentaje configurable, se muestra una advertencia
- [ ] El promedio ponderado lo recalcula el sistema con cada recepción y nunca se edita a mano

## HU-36 · Avisar recepción excedida

> Como almacenero, quiero recibir una advertencia antes de confirmar una recepción que exceda lo requerido, para evitar registrar cantidades no justificadas.

- **Épica**: E3. Registrar las compras y sus recepciones
- **Módulo**: Compras / Recepciones · **Prioridad**: Media · **Puntos**: 2
- **Responsable**: Backend · Henry Ticona
- **Depende de**: HU-35, HU-19

**Criterios de aceptación**

- [ ] La advertencia aparece antes de confirmar, no después
- [ ] Indica requerido, ya recibido y exceso
- [ ] Puede continuarse dejando registro del motivo

## HU-41 · Registrar ingreso sin compra

> Como almacenero, quiero registrar un ingreso que no viene de una compra, para reflejar el material que llega por otra vía.

- **Épica**: E4. Controlar el movimiento de materiales en almacén
- **Módulo**: Almacén / Kardex · **Prioridad**: Media · **Puntos**: 3
- **Responsable**: Android · Nicolás Pachamora
- **Depende de**: HU-35

**Criterios de aceptación**

- [ ] Exige origen declarado: saldo de otra obra, devolución de cuadrilla, préstamo, sobrante u otro
- [ ] El origen "saldo de otra obra" solo se admite si esa obra no está registrada en el sistema; si lo está, debe usarse el traslado (HU-40)
- [ ] Exige costo unitario para poder valorizarlo
- [ ] Queda distinguible de los ingresos originados en una recepción

## HU-44 · Reportar entradas y salidas semanales

> Como almacenero, quiero revisar las entradas y salidas agrupadas por semana, para cuadrar el almacén con el corte que ya usamos.

- **Épica**: E4. Controlar el movimiento de materiales en almacén
- **Módulo**: Almacén / Kardex · **Prioridad**: Media · **Puntos**: 3
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-03, HU-40

**Criterios de aceptación**

- [ ] Se elige la semana a revisar
- [ ] Muestra saldo inicial, entradas, salidas y saldo final por insumo
- [ ] Muestra únicamente los insumos con movimiento en esa semana
- [ ] Puede exportarse

## HU-60 · Listar facturas de compra

> Como administrador, quiero revisar todas las facturas en un solo cuadro, para no abrirlas una por una.

- **Épica**: E6. Valorizar el consumo e informar el costo de obra
- **Módulo**: Reportes / Administración · **Prioridad**: Media · **Puntos**: 3
- **Responsable**: Web · Víctor Luján
- **Depende de**: HU-32

**Criterios de aceptación**

- [ ] Lista proveedor, tipo y número de comprobante, fecha, base, impuesto y total
- [ ] Permite filtrar por proveedor, por rango de fechas y por obra
- [ ] Puede exportarse
