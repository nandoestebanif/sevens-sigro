"""Paginación OPCIONAL y compatible hacia atrás para las listas que crecen con el uso de la obra.

Sin `limite` la consulta y la respuesta son exactamente las de siempre (la web y la app no cambian).
Con `limite` (y opcionalmente `desde`, un desplazamiento) se devuelve esa página; si quedan más filas, la respuesta
trae la cabecera `X-Siguiente-Desde` con el valor de `desde` para pedir la siguiente. El cuerpo sigue siendo una lista.
"""
from __future__ import annotations

from dataclasses import dataclass

from fastapi import Query, Response

CABECERA = "X-Siguiente-Desde"


@dataclass(frozen=True)
class Pagina:
    limite: int | None
    desde: int

    @property
    def sql(self) -> str:
        """Pide UNA fila de más para saber si hay otra página sin un count(*) aparte."""
        return "" if self.limite is None else f" LIMIT {self.limite + 1} OFFSET {self.desde}"

    def recortar(self, rows: list, respuesta: Response) -> list:
        if self.limite is None:
            return rows
        if len(rows) > self.limite:
            respuesta.headers[CABECERA] = str(self.desde + self.limite)
            return rows[: self.limite]
        return rows

    def aplicar(self, rows: list, respuesta: Response) -> list:
        """Para listas que ya se arman en memoria (ordenadas en Python): corta la página ahí."""
        if self.limite is None:
            return rows
        return self.recortar(rows[self.desde: self.desde + self.limite + 1], respuesta)


def paginacion(maximo: int = 500):
    """Dependencia: `limite` (1..maximo) y `desde` (>= 0). Ambos opcionales."""

    def _pagina(
        limite: int | None = Query(None, ge=1, le=maximo, description="Opcional: tamaño de página. Sin él, la lista completa."),
        desde: int = Query(0, ge=0, description="Opcional: cuántas filas saltar (usa X-Siguiente-Desde de la respuesta anterior)."),
    ) -> Pagina:
        return Pagina(limite, desde)

    return _pagina
