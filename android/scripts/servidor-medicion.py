"""Servidor falso SOLO para medir el rendimiento de la app en el emulador (docs/rendimiento-app.md).

No es la API de SIGRO ni la reemplaza: responde lo mínimo para pasar de «Entrar» a «Inicio» y
«Registrar salida» sin depender de Render ni de una cuenta real. Los insumos son [marcador], no datos
de la obra. El login tarda LATENCIA segundos a propósito, para que se vea «el plano se dibuja».

    python -I android/scripts/servidor-medicion.py 8765      # el emulador lo ve en http://10.0.2.2:8765
"""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LATENCIA = 2.5
OBRA = {"id": 1, "nombre": "[obra de medición]", "rol": "almacenero"}
USUARIO = {"id": 1, "nombre": "[almacenero de medición]", "usuario": "medicion"}
INSUMOS = [
    {"id": i, "codigo": f"MED{i:04d}", "descripcion": f"[insumo de medición {i}]",
     "unidad_s10": "und", "unidad_compra": "und", "tipo": "material", "activo": True}
    for i in range(1, 241)  # del orden de los 237 materiales del S10
]


class Manejador(BaseHTTPRequestHandler):
    def _responder(self, codigo: int, cuerpo) -> None:
        datos = json.dumps(cuerpo).encode()
        self.send_response(codigo)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(datos)))
        self.end_headers()
        self.wfile.write(datos)

    def do_POST(self) -> None:
        largo = int(self.headers.get("Content-Length", 0))
        self.rfile.read(largo)
        if self.path.endswith("/auth/login"):
            time.sleep(LATENCIA)
            self._responder(200, {"token": "medicion", "expira_en": 86400, "usuario": USUARIO, "obras": [OBRA]})
        elif "/dispositivos" in self.path:
            self._responder(201, {"id": 1, "usuario_id": 1, "identificador": "x", "estado": "pendiente"})
        else:
            self._responder(404, {"codigo": "no_existe", "mensaje": "No disponible en el servidor de medición"})

    def do_GET(self) -> None:
        if self.path.startswith("/api/v1/auth/yo"):
            self._responder(200, {"usuario": USUARIO, "obras": [OBRA]})
        elif self.path.startswith("/api/v1/catalogo/insumos"):
            self._responder(200, INSUMOS)
        elif "/dispositivos" in self.path:
            self._responder(200, [])
        else:
            self._responder(404, {"codigo": "no_existe", "mensaje": "No disponible en el servidor de medición"})

    def log_message(self, *args) -> None:  # silencioso
        pass


if __name__ == "__main__":
    puerto = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    ThreadingHTTPServer(("0.0.0.0", puerto), Manejador).serve_forever()
