"""Genera el diagrama ENTIDAD-RELACIÓN (nivel lógico) y lo VERIFICA contra la base migrada.

El físico (generar_diagrama.py) responde «cómo está guardado»; este responde «qué cosas existen en la obra y cómo
se relacionan», con nombres de negocio y sin tipos. Para que no pueda divergir del físico, el script falla si:
  - un atributo que dibuja no existe como columna en la base,
  - una tabla de la base no está dibujada (ni declarada como técnica),
  - una clave foránea entre entidades no tiene su relación dibujada.

Uso:  SIGRO_DB=sigro_dev python docs/generar_er.py
Salida: docs/diagrama-er.dot, .svg y .png  (requiere Graphviz: `dot`)
"""
from __future__ import annotations

import html
import os
import re
import subprocess
from pathlib import Path

import psycopg
from psycopg.rows import dict_row

BASE = os.environ.get("SIGRO_DB", "sigro_dev")
DSN = os.environ.get("SIGRO_DSN", f"postgresql://sigro_owner:sigro_owner_dev@localhost:5432/{BASE}")
SALIDA = Path(__file__).resolve().parent
ESQUEMAS = ["obra", "catalogo", "compras", "tareo", "outbox"]

# Tablas que existen por técnica y no son «cosas de la obra»: se explican en la leyenda, no se dibujan.
TECNICAS = {"compras.solicitud_correlativo"}

COLOR = {"obra": ("#1F4E79", "#EAF1F8"), "catalogo": ("#7A4B00", "#FBF3E4"), "compras": ("#2E6B3A", "#EAF5EC"),
         "tareo": ("#6B2E6B", "#F6ECF6"), "outbox": ("#8A3B12", "#FBEDE6")}
GRUPO = {"obra": "Obra y accesos", "catalogo": "Catálogo", "compras": "Compras", "tareo": "Mano de obra", "outbox": "Avisos (infraestructura)"}

# tabla -> (nombre de negocio, [(atributo, columna, marca)], regla de negocio)
#   marca:  "id" identifica a la entidad · "?" es opcional · "" normal
E = {
    "obra.usuario": ("Usuario", [("Usuario (nombre de acceso)", "usuario", "id"), ("Nombre", "nombre", ""), ("Clave (solo su hash)", "hash_clave", ""),
                                 ("Estado: activo / baja", "estado", ""), ("Fecha de baja", "fecha_baja", "?"),
                                 ("Bloqueado hasta", "bloqueado_hasta", "?")],
                     "No tiene rol: el rol vive en la Asignación"),
    "obra.proyecto": ("Obra", [("Nombre", "nombre", "id"), ("Fecha de inicio", "fecha_inicio", ""), ("Fecha de fin", "fecha_fin", ""),
                               ("Día de corte semanal", "dia_corte", ""), ("Estado: en ejecución / cerrado", "estado", "")],
                      "Todo lo demás cuelga de una obra"),
    "obra.periodo": ("Periodo (semana)", [("Número", "numero", "id"), ("Desde", "fecha_inicio", ""), ("Hasta", "fecha_fin", ""),
                                          ("Estado: abierto / cerrado", "estado", ""), ("Cerrado en", "cerrado_en", "?")],
                     "Lo genera el sistema al crear la obra"),
    "obra.almacen": ("Almacén", [("Nombre", "nombre", "id"), ("Tipo: central / frente", "tipo", ""), ("Estado: activo / inactivo", "estado", "")], ""),
    "obra.asignacion": ("Asignación", [("Rol: almacenero / SSOMA / residente / administrador / gerente", "rol", ""), ("Desde", "fecha_asignacion", ""), ("Hasta", "fecha_retiro", "?")],
                        "Una vigente por persona y obra · aquí vive el rol"),
    "obra.dispositivo": ("Dispositivo (celular)", [("Identificador", "identificador", ""), ("Estado: pendiente / activo / revocado", "estado", ""),
                                                   ("Solicitado en", "solicitado_en", ""), ("Autorizado en", "fecha_autorizacion", "?"),
                                                   ("Revocado en", "fecha_revocacion", "?"), ("Motivo de revocación", "motivo_revocacion", "?")],
                         "1 activo por responsable · máx. 2 por obra"),
    "obra.proyecto_plazo_historial": ("Ampliación de plazo", [("Fecha de fin anterior", "fecha_fin_anterior", ""), ("Fecha de fin nueva", "fecha_fin_nueva", ""),
                                                               ("Motivo", "motivo", ""), ("Fecha", "fecha", "")],
                                      "Libro: solo se agrega"),
    "catalogo.insumo": ("Insumo", [("Código", "codigo", "id"), ("Descripción", "descripcion", ""), ("Unidad del S10", "unidad_s10", ""),
                                   ("Unidad de compra", "unidad_compra", ""), ("Factor de conversión", "factor_conversion", ""),
                                   ("Tipo: material / mano de obra / equipo / subcontrato", "tipo", ""), ("Verificado", "verificado", ""), ("Activo", "activo", "")],
                       "El código no cambia · es de la empresa, no de la obra"),
    "catalogo.insumo_nombre_comercial": ("Nombre comercial", [("Nombre", "nombre", "id")], "Cómo lo llaman los proveedores"),
    "catalogo.insumo_factor_historial": ("Cambio de factor", [("Factor anterior", "factor_anterior", ""), ("Factor nuevo", "factor_nuevo", ""),
                                                              ("Motivo", "motivo", ""), ("Cambiado en", "cambiado_en", "")],
                                         "Libro: solo se agrega · quién y por qué (HU-17)"),
    "compras.proveedor": ("Proveedor", [("RUC", "ruc", "id"), ("Razón social", "razon_social", ""), ("Validación: validado / sin validar", "estado_validacion", ""),
                                        ("Validado en", "validado_en", "?"), ("Estado: activo / baja", "estado", ""), ("Fecha de baja", "fecha_baja", "?")],
                          "Es de la empresa, no de la obra"),
    "compras.compra": ("Compra", [("Tipo de comprobante", "tipo_comprobante", "id"), ("N.º de comprobante", "numero_comprobante", "id"),
                                  ("Fecha", "fecha", ""), ("Moneda: PEN / USD", "moneda", ""), ("Base imponible", "base_imponible", ""), ("IGV", "igv", ""),
                                  ("Total", "total", ""), ("Estado: registrada / anulada", "estado", ""), ("Motivo de anulación", "motivo_anulacion", "?"),
                                  ("Anulada en", "anulada_en", "?")],
                      "No se edita: se anula con motivo · comprobante único por proveedor"),
    "compras.compra_detalle": ("Línea de compra", [("Cantidad", "cantidad", ""), ("Unidad", "unidad", ""), ("Precio unitario", "precio_unitario", "")],
                               "Libro: solo se agrega · un insumo una vez por compra"),
    "compras.compra_adjunto": ("Adjunto de compra", [("Archivo", "archivo", ""), ("Nombre original", "nombre_original", ""), ("Tipo", "tipo_mime", ""),
                                                      ("Tamaño", "tamano_bytes", ""), ("Huella SHA-256", "sha256", "")],
                               "Libro · el mismo archivo no se sube dos veces"),
    "compras.solicitud": ("Solicitud de materiales", [("N.º (correlativo por obra)", "numero", "id"), ("Fecha", "fecha", ""),
                                                      ("Estado: emitida / anulada", "estado", ""), ("Motivo de anulación", "motivo_anulacion", "?"),
                                                      ("Anulada en", "anulada_en", "?")],
                          "Emitida por el residente · nunca es pedido automático"),
    "compras.solicitud_linea": ("Línea de solicitud", [("Descripción libre", "descripcion_libre", "?"), ("Cantidad", "cantidad", ""),
                                                        ("Unidad de compra", "unidad_compra", "")],
                                "Libro · el insumo puede faltar (línea libre)"),
    "tareo.trabajador": ("Trabajador", [("Tipo de documento: DNI / CE / pasaporte", "tipo_documento", ""), ("Documento", "documento", "id"), ("Nombre", "nombre", ""),
                                        ("Categoría: operario / oficial / peón", "categoria", ""), ("Cuadrilla", "cuadrilla", ""), ("Fecha de ingreso", "fecha_ingreso", ""),
                                        ("Fecha de salida", "fecha_salida", "?")], "Por obra · se da de baja, no se borra"),
    "tareo.jornada": ("Jornada", [("Fecha", "fecha", ""), ("Horas normales", "horas_normales", ""), ("Horas extra", "horas_extra", ""),
                                  ("Categoría (congelada al registrar)", "categoria", ""), ("Asistencia: presente / ausente / permiso", "estado", ""),
                                  ("Identificador del celular", "uuid_cliente", "?"), ("Motivo de corrección", "motivo_correccion", "?")],
                     "Libro: se corrige agregando otra · una vigente por trabajador y día"),
    "outbox.evento": ("Evento por avisar", [("Tipo", "tipo", ""), ("Contenido", "payload", ""), ("Creado en", "creado_en", ""),
                                            ("Entregado en", "enviado_en", "?"), ("Intentos", "intentos", ""), ("Último error", "ultimo_error", "?")],
                      "Se escribe en la misma transacción que el hecho"),
}

# (hijo, padre, columnas del hijo [None = todas las FK a ese padre], etiqueta, cardinalidad del padre, del hijo)
#   padre:  "1" exactamente uno · "0..1" opcional      hijo: "0..N"
R = [
    ("obra.asignacion", "obra.usuario", "usuario_id", "tiene", "1"),
    ("obra.asignacion", "obra.proyecto", "proyecto_id", "se asigna a", "1"),
    ("obra.dispositivo", "obra.usuario", "usuario_id", "usa", "1"),
    ("obra.dispositivo", "obra.proyecto", "proyecto_id", "se autoriza en", "1"),
    ("obra.periodo", "obra.proyecto", "proyecto_id", "se divide en", "1"),
    ("obra.almacen", "obra.proyecto", "proyecto_id", "tiene", "1"),
    ("obra.proyecto_plazo_historial", "obra.proyecto", "proyecto_id", "se amplía con", "1"),
    ("catalogo.insumo_nombre_comercial", "catalogo.insumo", "insumo_id", "se conoce como", "1"),
    ("catalogo.insumo_factor_historial", "catalogo.insumo", "insumo_id", "cambia de factor con", "1"),
    ("compras.compra", "obra.proyecto", "proyecto_id", "se registra en", "1"),
    ("compras.compra", "compras.proveedor", "proveedor_id", "se compra a", "1"),
    ("compras.compra", "compras.solicitud", "proyecto_id,solicitud_id", "se atiende con", "0..1"),
    ("compras.compra_detalle", "compras.compra", "compra_id", "se compone de", "1"),
    ("compras.compra_detalle", "catalogo.insumo", "insumo_id", "es de", "1"),
    ("compras.compra_adjunto", "compras.compra", "compra_id", "se respalda con", "1"),
    ("compras.solicitud", "obra.proyecto", "proyecto_id", "se emite en", "1"),
    ("compras.solicitud_linea", "compras.solicitud", "solicitud_id", "se compone de", "1"),
    ("compras.solicitud_linea", "catalogo.insumo", "insumo_id", "pide", "0..1"),
    ("tareo.trabajador", "obra.proyecto", "proyecto_id", "trabaja en", "1"),
    ("tareo.jornada", "tareo.trabajador", "proyecto_id,trabajador_id", "registra", "1"),
    ("tareo.jornada", "obra.periodo", "proyecto_id,periodo_id", "cae en", "1"),
    ("tareo.jornada", "tareo.jornada", "trabajador_id,fecha,corrige_a_id", "corrige a", "0..1"),
    ("outbox.evento", "obra.proyecto", "proyecto_id", "avisa sobre", "1"),
]
# Valores que el diagrama escribe a mano y la base fija con CHECK: se comprueban al generar.
VALORES = {
    ("obra.usuario", "estado"): ["activo", "baja"], ("obra.proyecto", "estado"): ["en_ejecucion", "cerrado"],
    ("obra.periodo", "estado"): ["abierto", "cerrado"], ("obra.almacen", "tipo"): ["central", "frente"],
    ("obra.almacen", "estado"): ["activo", "inactivo"],
    ("obra.asignacion", "rol"): ["almacenero", "ssoma", "residente", "administrador", "gerente"],
    ("obra.dispositivo", "estado"): ["pendiente", "activo", "revocado"],
    ("catalogo.insumo", "tipo"): ["material", "mano_obra", "equipo", "subcontrato"],
    ("compras.proveedor", "estado_validacion"): ["validado", "sin_validar"], ("compras.proveedor", "estado"): ["activo", "baja"],
    ("compras.compra", "moneda"): ["PEN", "USD"], ("compras.compra", "estado"): ["registrada", "anulada"],
    ("compras.solicitud", "estado"): ["emitida", "anulada"],
    ("tareo.trabajador", "tipo_documento"): ["DNI", "CE", "PASAPORTE"], ("tareo.trabajador", "categoria"): ["operario", "oficial", "peon"],
    ("tareo.jornada", "categoria"): ["operario", "oficial", "peon"], ("tareo.jornada", "estado"): ["presente", "ausente", "permiso"],
}
FUTURO = [  # la parte de Henry: el contrato que ya fija este esquema
    ("obra.almacen", "se guarda en"), ("obra.periodo", "cae en"), ("obra.dispositivo", "se registra desde"),
    ("catalogo.insumo", "mueve a"), ("compras.compra", "se recibe en (solo ingreso)"),
]
CARD = {"1": "teetee", "0..1": "teeodot"}


def esc(t) -> str:
    return html.escape(str(t), quote=True)


def verificar(c) -> list[tuple[str, str, str]]:
    cols = {f"{r['s']}.{r['t']}": set() for r in c.execute(
        "SELECT n.nspname s, cl.relname t FROM pg_class cl JOIN pg_namespace n ON n.oid=cl.relnamespace "
        "WHERE cl.relkind='r' AND n.nspname = ANY(%s)", (ESQUEMAS,))}
    for r in c.execute("SELECT n.nspname s, cl.relname t, a.attname a FROM pg_attribute a JOIN pg_class cl ON cl.oid=a.attrelid "
                       "JOIN pg_namespace n ON n.oid=cl.relnamespace WHERE cl.relkind='r' AND n.nspname = ANY(%s) "
                       "AND a.attnum>0 AND NOT a.attisdropped", (ESQUEMAS,)):
        cols[f"{r['s']}.{r['t']}"].add(r["a"])
    errores = []
    for t in cols:
        if t not in E and t not in TECNICAS:
            errores.append(f"la tabla {t} existe en la base y no está en el diagrama lógico")
    for t, (_, atrs, _) in E.items():
        if t not in cols:
            errores.append(f"{t} está en el diagrama y no existe en la base")
            continue
        for _, col, _ in atrs:
            if col not in cols[t]:
                errores.append(f"{t}.{col} está en el diagrama y no existe en la base")
    fks = [(f"{r['o']}", f"{r['d']}", r["oc"]) for r in c.execute("""
        SELECT cl.relnamespace::regnamespace::text||'.'||cl.relname o, k.confrelid::regclass::text d,
               (SELECT string_agg(a.attname, ',' ORDER BY array_position(k.conkey, a.attnum)) FROM pg_attribute a
                 WHERE a.attrelid = k.conrelid AND a.attnum = ANY(k.conkey)) oc
          FROM pg_constraint k JOIN pg_class cl ON cl.oid = k.conrelid
         WHERE k.contype = 'f' AND cl.relnamespace::regnamespace::text = ANY(%s)""", (ESQUEMAS,))]
    def clave(h, p, cc):        # el orden de las columnas de una FK compuesta no importa
        return (h, p, frozenset(cc.split(",")))

    dibujadas = {clave(h, p, cc) for h, p, cc, *_ in R}
    existentes = {clave(h, p, cc) for h, p, cc in fks}
    for h, p, cc in fks:
        if p == "obra.usuario" and clave(h, p, cc) not in dibujadas:
            continue            # autoría (registrado_por, anulada_por…): se explica en la leyenda
        if h in TECNICAS:
            continue
        if clave(h, p, cc) not in dibujadas:
            errores.append(f"la clave foránea {h}({cc}) -> {p} no tiene relación dibujada")
    for h, p, cc, *_ in R:
        if clave(h, p, cc) not in existentes:
            errores.append(f"la relación {h}({cc}) -> {p} está dibujada y no existe en la base")
    for (t, col), valores in VALORES.items():
        defs = [r["d"] for r in c.execute(
            "SELECT pg_get_constraintdef(k.oid) d FROM pg_constraint k WHERE k.contype='c' AND k.conrelid = %s::regclass", (t,))]
        permitido = next((d for d in defs if f"({col} " in d and "ANY" in d), None)
        if permitido is None:
            errores.append(f"{t}.{col} no tiene un CHECK de valores y el diagrama lista {valores}")
        else:
            en_base = set(re.findall(r"'([^']+)'", permitido))
            if en_base != set(valores):
                errores.append(f"{t}.{col}: la base permite {sorted(en_base)} y el diagrama dice {sorted(valores)}")
    if errores:
        raise SystemExit("El diagrama lógico se desvió de la base:\n  - " + "\n  - ".join(errores))
    return fks


def entidad(t: str) -> str:
    esquema = t.split(".")[0]
    nombre, atrs, regla = E[t]
    cab, _ = COLOR[esquema]
    filas = [f'<TR><TD BGCOLOR="{cab}"><FONT COLOR="white" POINT-SIZE="13"><B>{esc(nombre)}</B></FONT></TD></TR>']
    ids = [a for a, _, m in atrs if m == "id"]
    for a, _, m in atrs:
        if m == "id":
            filas.append(f'<TR><TD ALIGN="LEFT"><U><B>{esc(a)}</B></U></TD></TR>')
    for a, _, m in atrs:
        if m == "id":
            continue
        gris = ' COLOR="#777777"' if m == "?" else ""
        filas.append(f'<TR><TD ALIGN="LEFT"><FONT{gris}>{esc(a)}{" (opcional)" if m == "?" else ""}</FONT></TD></TR>')
    if regla:
        filas.append(f'<TR><TD ALIGN="LEFT" BGCOLOR="#FFF2CC"><FONT POINT-SIZE="9" COLOR="#7F6000"><I>{esc(regla)}</I></FONT></TD></TR>')
    return (f'    "{t}" [label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="3" COLOR="{cab}">'
            f'{"".join(filas)}</TABLE>>];')


def main() -> None:
    with psycopg.connect(DSN, row_factory=dict_row) as c:
        verificar(c)

    L = ['digraph ER {', '  graph [rankdir=TB, fontname="Helvetica", fontsize=26, labelloc=t, pad=0.4, nodesep=0.55, ranksep=1.0, '
         'splines=true, newrank=true, '
         'label=<<B>SIGRO · Diagrama entidad-relación (modelo lógico)</B><BR/><FONT POINT-SIZE="15">'
         'Qué cosas existen en la obra y cómo se relacionan · verificado contra la base migrada (0001 a 0006)</FONT>>];',
         '  node [shape=plain, fontname="Helvetica", fontsize=11];',
         '  edge [color="#444444", fontname="Helvetica", fontsize=10, arrowsize=1.1, dir=both];']
    for esquema in ESQUEMAS:
        _, fondo = COLOR[esquema]
        L.append(f'  subgraph cluster_{esquema} {{ label=<<B>{esc(GRUPO[esquema])}</B>>; fontsize=17; style="rounded,filled"; '
                 f'fillcolor="{fondo}"; color="{COLOR[esquema][0]}"; penwidth=2;')
        for t in E:
            if t.startswith(esquema + "."):
                L.append(entidad(t))
        L.append("  }")
    # La parte de Henry
    L.append('  subgraph cluster_henry { label=<<B>Almacén y costo (Henry) · aún sin migración</B>>; fontsize=17; style="rounded,dashed"; color="#999999";')
    L.append('    "almacen.movimiento" [label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="3" COLOR="#999999" STYLE="dashed">'
             '<TR><TD BGCOLOR="#7F7F7F"><FONT COLOR="white" POINT-SIZE="13"><B>Movimiento</B></FONT></TD></TR>'
             '<TR><TD ALIGN="LEFT"><U><B>Identificador del celular (único)</B></U></TD></TR>'
             '<TR><TD ALIGN="LEFT">Tipo · cantidad · costo unitario</TD></TR>'
             '<TR><TD ALIGN="LEFT" BGCOLOR="#FFF2CC"><FONT POINT-SIZE="9" COLOR="#7F6000"><I>Libro: solo se agrega · sin saldo guardado:<BR/>el saldo se suma desde los movimientos</I></FONT></TD></TR>'
             '</TABLE>>];')
    L.append("  }")

    for hijo, padre, _cols, verbo, card in R:
        if hijo == padre:       # autorrelación: la corrección apunta a la jornada original
            L.append(f'  "{hijo}" -> "{padre}" [taillabel="0..N", headlabel="0..1", label="{verbo}", '
                     f'arrowtail=crowodot, arrowhead=teeodot, headport=e, tailport=e];')
            continue
        L.append(f'  "{padre}" -> "{hijo}" [label=" {verbo} ", arrowtail={CARD[card]}, arrowhead=crowodot];')
    for tabla, verbo in FUTURO:
        L.append(f'  "{tabla}" -> "almacen.movimiento" [label=" {verbo} ", arrowtail=teetee, arrowhead=crowodot, style=dashed, color="#999999", fontcolor="#777777"];')

    L.append('  subgraph cluster_leyenda { label=<<B>Cómo leerlo</B>>; fontsize=16; style="rounded"; color="#666666";')
    L.append('    leyenda [label=<<TABLE BORDER="0" CELLBORDER="0" CELLSPACING="0" CELLPADDING="3">'
             '<TR><TD ALIGN="LEFT"><U><B>Subrayado</B></U>  lo que identifica a la entidad (en una obra, nunca se repite)</TD></TR>'
             '<TR><TD ALIGN="LEFT"><FONT COLOR="#777777">gris (opcional)</FONT>  puede quedar vacío</TD></TR>'
             '<TR><TD ALIGN="LEFT"><B>||──</B> uno y solo uno · <B>|o──</B> uno o ninguno · <B>──o&lt;</B> ninguno, uno o varios</TD></TR>'
             '<TR><TD ALIGN="LEFT">Leer así: «<I>Obra</I> se divide en (0..N) <I>Periodos</I>»; cada Periodo es de exactamente una Obra.</TD></TR>'
             '<TR><TD ALIGN="LEFT" BGCOLOR="#FFF2CC"><FONT COLOR="#7F6000"><I>Regla en amarillo</I>  lo que el motor de la base impide romper</FONT></TD></TR>'
             '<TR><TD ALIGN="LEFT"><B>Asignación</B> es la relación Usuario–Obra con atributo propio (el rol): una persona puede ser almacenero en una obra y nada en otra.</TD></TR>'
             '<TR><TD ALIGN="LEFT">Quién registró, anuló, autorizó o cerró cada hecho (siempre un <B>Usuario</B>) no se dibuja para no saturar; está en el físico.</TD></TR>'
             '<TR><TD ALIGN="LEFT">El contador del correlativo de solicitudes es una tabla técnica y no se dibuja.</TD></TR>'
             '<TR><TD ALIGN="LEFT">Línea punteada: contrato con el módulo de Henry (aún sin migración).</TD></TR>'
             '</TABLE>>];')
    L.append("  }")
    L.append("}")

    dot = SALIDA / "diagrama-er.dot"
    dot.write_text("\n".join(L), encoding="utf-8")
    for fmt, extra in (("svg", []), ("png", ["-Gdpi=110"])):
        subprocess.run(["dot", f"-T{fmt}", *extra, str(dot), "-o", str(SALIDA / f"diagrama-er.{fmt}")], check=True)
    print(f"{len(E)} entidades, {len(R)} relaciones, verificadas contra la base -> {SALIDA}/diagrama-er.(dot|svg|png)")


if __name__ == "__main__":
    main()
