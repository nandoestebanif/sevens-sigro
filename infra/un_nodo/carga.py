"""Carga de memoria del nodo único (ADR-10): 20 logins + 20 GET /obras A LA VEZ, y el pico de RSS del proceso.

    SIGRO_BASE=http://127.0.0.1:10000 SIGRO_ADMIN_USUARIO=admin SIGRO_ADMIN_CLAVE=... python infra/un_nodo/carga.py

SOLO contra un contenedor local o de CI. Nunca contra Render: Cloudflare bloquea más de 1 petición cada 5 s.

El login es lo más caro en memoria (Argon2id reserva 64 MiB por verificación): por eso van concurrentes, que es
lo que mataba el proceso. Lee el pico de /salud/servicios (`memoria_mb`, resource.getrusage del proceso único) y
sale con código 1 si alguna petición falló o si el pico supera SIGRO_CARGA_MAX_MB (300). Nunca imprime claves ni
tokens. Solo biblioteca estándar.
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE = os.environ.get("SIGRO_BASE", "http://127.0.0.1:10000").rstrip("/")
N = int(os.environ.get("SIGRO_CARGA_N", "20"))
HILOS = int(os.environ.get("SIGRO_CARGA_HILOS", "20"))
MAX_MB = float(os.environ.get("SIGRO_CARGA_MAX_MB", "300"))


def pedir(metodo: str, ruta: str, token: str | None = None, cuerpo: dict | None = None) -> tuple[int, object, float]:
    req = urllib.request.Request(f"{BASE}{ruta}", data=json.dumps(cuerpo).encode() if cuerpo else None, method=metodo)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    inicio = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            codigo, texto = r.status, r.read()
    except urllib.error.HTTPError as e:
        codigo, texto = e.code, e.read()
    except (urllib.error.URLError, ConnectionError, TimeoutError):
        codigo, texto = 0, b""
    try:
        datos = json.loads(texto or b"null")
    except ValueError:
        datos = None
    return codigo, datos, (time.perf_counter() - inicio) * 1000


def memoria() -> dict:
    _, cuerpo, _ = pedir("GET", "/salud/servicios")
    return {k: cuerpo.get(k) for k in ("memoria_mb", "memoria_actual_mb")} if isinstance(cuerpo, dict) else {}


def login() -> tuple[int, object, float]:
    return pedir("POST", "/api/v1/auth/login",
                 cuerpo={"usuario": os.environ["SIGRO_ADMIN_USUARIO"], "clave": os.environ["SIGRO_ADMIN_CLAVE"]})


def resumen(nombre: str, resultados: list[tuple[int, object, float]]) -> int:
    ms = sorted(r[2] for r in resultados)
    malos = [r[0] for r in resultados if r[0] != 200]
    print(f"{nombre}: {len(resultados) - len(malos)}/{len(resultados)} con 200 · mediana {ms[len(ms) // 2]:.0f} ms · "
          f"máx {ms[-1]:.0f} ms" + (f" · códigos malos {sorted(set(malos))}" if malos else ""), flush=True)
    return len(malos)


def main() -> int:
    antes = memoria()
    print(f"memoria antes: {antes}", flush=True)
    c, r, _ = login()
    if c != 200:
        print(f"MAL login inicial: {c}")
        return 1
    token = r["token"]
    with ThreadPoolExecutor(HILOS) as ex:
        logins = [ex.submit(login) for _ in range(N)]
        obras = [ex.submit(pedir, "GET", "/api/v1/obras", token) for _ in range(N)]
        malos = resumen(f"{N} logins", [f.result() for f in logins])
        malos += resumen(f"{N} GET /api/v1/obras", [f.result() for f in obras])
    despues = memoria()
    pico = despues.get("memoria_mb")
    print(f"memoria después: {despues}", flush=True)
    print(f"RSS máximo del proceso: {pico} MB (meta < {MAX_MB:.0f} MB)", flush=True)
    if malos:
        return 1
    return 0 if pico is not None and pico < MAX_MB else 1


if __name__ == "__main__":
    sys.exit(main())
