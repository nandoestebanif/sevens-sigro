"""Diagrama físico de la PROPUESTA 0010 con el mismo generador que el modelo oficial.

Uso:  python docs/propuestas/probar_0010.py && python docs/propuestas/diagrama_0010.py
Salida: docs/propuestas/fisico-almacen-costo.(svg|png)
"""
import os
import sys
from pathlib import Path

DOCS = Path(__file__).resolve().parents[1]
os.environ["SIGRO_DB"] = "sigro_propuesta_0010"
sys.path.insert(0, str(DOCS))
import introspeccion as I  # noqa: E402

I.SERVICIO_DE.update({"almacen": "almacen", "costo": "costo"})
I.ESQUEMAS[:] = list(I.SERVICIO_DE)
I.ROLES += ["sigro_svc_almacen", "sigro_svc_costo"]
I.COLOR.update({"almacen": ("#C2410C", "#FFF4EC"), "costo": ("#374151", "#F3F4F6")})
I.QUE_HACE.update({"almacen": "movimientos (libro), recepciones, traslados, conteos; saldo y promedio DERIVADOS",
                   "costo": "modelo de lectura: hechos ya valorizados recibidos por evento; gasto en recursos"})
import generar_diagrama as G  # noqa: E402

m = I.leer_modelo(I.DSN.replace("sigro_dev", "sigro_propuesta_0010"))
G.dibujar(m, ["almacen", "costo"], "SIGRO · microservicios almacen y costo · modelo físico propuesto (0010)",
          f"PROPUESTA no aplicada · PostgreSQL {m.version_pg} · cero claves foráneas hacia otros servicios: réplicas locales almacen_ref e insumo_ref",
          DOCS / "propuestas" / "fisico-almacen-costo", con_almacen=False)
(DOCS / "propuestas" / "fisico-almacen-costo.dot").unlink(missing_ok=True)
print("propuestas/fisico-almacen-costo.(svg|png)")
