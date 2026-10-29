"""Deja en la imagen de un servicio SOLO su código (lo usa el Dockerfile: `python -m app.servicios.podar compras`).

Si un servicio importara código de otro, su contenedor no arrancaría: la independencia queda probada
en cada construcción, no solo en una revisión de código.
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

APP = Path(__file__).resolve().parents[1]
RAIZ = APP.parent

MODULOS = {
    "obras": {"obra", "notificaciones", "outbox"},
    "catalogo": {"catalogo"},
    "compras": {"compras"},
    "tareo": {"tareo"},
    "relevo": {"outbox"},
    "gateway": set(),
}
ENTRADAS = {"obras", "catalogo", "compras", "tareo", "gateway"}


def podar(servicio: str, raiz: Path = RAIZ) -> list[str]:
    if servicio == "migrador":          # el migrador necesita todos los modelos y migraciones
        return []
    if servicio not in MODULOS:
        raise SystemExit(f"servicio desconocido: {servicio} (opciones: {', '.join(sorted(MODULOS))}, migrador)")
    app, quitados = raiz / "app", []
    for carpeta in (app / "modulos").iterdir():
        if carpeta.is_dir() and carpeta.name != "__pycache__" and carpeta.name not in MODULOS[servicio]:
            shutil.rmtree(carpeta)
            quitados.append(f"app/modulos/{carpeta.name}")
    for entrada in ENTRADAS - {servicio}:
        archivo = app / "servicios" / f"{entrada}.py"
        if archivo.exists():
            archivo.unlink()
            quitados.append(f"app/servicios/{entrada}.py")
    for extra in ("migraciones", "alembic.ini", "semillas"):
        destino = raiz / extra
        if destino.is_dir():
            shutil.rmtree(destino)
            quitados.append(extra)
        elif destino.exists():
            destino.unlink()
            quitados.append(extra)
    return quitados


if __name__ == "__main__":
    for q in podar(sys.argv[1]):
        print(f"podado: {q}")
