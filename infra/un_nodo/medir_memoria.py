"""Pico de memoria del nodo único SIN Docker: levanta el lanzador con Python, corre la carga y mide el RSS con psutil.

    pip install psutil
    SIGRO_DB_OBRAS=... (las 6 cadenas, o 7 con SIGRO_DB_RELEVO) SIGRO_JWT_SECRET=... SIGRO_SERVICIO_TOKEN=... \
    SIGRO_ADMIN_USUARIO=admin SIGRO_ADMIN_CLAVE=... python infra/un_nodo/medir_memoria.py

- Lanza `infra/un_nodo/un_proceso.py` como proceso hijo (con `SIGRO_UN_NODO_API` y `SIGRO_UN_NODO_RAIZ` apuntando a
  este repositorio) y mide cada 50 ms el RSS del proceso y de sus hijos, si los hubiera (deben ser 0).
- Cuando el gateway responde, corre `infra/un_nodo/carga.py` (20 logins + 20 GET /obras a la vez) contra él.
- Imprime el RSS en reposo, el pico durante la carga y la meta (SIGRO_CARGA_MAX_MB, 300). Código 1 si no se cumple.
- En Windows el RSS es el «working set»: comparable, no idéntico, al de Linux. La medida que vale para Render es la
  del job `un-nodo` de CI (Linux, contenedor con 512 MB) y, ya desplegado, `memoria_mb` de /salud/servicios.

Nunca imprime cadenas de conexión, claves ni tokens.
"""
from __future__ import annotations

import os
import subprocess
import sys
import threading
import time
import urllib.request
from pathlib import Path

import psutil

RAIZ = Path(__file__).resolve().parents[2]
PUERTO = int(os.environ.get("PORT", "10000"))
MAX_MB = float(os.environ.get("SIGRO_CARGA_MAX_MB", "300"))


class Muestreo:
    def __init__(self, pid: int) -> None:
        self.proceso = psutil.Process(pid)
        self.pico = 0.0
        self.max_hijos = 0
        self.ultimo = 0.0
        self._parar = threading.Event()
        self._hilo = threading.Thread(target=self._medir, daemon=True)

    def rss_mb(self) -> tuple[float, int]:
        try:
            hijos = self.proceso.children(recursive=True)
            total = self.proceso.memory_info().rss
            for h in hijos:
                try:
                    total += h.memory_info().rss
                except psutil.Error:
                    pass
            return total / 2**20, len(hijos)
        except psutil.Error:
            return 0.0, 0

    def _medir(self) -> None:
        while not self._parar.is_set():
            mb, n = self.rss_mb()
            self.ultimo, self.pico, self.max_hijos = mb, max(self.pico, mb), max(self.max_hijos, n)
            time.sleep(0.05)

    def empezar(self) -> None:
        self._hilo.start()

    def reiniciar_pico(self) -> None:
        self.pico = self.ultimo

    def parar(self) -> None:
        self._parar.set()
        self._hilo.join()


def esperar_gateway(p: subprocess.Popen, limite_s: float = 90) -> None:
    fin = time.monotonic() + limite_s
    while time.monotonic() < fin:
        if p.poll() is not None:
            raise SystemExit(f"el lanzador terminó al arrancar (código {p.returncode})")
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{PUERTO}/salud", timeout=2):
                return
        except OSError:
            time.sleep(0.5)
    raise SystemExit("el gateway no respondió a tiempo")


def main() -> int:
    entorno = {**os.environ, "PORT": str(PUERTO), "SIGRO_UN_NODO_API": str(RAIZ / "api"),
               "SIGRO_UN_NODO_RAIZ": str(RAIZ), "PYTHONIOENCODING": "utf-8"}
    log = open(os.environ.get("SIGRO_MEDIR_LOG", os.devnull), "w", encoding="utf-8")
    p = subprocess.Popen([sys.executable, str(RAIZ / "infra" / "un_nodo" / "un_proceso.py")], env=entorno,
                         stdout=log, stderr=subprocess.STDOUT)
    m = Muestreo(p.pid)
    m.empezar()
    try:
        esperar_gateway(p)
        time.sleep(3)
        reposo = m.ultimo
        print(f"RSS en reposo (todas las apps cargadas): {reposo:.1f} MB", flush=True)
        m.reiniciar_pico()
        carga = subprocess.run([sys.executable, str(RAIZ / "infra" / "un_nodo" / "carga.py")],
                               env={**entorno, "SIGRO_BASE": f"http://127.0.0.1:{PUERTO}",
                                    "SIGRO_CARGA_MAX_MB": "100000"})   # la meta se juzga aquí, con psutil
        time.sleep(1)
    finally:
        m.parar()
        p.terminate()
        try:
            p.wait(timeout=15)
        except subprocess.TimeoutExpired:
            p.kill()
    print(f"RSS pico durante la carga (proceso + {m.max_hijos} hijos): {m.pico:.1f} MB · meta < {MAX_MB:.0f} MB",
          flush=True)
    return 0 if carga.returncode == 0 and m.pico < MAX_MB and m.max_hijos == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
