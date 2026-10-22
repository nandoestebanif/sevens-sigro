"""Genera el diagrama físico DESDE la base migrada (no dibujado a mano: no puede divergir).

Uso:  SIGRO_DB=sigro_dev python docs/generar_diagrama.py
Salida: docs/diagrama-fisico.dot, .svg y .png  (requiere Graphviz: `dot`)
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
COLOR = {  # cabecera, fondo del grupo
    "obra": ("#1F4E79", "#EAF1F8"),
    "catalogo": ("#7A4B00", "#FBF3E4"),
    "compras": ("#2E6B3A", "#EAF5EC"),
    "tareo": ("#6B2E6B", "#F6ECF6"),
    "outbox": ("#8A3B12", "#FBEDE6"),
}
LIBROS = {"obra.proyecto_plazo_historial", "tareo.jornada", "compras.compra_detalle", "compras.compra_adjunto",
          "compras.solicitud_linea"}


def consultar(c, sql, *a):
    return c.execute(sql, a).fetchall()


def esc(t) -> str:
    return html.escape(str(t), quote=True)


def main() -> None:
    with psycopg.connect(DSN, row_factory=dict_row) as c:
        tablas = consultar(c, """
            SELECT n.nspname AS esquema, cl.relname AS tabla, cl.oid AS oid
              FROM pg_class cl JOIN pg_namespace n ON n.oid = cl.relnamespace
             WHERE cl.relkind = 'r' AND n.nspname = ANY(%s) ORDER BY 1, 2""", ESQUEMAS)

        columnas, pk, fk, uq, disparadores = {}, {}, {}, {}, {}
        aristas = []
        for t in tablas:
            clave = f"{t['esquema']}.{t['tabla']}"
            columnas[clave] = consultar(c, """
                SELECT a.attname AS nombre, format_type(a.atttypid, a.atttypmod) AS tipo, a.attnotnull AS no_nulo,
                       (a.attidentity <> '') AS identidad
                  FROM pg_attribute a WHERE a.attrelid = %s AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum""", t["oid"])
            filas = consultar(c, """
                SELECT i.indisprimary AS es_pk, pg_get_indexdef(i.indexrelid) AS def, ic.relname AS nombre,
                       (SELECT array_agg(a.attname ORDER BY k.ord) FROM unnest(i.indkey) WITH ORDINALITY k(attnum, ord)
                          JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum WHERE k.attnum > 0) AS cols
                  FROM pg_index i JOIN pg_class ic ON ic.oid = i.indexrelid
                 WHERE i.indrelid = %s AND i.indisunique""", t["oid"])
            pk[clave] = next((f["cols"] for f in filas if f["es_pk"]), [])
            uq[clave] = []
            for f in filas:
                if f["es_pk"]:
                    continue
                donde = re.search(r"WHERE \((.*)\)$", f["def"])
                if donde:
                    donde = re.sub(r"\)?::text", "", donde.group(1))
                expr = re.search(r"\((.*?)\)(?: WHERE|$)", f["def"])
                cols = f["cols"] or []
                etiqueta = ", ".join(cols) if cols else (expr.group(1) if expr else f["nombre"])
                if len(cols) and "lower(" in f["def"]:
                    etiqueta = re.search(r"USING btree \((.*?)\)(?: WHERE|$)", f["def"]).group(1)
                uq[clave].append((etiqueta, donde if donde else None))
            for r in consultar(c, """
                SELECT conname, confrelid::regclass::text AS destino, conkey, confkey, conrelid
                  FROM pg_constraint WHERE conrelid = %s AND contype = 'f'""", t["oid"]):
                origen = [a["attname"] for a in consultar(c, "SELECT attname FROM pg_attribute WHERE attrelid=%s AND attnum = ANY(%s) ORDER BY array_position(%s, attnum)", t["oid"], r["conkey"], r["conkey"])]
                dest_oid = c.execute("SELECT %s::regclass::oid AS o", (r["destino"],)).fetchone()["o"]
                dest = [a["attname"] for a in consultar(c, "SELECT attname FROM pg_attribute WHERE attrelid=%s AND attnum = ANY(%s) ORDER BY array_position(%s, attnum)", dest_oid, r["confkey"], r["confkey"])]
                destino = r["destino"] if "." in r["destino"] else f"public.{r['destino']}"
                aristas.append((clave, origen, destino, dest, len(origen) > 1))
            fk[clave] = {col for a in aristas if a[0] == clave for col in a[1]}
            disparadores[clave] = [x["tgname"] for x in consultar(
                c, "SELECT tgname FROM pg_trigger WHERE tgrelid = %s AND NOT tgisinternal ORDER BY 1", t["oid"])]

        vistas = [f"{r['schemaname']}.{r['viewname']}" for r in consultar(
            c, "SELECT schemaname, viewname FROM pg_views WHERE schemaname = ANY(%s) ORDER BY 1, 2", ESQUEMAS)]
        funciones = [f"{r['nspname']}.{r['proname']}()" for r in consultar(
            c, "SELECT n.nspname, p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace "
               "WHERE n.nspname = ANY(%s) AND p.prosecdef ORDER BY 1, 2", ESQUEMAS)]

    L = ['digraph SIGRO {', '  graph [rankdir=LR, fontname="Helvetica", fontsize=26, labelloc=t, pad=0.4, nodesep=0.35, ranksep=1.3, '
         'splines=true, compound=true, newrank=true, '
         'label=<<B>SIGRO · Diagrama físico de la base de datos (PostgreSQL)</B><BR/><FONT POINT-SIZE="15">'
         'Módulos obra, catálogo, compras, tareo y outbox · generado desde la base migrada (migraciones 0001 a 0006)</FONT>>];',
         '  node [shape=plain, fontname="Helvetica", fontsize=11];',
         '  edge [color="#555555", arrowsize=0.7, fontname="Helvetica", fontsize=9];']

    def nodo(clave: str) -> str:
        esquema, nombre = clave.split(".")
        cab, _ = COLOR[esquema]
        insignias = []
        if clave in LIBROS:
            insignias.append('<FONT COLOR="#FFD966"> APPEND-ONLY</FONT>')
        elif clave == "outbox.evento":
            insignias.append('<FONT COLOR="#FFD966"> bandeja de salida</FONT>')
        elif clave == "compras.solicitud_correlativo":
            insignias.append('<FONT COLOR="#FFD966"> contador del servidor</FONT>')
        filas = [f'<TR><TD COLSPAN="3" BGCOLOR="{cab}"><FONT COLOR="white"><B>{esc(esquema)}.</B><B>{esc(nombre)}</B></FONT>{"".join(insignias)}</TD></TR>']
        for col in columnas[clave]:
            marcas = []
            if col["nombre"] in pk[clave]:
                marcas.append("PK")
            if col["nombre"] in fk[clave]:
                marcas.append("FK")
            tipo = col["tipo"].replace("timestamp with time zone", "timestamptz").replace("character varying", "varchar")
            nulo = "" if col["no_nulo"] else "?"
            filas.append(
                f'<TR><TD ALIGN="LEFT" PORT="c_{col["nombre"]}"><FONT POINT-SIZE="10" COLOR="#B00020"><B>{" ".join(marcas) or "  "}</B></FONT></TD>'
                f'<TD ALIGN="LEFT">{esc(col["nombre"])}</TD><TD ALIGN="LEFT"><FONT COLOR="#666666" POINT-SIZE="10">{esc(tipo)}{nulo}</FONT></TD></TR>')
        for etiqueta, donde in uq[clave]:
            txt = f"UNIQUE ({esc(etiqueta)})" + (f"<BR/>  WHERE {esc(donde)}" if donde else "")
            filas.append(f'<TR><TD COLSPAN="3" ALIGN="LEFT" BGCOLOR="#FFF2CC"><FONT POINT-SIZE="10" COLOR="#7F6000"><B>{txt}</B></FONT></TD></TR>')
        extra = [d for d in disparadores[clave] if not d.endswith(("_no_borrar", "_no_truncar", "_append_only"))]
        tags = []
        if any(d.endswith("_append_only") for d in disparadores[clave]):
            tags.append("sin UPDATE · sin DELETE")
        elif any(d.endswith("_no_borrar") for d in disparadores[clave]):
            tags.append("sin DELETE (se da de baja)")
        if extra:
            tags.append("reglas: " + ", ".join(e.removeprefix("tg_") for e in extra))
        for tx in tags:
            filas.append(f'<TR><TD COLSPAN="3" ALIGN="LEFT" BGCOLOR="#F2F2F2"><FONT POINT-SIZE="9" COLOR="#444444">{esc(tx)}</FONT></TD></TR>')
        return f'    "{clave}" [label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="3" COLOR="{cab}">{"".join(filas)}</TABLE>>];'

    for esquema in ESQUEMAS:
        _, fondo = COLOR[esquema]
        L.append(f'  subgraph cluster_{esquema} {{ label=<<B>esquema {esquema}</B>>; fontsize=18; style="rounded,filled"; '
                 f'fillcolor="{fondo}"; color="{COLOR[esquema][0]}"; penwidth=2;')
        for t in tablas:
            if t["esquema"] == esquema:
                L.append(nodo(f"{esquema}.{t['tabla']}"))
        L.append("  }")

    # La parte de Henry: el contrato que ya fija este esquema
    L.append('  subgraph cluster_henry { label=<<B>esquema almacen (Henry) · aún sin migración</B>>; fontsize=18; style="rounded,dashed"; color="#999999";')
    L.append('    "almacen.movimiento" [label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="3" COLOR="#999999" STYLE="dashed">'
             '<TR><TD COLSPAN="2" BGCOLOR="#7F7F7F"><FONT COLOR="white"><B>almacen.movimiento</B></FONT><FONT COLOR="#FFD966"> APPEND-ONLY</FONT></TD></TR>'
             '<TR><TD ALIGN="LEFT" PORT="c_uuid_cliente">uuid_cliente</TD><TD ALIGN="LEFT"><FONT COLOR="#B00020"><B>UNIQUE</B></FONT></TD></TR>'
             '<TR><TD ALIGN="LEFT" PORT="c_almacen_id">almacen_id, proyecto_id</TD><TD ALIGN="LEFT"><FONT COLOR="#B00020">FK</FONT></TD></TR>'
             '<TR><TD ALIGN="LEFT" PORT="c_periodo_id">periodo_id, proyecto_id</TD><TD ALIGN="LEFT"><FONT COLOR="#B00020">FK</FONT></TD></TR>'
             '<TR><TD ALIGN="LEFT" PORT="c_dispositivo_id">dispositivo_id, proyecto_id</TD><TD ALIGN="LEFT"><FONT COLOR="#B00020">FK</FONT></TD></TR>'
             '<TR><TD ALIGN="LEFT" PORT="c_insumo_id">insumo_id</TD><TD ALIGN="LEFT"><FONT COLOR="#B00020">FK</FONT></TD></TR>'
             '<TR><TD ALIGN="LEFT" PORT="c_compra_id">compra_id (solo recepción)</TD><TD ALIGN="LEFT"><FONT COLOR="#B00020">FK</FONT></TD></TR>'
             '<TR><TD ALIGN="LEFT">tipo · cantidad &gt; 0 · costo_unitario</TD><TD></TD></TR>'
             '<TR><TD COLSPAN="2" ALIGN="LEFT" BGCOLOR="#FFF2CC"><FONT POINT-SIZE="10" COLOR="#7F6000"><B>SIN columna de saldo.</B><BR ALIGN="LEFT"/>'
             '<B>El saldo se suma desde los movimientos.</B><BR ALIGN="LEFT"/>'
             '<B>Sin UPDATE ni DELETE: corregir = reverso.</B><BR ALIGN="LEFT"/></FONT></TD></TR></TABLE>>];')
    L.append("  }")
    for campo, destino in (("c_almacen_id", "obra.almacen:c_id"), ("c_periodo_id", "obra.periodo:c_id"),
                           ("c_dispositivo_id", "obra.dispositivo:c_id"), ("c_insumo_id", "catalogo.insumo:c_id"),
                           ("c_compra_id", "compras.compra:c_id")):
        L.append(f'  "almacen.movimiento":{campo} -> "{destino.split(":")[0]}":{destino.split(":")[1]} [style=dashed, color="#999999"];')

    for origen, ocols, destino, dcols, compuesta in aristas:
        if destino.split(".")[0] not in ESQUEMAS or destino == "obra.usuario":
            continue      # las FK de autoría hacia usuario saturan el dibujo; se declaran en la leyenda
        L.append(f'  "{origen}":c_{ocols[0]} -> "{destino}":c_{dcols[0]}' + (' [color="#2F5597", penwidth=1.4]' if compuesta else "") + ";")

    # Leyenda y frontera
    L.append('  subgraph cluster_leyenda { label=<<B>Cómo leerlo</B>>; fontsize=16; style="rounded"; color="#666666";')
    L.append('    leyenda [label=<<TABLE BORDER="0" CELLBORDER="0" CELLSPACING="0" CELLPADDING="3">'
             '<TR><TD ALIGN="LEFT"><FONT COLOR="#B00020"><B>PK / FK</B></FONT>  clave primaria / foránea (flecha del hijo al padre)</TD></TR>'
             '<TR><TD ALIGN="LEFT"><FONT COLOR="#2F5597"><B>Flecha azul gruesa</B></FONT>  FK compuesta (id, proyecto_id): el motor garantiza que ambos son de la misma obra</TD></TR>'
             '<TR><TD ALIGN="LEFT" BGCOLOR="#FFF2CC"><FONT COLOR="#7F6000"><B>UNIQUE</B></FONT>  índice único que sostiene una regla del dominio (parcial si trae WHERE)</TD></TR>'
             '<TR><TD ALIGN="LEFT"><B>?</B> junto al tipo: admite NULL</TD></TR>'
             '<TR><TD ALIGN="LEFT">Las FK de autoría hacia <B>obra.usuario</B> (registrado_por, autorizado_por, creado_por, anulada_por...) están marcadas FK pero no dibujadas, para no saturar.</TD></TR>'
             '<TR><TD ALIGN="LEFT"><B>APPEND-ONLY</B>  libro: solo se inserta (disparador + sin permiso de UPDATE/DELETE para la aplicación)</TD></TR>'
             f'<TR><TD ALIGN="LEFT">Vistas derivadas: {esc(", ".join(v.split(".")[1] for v in vistas))}</TD></TR>'
             f'<TR><TD ALIGN="LEFT">Funciones SECURITY DEFINER (únicas que escriben periodos, plazo y correlativos): {esc(", ".join(funciones))}</TD></TR>'
             '<TR><TD ALIGN="LEFT" BGCOLOR="#E8E8E8"><B>Frontera:</B> los microservicios NO leen ni escriben en esta base. Integraciones (S10, SUNAT, RENIEC) devuelve datos y la API decide qué guardar;</TD></TR>'
             '<TR><TD ALIGN="LEFT" BGCOLOR="#E8E8E8">Notificaciones recibe por HTTP lo que el relevo toma de <B>outbox.evento</B> (escrito en la misma transacción que el hecho) y guarda su propia copia.</TD></TR>'
             '</TABLE>>];')
    L.append("  }")
    L.append("}")

    dot = SALIDA / "diagrama-fisico.dot"
    dot.write_text("\n".join(L), encoding="utf-8")
    for fmt, extra in (("svg", []), ("png", ["-Gdpi=110"])):
        subprocess.run(["dot", f"-T{fmt}", *extra, str(dot), "-o", str(SALIDA / f"diagrama-fisico.{fmt}")], check=True)
    print(f"{len(tablas)} tablas, {len(aristas)} claves foráneas, {len(vistas)} vistas -> {SALIDA}/diagrama-fisico.(dot|svg|png)")


if __name__ == "__main__":
    main()
