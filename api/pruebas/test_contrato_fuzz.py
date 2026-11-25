"""Fuzzing del contrato con Schemathesis (revisión de seguridad del 24/11/2026).

Genera peticiones válidas e inválidas para CADA operación de docs/openapi.json y las manda al gateway en proceso (todos
los servicios, PostgreSQL real, todo se revierte). Con el token de un administrador asignado a la obra, ninguna
respuesta puede ser 500: un dato raro debe terminar en 4xx con {codigo, mensaje}, nunca en un error del servidor.
Las rutas que dependen de Notificaciones e Integraciones (fuera del proceso de prueba) se saltan: ver EXTERNAS.

Primer hallazgo (24/11/2026): un texto con el carácter NUL daba 500 en toda ruta con texto → test_hallazgos_fuzzing.py.

Es lenta (≈ 90 operaciones × N ejemplos): corre solo con SIGRO_FUZZ=1.
    SIGRO_FUZZ=1 python -m pytest pruebas/test_contrato_fuzz.py -q
"""
from __future__ import annotations

import os
from pathlib import Path

import pytest

schemathesis = pytest.importorskip("schemathesis")
from hypothesis import HealthCheck, given, settings  # noqa: E402

pytestmark = pytest.mark.skipif(os.environ.get("SIGRO_FUZZ") != "1", reason="fuzzing lento: SIGRO_FUZZ=1 para correrlo")

CONTRATO = Path(__file__).resolve().parents[1] / "docs" / "openapi.json"
# Rutas que dependen de servicios fuera del proceso (Notificaciones, Integraciones/S10): aquí solo esperarían la red.
EXTERNAS = ("/api/v1/notificaciones", "/api/v1/catalogo/importaciones-s10")
EJEMPLOS = int(os.environ.get("SIGRO_FUZZ_EJEMPLOS", "15"))


def test_ninguna_operacion_responde_5xx(mundo):
    eq = mundo.f.obra_completa()
    cabeceras = mundo.token(eq["admin"])
    esquema = schemathesis.openapi.from_path(CONTRATO)
    esquema.app = mundo.http.app
    fallas: list[str] = []

    for resultado in esquema.get_all_operations():
        op = resultado.ok()
        if op.path.startswith(EXTERNAS):
            continue

        @settings(max_examples=EJEMPLOS, deadline=None, database=None,
                  suppress_health_check=list(HealthCheck))
        @given(case=op.as_strategy())
        def probar(case):
            r = case.call(headers=cabeceras)
            if r.status_code >= 500:
                fallas.append(f"{op.method.upper()} {op.path} → {r.status_code} · {case.path_parameters} {str(case.body)[:120]}")

        probar()

    distintas = sorted({f.split(" · ")[0] for f in fallas})
    assert not distintas, "Respuestas 500:\n" + "\n".join(distintas) + "\n\nEjemplos:\n" + "\n".join(fallas[:10])
