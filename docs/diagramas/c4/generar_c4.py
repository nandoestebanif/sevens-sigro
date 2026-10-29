"""C4 de SIGRO (C1 contexto, C2 contenedores, C3 componentes) con Graphviz y la notación de colores del C4.
Uso: python docs/diagramas/c4/generar_c4.py  →  c1-contexto.png, c2-contenedores.png, c3-*.png (y .svg)"""
import html, subprocess
from pathlib import Path
AQUI = Path(__file__).resolve().parent
COL = {"persona": "#08427B", "sistema": "#1168BD", "externo": "#8A8A8A", "contenedor": "#438DD5", "componente": "#85BBF0", "db": "#438DD5"}

def nodo(id, tipo, nombre, tec, desc):
    c = COL[tipo]; fc = "#000000" if tipo == "componente" else "#FFFFFF"
    et = {"persona": "Persona", "sistema": "Sistema", "externo": "Sistema externo", "contenedor": "Contenedor", "componente": "Componente", "db": "Base de datos"}[tipo]
    t = f'<FONT POINT-SIZE="10">[{html.escape(et)}{": " + html.escape(tec) if tec else ""}]</FONT>'
    forma = "cylinder" if tipo == "db" else ("box")
    estilo = "filled,rounded" if tipo != "db" else "filled"
    return (f'"{id}" [shape={forma}, style="{estilo}", fillcolor="{c}", color="{c}", fontcolor="{fc}", margin="0.25,0.12", width=2.6, '
            f'label=<<B>{html.escape(nombre)}</B><BR/>{t}<BR/><BR/><FONT POINT-SIZE="11">{html.escape(desc)}</FONT>>];')

def rel(a, b, txt, tec=""):
    e = html.escape(txt) + (f'<BR/><FONT POINT-SIZE="9">[{html.escape(tec)}]</FONT>' if tec else "")
    return f'"{a}" -> "{b}" [label=<{e}>];'

def dibujar(nombre, titulo, cuerpo, rankdir="TB"):
    dot = ("digraph C4 {\n" f'graph [rankdir={rankdir}, fontname="Helvetica", fontsize=22, labelloc=t, label=<<B>{html.escape(titulo)}</B>>, pad=0.4, nodesep=0.6, ranksep=0.9, newrank=true];\n'
           'node [fontname="Helvetica", fontsize=13]; edge [fontname="Helvetica", fontsize=10, color="#555555", fontcolor="#333333", arrowsize=0.8];\n'
           + "\n".join(cuerpo) + "\n}")
    (AQUI / f"{nombre}.dot").write_text(dot, encoding="utf-8")
    for fmt, x in (("png", ["-Gdpi=130"]), ("svg", [])):
        subprocess.run(["dot", f"-T{fmt}", *x, str(AQUI / f"{nombre}.dot"), "-o", str(AQUI / f"{nombre}.{fmt}")], check=True)
    (AQUI / f"{nombre}.dot").unlink()

def caja(id, titulo, nodos):
    return [f'subgraph cluster_{id} {{ label=<<B>{html.escape(titulo)}</B>>; style="dashed"; color="#444444"; fontsize=14;'] + nodos + ["}"]

# C1
dibujar("c1-contexto", "C1 · Contexto del sistema · SIGRO para SEVENS INGENIEROS SAC", [
  nodo("res", "persona", "Residente de obra", "", "Importa el S10, define la reposición, registra compras y revisa el gasto"),
  nodo("adm", "persona", "Administrador / Gerente", "", "Usuarios, accesos, celulares autorizados y reportes"),
  nodo("alm", "persona", "Almacenero", "", "Registra ingresos y salidas en obra, con o sin señal"),
  nodo("sso", "persona", "Ingeniero SSOMA", "", "Trabajadores, asistencia y herramientas"),
  nodo("sigro", "sistema", "SIGRO", "", "Controla los recursos de la obra (materiales, herramientas y horas hombre): del S10 al gasto"),
  nodo("s10", "externo", "S10 Presupuestos", "", "Reportes PDF del presupuesto: relación de insumos y APU"),
  nodo("fac", "externo", "SUNAT / RENIEC (vía Factiliza)", "", "Valida RUC de proveedores y DNI de trabajadores"),
  "{rank=same; res; adm; alm; sso;}", "{rank=same; s10; fac;}",
  rel("res", "sigro", "Usa la web de oficina", "HTTPS"), rel("adm", "sigro", "Usa la web de oficina", "HTTPS"),
  rel("alm", "sigro", "Usa la app del almacén", "HTTPS · cola sin señal"), rel("sso", "sigro", "Usa la app", "HTTPS"),
  rel("sigro", "s10", "Lee el PDF exportado del presupuesto"), rel("sigro", "fac", "Consulta RUC y DNI", "HTTPS · token")])

# C2
c2 = [nodo("of", "persona", "Residente / Administrador / Gerente", "", "Trabaja en la oficina de obra"),
      nodo("al", "persona", "Almacenero / SSOMA", "", "Trabaja en la obra"),
      nodo("fac", "externo", "Factiliza", "", "SUNAT y RENIEC")]
c2 += caja("sig", "SIGRO", [
  nodo("web", "contenedor", "Web de oficina", "React 18 + TypeScript", "Preparación, control, abastecimiento y accesos"),
  nodo("app", "contenedor", "App del almacenero", "Kotlin Multiplatform", "Android e iOS; cola sin señal con uuid_cliente"),
  nodo("gw", "contenedor", "Gateway", "Starlette + httpx", "Única puerta pública /api/v1; /interno no se expone"),
  nodo("obras", "contenedor", "Servicio obras", "FastAPI", "Identidad, obras, periodos, almacenes, dispositivos; decide quién puede qué"),
  nodo("cat", "contenedor", "Servicio catálogo", "FastAPI", "Código único de insumo y factor de conversión"),
  nodo("comp", "contenedor", "Servicio compras", "FastAPI", "Proveedores, compras, requerimiento, reposición"),
  nodo("tar", "contenedor", "Servicio tareo", "FastAPI", "Trabajadores y asistencia"),
  nodo("alma", "contenedor", "Servicios almacén y costo", "FastAPI (por construir)", "Movimientos, saldo, kárdex, costo promedio"),
  nodo("integ", "contenedor", "Integraciones", "FastAPI", "PDF del S10, RUC y DNI"),
  nodo("rel", "contenedor", "Relevo", "Python", "Entrega la bandeja de salida (outbox)"),
  nodo("notif", "contenedor", "Notificaciones", "FastAPI", "Avisos a residente y administrador"),
  nodo("db", "db", "PostgreSQL 16", "un esquema y un usuario por servicio", "obra · catalogo · compras · tareo · outbox"),
  "{rank=same; web; app;}", "{rank=same; obras; cat; comp; tar; alma;}"])
c2 += [rel("of", "web", "Usa", "HTTPS"), rel("al", "app", "Usa", "en obra"),
       rel("web", "gw", "JSON · JWT", "HTTPS"), rel("app", "gw", "JSON · JWT · reenvío idempotente", "HTTPS"),
       rel("gw", "obras", "reenvía"), rel("gw", "cat", "reenvía"), rel("gw", "comp", "reenvía"), rel("gw", "tar", "reenvía"),
       '"gw" -> "alma" [style=dashed];',
       rel("comp", "obras", "/interno/autorizar", "token de servicio"), rel("comp", "cat", "/interno/insumos/buscar", "token de servicio"),
       rel("comp", "integ", "valida RUC", "token de servicio"), rel("integ", "fac", "consulta", "HTTPS"),
       rel("obras", "db", "rol sigro_svc_obras"), rel("cat", "db", "rol sigro_svc_catalogo"), rel("comp", "db", "rol sigro_svc_compras"), rel("tar", "db", "rol sigro_svc_tareo"),
       rel("rel", "db", "lee el outbox"), rel("rel", "notif", "POST /eventos", "token de servicio")]
dibujar("c2-contenedores", "C2 · Contenedores · SIGRO en microservicios (ADR-09)", c2)

# C3 compras
c3 = [nodo("gw", "contenedor", "Gateway", "Starlette", "/api/v1"), nodo("obras", "contenedor", "Servicio obras", "FastAPI", "/interno/autorizar"),
      nodo("cat", "contenedor", "Servicio catálogo", "FastAPI", "/interno/insumos/buscar"),
      nodo("db", "db", "PostgreSQL", "esquema compras + outbox", "rol sigro_svc_compras")]
c3 += caja("c", "Servicio compras", [
  nodo("rutas", "componente", "Rutas", "FastAPI router", "compras, solicitudes, requerimiento, saldo por comprar, reposición, proveedores"),
  nodo("dep", "componente", "Autorización", "dependencias.py", "Pide a obras el rol del usuario en la obra (regla 9)"),
  nodo("cli", "componente", "Cliente del catálogo", "catalogo_cliente.py", "Completa código, nombre y factor del insumo"),
  nodo("req", "componente", "Requerimiento y saldo", "requerimiento.py", "Requerido − comprado en unidad de compra; avisa, no bloquea"),
  nodo("mod", "componente", "Modelo de datos", "SQLAlchemy 2", "proveedor, compra, detalle, solicitud, requerimiento"),
  nodo("ev", "componente", "Publicador de eventos", "eventos.py", "Escribe el aviso en el outbox en la misma transacción")])
c3 += [rel("gw", "rutas", "HTTP"), rel("rutas", "dep", "usa"), rel("dep", "obras", "POST /interno/autorizar"), rel("rutas", "cli", "usa"),
       rel("cli", "cat", "POST /interno/insumos/buscar"), rel("rutas", "req", "usa"), rel("rutas", "mod", "lee y escribe"), rel("req", "mod", "lee"),
       rel("rutas", "ev", "publica"), rel("mod", "db", "SQL"), rel("ev", "db", "INSERT outbox.evento")]
dibujar("c3-componentes-compras", "C3 · Componentes del servicio compras (mismo patrón en obras, catálogo y tareo)", c3)

# C3 app
a = [nodo("alm", "persona", "Almacenero", "", "En obra, con o sin señal"), nodo("gw", "contenedor", "Gateway SIGRO", "HTTPS", "/api/v1")]
a += caja("a", "App del almacenero (Kotlin Multiplatform)", [
  nodo("ui", "componente", "Pantallas", "Compose Multiplatform", "Entrar, Elegir obra, Almacén, Salida, Mis registros, Mi celular"),
  nodo("vm", "componente", "ViewModels", "Kotlin", "Uno por pantalla; la pantalla solo pinta"),
  nodo("dom", "componente", "Dominio", "Kotlin puro", "Modelos, ValidarSalida, interfaces de repositorio"),
  nodo("repo", "componente", "Repositorios", "Kotlin", "Sesión, catálogo, salidas (cola sin señal), dispositivo"),
  nodo("room", "componente", "Base local", "Room KMP + SQLite", "salida_local: por enviar / enviado / rechazado"),
  nodo("api", "componente", "Cliente de la API", "Ktor", "Errores {codigo, mensaje}; reenvío idempotente"),
  nodo("sync", "componente", "Sincronización", "WorkManager · al abrir en iOS", "Envía la cola al volver la señal"),
  nodo("di", "componente", "Inyección de dependencias", "Koin", "Módulos por plataforma")])
a += [rel("alm", "ui", "usa"), rel("ui", "vm", "eventos"), rel("vm", "dom", "valida"), rel("vm", "repo", "usa"), rel("repo", "room", "guarda primero"),
      rel("repo", "api", "envía"), rel("sync", "repo", "sincronizar()"), rel("api", "gw", "JSON · JWT", "HTTPS"), '"di" -> "repo" [style=dotted, label="provee"];']
dibujar("c3-componentes-app", "C3 · Componentes de la app del almacenero", a)
print("ok")
