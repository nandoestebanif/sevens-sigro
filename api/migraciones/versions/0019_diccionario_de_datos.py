"""Migración 0019 (va DESPUÉS de la 0018). Diccionario de datos en la propia base + un índice.

Revisión de calidad del 24/11/2026: las 36 tablas y 319 columnas de los esquemas de los servicios no tenían descripción.
Esta migración escribe COMMENT ON TABLE / COMMENT ON COLUMN para todas, así el diccionario vive donde viven los datos:
lo leen psql (\\d+), DBeaver, pgAdmin y cualquier herramienta de documentación, y no se desfasa de un Word aparte.
Una prueba (test_migracion_0019.py) exige que toda columna nueva traiga su descripción.

Además: índice parcial en almacen.movimiento(conteo_id). Es la única llave foránea sin índice que se consulta (el
cierre de un conteo busca sus ajustes); las otras 28 sin índice propio son columnas de auditoría (creado_por,
anulado_por…) o ya están cubiertas por un índice compuesto.

No cambia datos ni permisos (COMMENT no se concede). Base con datos: solo agrega metadatos.
Vuelta atrás: borra las descripciones y el índice.

Propuesta para el PO (Víctor Adiel Luján); la redacta Claude a pedido de Fernando Istaña el 24/11/2026. Revisar antes
de aplicar: solo Adiel integra migraciones.

Revision ID: 0019
Revises: 0018
"""
from alembic import op

revision = "0019"
down_revision = "0018"
branch_labels = None
depends_on = None

AUDITORIA = {
    "creado_en": "Momento en que se creó la fila (hora del servidor).",
    "creado_por": "Usuario que creó la fila (obra.usuario.id).",
    "registrado_por": "Usuario que registró la fila (obra.usuario.id).",
    "fecha_servidor": "Hora del servidor al recibir el registro; es la que vale para el libro y los cortes.",
    "fecha_dispositivo": "Hora del celular al registrar (sin señal puede ir antes que fecha_servidor); dato informativo.",
    "uuid_cliente": "Identificador generado en el dispositivo; clave de idempotencia: un reenvío no duplica (regla 3).",
    "dispositivo_id": "Dispositivo autorizado desde el que se registró (obra.dispositivo.id, regla 5).",
    "proyecto_id": "Obra a la que pertenece (obra.proyecto.id); todo permiso se verifica por obra (regla 9).",
    "insumo_id": "Insumo (catalogo.insumo.id): mismo código en todas las obras (regla 8).",
    "almacen_id": "Almacén de la obra (obra.almacen.id).",
    "periodo_id": "Periodo de la obra al que se imputa (obra.periodo.id).",
    "motivo_anulacion": "Por qué se anuló; obligatorio al anular (nada se borra).",
    "id": "Identificador interno.",
}

TABLAS = {
    # ── obra ────────────────────────────────────────────────────────────────────────────────────────────────────
    "obra.usuario": ("Personas que usan SIGRO. Se dan de baja con fecha, no se borran (regla 10).", {
        "nombre": "Nombre completo para mostrar.",
        "usuario": "Nombre de usuario para entrar; único.",
        "hash_clave": "Contraseña con Argon2id; nunca se guarda la clave.",
        "estado": "activo o baja.",
        "fecha_baja": "Desde cuándo está de baja; nulo si está activo.",
        "intentos_fallidos": "Intentos de entrada fallidos seguidos; al llegar al tope se bloquea la cuenta.",
        "bloqueado_hasta": "Hasta cuándo no puede entrar tras demasiados intentos; nulo si no está bloqueado.",
    }),
    "obra.proyecto": ("Obras de la empresa; es el alcance de todo permiso (regla 9).", {
        "nombre": "Nombre de la obra (p. ej. Multifamiliar Flores).",
        "fecha_inicio": "Inicio del plazo de la obra.",
        "fecha_fin": "Fin vigente del plazo; sus cambios quedan en proyecto_plazo_historial.",
        "dia_corte": "Día de la semana en que cierra cada periodo (ISO: 1 lunes … 7 domingo).",
        "estado": "en_ejecucion o cerrado.",
    }),
    "obra.proyecto_plazo_historial": ("Cada cambio del fin de plazo de una obra, con motivo y autor (solo se agrega).", {
        "fecha_fin_anterior": "Fin de plazo antes del cambio.",
        "fecha_fin_nueva": "Fin de plazo después del cambio.",
        "motivo": "Por qué cambió el plazo.",
        "autor": "Usuario que cambió el plazo.",
        "fecha": "Momento del cambio.",
    }),
    "obra.periodo": ("Semanas de la obra; un periodo cerrado ya no admite registros.", {
        "numero": "Número de semana dentro de la obra (1, 2, …).",
        "fecha_inicio": "Primer día del periodo.",
        "fecha_fin": "Último día del periodo.",
        "estado": "abierto o cerrado.",
        "cerrado_en": "Momento del cierre.",
        "cerrado_por": "Usuario que cerró el periodo.",
    }),
    "obra.almacen": ("Almacenes de una obra: el central y los de frente.", {
        "nombre": "Nombre del almacén.",
        "tipo": "central o frente.",
        "estado": "activo o inactivo.",
    }),
    "obra.asignacion": ("Quién trabaja en qué obra y con qué rol; un retiro es fecha, no borrado.", {
        "usuario_id": "Persona asignada (obra.usuario.id).",
        "rol": "almacenero, ssoma, residente, administrador o gerente.",
        "asignado_por": "Usuario que hizo la asignación.",
        "fecha_asignacion": "Desde cuándo tiene el rol en la obra.",
        "fecha_retiro": "Desde cuándo ya no lo tiene; nulo si sigue asignado.",
        "retirado_por": "Usuario que retiró la asignación.",
    }),
    "obra.dispositivo": ("Celulares autorizados por obra: hasta dos, uno por responsable (regla 5).", {
        "usuario_id": "Responsable del celular (almacenero o ingeniero SSOMA).",
        "identificador": "Código que la app genera en su primera instalación y conserva.",
        "estado": "pendiente, activo o revocado.",
        "solicitado_en": "Momento en que la app pidió la autorización.",
        "autorizado_por": "Usuario que autorizó el celular.",
        "fecha_autorizacion": "Momento de la autorización.",
        "revocado_por": "Usuario que revocó el celular.",
        "fecha_revocacion": "Momento de la revocación.",
        "motivo_revocacion": "Por qué se revocó (pérdida, cambio de equipo…).",
    }),
    # ── catalogo ────────────────────────────────────────────────────────────────────────────────────────────────
    "catalogo.insumo": ("Catálogo único de insumos, importado del S10; el mismo código en todas las obras (regla 8).", {
        "codigo": "Código del insumo en el S10; único.",
        "descripcion": "Descripción del S10.",
        "unidad_s10": "Unidad en que el S10 planifica y valoriza (p. ej. kg).",
        "unidad_compra": "Unidad en que se compra y se registra en obra (p. ej. varilla).",
        "factor_conversion": "Cuántas unidades del S10 hay en una unidad de compra; sus cambios van al historial.",
        "tipo": "material, mano_obra, equipo o subcontrato.",
        "activo": "Falso si ya no se usa (no se borra).",
        "verificado": "Verdadero cuando alguien revisó la unidad y el factor.",
        "medida": "Medida comercial (diámetro, tamaño…); ficha del insumo.",
        "norma": "Norma técnica que cumple; ficha del insumo.",
        "marca": "Marca habitual; ficha del insumo.",
    }),
    "catalogo.insumo_factor_historial": ("Cada cambio del factor de conversión de un insumo (solo se agrega).", {
        "factor_anterior": "Factor antes del cambio.",
        "factor_nuevo": "Factor después del cambio.",
        "motivo": "Por qué cambió.",
        "cambiado_por": "Usuario que lo cambió.",
        "cambiado_en": "Momento del cambio.",
    }),
    "catalogo.insumo_nombre_comercial": ("Otros nombres con que se conoce un insumo en obra o en las facturas.", {
        "nombre": "Nombre comercial o de obra.",
    }),
    # ── compras ─────────────────────────────────────────────────────────────────────────────────────────────────
    "compras.proveedor": ("Proveedores; RUC único en todo el sistema, validado contra SUNAT.", {
        "ruc": "RUC de 11 dígitos; único.",
        "razon_social": "Razón social según SUNAT.",
        "estado_validacion": "validado, sin_validar o con_alerta_sunat.",
        "validado_en": "Momento de la última validación correcta en SUNAT.",
        "estado": "activo o baja.",
        "fecha_baja": "Desde cuándo está de baja; nulo si está activo.",
        "alerta_sunat": "Texto de la alerta de SUNAT (no habido, baja de oficio…), si la hay.",
        "consultado_en": "Momento de la última consulta a SUNAT, haya salido bien o no.",
    }),
    "compras.solicitud": ("Solicitudes de compra emitidas en la obra; se anulan con motivo, no se borran.", {
        "numero": "Correlativo de la solicitud dentro de la obra.",
        "fecha": "Fecha de la solicitud.",
        "estado": "emitida o anulada.",
        "anulada_por": "Usuario que la anuló.",
        "anulada_en": "Momento de la anulación.",
        "emitida_por": "Usuario que la emitió.",
    }),
    "compras.solicitud_correlativo": ("Último número de solicitud usado por obra (para numerar sin huecos).", {
        "ultimo": "Último número emitido en la obra.",
    }),
    "compras.solicitud_linea": ("Insumos pedidos en una solicitud de compra.", {
        "solicitud_id": "Solicitud a la que pertenece.",
        "descripcion_libre": "Descripción cuando lo pedido aún no está en el catálogo.",
        "cantidad": "Cantidad pedida, en unidad de compra.",
        "unidad_compra": "Unidad de compra de la cantidad.",
    }),
    "compras.compra": ("Compras de la obra con su comprobante; se anulan con motivo, no se borran.", {
        "proveedor_id": "Proveedor (compras.proveedor.id).",
        "tipo_comprobante": "factura, boleta, nota_venta u otro.",
        "numero_comprobante": "Serie y número del comprobante (p. ej. F001-4512).",
        "fecha": "Fecha del comprobante.",
        "moneda": "PEN o USD.",
        "base_imponible": "Importe sin IGV.",
        "igv": "IGV del comprobante.",
        "total": "Importe total del comprobante.",
        "estado": "registrada o anulada.",
        "anulada_por": "Usuario que la anuló.",
        "anulada_en": "Momento de la anulación.",
        "solicitud_id": "Solicitud de compra que atiende, si la hay.",
    }),
    "compras.compra_detalle": ("Líneas de una compra: insumo, cantidad y precio.", {
        "compra_id": "Compra a la que pertenece.",
        "cantidad": "Cantidad comprada.",
        "unidad": "Unidad de la cantidad (la de compra).",
        "precio_unitario": "Precio por unidad según el comprobante.",
        "precio_referencia": "Promedio vigente del insumo al registrar; base para avisar precios fuera de lo habitual.",
        "fuera_de_lo_habitual": "Verdadero si el precio se aparta del de referencia más que el límite (HU-33); avisa, no bloquea.",
        "nota_precio": "Explicación de quien registró cuando el precio salió fuera de lo habitual.",
    }),
    "compras.compra_adjunto": ("Comprobantes adjuntos a una compra (HU-34): se anulan con motivo, no se reemplazan.", {
        "compra_id": "Compra a la que pertenece.",
        "archivo": "Clave del archivo en el almacenamiento de objetos.",
        "nombre_original": "Nombre del archivo tal como se subió.",
        "tipo_mime": "application/pdf, image/jpeg o image/png, decidido por la firma del archivo.",
        "tamano_bytes": "Tamaño en bytes (hasta 10 MB).",
        "sha256": "Huella del contenido; el mismo archivo no se adjunta dos veces a una compra.",
        "subido_por": "Usuario que lo subió.",
        "subido_en": "Momento de la subida.",
        "almacenamiento": "Dónde están los bytes: bd (compra_adjunto_contenido) o s3.",
        "anulado_por": "Usuario que lo anuló.",
        "anulado_en": "Momento de la anulación.",
    }),
    "compras.compra_adjunto_contenido": ("Bytes de los comprobantes mientras se guardan en la base (ADR-11).", {
        "adjunto_id": "Adjunto al que pertenecen (compras.compra_adjunto.id).",
        "contenido": "Contenido binario del archivo.",
    }),
    "compras.requerimiento": ("Lo planificado por insumo y obra, importado del S10 o agregado a mano con motivo.", {
        "cantidad_planificada": "Cantidad planificada vigente (incluye desperdicio y reserva; pasarse avisa, regla 7).",
        "precio_planificado": "Precio unitario planificado en el S10.",
        "origen": "s10 o manual.",
        "motivo_alta": "Por qué se agregó a mano (obligatorio si origen = manual).",
    }),
    "compras.requerimiento_ajuste": ("Cada ajuste de la cantidad planificada, con motivo (solo se agrega).", {
        "requerimiento_id": "Línea del requerimiento ajustada.",
        "cantidad_anterior": "Cantidad antes del ajuste.",
        "cantidad_nueva": "Cantidad después del ajuste.",
        "motivo": "Por qué se ajustó.",
        "ajustado_por": "Usuario que ajustó.",
        "ajustado_en": "Momento del ajuste.",
    }),
    "compras.partida": ("Partidas del presupuesto S10 (reservadas: el control hoy es por insumo, regla 6).", {
        "codigo": "Código de la partida en el S10.",
        "descripcion": "Descripción de la partida.",
        "unidad": "Unidad de medida de la partida.",
    }),
    "compras.partida_recurso": ("Recursos que consume cada partida según el análisis de precios unitarios.", {
        "partida_id": "Partida (compras.partida.id).",
        "cantidad_por_unidad": "Cantidad del insumo por unidad de partida.",
        "unidad": "Unidad de esa cantidad.",
    }),
    "compras.parametro_reposicion": ("Stock de seguridad, punto de reposición y nivel máximo por obra e insumo; cada cambio es fila nueva.", {
        "stock_seguridad": "Cantidad mínima que debe quedar para no detener la obra.",
        "punto_reposicion": "Al bajar de esta cantidad se sugiere comprar.",
        "nivel_maximo": "Hasta cuánto conviene reponer.",
        "vigente_desde": "Desde cuándo rige este parámetro; vale el último.",
    }),
    "compras.insumo_critico": ("Marcas de insumo crítico por obra (HU-28); libro solo-agregar, vale la última.", {
        "critico": "Verdadero si se marcó como crítico; falso si se desmarcó.",
        "registrado_en": "Momento de la marca.",
    }),
    # ── tareo ───────────────────────────────────────────────────────────────────────────────────────────────────
    "tareo.trabajador": ("Trabajadores de la obra; el que se retira se da de baja con fecha (regla 10).", {
        "nombre": "Nombre completo.",
        "tipo_documento": "DNI, CE o PASAPORTE.",
        "documento": "Número de documento.",
        "categoria": "operario, oficial o peon.",
        "cuadrilla": "Cuadrilla a la que pertenece.",
        "fecha_ingreso": "Primer día en la obra.",
        "fecha_salida": "Último día en la obra; nulo si sigue.",
    }),
    "tareo.jornada": ("Asistencia diaria y horas de cada trabajador; una corrección es fila nueva que apunta a la original.", {
        "trabajador_id": "Trabajador (tareo.trabajador.id).",
        "fecha": "Día trabajado.",
        "estado": "presente, ausente o permiso.",
        "horas_normales": "Horas normales del día.",
        "horas_extra": "Horas extra del día.",
        "categoria": "Categoría del trabajador ese día (operario, oficial o peon).",
        "corrige_a_id": "Jornada que esta fila corrige, si es una corrección.",
        "motivo_correccion": "Por qué se corrigió.",
    }),
    # ── almacen ─────────────────────────────────────────────────────────────────────────────────────────────────
    "almacen.movimiento": ("Libro de movimientos de almacén: no se modifica ni se borra (regla 1); el saldo se deriva de aquí (regla 2).", {
        "tipo": "ingreso_compra, ingreso_sin_compra, salida, traslado_salida, traslado_ingreso, ajuste_conteo o reverso.",
        "sentido": "1 si entra al almacén, -1 si sale.",
        "cantidad": "Cantidad movida, en la unidad del S10.",
        "costo_unitario": "Costo por unidad: en un ingreso, el de compra; en una salida, el promedio ponderado congelado en ese instante (regla 4).",
        "moneda": "PEN o USD.",
        "motivo": "Motivo del movimiento (obligatorio en reversos y ajustes).",
        "quien_retira": "Persona que retira el material en una salida.",
        "frente": "Frente de trabajo al que va la salida.",
        "origen": "De dónde vino un ingreso sin compra: saldo_otra_obra, devolucion_cuadrilla, prestamo, sobrante u otro.",
        "origen_detalle": "Detalle libre del origen.",
        "recepcion_id": "Recepción de compra que originó el ingreso.",
        "traslado_id": "Traslado al que pertenece este movimiento.",
        "conteo_id": "Conteo que originó el ajuste.",
        "revierte_a_id": "Movimiento que este reverso anula (uno solo por movimiento).",
    }),
    "almacen.recepcion": ("Recepciones de una compra en el almacén (HU-35/36); generan los ingresos.", {
        "compra_id": "Compra recibida (compras.compra.id).",
        "motivo_exceso": "Por qué se recibió más de lo comprado, si pasó.",
    }),
    "almacen.traslado": ("Traslados entre almacenes u obras (HU-42); generan una salida y un ingreso.", {
        "origen_proyecto_id": "Obra de donde sale.",
        "origen_almacen_id": "Almacén de donde sale.",
        "destino_proyecto_id": "Obra a donde llega.",
        "destino_almacen_id": "Almacén a donde llega.",
        "motivo": "Por qué se traslada.",
        "es_cierre": "Verdadero si es parte del cierre de la obra (HU-47).",
    }),
    "almacen.conteo": ("Conteos físicos del almacén (HU-46); al confirmarse generan ajustes.", {
        "fecha": "Día del conteo.",
        "estado": "borrador o confirmado.",
        "es_cierre": "Verdadero si es el conteo de cierre de la obra.",
        "confirmado_por": "Usuario que confirmó el conteo.",
        "confirmado_en": "Momento de la confirmación.",
    }),
    "almacen.conteo_linea": ("Cantidades contadas por insumo en un conteo.", {
        "conteo_id": "Conteo al que pertenece.",
        "saldo_teorico": "Saldo que daba el libro al contar.",
        "cantidad_contada": "Cantidad encontrada físicamente.",
        "diferencia": "Contada menos teórica; genera el ajuste.",
        "motivo": "Explicación de la diferencia.",
    }),
    "almacen.almacen_ref": ("Copia local de los almacenes de obras (el servicio almacén no lee la base de obras).", {
        "nombre": "Nombre del almacén.",
        "tipo": "central o frente.",
        "estado": "activo o inactivo.",
        "sincronizado_en": "Última vez que se copió desde obras.",
    }),
    "almacen.insumo_ref": ("Copia local de los insumos del catálogo que se mueven en almacén.", {
        "codigo": "Código del S10.",
        "descripcion": "Descripción del S10.",
        "unidad": "Unidad del S10.",
        "tipo": "material o equipo.",
        "activo": "Falso si ya no se usa.",
        "sincronizado_en": "Última vez que se copió desde catálogo.",
    }),
    # ── costo ───────────────────────────────────────────────────────────────────────────────────────────────────
    "costo.hecho": ("Hechos valorizados que llegan por eventos (outbox): base del tablero de gasto en recursos (HU-58).", {
        "evento_id": "Evento del outbox que originó el hecho.",
        "familia": "material, mano_obra o equipo.",
        "clase": "ingreso, consumo, traslado, ajuste, compra u horas.",
        "insumo_codigo": "Código del insumo en el S10.",
        "cantidad": "Cantidad del hecho.",
        "unidad": "Unidad de la cantidad.",
        "costo_unitario": "Costo por unidad congelado en el hecho.",
        "importe": "Cantidad por costo unitario.",
        "moneda": "PEN o USD.",
        "fecha": "Día del hecho.",
        "referencia": "Registro de origen para volver a él (p. ej. almacen.movimiento:123).",
    }),
    "costo.evento_recibido": ("Eventos ya procesados por costo: un evento repetido no se cuenta dos veces.", {
        "evento_id": "Identificador del evento en el outbox.",
        "tipo": "Tipo de evento.",
        "recibido_en": "Momento en que se procesó.",
    }),
    # ── outbox ──────────────────────────────────────────────────────────────────────────────────────────────────
    "outbox.evento": ("Bandeja de salida (patrón outbox): eventos que el relevo entrega a avisos o a integración.", {
        "tipo": "Tipo de evento (p. ej. salida registrada).",
        "payload": "Contenido del evento en JSON.",
        "enviado_en": "Momento de la entrega; nulo si sigue pendiente.",
        "intentos": "Cuántas veces se intentó entregar.",
        "siguiente_intento": "Cuándo se vuelve a intentar.",
        "ultimo_error": "Error del último intento fallido.",
        "destino": "aviso o integracion.",
    }),
}


# Columnas de cada tabla en la 0018 (fijas: así la migración también se puede emitir como SQL sin base, --sql).
COLUMNAS = {
    "almacen.almacen_ref": "id proyecto_id nombre tipo estado sincronizado_en".split(),
    "almacen.conteo": "id uuid_cliente proyecto_id almacen_id fecha estado es_cierre registrado_por creado_en confirmado_por confirmado_en".split(),
    "almacen.conteo_linea": "id conteo_id insumo_id saldo_teorico cantidad_contada diferencia motivo".split(),
    "almacen.insumo_ref": "id codigo descripcion unidad tipo activo sincronizado_en".split(),
    "almacen.movimiento": "id uuid_cliente proyecto_id almacen_id insumo_id tipo sentido cantidad costo_unitario moneda motivo quien_retira frente origen origen_detalle recepcion_id traslado_id conteo_id revierte_a_id periodo_id dispositivo_id registrado_por fecha_dispositivo fecha_servidor".split(),
    "almacen.recepcion": "id uuid_cliente proyecto_id almacen_id compra_id motivo_exceso registrado_por dispositivo_id fecha_dispositivo fecha_servidor".split(),
    "almacen.traslado": "id uuid_cliente origen_proyecto_id origen_almacen_id destino_proyecto_id destino_almacen_id motivo es_cierre registrado_por fecha_servidor".split(),
    "catalogo.insumo": "id codigo descripcion unidad_s10 unidad_compra factor_conversion tipo activo verificado creado_por creado_en medida norma marca".split(),
    "catalogo.insumo_factor_historial": "id insumo_id factor_anterior factor_nuevo motivo cambiado_por cambiado_en".split(),
    "catalogo.insumo_nombre_comercial": "id insumo_id nombre".split(),
    "compras.compra": "id proyecto_id proveedor_id tipo_comprobante numero_comprobante fecha moneda base_imponible igv total estado motivo_anulacion anulada_por anulada_en registrado_por creado_en solicitud_id".split(),
    "compras.compra_adjunto": "id compra_id archivo nombre_original tipo_mime tamano_bytes sha256 subido_por subido_en almacenamiento anulado_por anulado_en motivo_anulacion".split(),
    "compras.compra_adjunto_contenido": "adjunto_id contenido".split(),
    "compras.compra_detalle": "id compra_id insumo_id cantidad unidad precio_unitario precio_referencia fuera_de_lo_habitual nota_precio".split(),
    "compras.insumo_critico": "id proyecto_id insumo_id critico registrado_por registrado_en".split(),
    "compras.parametro_reposicion": "id proyecto_id insumo_id stock_seguridad punto_reposicion registrado_por vigente_desde nivel_maximo".split(),
    "compras.partida": "id proyecto_id codigo descripcion unidad creado_por creado_en".split(),
    "compras.partida_recurso": "id partida_id insumo_id cantidad_por_unidad unidad creado_en".split(),
    "compras.proveedor": "id ruc razon_social estado_validacion validado_en estado fecha_baja creado_en alerta_sunat consultado_en".split(),
    "compras.requerimiento": "id proyecto_id insumo_id cantidad_planificada precio_planificado creado_por creado_en origen motivo_alta".split(),
    "compras.requerimiento_ajuste": "id requerimiento_id cantidad_anterior cantidad_nueva motivo ajustado_por ajustado_en".split(),
    "compras.solicitud": "id proyecto_id numero fecha estado motivo_anulacion anulada_por anulada_en emitida_por creado_en".split(),
    "compras.solicitud_correlativo": "proyecto_id ultimo".split(),
    "compras.solicitud_linea": "id solicitud_id insumo_id descripcion_libre cantidad unidad_compra".split(),
    "costo.evento_recibido": "evento_id tipo recibido_en".split(),
    "costo.hecho": "id evento_id proyecto_id familia clase insumo_codigo cantidad unidad costo_unitario importe moneda fecha referencia".split(),
    "obra.almacen": "id proyecto_id nombre tipo estado creado_en".split(),
    "obra.asignacion": "id usuario_id proyecto_id rol asignado_por fecha_asignacion fecha_retiro retirado_por".split(),
    "obra.dispositivo": "id proyecto_id usuario_id identificador estado solicitado_en autorizado_por fecha_autorizacion revocado_por fecha_revocacion motivo_revocacion".split(),
    "obra.periodo": "id proyecto_id numero fecha_inicio fecha_fin estado cerrado_en cerrado_por".split(),
    "obra.proyecto": "id nombre fecha_inicio fecha_fin dia_corte estado creado_por creado_en".split(),
    "obra.proyecto_plazo_historial": "id proyecto_id fecha_fin_anterior fecha_fin_nueva motivo autor fecha".split(),
    "obra.usuario": "id nombre usuario hash_clave estado fecha_baja intentos_fallidos bloqueado_hasta creado_en".split(),
    "outbox.evento": "id tipo proyecto_id payload creado_en enviado_en intentos siguiente_intento ultimo_error destino".split(),
    "tareo.jornada": "id uuid_cliente proyecto_id trabajador_id fecha estado horas_normales horas_extra categoria periodo_id registrado_por fecha_servidor corrige_a_id motivo_correccion".split(),
    "tareo.trabajador": "id proyecto_id nombre tipo_documento documento categoria cuadrilla fecha_ingreso fecha_salida registrado_por creado_en".split(),
}


def _texto(t: str) -> str:
    return "'" + t.replace("'", "''") + "'"




def upgrade() -> None:
    faltan = []
    for tabla, (desc, cols) in TABLAS.items():
        op.execute(f"COMMENT ON TABLE {tabla} IS {_texto(desc)}")
        for col in COLUMNAS[tabla]:
            texto = cols.get(col) or AUDITORIA.get(col)
            if texto is None:
                faltan.append(f"{tabla}.{col}")
                continue
            op.execute(f"COMMENT ON COLUMN {tabla}.{col} IS {_texto(texto)}")
    if faltan:
        raise RuntimeError("Columnas sin descripción en la 0019: " + ", ".join(faltan))
    op.execute("CREATE INDEX ix_movimiento_conteo_id ON almacen.movimiento (conteo_id) WHERE conteo_id IS NOT NULL")


def downgrade() -> None:
    op.execute("DROP INDEX IF EXISTS almacen.ix_movimiento_conteo_id")
    for tabla in TABLAS:
        op.execute(f"COMMENT ON TABLE {tabla} IS NULL")
        for col in COLUMNAS[tabla]:
            op.execute(f"COMMENT ON COLUMN {tabla}.{col} IS NULL")
