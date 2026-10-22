# ─────────────────────────────────────────────────────────────────────────────
# SIGRO · microservicio de integraciones
# Responsable: Fernando Istaña
#
# Regla de arquitectura (ADR-01): todo lo que depende de terceros vive aquí.
# Este servicio NO escribe en la base de datos de la API: devuelve datos
# normalizados y la API decide qué guardar.
#
# Endpoints: consulta de RUC contra SUNAT y de DNI contra RENIEC, ambos a
# través de Factiliza (https://docs.factiliza.com) con el MISMO token del plan
# gratuito. El token es personal y NUNCA se escribe en el código ni se sube al
# repositorio: vive en la variable de entorno FACTILIZA_TOKEN (ver LEEME).
#
# Si Factiliza no responde, devolvemos 503 y quien llama sigue sin bloquear:
# el proveedor queda «sin validar» (HU-31) y el trabajador se registra con el
# nombre escrito a mano (HU-62). Un servicio externo caído nunca detiene la obra.
# ─────────────────────────────────────────────────────────────────────────────
import os
import re
import time

import httpx
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from s10 import leer_relacion_de_insumos

app = FastAPI(
    title="SIGRO · integraciones",
    description="Consulta de RUC en SUNAT y de DNI en RENIEC (vía Factiliza), e importación del S10.",
    version="0.2.0",
)

# La web de desarrollo corre en otro puerto, así que el navegador exige CORS.
# Solo los orígenes locales del equipo; en producción se reemplaza por el dominio real.
app.add_middleware(
    CORSMiddleware,
    # cualquier puerto de localhost (vite dev y preview cambian de puerto);
    # en producción se reemplaza por el dominio real de la web
    allow_origin_regex=r"http://localhost:\d+",
    allow_methods=["GET", "POST"],  # POST: la importación del S10 sube el archivo
    allow_headers=["*"],
)

FACTILIZA_URL = "https://api.factiliza.com/v1/ruc/info/{ruc}"
FACTILIZA_DNI_URL = "https://api.factiliza.com/v1/dni/info/{dni}"
TTL_SEGUNDOS = 60 * 60 * 24  # lo consultado hoy no se vuelve a consultar hoy (cuida el plan gratuito)
_cache: dict[str, tuple[float, "RucNormalizado"]] = {}
_cache_dni: dict[str, tuple[float, "DniNormalizado"]] = {}


async def _consultar_factiliza(url: str) -> dict:
    """Llama a Factiliza con el token del entorno y devuelve el bloque data.
    Toda la política común vive aquí: sin token → 503, upstream caído → 503,
    documento mal formado para Factiliza → 422."""
    token = os.environ.get("FACTILIZA_TOKEN")
    if not token:
        raise HTTPException(503, "FACTILIZA_TOKEN no está configurado.")
    try:
        async with httpx.AsyncClient(timeout=8.0) as cliente:
            r = await cliente.get(url, headers={"Authorization": f"Bearer {token}"})
    except httpx.HTTPError:
        raise HTTPException(503, "Factiliza no respondió; reintentar después.")
    if r.status_code != 200:
        codigo = 422 if r.status_code == 400 else 503
        raise HTTPException(codigo, f"Factiliza respondió {r.status_code}.")
    cuerpo = r.json()
    datos = cuerpo.get("data") or {}
    if not cuerpo.get("success") or not datos:
        raise HTTPException(503, "Factiliza devolvió una respuesta sin datos.")
    return datos


class RucNormalizado(BaseModel):
    """Lo que la API de SIGRO necesita saber de un RUC, y nada más.
    Si Factiliza cambia su formato, se toca este archivo y ningún otro."""

    ruc: str
    razon_social: str
    estado_sunat: str      # ACTIVO, SUSPENSION TEMPORAL, BAJA…
    condicion: str         # HABIDO / NO HABIDO
    direccion: str | None
    apto_para_comprar: bool  # ACTIVO y HABIDO


class DniNormalizado(BaseModel):
    """Lo que SIGRO necesita de un DNI para registrar a un trabajador (HU-62):
    el nombre tal como está en RENIEC, para no escribirlo con errores."""

    dni: str
    nombres: str
    apellido_paterno: str
    apellido_materno: str
    nombre_completo: str


@app.get("/salud")
def salud() -> dict:
    return {
        "servicio": "integraciones",
        "factiliza_configurado": bool(os.environ.get("FACTILIZA_TOKEN")),
    }


@app.get("/ruc/{ruc}", response_model=RucNormalizado)
async def consultar_ruc(ruc: str) -> RucNormalizado:
    if not re.fullmatch(r"\d{11}", ruc):
        raise HTTPException(422, "El RUC debe tener 11 dígitos.")

    en_cache = _cache.get(ruc)
    if en_cache and time.time() - en_cache[0] < TTL_SEGUNDOS:
        return en_cache[1]

    datos = await _consultar_factiliza(FACTILIZA_URL.format(ruc=ruc))

    estado = (datos.get("estado") or "").strip().upper()
    condicion = (datos.get("condicion") or "").strip().upper()
    normalizado = RucNormalizado(
        ruc=str(datos.get("numero") or ruc),
        razon_social=(datos.get("nombre_o_razon_social") or "").strip(),
        estado_sunat=estado or "DESCONOCIDO",
        condicion=condicion or "DESCONOCIDA",
        direccion=(datos.get("direccion_completa") or None),
        apto_para_comprar=(estado == "ACTIVO" and condicion == "HABIDO"),
    )
    _cache[ruc] = (time.time(), normalizado)
    return normalizado


@app.get("/dni/{dni}", response_model=DniNormalizado)
async def consultar_dni(dni: str) -> DniNormalizado:
    """HU-62: con el DNI del trabajador se trae su nombre del padrón de RENIEC,
    para registrarlo tal como figura y no como suene en obra. Si el servicio no
    responde, el nombre se escribe a mano: el registro nunca se bloquea."""
    if not re.fullmatch(r"\d{8}", dni):
        raise HTTPException(422, "El DNI debe tener 8 dígitos.")

    en_cache = _cache_dni.get(dni)
    if en_cache and time.time() - en_cache[0] < TTL_SEGUNDOS:
        return en_cache[1]

    datos = await _consultar_factiliza(FACTILIZA_DNI_URL.format(dni=dni))

    nombres = (datos.get("nombres") or "").strip()
    paterno = (datos.get("apellido_paterno") or "").strip()
    materno = (datos.get("apellido_materno") or "").strip()
    completo = (datos.get("nombre_completo") or f"{nombres} {paterno} {materno}").strip()
    if not completo:
        raise HTTPException(503, "RENIEC (vía Factiliza) devolvió un registro sin nombre.")

    normalizado = DniNormalizado(
        dni=str(datos.get("numero") or dni),
        nombres=nombres,
        apellido_paterno=paterno,
        apellido_materno=materno,
        nombre_completo=completo,
    )
    _cache_dni[dni] = (time.time(), normalizado)
    return normalizado


# ── HU-07 · importar el catálogo de insumos desde el S10 ─────────────────────

class InsumoImportado(BaseModel):
    codigo: str
    descripcion: str
    unidad: str
    tipo: str  # mano_obra · material · equipo · subcontrato
    cantidad: float
    precio: float
    parcial: float
    accion: str  # crear | actualizar (según los códigos que ya existen en la API)
    advertencia: str | None


class ResumenImportacion(BaseModel):
    leidos: int
    a_crear: int
    a_actualizar: int
    fallidos: int
    por_tipo: dict[str, int]
    parcial_total: float


class ResultadoImportacion(BaseModel):
    obra: str | None
    subpresupuesto: str | None
    fecha: str | None
    resumen: ResumenImportacion
    insumos: list[InsumoImportado]
    fallidos: list[dict]


@app.post("/s10/relacion-insumos", response_model=ResultadoImportacion)
async def importar_relacion_de_insumos(
    archivo: UploadFile = File(..., description="La «Relación de insumos» exportada del S10 (PDF)"),
    codigos_existentes: str = Form("", description="Códigos que la API ya tiene, separados por coma: esos se marcan «actualizar», no «crear»"),
) -> ResultadoImportacion:
    """HU-07: lee la Relación de insumos del S10 y devuelve los insumos
    normalizados con la acción que corresponde a cada uno. Este servicio no
    escribe en la base (ADR-01): la API aplica crear/actualizar con el código
    como clave, por eso repetir la importación no duplica — y los saldos no se
    tocan jamás, porque el catálogo no es el almacén."""
    if not (archivo.filename or "").lower().endswith(".pdf"):
        raise HTTPException(422, "Se espera el PDF exportado del S10.")

    contenido = await archivo.read()
    # seguridad: la Relación real pesa <1 MB; un archivo gigante no es un S10
    # y solo serviría para tumbar el servicio mientras lo parsea
    if len(contenido) > 15 * 1024 * 1024:
        raise HTTPException(413, "El archivo supera los 15 MB; la Relación de insumos del S10 pesa menos de 1 MB.")
    try:
        lectura = leer_relacion_de_insumos(contenido)
    except Exception:
        raise HTTPException(422, "El archivo no se pudo leer como un PDF del S10.")

    if not lectura.insumos:
        raise HTTPException(
            422,
            "El PDF no trae filas de insumos reconocibles: ¿es la «Relación de insumos» "
            "(precios y cantidades de recursos requeridos por tipo)?",
        )

    existentes = {c.strip() for c in codigos_existentes.split(",") if c.strip()}
    insumos = [
        InsumoImportado(
            codigo=i.codigo, descripcion=i.descripcion, unidad=i.unidad, tipo=i.tipo,
            cantidad=i.cantidad, precio=i.precio, parcial=i.parcial,
            accion="actualizar" if i.codigo in existentes else "crear",
            advertencia=i.advertencia,
        )
        for i in lectura.insumos
    ]
    por_tipo: dict[str, int] = {}
    for i in insumos:
        por_tipo[i.tipo] = por_tipo.get(i.tipo, 0) + 1

    return ResultadoImportacion(
        obra=lectura.obra,
        subpresupuesto=lectura.subpresupuesto,
        fecha=lectura.fecha,
        resumen=ResumenImportacion(
            leidos=len(insumos),
            a_crear=sum(1 for i in insumos if i.accion == "crear"),
            a_actualizar=sum(1 for i in insumos if i.accion == "actualizar"),
            fallidos=len(lectura.fallidos),
            por_tipo=por_tipo,
            parcial_total=round(sum(i.parcial for i in insumos), 2),
        ),
        insumos=insumos,
        fallidos=lectura.fallidos,
    )
