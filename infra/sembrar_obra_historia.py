"""SIGRO · crea una obra de DEMOSTRACIÓN con ~4 meses de uso (datos de prueba), usando la API como lo haría la gente.

Uso (Python 3.10+):   pip install httpx
    python sembrar_obra_historia.py
Pide el usuario, la clave y (si lo tiene) el PIN de un ADMINISTRADOR; no los guarda ni los imprime.
Variables opcionales: SIGRO_URL (por defecto sevens-sigro), SIGRO_DEMO_DIAS (días de historia, por defecto 120).

Qué deja en «SIGRO · Obra con historia (datos de prueba)», todo con su fecha real en el pasado:
  - requerimiento (relación de insumos) con cantidades y precios planificados, parámetros de reposición y críticos;
  - compras cada ~9 días con su recepción, una compra anulada y solicitudes de compra del residente;
  - salidas casi todos los días, traslados al frente, ingresos sin compra y algunos reversos;
  - préstamos de herramientas (devueltos, uno dañado, uno atrasado) y dos conteos físicos con pérdida;
  - trabajadores (PASAPORTE «HIST0001…», no son personas reales) con su asistencia de las últimas semanas;
  - los periodos (semanas) viejos cerrados, como en una obra de verdad.
Todo pasa por las reglas del servidor (permisos, stock, periodos, uuid_cliente). Lo registrado no se borra (R1):
por eso va en una obra aparte. Correrlo dos veces agrega otra tanda: córrelo UNA vez.
"""
import getpass, os, random, secrets, sys, uuid
from datetime import date, datetime, time, timedelta, timezone
import httpx

URL = (os.environ.get("SIGRO_URL") or "https://sevens-sigro.onrender.com").rstrip("/") + "/api/v1"
OBRA = os.environ.get("SIGRO_DEMO_NOMBRE") or "SIGRO · Obra con historia (datos de prueba)"
DIAS = int(os.environ.get("SIGRO_DEMO_DIAS") or 120)
hoy = date.today()
INICIO = hoy - timedelta(days=DIAS)
LIMA = timezone(timedelta(hours=-5))
rnd = random.Random(2026)  # misma historia cada vez que se genera
cli = httpx.Client(timeout=120)
PIN = {"token": None}
fallas = []


def u(): return str(uuid.uuid4())


def ms(d: date, hora=10): return int(datetime.combine(d, time(hora, rnd.randint(0, 59)), LIMA).timestamp() * 1000)


def llamar(tok, metodo, ruta, cuerpo=None, ok=(200, 201), callar=()):
    cab = {"Authorization": f"Bearer {tok}"} if tok else {}
    if tok and PIN["token"]: cab["X-SIGRO-PIN"] = PIN["token"]
    for intento in range(3):
        try:
            r = cli.request(metodo, URL + ruta, json=cuerpo, headers=cab)
            break
        except httpx.HTTPError:
            if intento == 2: raise
    if r.status_code not in ok:
        cod = (r.json().get("codigo") if r.headers.get("content-type", "").startswith("application/json") else "") or ""
        if cod not in callar:
            fallas.append(f"{metodo} {ruta} → {r.status_code} {cod}")
            print(f"   ! {metodo} {ruta} → {r.status_code} {r.text[:160]}")
        return None
    return r.json() if r.content else {}


def entrar(usuario, clave):
    d = llamar(None, "POST", "/auth/login", {"usuario": usuario, "clave": clave})
    if not d: sys.exit(f"No se pudo entrar como {usuario}.")
    return d.get("access_token") or d.get("token")


def laborable(d: date): return d.weekday() < 6  # lunes a sábado, como en obra


print(f"Servidor: {URL}  ·  historia de {DIAS} días ({INICIO} → {hoy})")
adm = entrar(input("Usuario administrador: ").strip(), getpass.getpass("Clave: "))
if (llamar(adm, "GET", "/auth/pin", callar=("no_encontrado",)) or {}).get("configurado"):
    v = llamar(adm, "POST", "/auth/pin/verificar", {"pin": getpass.getpass("PIN del panel de administración: ")})
    if not v: sys.exit("PIN incorrecto: no se tocó nada.")
    PIN["token"] = v.get("pin_token")

# 1 · obra y almacenes ------------------------------------------------------------------------------------------
obra = next((o for o in (llamar(adm, "GET", "/obras") or []) if o.get("nombre") == OBRA), None)
if obra:
    print(f"La obra «{OBRA}» ya existe (#{obra['id']}). Este script se corre una sola vez; no se agregó nada.")
    sys.exit(0)
obra = llamar(adm, "POST", "/obras", {"nombre": OBRA, "fecha_inicio": str(INICIO), "fecha_fin": str(hoy + timedelta(days=240))})
if not obra: sys.exit("No se pudo crear la obra.")
P = obra["id"]
alm = {}
for nombre, tipo in (("Almacén central", "central"), ("Frente torre B", "frente")):
    alm[tipo] = llamar(adm, "POST", f"/obras/{P}/almacenes", {"nombre": nombre, "tipo": tipo})["id"]
C, F = alm["central"], alm["frente"]
print(f"1. Obra #{P} creada, del {INICIO} al {hoy + timedelta(days=240)}, con 2 almacenes")

# 2 · personas de prueba con su rol en ESTA obra ------------------------------------------------------------------
usuarios = {x["usuario"]: x for x in llamar(adm, "GET", "/usuarios") or []}
cred, tok = {}, {}
for usuario, nombre, rol in (("demo.almacenero", "Almacenero de prueba", "almacenero"), ("demo.ssoma", "SSOMA de prueba", "ssoma"),
                             ("demo.residente", "Residente de prueba", "residente")):
    clave = secrets.token_urlsafe(9) + "Aa9"
    if usuario in usuarios:
        llamar(adm, "POST", f"/usuarios/{usuarios[usuario]['id']}/clave-temporal", {"clave": clave})
        uid = usuarios[usuario]["id"]
    else:
        uid = llamar(adm, "POST", "/usuarios", {"nombre": nombre, "usuario": usuario, "clave": clave})["id"]
    llamar(adm, "POST", f"/obras/{P}/asignaciones", {"usuario_id": uid, "rol": rol}, callar=("uq_asignacion_vigente",))
    cred[rol] = (usuario, clave)
    tok[rol] = entrar(usuario, clave)
A, S, R = tok["almacenero"], tok["ssoma"], tok["residente"]
print("2. Almacenero, SSOMA y residente de prueba asignados")

CEL = "demo-historia-" + secrets.token_hex(4)
llamar(A, "POST", f"/obras/{P}/dispositivos", {"identificador": CEL})
disp = next((d for d in llamar(adm, "GET", f"/obras/{P}/dispositivos") or [] if d.get("identificador") == CEL), None)
if disp: llamar(adm, "POST", f"/obras/{P}/dispositivos/{disp['id']}/autorizar", {})

# 3 · insumos: materiales del catálogo de la empresa y herramientas ---------------------------------------------
cat = [i for i in llamar(adm, "GET", "/catalogo/insumos") or [] if i.get("activo", True)]
CLAVES = [("CEMENTO", 28.5, 2400), ("ARENA GRUESA", 55, 180), ("PIEDRA CHANCADA", 68, 160), ("ACERO CORRUGADO", 4.1, 36000),
          ("ALAMBRE NEGRO", 5.4, 900), ("CLAVOS", 5.2, 600), ("LADRILLO", 0.9, 42000), ("ARENA FINA", 50, 90), ("YESO", 9.5, 400),
          ("TUBERIA PVC", 14, 300), ("PEGAMENTO", 32, 60), ("MADERA", 6.5, 3000), ("HORMIGON", 60, 120), ("PINTURA", 48, 120)]
mats, usados = [], set()
for clave, precio, plan in CLAVES:
    i = next((x for x in cat if x.get("tipo") == "material" and clave in x["descripcion"].upper() and x["id"] not in usados), None)
    if i: mats.append({**i, "precio": precio, "plan": plan}); usados.add(i["id"])
for x in cat:  # si el catálogo tiene pocos de esos, se completan con otros materiales
    if len(mats) >= 12: break
    if x.get("tipo") == "material" and x["id"] not in usados:
        mats.append({**x, "precio": round(rnd.uniform(5, 60), 2), "plan": rnd.choice([200, 400, 800])}); usados.add(x["id"])
eqs = [i for i in cat if i.get("tipo") == "equipo"][:4]
for desc in ("TALADRO PERCUTOR 800 W", "AMOLADORA ANGULAR 4 1/2\"", "ANDAMIO METALICO (CUERPO)", "VIBRADOR DE CONCRETO")[len(eqs):]:
    n = llamar(adm, "POST", "/catalogo/insumos", {"descripcion": desc, "unidad": "und", "tipo": "equipo"})
    if n: eqs.append(n)
if len(mats) < 4: sys.exit("El catálogo tiene muy pocos materiales para armar la historia.")
fac = {m["id"]: float(m.get("factor_conversion") or 1) for m in mats}
print(f"3. {len(mats)} materiales del catálogo y {len(eqs)} herramientas")

# 4 · requerimiento (lo planificado), parámetros de reposición y críticos -----------------------------------------
llamar(adm, "POST", f"/obras/{P}/requerimiento/importar",
       {"filas": [{"insumo_codigo": m["codigo"], "cantidad": str(m["plan"]), "precio": str(m["precio"])} for m in mats]})
for m in mats[:7]:
    dia = m["plan"] / 300
    llamar(adm, "POST", f"/obras/{P}/parametros-reposicion",
           {"insumo_codigo": m["codigo"], "stock_seguridad": str(round(dia * 5, 2)), "punto_reposicion": str(round(dia * 12, 2))})
for m in mats[:2]:
    llamar(adm, "POST", f"/obras/{P}/criticos", {"insumo_codigo": m["codigo"], "critico": True})
print("4. Requerimiento planificado, parámetros de reposición y 2 insumos críticos")

prov = next((p for p in llamar(adm, "GET", "/proveedores") or [] if "PRUEBA SIGRO" in p.get("razon_social", "").upper()), None)
if not prov: prov = llamar(adm, "POST", "/proveedores", {"ruc": "10000001", "razon_social": "PROVEEDOR DE PRUEBA SIGRO (DEMO)"})

# 5 · la historia, día por día ----------------------------------------------------------------------------------
stock = {(C, m["id"]): 0.0 for m in mats} | {(F, m["id"]): 0.0 for m in mats}
n0 = secrets.randbelow(80000) + 1000
cuenta = {"compras": 0, "salidas": 0, "traslados": 0, "reversos": 0, "ingresos": 0, "prestamos": 0, "conteos": 0}
salidas_hechas, prestamos = [], []
FRENTES = ["Torre A · cimentación", "Torre A · columnas", "Torre A · losa piso 2", "Torre B · muros", "Torre B · tarrajeo", "Exteriores"]
GENTE = ["Capataz Luis Ramos (demo)", "Capataz Ana Torres (demo)", "Operario Juan Paz (demo)", "Operaria Rosa Vega (demo)", "Cuadrilla de encofrado (demo)"]


def comprar(d: date, lote, extra=()):
    global n0
    lineas = list(extra) + [{"insumo_codigo": m["codigo"], "cantidad": str(q), "unidad_compra": m.get("unidad_compra") or m.get("unidad_s10"),
               "precio_unitario": str(round(m["precio"] * rnd.uniform(0.95, 1.08) * fac[m["id"]], 2))} for m, q in lote]
    n0 += 1
    c = llamar(A, "POST", f"/obras/{P}/compras", {"proveedor_id": prov["id"], "tipo_comprobante": rnd.choice(["factura", "factura", "boleta"]),
                                                  "numero_comprobante": f"H{n0 // 1000:03d}-{n0}", "fecha": str(d), "lineas": lineas})
    if not c: return None
    if llamar(A, "POST", f"/obras/{P}/almacen/recepciones", {"uuid_cliente": u(), "compra_id": c["id"], "almacen_id": C, "fecha": str(d), "hora_dispositivo_ms": ms(d, 9)}):
        for m, q in lote: stock[(C, m["id"])] += q * fac[m["id"]]
        cuenta["compras"] += 1
    return c


d = INICIO
dia_n = 0
while d < hoy:
    if laborable(d):
        dia_n += 1
        # compras: al arrancar y luego cada ~9 días, de lo que se está acabando
        if dia_n == 1 or dia_n % 8 == 0:
            lote = []
            for m in rnd.sample(mats, k=min(len(mats), 5 if dia_n > 1 else len(mats))):
                q = max(1, round(m["plan"] / (DIAS / 9) * rnd.uniform(1.0, 1.6) / fac[m["id"]]))
                lote.append((m, q))
            comprar(d, lote, [{"insumo_codigo": e["codigo"], "cantidad": str(q), "unidad_compra": e.get("unidad_compra") or "und",
                               "precio_unitario": str(p)} for e, q, p in zip(eqs, (3, 2, 12, 2), (420, 260, 140, 1800))] if dia_n == 1 else ())
        # salidas del día
        for _ in range(rnd.choice([1, 2, 2, 3])):
            m = rnd.choice(mats)
            q = round(m["plan"] / (DIAS * 0.9) * rnd.uniform(0.6, 1.8), 2 if m["plan"] < 1000 else 0)
            if q <= 0 or stock[(C, m["id"])] < q: continue
            s = llamar(A, "POST", f"/obras/{P}/almacen/salidas", {"uuid_cliente": u(), "dispositivo": CEL, "almacen_id": C, "insumo_id": m["id"],
                                                                 "cantidad": str(q), "fecha": str(d), "hora_dispositivo_ms": ms(d, rnd.randint(7, 16)),
                                                                 "motivo": rnd.choice(["Vaciado", "Encofrado", "Albañilería", "Tarrajeo", "Habilitación de acero", "Instalaciones"]) + " · " + rnd.choice(FRENTES),
                                                                 "quien_retira": rnd.choice(GENTE), "frente": rnd.choice(FRENTES)})
            if s:
                stock[(C, m["id"])] -= q
                salidas_hechas.append((s, d))
                cuenta["salidas"] += 1
        # traslado al frente cada dos semanas
        if dia_n % 12 == 6:
            m = rnd.choice(mats)
            q = round(stock[(C, m["id"])] * 0.15, 2)
            if q > 0 and llamar(A, "POST", f"/obras/{P}/almacen/traslados", {"uuid_cliente": u(), "origen_almacen_id": C, "destino_almacen_id": F,
                                                                              "fecha": str(d), "hora_dispositivo_ms": ms(d, 11),
                                                                              "motivo": "Abastecer el frente de la torre B", "lineas": [{"insumo_id": m["id"], "cantidad": str(q)}]}):
                stock[(C, m["id"])] -= q; stock[(F, m["id"])] += q; cuenta["traslados"] += 1
        # ingresos sin compra (sobrante de otra obra, devolución de cuadrilla)
        if dia_n in (15, 55):
            m = mats[dia_n % len(mats)]
            q = max(1, round(m["plan"] * 0.02))
            if llamar(A, "POST", f"/obras/{P}/almacen/ingresos", {"uuid_cliente": u(), "almacen_id": C, "insumo_id": m["id"], "cantidad": str(q),
                                                                  "costo_unitario": str(m["precio"]), "fecha": str(d), "hora_dispositivo_ms": ms(d, 15),
                                                                  "origen": "saldo_otra_obra" if dia_n == 15 else "devolucion_cuadrilla",
                                                                  "origen_detalle": "Sobrante de la obra Residencial Los Pinos (demo)" if dia_n == 15 else "Devolución de la cuadrilla de acabados (demo)"}):
                stock[(C, m["id"])] += q; cuenta["ingresos"] += 1
        # préstamos de herramientas
        if eqs and dia_n % 9 == 3:
            e = eqs[(dia_n // 9) % len(eqs)]
            for x in [x for x in prestamos if x.get("insumo_id") == e["id"] and not x.get("_devuelto")]:  # vuelve lo anterior
                danado = cuenta["prestamos"] == 4
                if llamar(A, "POST", f"/obras/{P}/almacen/prestamos/{x['id']}/devolucion",
                          {"estado": "danado", "nota": "Volvió con el cable pelado (demo)"} if danado else {"estado": "bueno"}, callar=("ya_devuelto",)):
                    x["_devuelto"] = True
            x = llamar(A, "POST", f"/obras/{P}/almacen/prestamos", {"uuid_cliente": u(), "almacen_id": C, "insumo_id": e["id"], "cantidad": "1",
                                                                   "responsable": rnd.choice(GENTE), "fecha": str(d), "hora_dispositivo_ms": ms(d, 8),
                                                                   "devolver_el": str(d + timedelta(days=rnd.choice([3, 5, 7])))},
                       callar=("sin_disponible", "stock_insuficiente"))
            if x: prestamos.append(x); cuenta["prestamos"] += 1
        # conteos físicos con una diferencia
        if dia_n in (int(DIAS * 0.45), int(DIAS * 0.8)):
            ct = llamar(A, "POST", f"/obras/{P}/almacen/conteos", {"uuid_cliente": u(), "fecha": str(d), "almacen_id": C, "hora_dispositivo_ms": ms(d, 17)},
                        callar=("conteo_abierto",))
            if ct:
                for m in rnd.sample(mats, 3):
                    real = stock[(C, m["id"])]
                    if real <= 0: continue
                    perdida = round(min(real, max(1, real * 0.03)), 2)
                    llamar(A, "POST", f"/obras/{P}/almacen/conteos/{ct['id']}/lineas",
                           {"insumo_id": m["id"], "cantidad_contada": str(round(real - perdida, 2)), "motivo": "Pérdida o merma detectada en el conteo (demo)"})
                    stock[(C, m["id"])] -= perdida
                if llamar(A, "POST", f"/obras/{P}/almacen/conteos/{ct['id']}/confirmar", {}): cuenta["conteos"] += 1
    d += timedelta(days=1)
    if d.day == 1 or d == hoy: print(f"   … {d}: {cuenta['compras']} compras, {cuenta['salidas']} salidas", flush=True)

# devoluciones: vuelve todo lo que sigue afuera menos el último préstamo, que queda atrasado
for x in [x for x in prestamos[:-1] if not x.get("_devuelto")]:
    llamar(A, "POST", f"/obras/{P}/almacen/prestamos/{x['id']}/devolucion", {"estado": "bueno"}, callar=("ya_devuelto",))
# reversos: correcciones de salidas mal registradas
for s, _ in rnd.sample(salidas_hechas, min(3, len(salidas_hechas))):
    if llamar(A, "POST", f"/obras/{P}/almacen/movimientos/{s['id']}/reverso", {"uuid_cliente": u(), "motivo": "Se registró con la cantidad equivocada (demo)"},
              callar=("periodo_cerrado",)):
        cuenta["reversos"] += 1
print("5. Historia: " + ", ".join(f"{v} {k}" for k, v in cuenta.items()))

# 6 · una compra anulada y solicitudes de compra del residente ------------------------------------------------------
c = llamar(A, "POST", f"/obras/{P}/compras", {"proveedor_id": prov["id"], "tipo_comprobante": "factura", "numero_comprobante": f"H999-{n0 + 7}",
                                              "fecha": str(hoy - timedelta(days=2)), "lineas": [{"insumo_codigo": mats[0]["codigo"], "cantidad": "10",
                                              "unidad_compra": mats[0].get("unidad_compra") or "und", "precio_unitario": str(mats[0]["precio"])}]})
if c: llamar(A, "POST", f"/obras/{P}/compras/{c['id']}/anular", {"motivo": "Factura duplicada por el proveedor (demo)"})
for m in rnd.sample(mats, 2):
    llamar(R, "POST", f"/obras/{P}/solicitudes", {"lineas": [{"insumo_codigo": m["codigo"], "cantidad": str(max(1, round(m["plan"] * 0.1 / fac[m["id"]]))),
                                                              "unidad_compra": m.get("unidad_compra") or "und"}]})
print("6. Una compra anulada y 2 solicitudes de compra")

# 7 · personal de prueba (PASAPORTE «HIST0001…», no son personas reales) y asistencia de las últimas semanas --------
CUADRILLA = [("Luis", "Ramos", "operario", "Estructuras"), ("Ana", "Torres", "oficial", "Estructuras"), ("Juan", "Paz", "peon", "Estructuras"),
             ("Rosa", "Vega", "oficial", "Acabados"), ("Pedro", "Quispe", "operario", "Acabados"), ("Elena", "Huamán", "peon", "Acabados"),
             ("Mario", "Rojas", "operario", "Instalaciones"), ("Carla", "Díaz", "oficial", "Instalaciones")]
trab = []
for k, (nom, ap, cat_, cua) in enumerate(CUADRILLA):
    t = llamar(S, "POST", f"/obras/{P}/trabajadores", {"nombres": nom, "apellido_paterno": ap, "apellido_materno": "(demo)", "tipo_documento": "PASAPORTE",
                                                       "documento": f"HIST{k + 1:04d}", "categoria": cat_, "cuadrilla": cua,
                                                       "fecha_ingreso": str(INICIO + timedelta(days=k * 3))}, callar=("documento_duplicado",))
    if t: trab.append(t)
jornadas = 0
for back in range(35, 0, -1):
    d = hoy - timedelta(days=back)
    if not laborable(d): continue
    for t in trab:
        falta = rnd.random() < 0.06
        if llamar(S, "POST", f"/obras/{P}/jornadas", {"trabajador_id": t["id"], "fecha": str(d), "estado": "ausente" if falta else "presente",
                                                      "horas_normales": "0" if falta else "8", "horas_extra": "0" if falta else rnd.choice(["0", "0", "0", "1", "2"]),
                                                      "uuid_cliente": u()}, callar=("uq_jornada_original",)):
            jornadas += 1
print(f"7. {len(trab)} trabajadores de prueba y {jornadas} jornadas")

# 8 · periodos viejos cerrados (como en una obra real: lo cerrado solo se corrige con reverso) ---------------------
cerrados = 0
for p in llamar(adm, "GET", f"/obras/{P}/periodos") or []:
    if p.get("estado") != "cerrado" and date.fromisoformat(str(p["fecha_fin"])[:10]) < hoy - timedelta(days=21):
        if llamar(adm, "POST", f"/obras/{P}/periodos/{p['numero']}/cerrar", {}, callar=("periodo_cerrado",)): cerrados += 1
print(f"8. {cerrados} periodos cerrados")

if disp: llamar(adm, "POST", f"/obras/{P}/dispositivos/{disp['id']}/revocar", {"motivo": "Celular del script de demostración"})
print(f"\nListo{' con ' + str(len(fallas)) + ' avisos (arriba)' if fallas else ''}. En la web: «Cambiar de obra» → «{OBRA}».")
print("Usuarios de prueba (guárdalos y no los compartas fuera del equipo; sus claves cambiaron):")
for rol, (usr, cl) in cred.items(): print(f"   {rol:<11} usuario: {usr:<16} clave: {cl}")
