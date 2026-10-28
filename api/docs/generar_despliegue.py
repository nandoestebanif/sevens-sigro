"""Diagrama de DESPLIEGUE de los microservicios (vista física de la arquitectura, ADR-09).

Fuentes, para que no se contradiga con lo que de verdad se levanta:
  - contenedores, imágenes, puertos, volúmenes, usuario de base de cada servicio: ../docker-compose.yml
  - qué rutas públicas atiende cada servicio: app/enrutado.py (la misma tabla que usa el gateway)
  - llamadas internas: LLAMADAS (abajo); cada una cita el archivo donde ocurre y el script FALLA si ese código ya no existe.

Uso:  python docs/generar_despliegue.py
Salida: docs/diagrama-despliegue.(dot|svg|png) y docs/diagrama-despliegue.drawio (editable)
"""
from __future__ import annotations

import html
import re
import subprocess
import sys
from pathlib import Path

import yaml

DOCS = Path(__file__).resolve().parent
API = DOCS.parent
RAIZ = API.parent
sys.path.insert(0, str(API))
sys.path.insert(0, str(DOCS))
from app.enrutado import TABLA  # noqa: E402
from introspeccion import COLOR  # noqa: E402

ESQUEMA_DE = {"obras": "obra", "catalogo": "catalogo", "compras": "compras", "tareo": "tareo", "relevo": "outbox"}
COLOR_SVC = {s: COLOR[e] for s, e in ESQUEMA_DE.items()}
COLOR_SVC |= {"gateway": ("#111827", "#F3F4F6"), "integraciones": ("#7A4B00", "#FFF7E6"), "notificaciones": ("#8A3B12", "#FFF1EA"),
              "migrar": ("#555555", "#F2F2F2"), "db": ("#336791", "#EAF2FA")}

# (origen, destino, qué, archivo que lo demuestra, texto que debe aparecer en ese archivo)
LLAMADAS = [
    ("catalogo", "obras", "POST /interno/autorizar", "app/dependencias.py", '"/interno/autorizar"'),
    ("compras", "obras", "POST /interno/autorizar · destinatarios", "app/eventos.py", "/destinatarios"),
    ("tareo", "obras", "POST /interno/autorizar · GET periodos", "app/modulos/tareo/rutas.py", "/periodos"),
    ("compras", "catalogo", "POST /interno/insumos/buscar", "app/modulos/compras/catalogo_cliente.py", "/interno/insumos/buscar"),
    ("compras", "integraciones", "GET /ruc/{ruc} (SUNAT)", "app/integraciones.py", "/ruc/"),
    ("obras", "notificaciones", "GET /notificaciones (campana)", "app/modulos/notificaciones/rutas.py", '"/notificaciones"'),
    ("relevo", "notificaciones", "POST /eventos", "app/relay.py", "/eventos"),
]


def verificar_llamadas() -> None:
    for o, d, _, archivo, texto in LLAMADAS:
        if texto not in (API / archivo).read_text(encoding="utf-8"):
            raise SystemExit(f"La llamada {o} → {d} ya no está en {archivo}: actualiza LLAMADAS.")


def rutas_por_servicio() -> dict[str, list[str]]:
    r: dict[str, list[str]] = {}
    for patron, svc in TABLA:
        p = patron.pattern.removeprefix("^/api/v1").replace(r"/obras/\d+/", "/obras/{id}/").replace("(/|$)", "")
        p = re.sub(r"\(([^)]*)\)", lambda m: "{" + m.group(1).replace("|", ", ") + "}", p)
        r.setdefault(svc, []).append(p)
    return r


def leer_compose() -> dict:
    return yaml.safe_load((RAIZ / "docker-compose.yml").read_text(encoding="utf-8"))["services"]


def usuario_db(entorno: dict) -> str | None:
    url = (entorno or {}).get("SIGRO_APP_DATABASE_URL", "")
    m = re.search(r"//([^:@]+)", url)
    return m.group(1) if m else None


def esc(t) -> str:
    return html.escape(str(t), quote=True)


def fichas() -> dict[str, dict]:
    svc, rutas = leer_compose(), rutas_por_servicio()
    out = {}
    for nombre, s in svc.items():
        build = s.get("build")
        imagen = s.get("image") or (f"build {build['context']} · SERVICIO={build.get('args', {}).get('SERVICIO')}"
                                    if isinstance(build, dict) else f"build {build}")
        lineas = [imagen]
        if s.get("ports"):
            lineas.append("puerto público " + ", ".join(p.split(":")[0] for p in s["ports"]))
        elif nombre not in ("db", "migrar", "relevo"):
            lineas.append("solo red interna :8000")
        if u := usuario_db(s.get("environment")):
            lineas.append(f"base: usuario {u} → rol sigro_svc_{nombre} (esquema {ESQUEMA_DE.get(nombre, '?')})")
        if s.get("volumes"):
            vols = [v for v in s["volumes"] if not v.startswith("./")]
            if vols:
                lineas.append("volumen " + ", ".join(v.split(":")[0] for v in vols))
        if s.get("command"):
            lineas.append("proceso: " + " ".join(s["command"]))
        if nombre in rutas:
            lineas += [f"/api/v1{r}" for r in rutas[nombre]]
        if nombre == "migrar":
            lineas.append("corre una vez: alembic upgrade head + un usuario por servicio")
        if nombre == "db":
            lineas.append("esquemas: obra · catalogo · compras · tareo · outbox")
            lineas.append("dueño sigro_owner (solo migrar lo usa)")
        if nombre == "integraciones":
            lineas.append("exige X-Servicio-Token · token de Factiliza solo en .env")
        if nombre == "notificaciones":
            lineas.append("guarda su propia copia de los avisos")
        if nombre == "gateway":
            lineas.append("/interno/* nunca se expone (404)")
        out[nombre] = {"lineas": lineas, "restart": s.get("restart")}
    return out


def dot() -> str:
    f = fichas()

    def caja(nombre: str, titulo: str | None = None) -> str:
        cab, fondo = COLOR_SVC.get(nombre, ("#444444", "#FFFFFF"))
        filas = "".join(f'<TR><TD ALIGN="LEFT"><FONT POINT-SIZE="10">{esc(l)}</FONT></TD></TR>' for l in f[nombre]["lineas"])
        return (f'"{nombre}" [label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="4" COLOR="{cab}" BGCOLOR="{fondo}">'
                f'<TR><TD BGCOLOR="{cab}"><FONT COLOR="white"><B>{esc(titulo or nombre)}</B></FONT></TD></TR>{filas}</TABLE>>];')

    L = ['digraph D {',
         '  graph [rankdir=LR, fontname="Helvetica", fontsize=24, labelloc=t, pad=0.4, nodesep=0.4, ranksep=1.1, compound=true, newrank=true,',
         '         label=<<B>SIGRO · Diagrama de despliegue (microservicios, ADR-09)</B><BR/>'
         '<FONT POINT-SIZE="14">generado desde docker-compose.yml y app/enrutado.py</FONT>>];',
         '  node [shape=plain, fontname="Helvetica", fontsize=11]; edge [fontname="Helvetica", fontsize=10, color="#444444"];',
         '  subgraph cluster_clientes { label=<<B>Dispositivos de los usuarios</B>>; style="rounded,dashed"; color="#888888";',
         '    web [label=<<TABLE BORDER="1" CELLBORDER="0" CELLPADDING="4" COLOR="#2563EB"><TR><TD BGCOLOR="#2563EB"><FONT COLOR="white"><B>Web de oficina</B></FONT></TD></TR>'
         '<TR><TD ALIGN="LEFT"><FONT POINT-SIZE="10">React 18 · navegador</FONT></TD></TR><TR><TD ALIGN="LEFT"><FONT POINT-SIZE="10">residente, administrador, gerente</FONT></TD></TR></TABLE>>];',
         '    app [label=<<TABLE BORDER="1" CELLBORDER="0" CELLPADDING="4" COLOR="#C2410C"><TR><TD BGCOLOR="#C2410C"><FONT COLOR="white"><B>App del almacenero</B></FONT></TD></TR>'
         '<TR><TD ALIGN="LEFT"><FONT POINT-SIZE="10">KMP · Android e iOS</FONT></TD></TR><TR><TD ALIGN="LEFT"><FONT POINT-SIZE="10">Room: cola sin señal · uuid_cliente</FONT></TD></TR>'
         '<TR><TD ALIGN="LEFT"><FONT POINT-SIZE="10">WorkManager reintenta al volver la señal</FONT></TD></TR></TABLE>>];',
         '  }',
         '  subgraph cluster_host { label=<<B>Servidor · Docker Compose «sigro» · red interna</B>>; style="rounded,filled"; fillcolor="#FAFAFA"; color="#111827"; penwidth=2;',
         "    " + caja("gateway", "gateway · única puerta pública"),
         '    subgraph cluster_dominio { label=<<B>Microservicios de dominio</B>>; style="rounded"; color="#999999";',
         *["      " + caja(s) for s in ("obras", "catalogo", "compras", "tareo")],
         '      almacen [label=<<TABLE BORDER="1" CELLBORDER="0" CELLPADDING="4" COLOR="#999999" STYLE="dashed"><TR><TD BGCOLOR="#9CA3AF"><FONT COLOR="white"><B>almacen · costo</B></FONT></TD></TR>'
         '<TR><TD ALIGN="LEFT"><FONT POINT-SIZE="10">por construir (mismo patrón)</FONT></TD></TR><TR><TD ALIGN="LEFT"><FONT POINT-SIZE="10">salidas, entradas, saldo, kárdex, costo promedio</FONT></TD></TR></TABLE>>];',
         '    }',
         '    subgraph cluster_apoyo { label=<<B>Servicios de apoyo</B>>; style="rounded"; color="#999999";',
         *["      " + caja(s) for s in ("relevo", "notificaciones", "integraciones")],
         '    }',
         '    subgraph cluster_datos { label=<<B>Datos</B>>; style="rounded"; color="#336791";',
         "      " + caja("db", "PostgreSQL 16 · una instancia"),
         "      " + caja("migrar", "migrar (one-shot)"),
         '    }',
         '  }',
         '  factiliza [label=<<TABLE BORDER="1" CELLBORDER="0" CELLPADDING="4" COLOR="#666666" STYLE="dashed"><TR><TD><B>API externa de consulta</B></TD></TR>'
         '<TR><TD><FONT POINT-SIZE="10">RUC (SUNAT) · DNI (RENIEC)</FONT></TD></TR></TABLE>>];',
         '  web -> gateway [label="HTTPS · JSON · Bearer JWT", penwidth=2];',
         '  app -> gateway [label="HTTPS · JSON · Bearer JWT\\nreenvío idempotente", penwidth=2];',
         '  web -> integraciones [label="hoy: importación S10 directa\\n(pendiente: pasar por el gateway)", color="#C00000", fontcolor="#C00000", style=dashed];']
    for s in ("obras", "catalogo", "compras", "tareo"):
        L.append(f'  gateway -> {s} [label="reenvía según enrutado.py", color="#111827"];')
    L.append('  gateway -> almacen [style=dashed, color="#999999"];')
    for o, d, que, _, _ in LLAMADAS:
        color = "#8A3B12" if d == "notificaciones" else ("#7A4B00" if d == "integraciones" else "#1F4E79")
        L.append(f'  {o} -> {d} [label="{esc(que)}\\nX-Servicio-Token", color="{color}", fontcolor="{color}", style=dashed];')
    for s in ("obras", "catalogo", "compras", "tareo", "relevo"):
        L.append(f'  {s} -> db [label="sigro_{s}", color="#336791", fontcolor="#336791"];')
    L.append('  migrar -> db [label="sigro_owner", color="#555555"];')
    L.append('  integraciones -> factiliza [label="HTTPS · Bearer (token en .env)", style=dashed];')
    L.append('  { rank=same; web; app; }')
    L.append("}")
    return "\n".join(L)


def drawio() -> None:
    """Versión editable: cada contenedor como caja, posiciones de Graphviz."""
    from generar_drawio import Lienzo, ancho_texto, disponer
    f = fichas()
    extra = {"web": ["React 18 · navegador", "residente, administrador, gerente"],
             "app": ["KMP · Android e iOS", "Room: cola sin señal · uuid_cliente"],
             "almacen": ["por construir (mismo patrón)", "salidas, entradas, saldo, kárdex, costo"],
             "factiliza": ["API externa: RUC (SUNAT) · DNI (RENIEC)"]}
    nodos_txt = {**{k: v["lineas"] for k, v in f.items()}, **extra}
    grupo = {"web": "clientes", "app": "clientes", "gateway": "host", "obras": "host", "catalogo": "host", "compras": "host", "tareo": "host",
             "almacen": "host", "relevo": "host", "notificaciones": "host", "integraciones": "host", "db": "host", "migrar": "host", "factiliza": None}
    nodos = {}
    for n, lineas in nodos_txt.items():
        w = max(ancho_texto(l, 160, 30) for l in [n, *lineas])
        nodos[n] = (w, 30 + 18 * len(lineas) + 8, grupo[n])
    aristas = [("web", "gateway"), ("app", "gateway"), ("web", "integraciones")] + \
              [("gateway", s) for s in ("obras", "catalogo", "compras", "tareo", "almacen")] + \
              [(o, d) for o, d, *_ in LLAMADAS] + [(s, "db") for s in ("obras", "catalogo", "compras", "tareo", "relevo")] + \
              [("migrar", "db"), ("integraciones", "factiliza")]
    pos, cajas, _ = disponer(nodos, aristas, {"clientes": "Dispositivos", "host": "Servidor"}, rankdir="LR", nodesep=0.5, ranksep=1.2)
    L = Lienzo("Despliegue")
    dy = 80
    L.vertice('<b style="font-size:22px">SIGRO · Diagrama de despliegue (microservicios, ADR-09)</b><br>'
              '<span style="font-size:13px;color:#555">generado desde docker-compose.yml y app/enrutado.py</span>',
              "text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;", 0, 0, 900, 60)
    for g, (x, y, w, h) in cajas.items():
        etiqueta = "Servidor · Docker Compose «sigro» · red interna" if g == "host" else "Dispositivos de los usuarios"
        L.vertice(etiqueta, "rounded=1;arcSize=2;html=1;dashed=%d;fillColor=#FAFAFA;strokeColor=#111827;verticalAlign=top;align=left;"
                            "spacingLeft=10;fontStyle=1;fontSize=14;" % (g == "clientes"), x, y + dy, w, h)
    ids = {}
    for n, (x, y) in pos.items():
        cab, fondo = COLOR_SVC.get(n, {"web": ("#2563EB", "#EFF6FF"), "app": ("#C2410C", "#FFF7ED")}.get(n, ("#777777", "#FFFFFF")))
        cuerpo = "<br>".join(html.escape(l) for l in nodos_txt[n])
        ids[n] = L.vertice(f'<b style="color:{cab}">{n}</b><br><span style="font-size:10px">{cuerpo}</span>',
                           f"rounded=1;arcSize=6;html=1;whiteSpace=wrap;align=left;verticalAlign=top;spacing=8;fillColor={fondo};"
                           f"strokeColor={cab};strokeWidth=2;{'dashed=1;' if n in ('almacen', 'factiliza') else ''}",
                           x, y + dy, nodos[n][0], nodos[n][1])
    etiquetas = {(o, d): q + " · X-Servicio-Token" for o, d, q, *_ in LLAMADAS}
    etiquetas |= {("web", "gateway"): "HTTPS · Bearer JWT", ("app", "gateway"): "HTTPS · Bearer JWT · reenvío idempotente",
                  ("web", "integraciones"): "hoy: S10 directo (pendiente: vía gateway)", ("migrar", "db"): "sigro_owner",
                  ("integraciones", "factiliza"): "HTTPS · token en .env"}
    for o, d in aristas:
        etq = etiquetas.get((o, d)) or (f"sigro_{o}" if d == "db" else "")
        rojo = (o, d) == ("web", "integraciones")
        L.arista(ids[o], ids[d], "edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;fontSize=10;endArrow=block;endFill=1;labelBackgroundColor=#FFFFFF;"
                                 + ("strokeColor=#C00000;fontColor=#C00000;dashed=1;" if rojo else "strokeColor=#444444;"), etq)
    L.guardar(DOCS / "diagrama-despliegue.drawio")


def main() -> None:
    verificar_llamadas()
    d = DOCS / "diagrama-despliegue.dot"
    d.write_text(dot(), encoding="utf-8")
    for fmt, extra in (("svg", []), ("png", ["-Gdpi=120"])):
        subprocess.run(["dot", f"-T{fmt}", *extra, str(d), "-o", str(d.with_suffix(f".{fmt}"))], check=True)
    drawio()
    print(f"despliegue: {len(leer_compose())} contenedores, {len(LLAMADAS)} llamadas internas -> {DOCS}/diagrama-despliegue.*")


if __name__ == "__main__":
    main()
