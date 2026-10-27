"""Semilla: el catálogo de insumos normalizado del cliente (2 243) → catalogo.insumo.   (HT-07 · T-247)

Uso:
    python -m semillas.cargar_catalogo semillas/catalogo-cliente.json [--dry-run]

- Idempotente: el código del cliente es la clave. Si ya existe, solo actualiza la descripción si cambió; no toca unidad,
  factor ni verificación. Cargar dos veces no duplica nada.
- Entran como `material` y ya verificados: la lista es del cliente. El archivo no trae tipo; los equipos, subcontratos y la
  mano de obra llegan con la importación del S10 (HU-07).
- La unidad del cliente (UNIDADES, BOLSAS…) se guarda como unidad de compra y también como unidad del S10 mientras no haya
  una fila del S10 que la sustituya.
- NO se cargan precios ni marcas: el precio es de cada compra y la marca no es un nombre comercial verificado.

ATENCIÓN: el código del cliente («68», «1617») NO es el código del S10 («0221000000»). Cargar el catálogo del cliente y luego
importar el S10 crea dos filas para un mismo material hasta que se defina el emparejamiento S10 ↔ catálogo. Ese emparejamiento
es decisión de Fernando (dueño del catálogo): confírmenlo antes de cargar en el ambiente que verá el cliente.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from collections import Counter
from pathlib import Path

from sqlalchemy import create_engine, text


def leer(ruta: Path) -> list[dict]:
    datos = json.loads(ruta.read_text(encoding="utf-8"))
    if not isinstance(datos, list):
        raise SystemExit("Se esperaba una lista de insumos.")
    return datos


def preparar(filas: list[dict]) -> tuple[list[tuple[str, str, str]], Counter]:
    """([(codigo, descripcion, unidad)], resumen). Descarta solo lo que la base nunca aceptaría, y lo cuenta."""
    vistos: dict[str, tuple[str, str]] = {}
    resumen: Counter = Counter()
    for f in filas:
        codigo = str(f.get("codigo", "")).strip()
        nombre = " ".join(str(f.get("nombre", "")).split())
        unidad = " ".join(str(f.get("um", "")).split())
        if not codigo or not nombre or not unidad:
            resumen["descartado_incompleto"] += 1
            continue
        if codigo in vistos:
            resumen["repetido_en_el_archivo"] += 1
            continue
        vistos[codigo] = (nombre, unidad)
        resumen["valido"] += 1
    return [(c, n, u) for c, (n, u) in vistos.items()], resumen


def cargar(url: str, insumos: list[tuple[str, str, str]]) -> Counter:
    motor = create_engine(url)
    resultado: Counter = Counter()
    with motor.begin() as c:
        for codigo, nombre, unidad in insumos:
            fila = c.execute(
                text(
                    "INSERT INTO catalogo.insumo (codigo, descripcion, unidad_s10, unidad_compra, tipo, verificado) "
                    "VALUES (:c, :d, :u, :u, 'material', true) "
                    "ON CONFLICT (codigo) DO UPDATE SET descripcion = EXCLUDED.descripcion "
                    "WHERE catalogo.insumo.descripcion IS DISTINCT FROM EXCLUDED.descripcion "
                    "RETURNING (xmax = 0) AS insertado"
                ),
                {"c": codigo, "d": nombre, "u": unidad},
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

    insumos, resumen = preparar(leer(a.archivo))
    print(f"Leídos {sum(resumen.values())} registros: {dict(resumen)}")
    if a.dry_run:
        print("Dry-run: no se escribió nada.")
        return 0
    if not a.url:
        print("Falta la conexión: define SIGRO_APP_DATABASE_URL o usa --url.", file=sys.stderr)
        return 2
    print(f"Resultado: {dict(cargar(a.url, insumos))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
