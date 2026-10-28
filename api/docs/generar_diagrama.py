"""Modelo FÍSICO de la base, por microservicio (ADR-09), generado DESDE la base migrada (no puede divergir).

Uso:  SIGRO_DB=sigro_dev python docs/generar_diagrama.py
Salida (requiere Graphviz: `dot`):
  docs/diagrama-fisico.(dot|svg|png)            todos los servicios en una lámina
  docs/fisico/<servicio>.(svg|png)               una lámina por servicio, con las tablas ajenas que referencia
"""
from __future__ import annotations

import html
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from introspeccion import COLOR, ESQUEMAS, QUE_HACE, SERVICIO_DE, Modelo, Tabla, leer_modelo  # noqa: E402

SALIDA = Path(__file__).resolve().parent
ROJO = "#C00000"           # FK que cruza de un servicio a otro (deuda de la fase 1)
INSIGNIA = {"obra.proyecto_plazo_historial": "APPEND-ONLY", "tareo.jornada": "APPEND-ONLY", "compras.compra_detalle": "APPEND-ONLY",
            "compras.compra_adjunto": "APPEND-ONLY", "compras.solicitud_linea": "APPEND-ONLY", "catalogo.insumo_factor_historial": "APPEND-ONLY",
            "compras.requerimiento_ajuste": "APPEND-ONLY", "outbox.evento": "bandeja de salida",
            "compras.solicitud_correlativo": "contador (solo lo toca su función)"}


def esc(t) -> str:
    return html.escape(str(t), quote=True)


def etiqueta_servicio(esquema: str) -> str:
    s = SERVICIO_DE[esquema]
    return (f'<<B>microservicio {s}</B>  ·  esquema <B>{esquema}</B>  ·  rol <B>sigro_svc_{s}</B><BR/>'
            f'<FONT POINT-SIZE="12">{esc(QUE_HACE[s])}</FONT>>')


def nodo(m: Modelo, t: Tabla) -> str:
    cab, _ = COLOR[t.esquema]
    fk_cols = m.fk_cols(t.clave)
    cruzan = {c for f in m.fks if f.origen == t.clave and f.entre_servicios for c in f.cols}
    ins = INSIGNIA.get(t.clave) or ("APPEND-ONLY" if t.append_only else "")
    filas = [f'<TR><TD COLSPAN="3" BGCOLOR="{cab}"><FONT COLOR="white"><B>{esc(t.clave)}</B></FONT>'
             + (f'<FONT COLOR="#FFD966"> {esc(ins)}</FONT>' if ins else "") + "</TD></TR>"]
    for col in t.columnas:
        marcas = [x for x, ok in (("PK", col.nombre in t.pk), ("FK*" if col.nombre in cruzan else "FK", col.nombre in fk_cols)) if ok]
        color = "#E46C0A" if col.nombre in cruzan else "#B00020"
        filas.append(
            f'<TR><TD ALIGN="LEFT" PORT="c_{col.nombre}"><FONT POINT-SIZE="10" COLOR="{color}"><B>{" ".join(marcas) or "  "}</B></FONT></TD>'
            f'<TD ALIGN="LEFT">{esc(col.nombre)}</TD><TD ALIGN="LEFT"><FONT COLOR="#666666" POINT-SIZE="10">'
            f'{esc(col.tipo)}{"" if col.no_nulo else "?"}</FONT></TD></TR>')
    for etiqueta, donde in t.unicos:
        txt = f"UNIQUE ({esc(etiqueta)})" + (f"<BR/>  WHERE {esc(donde)}" if donde else "")
        filas.append(f'<TR><TD COLSPAN="3" ALIGN="LEFT" BGCOLOR="#FFF2CC"><FONT POINT-SIZE="10" COLOR="#7F6000"><B>{txt}</B></FONT></TD></TR>')
    pie = []
    if t.append_only:
        pie.append("sin UPDATE · sin DELETE")
    elif t.sin_borrar:
        pie.append("sin DELETE (se da de baja)")
    if t.reglas:
        pie.append("reglas: " + ", ".join(t.reglas))
    if t.checks:
        pie.append(f"{len(t.checks)} CHECK · {len(t.indices)} índices")
    pie.append("permisos: " + (" · ".join(f"{r.removeprefix('sigro_svc_')} {p}" for r, p in t.privilegios.items())
                               or "ninguno directo (solo su función SECURITY DEFINER)"))
    for tx in pie:
        filas.append(f'<TR><TD COLSPAN="3" ALIGN="LEFT" BGCOLOR="#F2F2F2"><FONT POINT-SIZE="9" COLOR="#444444">{esc(tx)}</FONT></TD></TR>')
    return (f'    "{t.clave}" [label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="3" COLOR="{cab}">'
            f'{"".join(filas)}</TABLE>>];')


def fantasma(clave: str) -> str:
    esquema = clave.split(".")[0]
    return (f'    "{clave}" [label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="4" COLOR="#999999" STYLE="dashed">'
            f'<TR><TD PORT="c_id" BGCOLOR="#EEEEEE"><FONT COLOR="#555555"><B>{esc(clave)}</B></FONT></TD></TR>'
            f'<TR><TD><FONT POINT-SIZE="9" COLOR="#777777">de otro servicio: {esc(SERVICIO_DE.get(esquema, esquema))}<BR/>'
            f'no la lee; solo la referencia (fase 1)</FONT></TD></TR></TABLE>>];')


ALMACEN = ('  subgraph cluster_almacen { label=<<B>microservicio almacen · esquema almacen · rol sigro_svc_almacen</B><BR/>'
           '<FONT POINT-SIZE="12">por construir: movimientos, saldo y kárdex (ver propuesta 0010)</FONT>>; fontsize=18; style="rounded,dashed"; color="#999999";\n'
           '    "almacen.movimiento" [label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="3" COLOR="#999999" STYLE="dashed">'
           '<TR><TD COLSPAN="2" BGCOLOR="#7F7F7F"><FONT COLOR="white"><B>almacen.movimiento</B></FONT><FONT COLOR="#FFD966"> APPEND-ONLY</FONT></TD></TR>'
           '<TR><TD ALIGN="LEFT">uuid_cliente</TD><TD ALIGN="LEFT"><FONT COLOR="#B00020"><B>UNIQUE</B></FONT></TD></TR>'
           '<TR><TD ALIGN="LEFT" PORT="c_almacen_id">almacen_id, proyecto_id</TD><TD><FONT COLOR="#C00000">FK</FONT></TD></TR>'
           '<TR><TD ALIGN="LEFT" PORT="c_insumo_id">insumo_id</TD><TD><FONT COLOR="#C00000">FK</FONT></TD></TR>'
           '<TR><TD ALIGN="LEFT">tipo · cantidad &gt; 0 · costo_unitario congelado</TD><TD></TD></TR>'
           '<TR><TD COLSPAN="2" ALIGN="LEFT" BGCOLOR="#FFF2CC"><FONT POINT-SIZE="10" COLOR="#7F6000"><B>Sin columna de saldo · corregir = reverso</B></FONT></TD></TR>'
           '</TABLE>>];\n  }\n'
           '  "almacen.movimiento":c_almacen_id -> "obra.almacen":c_id [style=dashed, color="#999999"];\n'
           '  "almacen.movimiento":c_insumo_id -> "catalogo.insumo":c_id [style=dashed, color="#999999"];')


def leyenda(m: Modelo, global_: bool) -> str:
    n_cruzan = len(m.fks_entre_servicios)
    n_usuario = sum(1 for f in m.fks_entre_servicios if f.destino == "obra.usuario")
    filas = [
        '<FONT COLOR="#B00020"><B>PK / FK</B></FONT>  clave primaria / foránea (la flecha va del hijo al padre) · <FONT COLOR="#E46C0A"><B>FK*</B></FONT> apunta a otro servicio · <B>?</B> admite NULL',
        '<FONT COLOR="#2F5597"><B>Flecha azul gruesa</B></FONT>  FK compuesta (id, proyecto_id): el motor garantiza que ambos son de la misma obra',
        f'<FONT COLOR="{ROJO}"><B>FK roja discontinua</B></FONT>  cruza de un servicio a otro. En todo el sistema hay <B>{n_cruzan}</B>; {n_usuario} son de autoría hacia obra.usuario'
        + (" (marcadas en rojo, no dibujadas)" if global_ else "") + '.',
        '   Fase 1 (hoy): se conservan para no perder integridad; el servicio NO puede leer la tabla ajena (sin permiso), solo la referencia.',
        '   Fase 2 (Sprint 2): se reemplazan por una réplica local alimentada por eventos y la FK se elimina (ADR-09).',
        '<FONT COLOR="#7F6000"><B>UNIQUE</B></FONT>  índice único que sostiene una regla del dominio (parcial si trae WHERE)',
        '<B>permisos</B>  lo que el rol de cada servicio puede hacer en la tabla: S select · I insert · U update · D delete. Ningún servicio tiene D.',
        f'<B>Vistas:</B> {esc(", ".join(m.vistas))}',
        f'<B>SECURITY DEFINER</B> (únicas que escriben periodos, plazo y correlativos, o validan contra otro esquema): {esc(", ".join(m.funciones_definer))}',
        '<B>Despliegue:</B> una sola instancia PostgreSQL, un esquema y un usuario por servicio; ningún servicio puede leer el esquema de otro.',
    ]
    celdas = "".join(f'<TR><TD ALIGN="LEFT">{f}</TD></TR>' for f in filas)
    return (f'  subgraph cluster_leyenda {{ label=<<B>Cómo leerlo</B>>; fontsize=16; style="rounded"; color="#666666";\n'
            f'    leyenda [label=<<TABLE BORDER="0" CELLBORDER="0" CELLSPACING="0" CELLPADDING="3">{celdas}</TABLE>>];\n  }}')


def dibujar(m: Modelo, esquemas: list[str], titulo: str, subtitulo: str, base: Path, con_almacen: bool) -> None:
    global_ = len(esquemas) > 1
    L = ['digraph SIGRO {',
         f'  graph [rankdir=LR, fontname="Helvetica", fontsize=26, labelloc=t, pad=0.4, nodesep=0.35, ranksep={1.3 if global_ else 1.0}, '
         f'splines=true, compound=true, newrank=true, label=<<B>{esc(titulo)}</B><BR/><FONT POINT-SIZE="15">{esc(subtitulo)}</FONT>>];',
         '  node [shape=plain, fontname="Helvetica", fontsize=11];',
         '  edge [color="#555555", arrowsize=0.7, fontname="Helvetica", fontsize=9];']
    for e in esquemas:
        cab, fondo = COLOR[e]
        L.append(f'  subgraph cluster_{e} {{ label={etiqueta_servicio(e)}; fontsize=18; style="rounded,filled"; '
                 f'fillcolor="{fondo}"; color="{cab}"; penwidth=2;')
        L += [nodo(m, t) for t in m.de(e)]
        L.append("  }")
    if con_almacen:
        L.append(ALMACEN)
    ajenas = sorted({f.destino for f in m.fks if f.origen.split(".")[0] in esquemas and f.destino.split(".")[0] not in esquemas})
    if not global_ and ajenas:
        L.append('  subgraph cluster_ajenas { label=<<B>tablas de otros servicios que este referencia</B>>; fontsize=14; style="rounded,dashed"; color="#999999";')
        L += [fantasma(a) for a in ajenas]
        L.append("  }")
    for f in m.fks:
        if f.origen.split(".")[0] not in esquemas or f.origen == f.destino:
            continue
        if global_ and f.destino == "obra.usuario":
            continue
        if f.entre_servicios:
            estilo = f'[color="{ROJO}", style=dashed, penwidth=1.3]'
        else:
            estilo = '[color="#2F5597", penwidth=1.4]' if f.compuesta else ""
        L.append(f'  "{f.origen}":c_{f.cols[0]} -> "{f.destino}":c_{f.dcols[0]} {estilo};')
    L.append(leyenda(m, global_))
    L.append("}")
    dot = base.with_suffix(".dot")
    dot.write_text("\n".join(L), encoding="utf-8")
    for fmt, extra in (("svg", []), ("png", ["-Gdpi=110"])):
        subprocess.run(["dot", f"-T{fmt}", *extra, str(dot), "-o", str(base.with_suffix(f".{fmt}"))], check=True)
    if not global_:
        dot.unlink()


def main() -> None:
    m = leer_modelo()
    sub = (f"PostgreSQL {m.version_pg} · migración {m.migracion} · {len(m.tablas)} tablas, {len(m.fks)} claves foráneas "
           f"({len(m.fks_entre_servicios)} entre servicios) · generado desde la base migrada")
    dibujar(m, ESQUEMAS, "SIGRO · Modelo físico de datos por microservicio", sub, SALIDA / "diagrama-fisico", con_almacen=True)
    (SALIDA / "fisico").mkdir(exist_ok=True)
    for e in ESQUEMAS:
        s = SERVICIO_DE[e]
        tablas = m.de(e)
        sub_s = (f"esquema {e} · rol sigro_svc_{s} · {len(tablas)} tablas · "
                 f"{sum(1 for f in m.fks_entre_servicios if f.origen.startswith(e + '.'))} FK hacia otros servicios · migración {m.migracion}")
        dibujar(m, [e], f"SIGRO · microservicio {s} · modelo físico", sub_s, SALIDA / "fisico" / s, con_almacen=False)
    print(f"{len(m.tablas)} tablas, {len(m.fks)} FK ({len(m.fks_entre_servicios)} entre servicios) -> diagrama-fisico.* y fisico/<servicio>.*")


if __name__ == "__main__":
    main()
