"""Genera los tres diagramas como archivos draw.io (.drawio) EDITABLES: físico y entidad-relación (el de despliegue: generar_despliegue.py).

Mismas fuentes que los PNG, para que no se contradigan:
  - físico: se lee de la base migrada (tablas, columnas, claves, índices únicos, disparadores);
  - entidad-relación: el modelo lógico de generar_er.py, que se verifica contra la base antes de dibujar;
  - despliegue: lo genera generar_despliegue.py (desde docker-compose.yml).
Las posiciones salen de Graphviz (`dot`), el dibujo es nativo de draw.io: cada entidad es un contenedor con una fila por
atributo y cada relación una flecha con notación de pata de gallo; todo se puede mover, renombrar y volver a conectar.

Uso:  SIGRO_DB=sigro_dev python docs/generar_drawio.py
Salida: docs/diagrama-fisico.drawio, diagrama-er.drawio
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import psycopg
from psycopg.rows import dict_row

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generar_er as ER  # noqa: E402

SALIDA = Path(__file__).resolve().parent
FILA = 20          # alto de una fila de atributo
CAB = 30           # alto de la cabecera de una entidad
LETRA = 6.4        # ancho aproximado por carácter a 11 px


# ───────────────────────────────────────────── utilidades de draw.io
class Lienzo:
    def __init__(self, nombre: str):
        self.nombre = nombre
        self.raiz = ET.Element("root")
        ET.SubElement(self.raiz, "mxCell", id="0")
        ET.SubElement(self.raiz, "mxCell", id="1", parent="0")
        self.n = 1
        self.ancho = self.alto = 0

    def id(self) -> str:
        self.n += 1
        return f"c{self.n}"

    def vertice(self, valor, estilo, x, y, w, h, padre="1", id=None):
        id = id or self.id()
        c = ET.SubElement(self.raiz, "mxCell", id=id, value=valor, style=estilo, vertex="1", parent=padre)
        ET.SubElement(c, "mxGeometry", {"x": str(round(x)), "y": str(round(y)), "width": str(round(w)), "height": str(round(h)), "as": "geometry"})
        if padre == "1":
            self.ancho, self.alto = max(self.ancho, x + w), max(self.alto, y + h)
        return id

    def arista(self, origen, destino, estilo, valor="", padre="1", puntos=None):
        id = self.id()
        c = ET.SubElement(self.raiz, "mxCell", id=id, value=valor, style=estilo, edge="1", source=origen, target=destino, parent=padre)
        g = ET.SubElement(c, "mxGeometry", {"relative": "1", "as": "geometry"})
        if puntos:
            arr = ET.SubElement(g, "Array", {"as": "points"})
            for x, y in puntos:
                ET.SubElement(arr, "mxPoint", x=str(round(x)), y=str(round(y)))
        return id

    def guardar(self, ruta: Path) -> None:
        modelo = ET.Element("mxGraphModel", dx="1400", dy="900", grid="1", gridSize="10", guides="1", tooltips="1", connect="1", arrows="1",
                            fold="1", page="1", pageScale="1", pageWidth=str(int(self.ancho + 120)), pageHeight=str(int(self.alto + 120)),
                            math="0", shadow="0")
        modelo.append(self.raiz)
        archivo = ET.Element("mxfile", host="app.diagrams.net", agent="SIGRO generar_drawio.py", version="24.0.0", type="device")
        d = ET.SubElement(archivo, "diagram", id=re.sub(r"\W", "", self.nombre)[:20] or "d", name=self.nombre)
        d.append(modelo)
        ET.indent(archivo)
        ruta.write_text('<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(archivo, encoding="unicode"), encoding="utf-8")


def ancho_texto(t: str, minimo=140, extra=26) -> float:
    plano = re.sub(r"<[^>]+>", "", t.replace("<br>", "\n"))
    return max(minimo, max((len(l) for l in plano.split("\n")), default=0) * LETRA + extra)


def disponer(nodos, aristas, grupos, rankdir="TB", nodesep=0.6, ranksep=1.0):
    """Posiciones con Graphviz. nodos: id -> (w, h, grupo|None); grupos: id -> etiqueta. Devuelve (pos nodos, cajas grupos)."""
    L = [f'digraph G {{ rankdir={rankdir}; nodesep={nodesep}; ranksep={ranksep}; newrank=true; compound=true; splines=polyline;',
         'node [shape=box, fixedsize=true, label=""]; graph [fontsize=14, margin=26, labeljust=l, labelloc=t];']
    for g, etiqueta in grupos.items():
        L.append(f'subgraph "cluster_{g}" {{ label="{etiqueta}"; ')
        for nid, (w, h, gr) in nodos.items():
            if gr == g:
                L.append(f'"{nid}" [width={w/72:.3f}, height={h/72:.3f}];')
        L.append("}")
    for nid, (w, h, gr) in nodos.items():
        if gr is None:
            L.append(f'"{nid}" [width={w/72:.3f}, height={h/72:.3f}];')
    for a, b in aristas:
        L.append(f'"{a}" -> "{b}";')
    L.append("}")
    r = subprocess.run(["dot", "-Tjson"], input="\n".join(L), capture_output=True, text=True, check=True)
    j = json.loads(r.stdout)
    _, _, ux, uy = (float(v) for v in j["bb"].split(","))
    pos, cajas, rutas = {}, {}, {}
    for e in j.get("edges", []):
        pts = []
        for op in e.get("_draw_", []):
            if op.get("op") == "b":
                pts = [(float(a), float(b)) for a, b in op["points"]]
        names = {o["_gvid"]: o["name"] for o in j["objects"]}
        rutas.setdefault((names[e["tail"]], names[e["head"]]), []).append([(x, uy - y) for x, y in pts])
    for o in j["objects"]:
        nombre = o["name"]
        if nombre.startswith("cluster_"):
            x0, y0, x1, y1 = (float(v) for v in o["bb"].split(","))
            cajas[nombre[8:]] = (x0, uy - y1, x1 - x0, y1 - y0)
        elif nombre in nodos:
            x, y = (float(v) for v in o["pos"].split(","))
            w, h, _ = nodos[nombre]
            pos[nombre] = (x - w / 2, uy - y - h / 2)
    return pos, cajas, rutas


def estilo_entidad(color):
    return (f"swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize={CAB};horizontalStack=0;resizeParent=1;resizeParentMax=0;"
            f"resizeLast=0;collapsible=0;marginBottom=0;html=1;fillColor={color};fontColor=#FFFFFF;strokeColor={color};swimlaneFillColor=#FFFFFF;"
            "fontSize=12;align=left;spacingLeft=8;")


ESTILO_FILA = ("text;html=1;strokeColor=none;fillColor=none;align=left;verticalAlign=middle;spacingLeft=8;spacingRight=6;overflow=hidden;"
               "rotatable=0;whiteSpace=nowrap;fontSize=11;")


def ensamblar(lienzo, entidades, grupos_estilo, pos, cajas, rutas, titulo, subtitulo, aristas_dibujo, leyenda_html, leyenda_ancho=620, extra_cajas=None):
    """Coloca grupos, entidades y aristas. entidades: id -> dict(grupo, color, cab, filas[(html, fill|None)], w, h, dashed)."""
    # desplazar todo para dejar sitio al título y a la leyenda
    dy = 150
    lienzo.vertice(f'<b style="font-size:22px">{titulo}</b><br><span style="font-size:13px;color:#555555">{subtitulo}</span>',
                   "text;html=1;strokeColor=none;fillColor=none;align=left;verticalAlign=top;whiteSpace=wrap;", 0, 0, 1100, 60)
    ids_grupo = {}
    for g, (x, y, w, h) in cajas.items():
        borde, fondo, etiqueta, discontinuo = grupos_estilo[g]
        ids_grupo[g] = lienzo.vertice(
            etiqueta, f"swimlane;startSize=30;html=1;rounded=1;arcSize=2;fontStyle=1;fontSize=14;fillColor={fondo};strokeColor={borde};"
                      f"strokeWidth=2;swimlaneFillColor={fondo};align=left;spacingLeft=10;collapsible=0;{'dashed=1;' if discontinuo else ''}",
            x, y + dy, w, h)
    ids = {}
    for eid, e in entidades.items():
        x, y = pos[eid]
        gx, gy = (cajas[e["grupo"]][0], cajas[e["grupo"]][1]) if e["grupo"] in ids_grupo else (0, 0)
        padre = ids_grupo.get(e["grupo"], "1")
        rel_y = (y - gy) if padre != "1" else y + dy
        ids[eid] = lienzo.vertice(e["cab"], estilo_entidad(e["color"]) + ("dashed=1;" if e.get("dashed") else ""), x - gx, rel_y, e["w"], e["h"], padre)
        for i, (html_fila, fondo) in enumerate(e["filas"]):
            est = ESTILO_FILA + (f"fillColor={fondo};" if fondo else "")
            lienzo.vertice(html_fila, est, 0, CAB + i * FILA, e["w"], FILA, ids[eid])
    for a in aristas_dibujo:
        origen, destino, estilo, valor = a
        pa, pb = entidades[origen]["grupo"], entidades[destino]["grupo"]
        # waypoints de Graphviz en coordenadas absolutas (padre = lienzo); se toman los puntos interiores de la curva
        ruta = (rutas.get((origen, destino)) or [[]]).pop(0)
        interiores = [(x, y + dy) for x, y in ruta[1:-1]][::2] if len(ruta) > 4 else []
        lienzo.arista(ids[origen], ids[destino], estilo, valor, "1", interiores)
    # leyenda
    bx = min((p[0] for p in pos.values()), default=0)
    lienzo.vertice(leyenda_html, "rounded=1;arcSize=3;html=1;whiteSpace=wrap;align=left;verticalAlign=top;fillColor=#FFFFFF;strokeColor=#666666;"
                                 "spacing=10;fontSize=11;", max(0, bx), 70, leyenda_ancho, 70 if leyenda_ancho > 700 else 80)
    return ids


ARISTA = ("rounded=0;html=1;strokeColor={c};strokeWidth={w};fontSize=10;"
          "fontColor={c};labelBackgroundColor=#FFFFFF;startArrow={ini};endArrow={fin};startFill=0;endFill=0;startSize=12;endSize=12;{extra}")


# ───────────────────────────────────────────── 1. Diagrama FÍSICO (desde la base)
def fisico() -> None:
    with psycopg.connect(ER.DSN, row_factory=dict_row) as c:
        def q(sql, *a):
            return c.execute(sql, a).fetchall()

        tablas = q("SELECT n.nspname AS esquema, cl.relname AS tabla, cl.oid AS oid FROM pg_class cl JOIN pg_namespace n ON n.oid = cl.relnamespace "
                   "WHERE cl.relkind = 'r' AND n.nspname = ANY(%s) ORDER BY 1, 2", ER.ESQUEMAS)
        cols, pk, uq, trig, fks = {}, {}, {}, {}, []
        for t in tablas:
            k = f"{t['esquema']}.{t['tabla']}"
            cols[k] = q("SELECT a.attname AS nombre, format_type(a.atttypid, a.atttypmod) AS tipo, a.attnotnull AS no_nulo FROM pg_attribute a "
                        "WHERE a.attrelid = %s AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum", t["oid"])
            idx = q("SELECT i.indisprimary AS es_pk, pg_get_indexdef(i.indexrelid) AS def, ic.relname AS nombre, "
                    "(SELECT array_agg(a.attname ORDER BY k.ord) FROM unnest(i.indkey) WITH ORDINALITY k(attnum, ord) JOIN pg_attribute a "
                    " ON a.attrelid = i.indrelid AND a.attnum = k.attnum WHERE k.attnum > 0) AS cols FROM pg_index i JOIN pg_class ic ON ic.oid = i.indexrelid "
                    "WHERE i.indrelid = %s AND i.indisunique", t["oid"])
            pk[k] = next((f["cols"] for f in idx if f["es_pk"]), [])
            uq[k] = []
            for f in idx:
                if f["es_pk"]:
                    continue
                donde = re.search(r"WHERE \((.*)\)$", f["def"])
                donde = re.sub(r"\)?::text", "", donde.group(1)) if donde else None
                etiqueta = ", ".join(f["cols"] or []) or f["nombre"]
                if f["cols"] and "lower(" in f["def"]:
                    etiqueta = re.search(r"USING btree \((.*?)\)(?: WHERE|$)", f["def"]).group(1)
                uq[k].append((etiqueta, donde))
            trig[k] = [r["tgname"] for r in q("SELECT tgname FROM pg_trigger WHERE tgrelid = %s AND NOT tgisinternal ORDER BY 1", t["oid"])]
            for r in q("SELECT confrelid::regclass::text AS destino, conkey, conrelid FROM pg_constraint WHERE conrelid = %s AND contype = 'f'", t["oid"]):
                origen = [a["attname"] for a in q("SELECT attname FROM pg_attribute WHERE attrelid=%s AND attnum = ANY(%s) ORDER BY array_position(%s, attnum)",
                                                  t["oid"], r["conkey"], r["conkey"])]
                destino = r["destino"] if "." in r["destino"] else f"public.{r['destino']}"
                anulable = any(not col["no_nulo"] for col in cols[k] if col["nombre"] in origen)
                fks.append((k, origen, destino, len(origen) > 1, anulable))
    fk_cols = {k: {col for o, cs, _, _, _ in fks if o == k for col in cs} for k in cols}
    LIBROS = {"obra.proyecto_plazo_historial", "tareo.jornada", "compras.compra_detalle", "compras.compra_adjunto", "compras.solicitud_linea"}

    ent = {}
    for k in cols:
        esq, nombre = k.split(".")
        borde = ER.COLOR[esq][0]
        filas = []
        for col in cols[k]:
            marca = " ".join(m for m, ok in (("PK", col["nombre"] in pk[k]), ("FK", col["nombre"] in fk_cols[k])) if ok)
            tipo = col["tipo"].replace("timestamp with time zone", "timestamptz").replace("character varying", "varchar") + ("" if col["no_nulo"] else "?")
            marca_html = f'<b><font color="#B00020">{marca}</font></b>' if marca else ""
            filas.append((f'<span style="display:inline-block;width:34px">{marca_html}</span>{col["nombre"]} <font color="#777777">{tipo}</font>', None))
        for etiqueta, donde in uq[k]:
            filas.append((f'<b><font color="#7F6000">UNIQUE ({etiqueta})</font></b>' + (f'<font color="#7F6000"> · solo si {donde}</font>' if donde else ""), "#FFF2CC"))
        tags = []
        if any(d.endswith("_append_only") for d in trig[k]):
            tags.append("sin UPDATE · sin DELETE")
        elif any(d.endswith("_no_borrar") for d in trig[k]):
            tags.append("sin DELETE (se da de baja)")
        extra = [d.removeprefix("tg_") for d in trig[k] if not d.endswith(("_no_borrar", "_no_truncar", "_append_only"))]
        if extra:
            tags.append("reglas: " + ", ".join(extra))
        for t in tags:
            filas.append((f'<font color="#444444" style="font-size:10px">{t}</font>', "#F2F2F2"))
        cab = f"<b>{k}</b>" + (' <font color="#FFD966" style="font-size:10px">· APPEND-ONLY</font>' if k in LIBROS else "") + \
              (' <font color="#FFD966" style="font-size:10px">· bandeja de salida</font>' if k == "outbox.evento" else "") + \
              (' <font color="#FFD966" style="font-size:10px">· contador</font>' if k == "compras.solicitud_correlativo" else "")
        plano = [re.sub(r"<[^>]+>", "", f[0]) for f in filas] + [re.sub(r"<[^>]+>", "", cab)]
        w = max(220, max(len(p) for p in plano) * LETRA + 40)
        ent[k] = dict(grupo=esq, color=borde, cab=cab, filas=filas, w=w, h=CAB + FILA * len(filas) + 4)

    # contrato de Henry (aún sin migración)
    mov = [(f'<b><font color="#B00020">UNIQUE</font></b> uuid_cliente', "#FFF2CC"), ("almacen_id, proyecto_id <b><font color=\"#B00020\">FK</font></b>", None),
           ("periodo_id, proyecto_id <b><font color=\"#B00020\">FK</font></b>", None), ("dispositivo_id, proyecto_id <b><font color=\"#B00020\">FK</font></b>", None),
           ("insumo_id <b><font color=\"#B00020\">FK</font></b>", None), ("compra_id (solo recepción) <b><font color=\"#B00020\">FK</font></b>", None),
           ("tipo · cantidad &gt; 0 · costo_unitario", None),
           ('<b><font color="#7F6000">SIN columna de saldo: se suma desde los movimientos</font></b>', "#FFF2CC"),
           ('<b><font color="#7F6000">Sin UPDATE ni DELETE: corregir = reverso</font></b>', "#FFF2CC")]
    ent["almacen.movimiento"] = dict(grupo="henry", color="#7F7F7F", dashed=True, cab='<b>almacen.movimiento</b> <font color="#FFD966" style="font-size:10px">· APPEND-ONLY</font>',
                                     filas=mov, w=360, h=CAB + FILA * len(mov) + 4)
    from introspeccion import SERVICIO_DE
    grupos_estilo = {e: (ER.COLOR[e][0], ER.COLOR[e][1], f"microservicio {SERVICIO_DE[e]} · esquema {e} · rol sigro_svc_{SERVICIO_DE[e]}", False)
                     for e in ER.ESQUEMAS}
    grupos_estilo["henry"] = ("#999999", "#FFFFFF", "microservicio almacen · esquema almacen · por construir", True)

    aristas, dibujo = [], []
    for o, cs, d, compuesta, anulable in fks:
        if d == "obra.usuario" or d.split(".")[0] not in ER.ESQUEMAS or o == d:
            continue
        aristas.append((d, o))
        ini = "ERzeroToOne" if anulable else "ERmandOne"
        cruza = o.split(".")[0] != d.split(".")[0]
        color = "#C00000" if cruza else ("#2F5597" if compuesta else "#555555")
        dibujo.append((d, o, ARISTA.format(c=color, w=2.5 if compuesta else 1.3, ini=ini, fin="ERzeroToMany", extra="dashed=1;" if cruza else ""),
                       ", ".join(cs) + (" · otro servicio (fase 1)" if cruza else "")))
    for origen, cs in (("obra.almacen", "almacen_id"), ("obra.periodo", "periodo_id"), ("obra.dispositivo", "dispositivo_id"),
                       ("catalogo.insumo", "insumo_id"), ("compras.compra", "compra_id")):
        aristas.append((origen, "almacen.movimiento"))
        dibujo.append((origen, "almacen.movimiento", ARISTA.format(c="#999999", w=1.3, ini="ERmandOne", fin="ERzeroToMany", extra="dashed=1;"), cs))

    nodos = {k: (e["w"], e["h"], e["grupo"]) for k, e in ent.items()}
    gr = {**{g: g for g in ER.ESQUEMAS}, "henry": "henry"}
    pos, cajas, rutas = disponer(nodos, aristas, gr, rankdir="LR", nodesep=0.45, ranksep=1.5)
    L = Lienzo("Diagrama físico")
    leyenda = ('<b>Cómo leerlo</b><br><font color="#B00020"><b>PK / FK</b></font> clave primaria / foránea · '
               '<b>flecha de pata de gallo</b>: el padre (| uno, o| opcional) apunta a sus hijos (o&lt; varios) · '
               '<font color="#2F5597"><b>flecha azul gruesa</b></font> = FK compuesta (id, proyecto_id): el motor garantiza que ambos son de la misma obra · '
               '<font color="#7F6000"><b>UNIQUE</b></font> = índice único que sostiene una regla (parcial si trae «solo si») · <b>?</b> = admite NULL · '
               'las FK de autoría hacia <b>obra.usuario</b> están marcadas FK pero no dibujadas · '
               '<font color="#C00000"><b>flecha roja discontinua</b></font> = FK que cruza a otro microservicio: fase 1 la conserva, el servicio no puede leer la tabla ajena; '
               'fase 2 la reemplaza por réplica local por eventos (ADR-09) · '
               '<b>Despliegue:</b> una instancia PostgreSQL, un esquema y un usuario por servicio.')
    ensamblar(L, ent, grupos_estilo, pos, cajas, rutas, "SIGRO · Modelo físico de datos por microservicio (PostgreSQL)",
              "Un esquema y un rol por microservicio · generado desde la base migrada (migraciones 0001 a 0009)", dibujo, leyenda, leyenda_ancho=1500)
    L.guardar(SALIDA / "diagrama-fisico.drawio")
    print(f"físico: {len(ent)} tablas, {len(dibujo)} flechas")


# ───────────────────────────────────────────── 2. ENTIDAD-RELACIÓN (modelo lógico verificado)
def entidad_relacion() -> None:
    with psycopg.connect(ER.DSN, row_factory=dict_row) as c:
        ER.verificar(c)          # falla si el modelo lógico se desvió de la base
    ent = {}
    for t, (nombre, atrs, regla) in ER.E.items():
        esq = t.split(".")[0]
        filas = []
        for a, _col, m in atrs:
            if m == "id":
                filas.append((f"<u><b>{a}</b></u>", None))
            elif m == "?":
                filas.append((f'<font color="#777777">{a} (opcional)</font>', None))
            else:
                filas.append((a, None))
        if regla:
            filas.append((f'<i><font color="#7F6000" style="font-size:10px">{regla}</font></i>', "#FFF2CC"))
        w = max(210, max(len(re.sub(r"<[^>]+>", "", f[0])) for f in filas) * LETRA + 36)
        ent[t] = dict(grupo=esq, color=ER.COLOR[esq][0], cab=f"<b>{nombre}</b>", filas=filas, w=w, h=CAB + FILA * len(filas) + 4)
    ent["almacen.movimiento"] = dict(grupo="henry", color="#7F7F7F", dashed=True, cab="<b>Movimiento</b>", w=330, h=CAB + FILA * 4 + 4, filas=[
        ("<u><b>Identificador del celular (único)</b></u>", None), ("Tipo · cantidad · costo unitario", None),
        ('<i><font color="#7F6000" style="font-size:10px">Libro: solo se agrega · sin saldo guardado</font></i>', "#FFF2CC"),
        ('<i><font color="#7F6000" style="font-size:10px">el saldo se suma desde los movimientos</font></i>', "#FFF2CC")])
    grupos_estilo = {e: (ER.COLOR[e][0], ER.COLOR[e][1], ER.GRUPO[e], False) for e in ER.ESQUEMAS}
    grupos_estilo["henry"] = ("#999999", "#FFFFFF", "Almacén y costo (Henry) · aún sin migración", True)
    aristas, dibujo = [], []
    for hijo, padre, _cols, verbo, card in ER.R:
        aristas.append((padre, hijo))
        ini = "ERmandOne" if card == "1" else "ERzeroToOne"
        extra = "loopStyle=orthogonal;" if hijo == padre else ""
        dibujo.append((padre, hijo, ARISTA.format(c="#444444", w=1.3, ini=ini, fin="ERzeroToMany", extra=extra), verbo))
    for tabla, verbo in ER.FUTURO:
        aristas.append((tabla, "almacen.movimiento"))
        dibujo.append((tabla, "almacen.movimiento", ARISTA.format(c="#999999", w=1.3, ini="ERmandOne", fin="ERzeroToMany", extra="dashed=1;"), verbo))
    nodos = {k: (e["w"], e["h"], e["grupo"]) for k, e in ent.items()}
    gr = {**{g: g for g in ER.ESQUEMAS}, "henry": "henry"}
    pos, cajas, rutas = disponer([] and {} or nodos, [(a, b) for a, b in aristas if a != b], gr, rankdir="TB", nodesep=0.7, ranksep=1.1)
    L = Lienzo("Entidad-relación")
    leyenda = ('<b>Cómo leerlo</b><br><u><b>Subrayado</b></u> lo que identifica a la entidad · <font color="#777777">gris (opcional)</font> puede quedar vacío · '
               '<b>||</b> uno y solo uno · <b>|o</b> uno o ninguno · <b>o&lt;</b> ninguno, uno o varios. Se lee: «Obra se divide en (0..N) Periodos»; cada Periodo es de exactamente una Obra. · '
               '<font color="#7F6000"><i>Regla en amarillo</i></font> = lo que el motor de la base impide romper · '
               '<b>Asignación</b> es la relación Usuario–Obra con atributo propio (el rol) · '
               'quién registró, anuló, autorizó o cerró cada hecho (siempre un Usuario) no se dibuja para no saturar · '
               'el contador del correlativo de solicitudes es una tabla técnica y no se dibuja · línea punteada: contrato con el módulo de Henry.')
    ensamblar(L, ent, grupos_estilo, pos, cajas, rutas, "SIGRO · Diagrama entidad-relación (modelo lógico)",
              "Qué cosas existen en la obra y cómo se relacionan · verificado contra la base migrada (0001 a 0006)", dibujo, leyenda, leyenda_ancho=1100)
    L.guardar(SALIDA / "diagrama-er.drawio")
    print(f"ER: {len(ER.E)} entidades, {len(dibujo)} relaciones")


# ───────────────────────────────────────────── 3. MICROSERVICIOS (a mano)
# El antiguo diagrama «microservicios» (de cuando era monolito modular) lo reemplaza docs/generar_despliegue.py.


if __name__ == "__main__":
    fisico()
    entidad_relacion()
