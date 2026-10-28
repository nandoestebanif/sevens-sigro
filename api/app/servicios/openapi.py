"""Contratos OpenAPI: uno por microservicio y uno unido con lo que expone el gateway (para Postman, HT-06).

    python -m app.servicios.openapi        # escribe docs/openapi-<servicio>.json y docs/openapi.json
"""
from __future__ import annotations

import json
from pathlib import Path

from app.servicios import fabricas

DOCS = Path(__file__).resolve().parents[2] / "docs"


def generar() -> dict:
    unido = {"openapi": "3.1.0", "info": {"title": "SIGRO · API pública (gateway)", "version": "0.2.0",
             "description": "Lo que la web y la app ven por el gateway. Cada ruta indica en `x-servicio` qué microservicio la atiende."},
             "paths": {}, "components": {"schemas": {}}}
    for nombre, fabrica in fabricas().items():
        doc = fabrica().openapi()
        (DOCS / f"openapi-{nombre}.json").write_text(json.dumps(doc, ensure_ascii=False, indent=1), encoding="utf-8")
        for ruta, ops in doc["paths"].items():
            if not ruta.startswith("/api/v1"):
                continue                        # /interno y /salud no salen por el gateway
            for op in ops.values():
                op["x-servicio"] = nombre
            unido["paths"][ruta] = ops
        unido["components"]["schemas"].update(doc.get("components", {}).get("schemas", {}))
    (DOCS / "openapi.json").write_text(json.dumps(unido, ensure_ascii=False, indent=1), encoding="utf-8")
    return unido


if __name__ == "__main__":
    print(f"{len(generar()['paths'])} rutas públicas documentadas")
