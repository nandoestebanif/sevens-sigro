# Flujos del sistema, paso a paso

Cada módulo contado como lo vive la persona, no como lo ve la base de datos. Sirve para
tres cosas: validar con el cliente que así es la obra, saber qué pantallas faltan, y que
quien programa entienda qué pasa antes y después de su historia.

Al final está lo que todavía **no** tiene flujo, que es tan importante como lo que sí.

---

## 1 · Preparar la obra · E1

Lo hace el residente, una sola vez, antes de que entre el primer saco de cemento.

1. **Crea la obra** con su nombre, su fecha de inicio y su fecha de fin. · HU-01
2. El sistema **genera solo los periodos semanales** de todo el plazo. El último puede ser más corto. · HU-03
3. Elige el **día de corte** si el contratante exige uno distinto del domingo. · HU-04
4. **Registra los almacenes**: el central y los frentes, si la obra tiene varios. · HU-05
5. El administrador **crea los usuarios**, les asigna rol y los asigna a esta obra. · HU-12, HU-13, HU-14
6. El almacenero y el ingeniero SSOMA piden autorización desde su celular; el administrador **autoriza hasta dos dispositivos** por obra. · HU-15

> Si el plazo se amplía después, queda registrado con fecha y autor, y los movimientos ya
> registrados conservan su periodo. · HU-02

## 2 · Cargar lo que la obra va a necesitar · E1 y E2

Lo hace el residente con los reportes que exporta del S10.

1. **Importa el catálogo** desde la relación de insumos: 237 materiales, 6 de mano de obra, equipos y subcontratos, cada uno con código, unidad y precio. · HU-07
2. Revisa las **filas rechazadas** —unidad no reconocida, cantidad vacía—, las corrige en el archivo y vuelve a importar. Importar dos veces no duplica. · HU-07
3. El almacenero (o el residente) define el **factor de conversión** de los insumos que se compran en una unidad distinta a la del S10. El acero es el caso obligatorio: llega en kilos, se compra en varillas; su reparto por diámetro es HU-18 (Sprint 2). · HU-17
4. **Importa el requerimiento total**: cuánto de cada insumo necesita la obra completa. · HU-19
5. Opcional: parte el requerimiento **por fases**, si la obra se controla por etapas. · HU-20
6. Da de alta **insumos que el presupuesto no previó**, cuando aparezcan. · HU-22
7. Define, por insumo, su **stock de seguridad y su punto de reposición**. · HU-25

## 3 · Comprar · E3

Lo hace el residente desde la oficina. El sistema **no compra**: registra lo que se compró.

1. Busca el proveedor, o lo **da de alta** sin salir del formulario de compra. · HU-29, HU-30
2. El sistema **valida su RUC en SUNAT** y trae la razón social. · HU-31
3. **Registra la compra**: proveedor, comprobante, insumos, cantidades y precios. · HU-32
4. Compara el precio pagado contra el **precio planificado** del S10. Si está por encima, lo muestra; no bloquea. · HU-33
5. **Adjunta el comprobante**. · HU-34

> Registrar la compra **no mueve el stock**. El stock se mueve cuando llega el material.

## 4 · Recibir y mover el material · E4

Lo hace el almacenero, en obra, desde el celular, con o sin señal.

1. Llega el camión: **registra la recepción** contra la compra. Si el proveedor entregó por partes, registra lo que llegó y la compra queda parcialmente recibida. · HU-35
2. Si lo recibido **supera lo comprado**, el sistema avisa. No bloquea. · HU-36
3. También puede entrar material **sin compra**: devolución de cuadrilla, saldo de otra obra, préstamo. Lleva su costo unitario, que es lo que permite valorizar después. · HU-41
4. Cada ingreso **recalcula el costo promedio ponderado** del insumo en ese almacén. El costo se toma **sin IGV** cuando el comprobante es factura (crédito fiscal, NIC 2.11); con boleta, el IGV sí es costo.
5. **Registra la salida**: almacén, insumo, cantidad, motivo y quién retira. El frente de destino es opcional. · HU-40
6. La salida se valoriza al promedio vigente y **ese valor queda congelado** en el movimiento.
7. Si no hay señal, el movimiento **se guarda en el celular y se encola**. · HU-37
8. Al volver la cobertura, **se envía solo**. Un reenvío con el mismo identificador no duplica. · HU-38
9. Si el servidor lo **rechaza** —por ejemplo, ya no había saldo—, el movimiento no se pierde: queda con su motivo para corregirlo o descartarlo. · HU-38
10. Cualquiera consulta el **stock** cuando quiera: por almacén y consolidado. · HU-43
11. ¿Se equivocó? **Corrige con un movimiento inverso** que apunta al original y que el residente autoriza (matriz de permisos: R y A). Nada se borra. · HU-45

## 5 · Avisar cuándo reponer · E2

Esto no lo hace nadie: lo hace el sistema. Es lo que ataca las paradas de obra.

1. Cada salida baja el saldo. Cuando el saldo cruza el **punto de reposición**, salta el aviso. · HU-26
2. El aviso **nunca va solo**: indica el insumo, el saldo actual, el punto de reposición y cuánto falta por comprar. Un mismo insumo no repite aviso mientras siga bajo el punto, y se marca atendido al registrarse una compra. · HU-26
3. El sistema **sugiere cuánto comprar**: el menor valor entre lo que falta por comprar y lo que exige la fase en curso, como referencia editable y nunca como pedido automático, mostrando el criterio. · HU-27
4. El residente revisa el **avance de compra y consumo** por insumo, y el **saldo por comprar**. · HU-23, HU-24
5. Los insumos marcados como **críticos** se vigilan aparte. · HU-28

## 6 · Herramientas y equipos · E5

Lo lleva el almacenero o el ingeniero SSOMA.

1. **Ingresan a la obra** con su estado inicial. · HU-48
2. Se **entregan a un trabajador**, que queda como responsable. · HU-49
3. Se **devuelven**, con su estado. · HU-50
4. En cualquier momento se consulta **quién tiene cada herramienta**. · HU-51
5. Si se rompe o se pierde, se **da de baja** con su motivo y su origen (residente, SSOMA o criterio propio). · HU-53 (Sprint 2)
6. Al cerrar la obra se **declara el estado** de todas: cuáles vuelven, cuáles no. · HU-54

## 7 · Mano de obra · E7

Lo lleva el almacenero o el ingeniero SSOMA, todos los días.

1. **Registra a los trabajadores** de la obra con su categoría y su cuadrilla. · HU-62
2. La lista **se modifica mientras la obra corre**: entra personal nuevo con su fecha de ingreso, y el que se retira se da de baja con su fecha de salida. No se borra a nadie.
3. Cada día **registra la asistencia**: presente, ausente o permiso, con horas normales y extra por separado. · HU-63
4. Una jornada cerrada **se corrige con un registro nuevo** que deja constancia del anterior.
5. El residente consulta las **horas acumuladas** por trabajador y por cuadrilla. · HU-64
6. El gerente ve el **costo de esas horas** y lo compara contra las horas que el S10 planificó. · HU-65

## 8 · Saber cuánto se gastó · E6

Lo mira el gerente y el residente. No se registra nada aquí: todo sale de lo anterior.

1. Cada salida ya quedó **valorizada** al costo del momento. · HU-55
2. El **inventario valorizado** dice cuánto capital está detenido en almacén. · HU-56
3. El **consumo se compara contra lo planificado**, insumo por insumo, en cantidad, importe y porcentaje. Los no planificados se informan aparte. · HU-57
4. El **tablero del gasto en recursos** suma material, herramientas y horas hombre. · HU-58
5. El tablero **declara qué no incluye**: gastos generales, subcontratos, servicios y planilla. No es el costo total de la obra.

## 9 · Cerrar la obra · E4 y E5

1. Se hace el **conteo físico** y cada diferencia con el sistema se justifica. · HU-46
2. Lo que sobra tiene **tres destinos** (confirmado por el cliente): se devuelve al proveedor, se registra como merma o vuelve al almacén central conservando su costo unitario; el traslado a otra obra es HU-42. · HU-47
3. Se **declara el estado de las herramientas**. · HU-54
4. La obra **no puede cerrarse con saldos sin confirmar**.
5. El cierre produce un acta con lo comprado, lo consumido y lo transferido.

---

## Lo que hoy no tiene flujo

Vale la pena decirlo en voz alta antes de que alguien lo dé por supuesto:

| Falta | Por qué no está |
|---|---|
| **Rendimientos y ratios** | Exigen cruzar partidas y metrados. El cliente y el Ing. Cercado coincidieron en postergarlo (decisión D7) |
| **Costo total de la obra** | El sistema informa el gasto en recursos. Gastos generales, subcontratos, servicios y planilla quedan fuera |
| **Imputación a partidas** | El cliente pidió postergarla. El control de costo es por insumo |
| **Órdenes de compra formales** | Hoy la orden se acuerda fuera de toda herramienta y el sistema registra la compra ya hecha |
| **Entrega de equipos de protección** | Pendiente de confirmar si el SSOMA los maneja como herramientas o aparte |
| **Planilla** | No es este producto y el tablero lo dice |

## Preguntas abiertas para el cliente

1. ¿El almacenero registra la salida **antes** o **después** de entregar el material?
2. ¿Qué hace si el insumo que va a sacar **no está en el catálogo**?
3. ¿Los cuatro motivos de salida alcanzan, o en obra hay más casos?
4. ¿El SSOMA entrega también los **equipos de protección**?
5. ¿Hay **más de un SSOMA** por obra? De eso depende el límite de dos dispositivos.
