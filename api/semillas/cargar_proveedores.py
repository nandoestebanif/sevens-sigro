"""Semilla de desarrollo: los proveedores reales del cliente → compras.proveedor.

Uso:
    python -m semillas.cargar_proveedores RUTA/proveedores-cliente.json [--dry-run]

- Idempotente: si el RUC ya existe, actualiza la razón social (si cambió) y NO toca su validación ni su baja.
  Cargar dos veces no duplica nada (misma regla que la importación del catálogo, HU-07).
- Todo entra «sin validar»: validarlos contra SUNAT es HU-31. Los registros con documento sucio
  (RUC de 6, 8, 9 o 13 dígitos, «DNI» con 11) se cargan igual y quedan en evidencia: no se corrigen ni se botan a mano.
- NO se cargan banco, cuenta, CCI ni titular: SIGRO no maneja pagos (queda pendiente preguntar a Pool).
- Se conecta con el usuario de la APLICACIÓN (sigro_app): demuestra que la carga no necesita poder de dueño.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from collections import Counter
from pathlib import Path

from sqlalchemy import create_engine, text

from app.modulos.compras.dominio import ruc_valido

FORMATO_DB = re.compile(r"^[0-9A-Za-z-]{4,20}$")     # ck_proveedor_ruc_formato


def leer(ruta: Path) -> list[dict]:
    datos = json.loads(ruta.read_text(encoding="utf-8"))
    if not isinstance(datos, list):
        raise SystemExit("Se esperaba una lista de proveedores.")
    return datos


def preparar(filas: list[dict]) -> tuple[list[tuple[str, str]], Counter]:
    """([(ruc, razon_social)], resumen). Descarta solo lo que la base nunca aceptaría, y lo cuenta."""
    vistos: dict[str, str] = {}
    resumen: Counter = Counter()
    for f in filas:
        ruc = str(f.get("numero_documento", "")).strip()
        razon = " ".join(str(f.get("proveedor", "")).split())
        if not razon or not FORMATO_DB.match(ruc):
            resumen["descartado_sin_formato"] += 1
            continue
        if ruc in vistos:
            resumen["repetido_en_el_archivo"] += 1
            continue
        vistos[ruc] = razon
        resumen["documento_sucio" if not ruc_valido(ruc) else "ruc_correcto"] += 1
    return list(vistos.items()), resumen


def cargar(url: str, proveedores: list[tuple[str, str]]) -> Counter:
    motor = create_engine(url)
    resultado: Counter = Counter()
    with motor.begin() as c:
        for ruc, razon in proveedores:
            fila = c.execute(
                text(
                    "INSERT INTO compras.proveedor (ruc, razon_social) VALUES (:r, :n) "
                    "ON CONFLICT (ruc) DO UPDATE SET razon_social = EXCLUDED.razon_social "
                    "WHERE compras.proveedor.razon_social IS DISTINCT FROM EXCLUDED.razon_social "
                    "RETURNING (xmax = 0) AS insertado"
                ),
                {"r": ruc, "n": razon},
            ).first()
            if fila is None:
                resultado["sin_cambios"] += 1
            else:
                resultado["insertados" if fila[0] else "actualizados"] += 1
    motor.dispose()
    return resultado


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("archivo", type=Path)
    ap.add_argument("--dry-run", action="store_true", help="solo cuenta; no escribe")
    ap.add_argument("--url", default=os.environ.get("SIGRO_APP_DATABASE_URL"), help="por defecto SIGRO_APP_DATABASE_URL")
    a = ap.parse_args(argv)

    proveedores, resumen = preparar(leer(a.archivo))
    print(f"Leídos {sum(resumen.values())} registros: {dict(resumen)}")
    if a.dry_run:
        print("Dry-run: no se escribió nada.")
        return 0
    if not a.url:
        print("Falta la conexión: define SIGRO_APP_DATABASE_URL o usa --url.", file=sys.stderr)
        return 2
    print(f"Resultado: {dict(cargar(a.url, proveedores))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
