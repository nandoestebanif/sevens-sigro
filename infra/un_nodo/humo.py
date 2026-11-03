"""Prueba de humo del ambiente en un nodo (ADR-10): TODO entra por el gateway, como la web y la app.

    SIGRO_BASE=http://127.0.0.1:8000 SIGRO_ADMIN_USUARIO=admin SIGRO_ADMIN_CLAVE=... python infra/un_nodo/humo.py

Recorre: salud de los servicios → login → obras → catálogo → proveedores → almacenero con celular autorizado →
ingreso → salida (costo congelado) → reenvío de la misma salida (no duplica) → stock que calcula el servidor.
Nunca imprime claves ni tokens. Solo biblioteca estándar. Sale con código 1 al primer paso que falle.
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from decimal import Decimal

BASE = os.environ.get("SIGRO_BASE", "http://127.0.0.1:8000").rstrip("/")


def pedir(metodo: str, ruta: str, token: str | None = None, cuerpo: dict | None = None,
          espera: float = 60) -> tuple[int, object, float]:
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    req = urllib.request.Request(f"{BASE}{ruta}", data=datos, method=metodo)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    inicio = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=espera) as r:
            codigo, texto = r.status, r.read()
    except urllib.error.HTTPError as e:
        codigo, texto = e.code, e.read()
    ms = (time.perf_counter() - inicio) * 1000
    try:
        return codigo, json.loads(texto or b"null"), ms
    except ValueError:
        return codigo, texto.decode(errors="replace")[:200], ms


def paso(nombre: str, ok: bool, detalle: str = "") -> None:
    print(f"{'OK ' if ok else 'MAL'} {nombre}{' · ' + detalle if detalle else ''}", flush=True)
    if not ok:
        sys.exit(1)


def esperar_listo(limite_s: float) -> None:
    fin = time.monotonic() + limite_s
    while True:
        try:
            codigo, cuerpo, ms = pedir("GET", "/salud/servicios", espera=15)
        except (urllib.error.URLError, ConnectionError, TimeoutError):
            codigo, cuerpo, ms = 0, None, 0
        if codigo == 200:
            nombres = ", ".join(s["servicio"] for s in cuerpo["servicios"])
            paso("salud de los servicios", True, f"{len(cuerpo['servicios'])} listos ({nombres})")
            return
        if time.monotonic() > fin:
            estados = {s["servicio"]: s["estado"] for s in cuerpo["servicios"]} if isinstance(cuerpo, dict) else codigo
            paso("salud de los servicios", False, f"sin listo en {limite_s:.0f} s: {estados}")
        time.sleep(3)


def main() -> None:
    esperar_listo(float(os.environ.get("SIGRO_HUMO_ESPERA", "120")))

    c, r, ms = pedir("POST", "/api/v1/auth/login", cuerpo={"usuario": os.environ["SIGRO_ADMIN_USUARIO"],
                                                           "clave": os.environ["SIGRO_ADMIN_CLAVE"]})
    paso("login del administrador", c == 200 and bool(r.get("token")), f"{c} en {ms:.0f} ms")
    admin = r["token"]

    c, obras, ms = pedir("GET", "/api/v1/obras", admin)
    paso("obras", c == 200 and len(obras) >= 1, f"{c} · {len(obras)} obra(s) en {ms:.0f} ms")
    obra = obras[0]["id"]

    c, insumos, ms = pedir("GET", "/api/v1/catalogo/insumos", admin)
    paso("catálogo", c == 200 and len(insumos) > 0, f"{c} · {len(insumos)} insumos en {ms:.0f} ms")
    insumo = next((i for i in insumos if i["tipo"] == "material" and i["activo"]), insumos[0])

    c, proveedores, ms = pedir("GET", "/api/v1/proveedores", admin)
    paso("proveedores", c == 200 and len(proveedores) > 0, f"{c} · {len(proveedores)} proveedores en {ms:.0f} ms")

    # almacenero de prueba, con su celular autorizado (reglas 5 y 9: lo verifica el servidor)
    sufijo = uuid.uuid4().hex[:6]
    clave = uuid.uuid4().hex + "Aa1!"
    c, u, _ = pedir("POST", "/api/v1/usuarios", admin, {"nombre": f"Almacenero humo {sufijo}",
                                                       "usuario": f"humo{sufijo}", "clave": clave})
    paso("alta del almacenero de prueba", c == 201, str(c))
    c, _, _ = pedir("POST", f"/api/v1/obras/{obra}/asignaciones", admin, {"usuario_id": u["id"], "rol": "almacenero"})
    paso("asignación como almacenero", c == 201, str(c))
    c, r, _ = pedir("POST", "/api/v1/auth/login", cuerpo={"usuario": f"humo{sufijo}", "clave": clave})
    paso("login del almacenero", c == 200, str(c))
    alm_token = r["token"]
    celular = f"humo-{sufijo}-android"
    c, d, _ = pedir("POST", f"/api/v1/obras/{obra}/dispositivos", alm_token, {"identificador": celular})
    paso("pedido de autorización del celular", c == 201, str(c))
    c, _, _ = pedir("POST", f"/api/v1/obras/{obra}/dispositivos/{d['id']}/autorizar", admin)
    paso("celular autorizado por el administrador", c == 200, str(c))

    c, almacenes, _ = pedir("GET", f"/api/v1/obras/{obra}/almacenes", alm_token)
    if c == 200 and not almacenes:
        c, a, _ = pedir("POST", f"/api/v1/obras/{obra}/almacenes", alm_token, {"nombre": "Almacén central", "tipo": "central"})
        almacenes = [a] if c == 201 else []
    paso("almacén de la obra", bool(almacenes), f"{len(almacenes)} almacén(es)")
    almacen = almacenes[0]["id"]

    c, ing, ms = pedir("POST", f"/api/v1/obras/{obra}/almacen/ingresos", alm_token, {
        "uuid_cliente": str(uuid.uuid4()), "dispositivo": celular, "almacen_id": almacen, "insumo_id": insumo["id"],
        "cantidad": "5", "costo_unitario": "12.50", "origen": "sobrante", "origen_detalle": "prueba de humo ADR-10"})
    paso("ingreso de almacén", c == 201, f"{c} en {ms:.0f} ms · {insumo['codigo']}")

    salida = {"uuid_cliente": str(uuid.uuid4()), "dispositivo": celular, "almacen_id": almacen,
              "insumo_id": insumo["id"], "cantidad": "2", "motivo": "prueba de humo ADR-10", "quien_retira": "Cuadrilla humo"}
    c, s1, ms = pedir("POST", f"/api/v1/obras/{obra}/almacen/salidas", alm_token, salida)
    paso("salida de almacén", c == 201 and Decimal(s1["costo_unitario"]) > 0,
         f"{c} en {ms:.0f} ms · costo congelado {s1.get('costo_unitario')} · saldo {s1.get('saldo')}")
    c, s2, _ = pedir("POST", f"/api/v1/obras/{obra}/almacen/salidas", alm_token, salida)
    paso("reenvío de la misma salida no duplica", c == 200 and s2["id"] == s1["id"], f"{c} · mismo id {s2.get('id')}")

    c, stock, ms = pedir("GET", f"/api/v1/obras/{obra}/almacen/stock", alm_token)
    paso("stock calculado por el servidor", c == 200, f"{c} en {ms:.0f} ms")

    c, _, _ = pedir("POST", f"/api/v1/obras/{obra}/almacen/salidas", admin, {**salida, "uuid_cliente": str(uuid.uuid4())})
    paso("un administrador no registra salidas (rol, servidor)", c == 403, str(c))
    print("HUMO OK", flush=True)


if __name__ == "__main__":
    main()
