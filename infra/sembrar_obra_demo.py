"""SIGRO · crea y llena la obra de DEMOSTRACIÓN (datos de prueba) usando la API, como lo haría la gente.

Uso (desde cualquier carpeta, con Python 3.10+ y httpx):   pip install httpx
    python sembrar_obra_demo.py
Pide el usuario y la clave de un ADMINISTRADOR (no se guardan). Todo pasa por las mismas reglas del servidor
(permisos, stock, periodos, uuid_cliente). Si se vuelve a correr reutiliza la obra, los usuarios y el proveedor
y agrega otra tanda de movimientos (cambia las claves de los usuarios de prueba).

Crea en la obra «SIGRO · Demostración (datos de prueba)»:
  almacenes, un almacenero y un SSOMA de prueba (claves al azar, se muestran al final), compras con recepción,
  ingresos, salidas, un traslado, un reverso, préstamos de herramientas (uno devuelto dañado), un conteo con
  diferencia, trabajadores (documento PASAPORTE «DEMO0001…», no son personas reales) y su asistencia.
No toca ninguna otra obra. Lo que se registra no se borra (regla 1): por eso va en una obra aparte.
"""
import getpass, os, secrets, sys, uuid
from datetime import date, timedelta
import httpx

URL = (os.environ.get("SIGRO_URL") or "https://sevens-sigro.onrender.com").rstrip("/") + "/api/v1"
OBRA = "SIGRO · Demostración (datos de prueba)"
hoy = date.today()
cli = httpx.Client(timeout=120)


def u(): return str(uuid.uuid4())


PIN = {"token": None}  # pase del panel de administración (migración 0024); vive solo en memoria


def llamar(tok, metodo, ruta, cuerpo=None, ok=(200, 201), callar=()):
    cab = {"Authorization": f"Bearer {tok}"} if tok else {}
    if tok and PIN["token"]: cab["X-SIGRO-PIN"] = PIN["token"]
    r = cli.request(metodo, URL + ruta, json=cuerpo, headers=cab)
    if r.status_code not in ok:
        cod = (r.json().get("codigo") if r.headers.get("content-type", "").startswith("application/json") else "") or ""
        if cod not in callar:
            print(f"   ! {metodo} {ruta} → {r.status_code} {r.text[:160]}")
        return None
    return r.json() if r.content else {}


def entrar(usuario, clave):
    d = llamar(None, "POST", "/auth/login", {"usuario": usuario, "clave": clave})
    if not d: sys.exit(f"No se pudo entrar como {usuario}.")
    return d.get("access_token") or d.get("token")


def clave_nueva(): return secrets.token_urlsafe(9) + "Aa9"


print(f"Servidor: {URL}")
adm = entrar(input("Usuario administrador: ").strip(), getpass.getpass("Clave: "))
# Si el administrador ya tiene PIN, crear usuarios y autorizar celulares lo piden (no se guarda ni se imprime).
if (llamar(adm, "GET", "/auth/pin", callar=("no_encontrado",)) or {}).get("configurado"):
    v = llamar(adm, "POST", "/auth/pin/verificar", {"pin": getpass.getpass("PIN del panel de administración: ")})
    if not v: sys.exit("PIN incorrecto: no se tocó nada.")
    PIN["token"] = v.get("pin_token")

# 1 · obra y almacenes
obra = next((o for o in (llamar(adm, "GET", "/obras") or []) if o.get("nombre") == OBRA), None)
if not obra:
    obra = llamar(adm, "POST", "/obras", {"nombre": OBRA, "fecha_inicio": str(hoy - timedelta(days=35)), "fecha_fin": str(hoy + timedelta(days=120))})
P = obra["id"]
print(f"1. Obra #{P}: {OBRA}")
alm = {a["tipo"]: a["id"] for a in llamar(adm, "GET", f"/obras/{P}/almacenes") or []}
for nombre, tipo in (("Almacén central", "central"), ("Frente torre B", "frente")):
    if tipo not in alm:
        alm[tipo] = llamar(adm, "POST", f"/obras/{P}/almacenes", {"nombre": nombre, "tipo": tipo})["id"]
C, F = alm["central"], alm["frente"]

# 2 · usuarios de prueba con su rol en ESTA obra
usuarios = {x["usuario"]: x for x in llamar(adm, "GET", "/usuarios") or []}
cred = {}
for usuario, nombre, rol in (("demo.almacenero", "Almacenero de prueba", "almacenero"), ("demo.ssoma", "SSOMA de prueba", "ssoma")):
    clave = clave_nueva()
    if usuario in usuarios:
        llamar(adm, "POST", f"/usuarios/{usuarios[usuario]['id']}/clave-temporal", {"clave": clave})
        uid = usuarios[usuario]["id"]
    else:
        uid = llamar(adm, "POST", "/usuarios", {"nombre": nombre, "usuario": usuario, "clave": clave})["id"]
    llamar(adm, "POST", f"/obras/{P}/asignaciones", {"usuario_id": uid, "rol": rol}, callar=("uq_asignacion_vigente",))
    cred[rol] = (usuario, clave)
A = entrar(*cred["almacenero"])
S = entrar(*cred["ssoma"])
print("2. Usuarios de prueba asignados (almacenero y SSOMA)")

# 3 · celular autorizado para el script (las salidas lo exigen, R5); al final se revoca
CEL = "demo-script-" + secrets.token_hex(4)
llamar(A, "POST", f"/obras/{P}/dispositivos", {"identificador": CEL})
disp = next((d for d in llamar(adm, "GET", f"/obras/{P}/dispositivos") or [] if d.get("identificador") == CEL), None)
if disp: llamar(adm, "POST", f"/obras/{P}/dispositivos/{disp['id']}/autorizar", {})

# 4 · catálogo: materiales que ya existen y herramientas (tipo «equipo»)
cat = llamar(adm, "GET", "/catalogo/insumos") or []
mats = [i for i in cat if i.get("tipo") == "material" and i.get("activo", True)][:4]
eqs = [i for i in cat if i.get("tipo") == "equipo" and i.get("activo", True)][:3]
for desc in ("TALADRO PERCUTOR 800 W", "AMOLADORA ANGULAR 4 1/2\"", "ANDAMIO METALICO (CUERPO)")[len(eqs):]:
    n = llamar(adm, "POST", "/catalogo/insumos", {"descripcion": desc, "unidad": "und", "tipo": "equipo"})
    if n: eqs.append(n)
print(f"4. Catálogo: {len(mats)} materiales y {len(eqs)} herramientas")

# 5 · proveedor de prueba (no es una empresa real) y compras con su recepción
prov = next((p for p in llamar(adm, "GET", "/proveedores") or [] if "PRUEBA SIGRO" in p.get("razon_social", "").upper()), None)
if not prov: prov = llamar(adm, "POST", "/proveedores", {"ruc": "10000001", "razon_social": "PROVEEDOR DE PRUEBA SIGRO (DEMO)"})


def linea(i, q, precio): return {"insumo_codigo": i["codigo"], "cantidad": str(q), "unidad_compra": i.get("unidad_compra") or i.get("unidad"), "precio_unitario": str(precio)}


n0 = secrets.randbelow(90000) + 1000
compras = []
for k, (lineas, tipo) in enumerate(((([linea(m, q, p) for m, q, p in zip(mats, (120, 80, 40, 30), (28.5, 4.2, 65, 12))]), "factura"),
                                    ([linea(e, q, p) for e, q, p in zip(eqs, (4, 3, 20), (350, 180, 120))], "boleta"))):
    if not lineas: continue
    c = llamar(A, "POST", f"/obras/{P}/compras", {"proveedor_id": prov["id"], "tipo_comprobante": tipo, "numero_comprobante": f"D00{k + 1}-{n0 + k}",
                                                   "fecha": str(hoy - timedelta(days=3 - k)), "lineas": lineas})
    if c:
        compras.append(c)
        llamar(A, "POST", f"/obras/{P}/almacen/recepciones", {"uuid_cliente": u(), "compra_id": c["id"], "almacen_id": C})
print(f"5. {len(compras)} compras registradas y recibidas en el almacén central")

# 6 · ingreso sin compra, salidas, traslado y un reverso
if mats:
    llamar(A, "POST", f"/obras/{P}/almacen/ingresos", {"uuid_cliente": u(), "almacen_id": F, "insumo_id": mats[0]["id"], "cantidad": "15",
                                                      "costo_unitario": "28.50", "origen": "sobrante"})
salidas = []
for m, q, motivo, quien in zip(mats, ("35", "20", "6", "4"), ("Vaciado de columnas eje 3", "Muros de albañilería piso 2", "Tarrajeo de fachada", "Encofrado de losa"),
                                ("Capataz Luis Ramos (demo)", "Capataz Ana Torres (demo)", "Operario Juan Paz (demo)", "Capataz Luis Ramos (demo)")):
    s = llamar(A, "POST", f"/obras/{P}/almacen/salidas", {"uuid_cliente": u(), "dispositivo": CEL, "almacen_id": C, "insumo_id": m["id"], "cantidad": q,
                                                         "motivo": motivo, "quien_retira": quien, "frente": "Torre A"})
    if s: salidas.append(s)
if len(mats) > 1:
    llamar(A, "POST", f"/obras/{P}/almacen/traslados", {"uuid_cliente": u(), "origen_almacen_id": C, "destino_almacen_id": F,
                                                       "motivo": "Abastecer el frente de la torre B", "lineas": [{"insumo_id": mats[1]["id"], "cantidad": "10"}]})
if salidas:
    llamar(A, "POST", f"/obras/{P}/almacen/movimientos/{salidas[-1]['id']}/reverso", {"uuid_cliente": u(), "motivo": "Se registró con la cantidad equivocada"})
print(f"6. Ingreso, {len(salidas)} salidas, un traslado y un reverso")

# 7 · préstamos de herramientas (uno atrasado, uno devuelto dañado)
pres = []
for e, q, quien, dias in zip(eqs, ("1", "1", "6"), ("Luis Ramos (demo)", "Ana Torres (demo)", "Cuadrilla de encofrado (demo)"), (-1, 2, 5)):
    x = llamar(A, "POST", f"/obras/{P}/almacen/prestamos", {"uuid_cliente": u(), "almacen_id": C, "insumo_id": e["id"], "cantidad": q,
                                                           "responsable": quien, "devolver_el": str(hoy + timedelta(days=dias))})
    if x: pres.append(x)
if len(pres) > 1:
    llamar(A, "POST", f"/obras/{P}/almacen/prestamos/{pres[1]['id']}/devolucion", {"estado": "danado", "nota": "Volvió con el disco rajado (demo)"})
print(f"7. {len(pres)} préstamos de herramientas")

# 8 · conteo físico con una diferencia (pérdida)
if len(mats) > 2:
    ct = llamar(A, "POST", f"/obras/{P}/almacen/conteos", {"uuid_cliente": u(), "fecha": str(hoy), "almacen_id": C}, callar=("conteo_abierto",))
    if ct:
        stock = llamar(A, "GET", f"/obras/{P}/almacen/stock") or {}
        saldo = next((float(r["cantidad"]) for r in stock.get("por_almacen", []) if r["insumo_id"] == mats[2]["id"] and r["almacen_id"] == C), None)
        if saldo is not None:
            llamar(A, "POST", f"/obras/{P}/almacen/conteos/{ct['id']}/lineas", {"insumo_id": mats[2]["id"], "cantidad_contada": str(max(saldo - 2, 0)),
                                                                                "motivo": "Pérdida: dos unidades rotas en el traslado (demo)"})
        llamar(A, "POST", f"/obras/{P}/almacen/conteos/{ct['id']}/confirmar", {})
print("8. Conteo físico con su ajuste")

# 9 · trabajadores de prueba (documento PASAPORTE «DEMO0001…»: no son personas reales) y asistencia
trab = {t["documento"]: t for t in llamar(S, "GET", f"/obras/{P}/trabajadores") or []}
for k, (nom, ap, cat_, cua) in enumerate((("Luis", "Ramos", "operario", "Estructuras"), ("Ana", "Torres", "oficial", "Estructuras"),
                                          ("Juan", "Paz", "peon", "Acabados"), ("Rosa", "Vega", "oficial", "Acabados"))):
    doc = f"DEMO{k + 1:04d}"
    if doc not in trab:
        t = llamar(S, "POST", f"/obras/{P}/trabajadores", {"nombres": nom, "apellido_paterno": ap, "apellido_materno": "(demo)", "tipo_documento": "PASAPORTE",
                                                           "documento": doc, "categoria": cat_, "cuadrilla": cua, "fecha_ingreso": str(hoy - timedelta(days=30))})
        if t: trab[doc] = t
for t in trab.values():
    for d in (1, 2):
        llamar(S, "POST", f"/obras/{P}/jornadas", {"trabajador_id": t["id"], "fecha": str(hoy - timedelta(days=d)), "estado": "presente",
                                                   "horas_normales": "8", "horas_extra": "2" if d == 1 else "0", "uuid_cliente": u()},
               callar=("jornada_duplicada", "ya_registrada", "uq_jornada_original"))
print(f"9. {len(trab)} trabajadores de prueba con su asistencia")
# 10 · prueba de permisos: el almacenero ya no registra personal (debe salir 403 y quedar en la bitácora)
r = cli.post(URL + f"/obras/{P}/trabajadores", json={}, headers={"Authorization": f"Bearer {A}"})
print(f"10. Permisos: el almacenero intenta registrar personal → {r.status_code} ({'rechazado, bien' if r.status_code == 403 else 'REVISAR'})")

# 10 · se revoca el celular del script: el de la demo se autoriza en la app
if disp: llamar(adm, "POST", f"/obras/{P}/dispositivos/{disp['id']}/revocar", {"motivo": "Celular del script de demostración"})
print("\nListo. En la web: «Cambiar de obra» → «" + OBRA + "».")
print("Usuarios de prueba (guárdalos y no los compartas fuera del equipo):")
for rol, (usr, cl) in cred.items(): print(f"   {rol:<11} usuario: {usr:<16} clave: {cl}")
