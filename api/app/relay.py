"""Relevo del outbox: entrega los eventos pendientes al microservicio de Notificaciones, con reintentos.

    python -m app.relay                 # se queda trabajando
    python -m app.relay --una-vez       # procesa lo pendiente y termina (útil para cron y pruebas)

Garantías
  - Al menos una vez: si el microservicio cae, el evento queda pendiente y se reintenta con espera creciente
    (5 s, 10 s, 20 s … hasta 1 h). El receptor deduplica por `evento_id`, así que el «al menos una vez» se vuelve «una vez».
  - Varios relevos a la vez no se pisan: cada evento se reclama con FOR UPDATE SKIP LOCKED.
  - Un evento que falla no frena a los demás.
"""
from __future__ import annotations

import argparse
import time
from collections.abc import Callable
from dataclasses import dataclass

import httpx
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.config import cargar

ESPERA_BASE_S = 5
ESPERA_MAX_S = 3600


def espera(intentos: int) -> int:
    """Segundos hasta el próximo intento tras `intentos` fallos: 5, 10, 20, 40 … tope 1 h."""
    return min(ESPERA_BASE_S * 2 ** max(intentos - 1, 0), ESPERA_MAX_S)


@dataclass
class Resultado:
    enviados: int = 0
    fallidos: int = 0


def enviar_http(evento: dict, cliente: httpx.Client | None = None) -> None:
    cfg = cargar()
    if not cfg.notificaciones_url or not cfg.servicio_token:
        raise RuntimeError("SIGRO_NOTIFICACIONES_URL y SIGRO_SERVICIO_TOKEN deben estar definidos")
    cuerpo = {
        "evento_id": f"api-{evento['id']}",
        "tipo": evento["tipo"],
        "proyecto_id": evento["proyecto_id"],
        "creado_en": evento["creado_en"].isoformat(),
        **{k: evento["payload"][k] for k in ("destinatarios", "titulo", "mensaje", "datos")},
    }
    propio = cliente is None
    c = cliente or httpx.Client(timeout=httpx.Timeout(5.0, connect=2.0))
    try:
        r = c.post(f"{cfg.notificaciones_url.rstrip('/')}/eventos", json=cuerpo, headers={"X-Servicio-Token": cfg.servicio_token})
        r.raise_for_status()
    finally:
        if propio:
            c.close()


def procesar(abrir_sesion: Callable[[], Session], enviar: Callable[[dict], None], limite: int = 100) -> Resultado:
    """Reclama y entrega eventos de uno en uno; cada resultado se confirma al instante (un fallo no deshace un envío ya hecho)."""
    res = Resultado()
    for _ in range(limite):
        with abrir_sesion() as s:
            fila = s.execute(
                text(
                    "SELECT id, tipo, proyecto_id, payload, creado_en, intentos FROM outbox.evento "
                    "WHERE enviado_en IS NULL AND siguiente_intento <= now() "
                    "ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED"
                )
            ).mappings().first()
            if fila is None:
                return res
            ev = dict(fila)
            try:
                enviar(ev)
            except Exception as e:      # cualquier fallo de entrega se reintenta: no se pierde el evento
                n = ev["intentos"] + 1
                s.execute(
                    text(
                        "UPDATE outbox.evento SET intentos = :n, ultimo_error = :e, "
                        "siguiente_intento = now() + make_interval(secs => :w) WHERE id = :i"
                    ),
                    {"n": n, "e": f"{type(e).__name__}: {e}"[:500], "w": espera(n), "i": ev["id"]},
                )
                res.fallidos += 1
            else:
                s.execute(
                    text("UPDATE outbox.evento SET enviado_en = now(), intentos = intentos + 1, ultimo_error = NULL WHERE id = :i"),
                    {"i": ev["id"]},
                )
                res.enviados += 1
            s.commit()
    return res


def main(argv: list[str] | None = None) -> int:
    from app.db import motor

    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--una-vez", action="store_true")
    ap.add_argument("--intervalo", type=float, default=3.0, help="segundos entre vueltas")
    a = ap.parse_args(argv)
    while True:
        r = procesar(lambda: Session(motor()), enviar_http)
        if r.enviados or r.fallidos:
            print(f"relevo: {r.enviados} entregados, {r.fallidos} pendientes de reintento", flush=True)
        if a.una_vez:
            return 0
        time.sleep(a.intervalo)


if __name__ == "__main__":
    raise SystemExit(main())
