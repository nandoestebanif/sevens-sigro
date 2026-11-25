"""Módulo obra: sesión, usuarios, asignación, proyecto, periodos, almacenes y dispositivos.

HU-01 crear proyecto · HU-02 ampliar plazo · HU-03 periodos · HU-12 iniciar sesión ·
HU-13 asignar rol · HU-14 asignar usuarios a obras · autorización de dispositivos (R5).
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, Query
from sqlalchemy import text
from sqlalchemy.orm import Session

from app import eventos, integraciones
from app.config import cargar
from app.db import SesionPorPeticion
from app.dependencias import Actor, exigir, exigir_en_alguna_obra, usuario_autenticado
from app.errores import ErrorSigro
from app.modulos.obra import esquemas as e
from app.seguridad import clave_aceptable, crear_token, hashear, verificar
from app.sql import fila, filas

auth = APIRouter(prefix="/auth", tags=["sesión"])
usuarios = APIRouter(prefix="/usuarios", tags=["usuarios"])
obras = APIRouter(prefix="/obras", tags=["obra"])


def _obras_de(s: Session, usuario_id: int) -> list[dict]:
    return filas(
        s,
        "SELECT p.id, p.nombre, a.rol, p.estado FROM obra.asignacion a JOIN obra.proyecto p ON p.id = a.proyecto_id "
        "WHERE a.usuario_id = :u AND a.fecha_retiro IS NULL ORDER BY (p.estado <> 'en_ejecucion'), p.nombre",
        u=usuario_id,
    )


# ------------------------------------------------------------------- sesión (HU-12)
@auth.post("/login", response_model=e.SesionOut)
def iniciar_sesion(datos: e.LoginIn, s: SesionPorPeticion):
    cfg = cargar()
    u = fila(s, "SELECT * FROM obra.usuario WHERE lower(usuario) = lower(:n)", n=datos.usuario.strip())
    ahora = datetime.now(timezone.utc)
    generico = ErrorSigro(401, "credenciales_invalidas", "Usuario o contraseña incorrectos.")

    if u is not None and u["bloqueado_hasta"] and u["bloqueado_hasta"] > ahora:
        # Misma respuesta que una clave mala: no se confirma si el usuario existe.
        verificar(datos.clave, u["hash_clave"])
        raise ErrorSigro(429, "cuenta_bloqueada", "Demasiados intentos. Espera unos minutos e inténtalo de nuevo.")

    ok = verificar(datos.clave, u["hash_clave"] if u else None)
    if u is None or not ok or u["estado"] != "activo":
        if u is not None and u["estado"] == "activo":
            intentos = u["intentos_fallidos"] + 1
            bloqueo = ahora + timedelta(minutes=cfg.bloqueo_minutos) if intentos >= cfg.intentos_max else None
            s.execute(
                text("UPDATE obra.usuario SET intentos_fallidos = :i, bloqueado_hasta = :b WHERE id = :id"),
                {"i": 0 if bloqueo else intentos, "b": bloqueo, "id": u["id"]},
            )
            s.commit()          # el intento fallido debe quedar registrado aunque la respuesta sea un error
        raise generico

    if u["intentos_fallidos"] or u["bloqueado_hasta"]:
        s.execute(text("UPDATE obra.usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = :id"), {"id": u["id"]})
    token, expira = crear_token(u["id"])
    return e.SesionOut(
        token=token, expira_en=expira,
        usuario=e.UsuarioMin(id=u["id"], nombre=u["nombre"], usuario=u["usuario"]),
        obras=[e.ObraRol(**o) for o in _obras_de(s, u["id"])],
    )


@auth.get("/yo", response_model=e.YoOut)
def quien_soy(a: Actor = Depends(usuario_autenticado)):
    u = fila(a.sesion, "SELECT id, nombre, usuario FROM obra.usuario WHERE id = :i", i=a.usuario_id)
    return e.YoOut(usuario=e.UsuarioMin(**u), obras=[e.ObraRol(**o) for o in _obras_de(a.sesion, a.usuario_id)])


@auth.post("/cambiar-clave", status_code=204)
def cambiar_clave(datos: e.CambiarClaveIn, a: Actor = Depends(usuario_autenticado)):
    u = fila(a.sesion, "SELECT hash_clave FROM obra.usuario WHERE id = :i", i=a.usuario_id)
    if not verificar(datos.clave_actual, u["hash_clave"]):
        raise ErrorSigro(403, "clave_actual_incorrecta", "La contraseña actual no es correcta.")
    motivo = clave_aceptable(datos.clave_nueva)
    if motivo:
        raise ErrorSigro(422, "clave_debil", motivo)
    a.sesion.execute(text("UPDATE obra.usuario SET hash_clave = :h WHERE id = :i"), {"h": hashear(datos.clave_nueva), "i": a.usuario_id})


# ---------------------------------------------------------------------- usuarios
_COLS_USUARIO = "id, nombre, usuario, estado, creado_en, fecha_baja, dni, correo, celular"


@usuarios.post("", response_model=e.UsuarioOut, status_code=201)
def crear_usuario(datos: e.UsuarioIn, a: Actor = Depends(exigir_en_alguna_obra("usuario.crear"))):
    motivo = clave_aceptable(datos.clave)
    if motivo:
        raise ErrorSigro(422, "clave_debil", motivo)
    return fila(
        a.sesion,
        "INSERT INTO obra.usuario (nombre, usuario, hash_clave, dni, correo, celular) "
        "VALUES (:n, :u, :h, :d, :c, :t) RETURNING " + _COLS_USUARIO,
        n=datos.nombre.strip(), u=datos.usuario.strip(), h=hashear(datos.clave),
        d=datos.dni, c=datos.correo, t=datos.celular,
    )


@usuarios.get("", response_model=list[e.UsuarioOut])
def listar_usuarios(a: Actor = Depends(exigir_en_alguna_obra("usuario.consultar"))):
    return filas(a.sesion, f"SELECT {_COLS_USUARIO} FROM obra.usuario ORDER BY nombre")


@usuarios.get("/consulta-dni/{dni}", response_model=e.DniReniecOut)
def consultar_dni_usuario(dni: str, a: Actor = Depends(exigir_en_alguna_obra("usuario.crear"))):
    """Nombre de la persona tal como está en RENIEC, para registrarla sin errores (la misma consulta que tareo)."""
    if not (len(dni) == 8 and dni.isdigit()):
        raise ErrorSigro(422, "dni_invalido", "El DNI tiene 8 dígitos.")
    r = integraciones.consultar_dni(dni)
    if r is None:
        raise ErrorSigro(404, "reniec_sin_respuesta", "RENIEC no respondió: escribe el nombre a mano.")
    apellidos = " ".join(x for x in (r.apellido_paterno, r.apellido_materno) if x)
    return {"dni": r.dni, "nombres": r.nombres, "apellido_paterno": r.apellido_paterno,
            "apellido_materno": r.apellido_materno, "nombre_completo": f"{r.nombres} {apellidos}"}


@usuarios.post("/{usuario_id}/contacto", response_model=e.UsuarioOut)
def actualizar_contacto(usuario_id: int, datos: e.ContactoIn, a: Actor = Depends(exigir_en_alguna_obra("usuario.crear"))):
    """Completa o corrige DNI, correo y celular. Lo que no se envía no cambia; enviarlo vacío lo quita."""
    enviados = datos.model_fields_set
    if not enviados:
        raise ErrorSigro(422, "sin_cambios", "Envía al menos el DNI, el correo o el celular.")
    r = fila(
        a.sesion,
        "UPDATE obra.usuario SET dni = CASE WHEN :cd THEN CAST(:d AS text) ELSE dni END, "
        "correo = CASE WHEN :cc THEN CAST(:c AS text) ELSE correo END, "
        "celular = CASE WHEN :ct THEN CAST(:t AS text) ELSE celular END "
        "WHERE id = :i RETURNING " + _COLS_USUARIO,
        i=usuario_id, d=datos.dni, c=datos.correo, t=datos.celular,
        cd="dni" in enviados, cc="correo" in enviados, ct="celular" in enviados,
    )
    if r is None:
        raise ErrorSigro(404, "no_existe", "Ese usuario no existe.")
    return r


@usuarios.post("/{usuario_id}/clave-temporal", response_model=e.UsuarioOut)
def poner_clave_temporal(usuario_id: int, datos: e.ClaveTemporalIn, a: Actor = Depends(exigir_en_alguna_obra("usuario.crear"))):
    """Para quien olvidó su clave: el administrador le pone una temporal (se la entrega en persona) y la persona la
    cambia con «Cambiar mi contraseña». También quita el bloqueo por intentos fallidos."""
    if usuario_id == a.usuario_id:
        raise ErrorSigro(409, "clave_propia", "Tu propia clave cámbiala con «Cambiar mi contraseña».")
    motivo = clave_aceptable(datos.clave)
    if motivo:
        raise ErrorSigro(422, "clave_debil", motivo)
    r = fila(
        a.sesion,
        "UPDATE obra.usuario SET hash_clave = :h, intentos_fallidos = 0, bloqueado_hasta = NULL "
        "WHERE id = :i AND estado = 'activo' RETURNING " + _COLS_USUARIO,
        h=hashear(datos.clave), i=usuario_id,
    )
    if r is None:
        raise ErrorSigro(404, "no_existe", "Ese usuario no existe o está de baja.")
    return r


@usuarios.post("/{usuario_id}/baja", response_model=e.UsuarioOut)
def dar_de_baja_usuario(usuario_id: int, a: Actor = Depends(exigir_en_alguna_obra("usuario.crear"))):
    """Nada se borra, tampoco las personas (R10): la baja es un estado con fecha, y corta el acceso al instante."""
    if usuario_id == a.usuario_id:
        raise ErrorSigro(409, "baja_propia", "No puedes darte de baja a ti mismo.")
    r = fila(
        a.sesion,
        "UPDATE obra.usuario SET estado = 'baja', fecha_baja = now() WHERE id = :i AND estado = 'activo' "
        "RETURNING " + _COLS_USUARIO,
        i=usuario_id,
    )
    if r is None:
        raise ErrorSigro(404, "no_existe", "Ese usuario no existe o ya estaba de baja.")
    a.sesion.execute(
        text("UPDATE obra.asignacion SET fecha_retiro = now(), retirado_por = :a WHERE usuario_id = :u AND fecha_retiro IS NULL"),
        {"a": a.usuario_id, "u": usuario_id},
    )
    return r


# ------------------------------------------------------------------ proyecto (HU-01)
def _obra_out(s: Session, obra_id: int, rol: str | None = None) -> dict:
    p = fila(s, "SELECT id, nombre, fecha_inicio, fecha_fin, dia_corte, estado FROM obra.proyecto WHERE id = :i", i=obra_id)
    per = fila(
        s,
        "SELECT count(*) AS total, "
        "coalesce(max(numero) FILTER (WHERE current_date BETWEEN fecha_inicio AND fecha_fin), "
        "         CASE WHEN current_date < min(fecha_inicio) THEN 0 ELSE count(*) END) AS actual "
        "FROM obra.periodo WHERE proyecto_id = :i",
        i=obra_id,
    )
    p.update(semanas_totales=per["total"], semana=per["actual"], rol=rol)
    return p


@obras.get("", response_model=list[e.ObraOut])
def listar_obras(a: Actor = Depends(usuario_autenticado)):
    """Solo las obras donde la persona está asignada (R9).

    Una sola consulta para todas (antes: 1 + 2 por obra). Mismos campos y mismo cálculo que `_obra_out`."""
    return filas(
        a.sesion,
        "SELECT p.id, p.nombre, p.fecha_inicio, p.fecha_fin, p.dia_corte, p.estado, a.rol, "
        "coalesce(per.total, 0) AS semanas_totales, per.actual AS semana "
        "FROM obra.asignacion a JOIN obra.proyecto p ON p.id = a.proyecto_id "
        "LEFT JOIN LATERAL ("
        "  SELECT count(*) AS total, "
        "  coalesce(max(numero) FILTER (WHERE current_date BETWEEN fecha_inicio AND fecha_fin), "
        "           CASE WHEN current_date < min(fecha_inicio) THEN 0 ELSE count(*) END) AS actual "
        "  FROM obra.periodo WHERE proyecto_id = p.id"
        ") per ON true "
        "WHERE a.usuario_id = :u AND a.fecha_retiro IS NULL ORDER BY (p.estado <> 'en_ejecucion'), p.nombre",
        u=a.usuario_id,
    )


@obras.post("", response_model=e.ObraOut, status_code=201)
def crear_obra(datos: e.ObraIn, a: Actor = Depends(exigir_en_alguna_obra("proyecto.crear"))):
    """Los periodos nacen solos (HU-03). Quien crea la obra queda asignado a ella con su mismo rol."""
    r = fila(
        a.sesion,
        "INSERT INTO obra.proyecto (nombre, fecha_inicio, fecha_fin, dia_corte, creado_por) "
        "VALUES (:n, :i, :f, :c, :u) RETURNING id",
        n=datos.nombre.strip(), i=datos.fecha_inicio, f=datos.fecha_fin, c=datos.dia_corte, u=a.usuario_id,
    )
    a.sesion.execute(
        text("INSERT INTO obra.asignacion (usuario_id, proyecto_id, rol, asignado_por) VALUES (:u, :p, :r, :u)"),
        {"u": a.usuario_id, "p": r["id"], "r": a.rol},
    )
    return _obra_out(a.sesion, r["id"], a.rol)


@obras.get("/{obra_id}", response_model=e.ObraOut)
def ver_obra(a: Actor = Depends(exigir("proyecto.consultar"))):
    return _obra_out(a.sesion, a.obra_id, a.rol)


@obras.post("/{obra_id}/cerrar", response_model=e.ObraOut)
def cerrar_obra(a: Actor = Depends(exigir("proyecto.cerrar"))):
    """Cerrar la obra no borra nada (R10): sus datos, compras y movimientos se conservan y se siguen consultando.
    La obra pasa a «cerrada»: va al final de la lista y ya no se le amplía el plazo. NO se reabre: lo impide la base
    (obra.tg_proyecto_cambios, desde la 0001), por eso la web pide confirmarlo."""
    r = fila(a.sesion, "UPDATE obra.proyecto SET estado = 'cerrado' WHERE id = :p AND estado = 'en_ejecucion' RETURNING id",
             p=a.obra_id)
    if r is None:
        raise ErrorSigro(409, "obra_ya_cerrada", "Esta obra ya estaba cerrada.")
    return _obra_out(a.sesion, a.obra_id, a.rol)


@obras.post("/{obra_id}/ampliar-plazo", response_model=e.ObraOut)
def ampliar_plazo(datos: e.AmpliarPlazoIn, a: Actor = Depends(exigir("proyecto.ampliar_plazo"))):
    """HU-02: el plazo no se pisa; la ampliación deja rastro y genera los periodos que faltan."""
    inicio = a.sesion.execute(text("SELECT fecha_inicio FROM obra.proyecto WHERE id = :p"), {"p": a.obra_id}).scalar()
    if inicio is not None and (datos.nueva_fecha_fin - inicio).days > e.PLAZO_MAXIMO_DIAS:
        raise ErrorSigro(422, "plazo_excesivo", "El plazo de una obra no puede pasar de 10 años.")
    a.sesion.execute(
        text("SELECT obra.ampliar_plazo(:p, :f, :m, :a)"),
        {"p": a.obra_id, "f": datos.nueva_fecha_fin, "m": datos.motivo.strip(), "a": a.usuario_id},
    )
    return _obra_out(a.sesion, a.obra_id, a.rol)


@obras.get("/{obra_id}/plazo-historial", response_model=list[e.PlazoHistorialOut])
def historial_plazo(a: Actor = Depends(exigir("proyecto.consultar"))):
    return filas(
        a.sesion,
        "SELECT h.id, h.fecha_fin_anterior, h.fecha_fin_nueva, h.motivo, u.nombre AS autor, h.fecha "
        "FROM obra.proyecto_plazo_historial h JOIN obra.usuario u ON u.id = h.autor "
        "WHERE h.proyecto_id = :p ORDER BY h.fecha DESC, h.id DESC",
        p=a.obra_id,
    )


@obras.get("/{obra_id}/periodos", response_model=list[e.PeriodoOut])
def listar_periodos(a: Actor = Depends(exigir("periodo.consultar"))):
    return filas(
        a.sesion,
        "SELECT id, numero, fecha_inicio, fecha_fin, estado FROM obra.periodo WHERE proyecto_id = :p ORDER BY numero",
        p=a.obra_id,
    )


@obras.post("/{obra_id}/periodos/{numero}/cerrar", response_model=e.PeriodoOut)
def cerrar_periodo(numero: int, a: Actor = Depends(exigir("periodo.cerrar"))):
    """HT-11 D4: cierra una semana; desde ese momento solo admite correcciones con reverso."""
    p = fila(
        a.sesion,
        "UPDATE obra.periodo SET estado = 'cerrado', cerrado_en = coalesce(cerrado_en, now()), "
        "cerrado_por = coalesce(cerrado_por, :u) WHERE proyecto_id = :p AND numero = :n "
        "RETURNING id, numero, fecha_inicio, fecha_fin, estado",
        u=a.usuario_id, p=a.obra_id, n=numero,
    )
    if p is None:
        raise ErrorSigro(404, "no_existe", f"La obra no tiene la semana {numero}.")
    return p


# ---------------------------------------------------------------------- almacenes
@obras.get("/{obra_id}/almacenes", response_model=list[e.AlmacenOut])
def listar_almacenes(a: Actor = Depends(exigir("almacen.consultar"))):
    return filas(a.sesion, "SELECT id, nombre, tipo, estado FROM obra.almacen WHERE proyecto_id = :p ORDER BY nombre", p=a.obra_id)


@obras.post("/{obra_id}/almacenes", response_model=e.AlmacenOut, status_code=201)
def crear_almacen(datos: e.AlmacenIn, a: Actor = Depends(exigir("almacen.registrar"))):
    return fila(
        a.sesion,
        "INSERT INTO obra.almacen (proyecto_id, nombre, tipo) VALUES (:p, :n, :t) RETURNING id, nombre, tipo, estado",
        p=a.obra_id, n=datos.nombre.strip(), t=datos.tipo,
    )


# ---------------------------------------------------------- asignación (HU-13 · HU-14)
@obras.get("/{obra_id}/asignaciones", response_model=list[e.AsignacionOut])
def listar_asignaciones(vigentes: bool = Query(True), a: Actor = Depends(exigir("asignacion.consultar"))):
    return filas(
        a.sesion,
        "SELECT a.id, a.usuario_id, u.nombre AS usuario_nombre, u.usuario, a.rol, a.fecha_asignacion, a.fecha_retiro "
        "FROM obra.asignacion a JOIN obra.usuario u ON u.id = a.usuario_id "
        "WHERE a.proyecto_id = :p AND (NOT :v OR a.fecha_retiro IS NULL) ORDER BY u.nombre, a.id",
        p=a.obra_id, v=vigentes,
    )


@obras.post("/{obra_id}/asignaciones", response_model=e.AsignacionOut, status_code=201)
def asignar(datos: e.AsignacionIn, a: Actor = Depends(exigir("asignacion.escribir"))):
    """El rol vive junto a la obra (R9): la misma persona puede ser almacenero aquí y nada allá."""
    u = fila(a.sesion, "SELECT id, estado FROM obra.usuario WHERE id = :i", i=datos.usuario_id)
    if u is None:
        raise ErrorSigro(404, "usuario_no_existe", "Ese usuario no existe.")
    if u["estado"] != "activo":
        raise ErrorSigro(409, "usuario_de_baja", "Ese usuario está de baja.")
    r = fila(
        a.sesion,
        "INSERT INTO obra.asignacion (usuario_id, proyecto_id, rol, asignado_por) VALUES (:u, :p, :r, :a) RETURNING id",
        u=datos.usuario_id, p=a.obra_id, r=datos.rol, a=a.usuario_id,
    )
    return fila(
        a.sesion,
        "SELECT a.id, a.usuario_id, u.nombre AS usuario_nombre, u.usuario, a.rol, a.fecha_asignacion, a.fecha_retiro "
        "FROM obra.asignacion a JOIN obra.usuario u ON u.id = a.usuario_id WHERE a.id = :i",
        i=r["id"],
    )


@obras.post("/{obra_id}/asignaciones/{asignacion_id}/retiro", response_model=e.AsignacionOut)
def retirar(asignacion_id: int, a: Actor = Depends(exigir("asignacion.escribir"))):
    """No se borra: se retira con fecha. Tampoco se deja la obra sin administrador."""
    x = fila(a.sesion, "SELECT * FROM obra.asignacion WHERE id = :i AND proyecto_id = :p", i=asignacion_id, p=a.obra_id)
    if x is None:
        raise ErrorSigro(404, "no_existe", "Esa asignación no existe en esta obra.")
    if x["fecha_retiro"] is not None:
        raise ErrorSigro(409, "ya_retirada", "Esa asignación ya fue retirada.")
    if x["rol"] == "administrador":
        otros = a.sesion.execute(
            text("SELECT count(*) FROM obra.asignacion WHERE proyecto_id = :p AND rol = 'administrador' AND fecha_retiro IS NULL AND id <> :i"),
            {"p": a.obra_id, "i": asignacion_id},
        ).scalar()
        if otros == 0:
            raise ErrorSigro(409, "ultimo_administrador", "La obra no puede quedarse sin administrador.")
    a.sesion.execute(
        text("UPDATE obra.asignacion SET fecha_retiro = now(), retirado_por = :a WHERE id = :i"),
        {"a": a.usuario_id, "i": asignacion_id},
    )
    return fila(
        a.sesion,
        "SELECT a.id, a.usuario_id, u.nombre AS usuario_nombre, u.usuario, a.rol, a.fecha_asignacion, a.fecha_retiro "
        "FROM obra.asignacion a JOIN obra.usuario u ON u.id = a.usuario_id WHERE a.id = :i",
        i=asignacion_id,
    )


# ------------------------------------------------------------------- dispositivos (R5)
_SQL_DISP = (
    "SELECT d.id, d.usuario_id, u.nombre AS usuario_nombre, d.identificador, d.estado, d.solicitado_en, "
    "d.fecha_autorizacion, d.fecha_revocacion, d.motivo_revocacion "
    "FROM obra.dispositivo d JOIN obra.usuario u ON u.id = d.usuario_id "
)


@obras.get("/{obra_id}/dispositivos", response_model=list[e.DispositivoOut])
def listar_dispositivos(a: Actor = Depends(exigir("dispositivo.consultar"))):
    return filas(a.sesion, _SQL_DISP + "WHERE d.proyecto_id = :p ORDER BY d.id", p=a.obra_id)


@obras.get("/{obra_id}/dispositivos/mios", response_model=list[e.DispositivoOut])
def mis_dispositivos(a: Actor = Depends(exigir("dispositivo.solicitar"))):
    """HU-15: el almacenero o el SSOMA ve SOLO sus celulares en esta obra (pendiente, activo o revocado), para que la app
    sepa si el suyo ya está autorizado. Listar los de todos sigue siendo del administrador y del residente."""
    return filas(a.sesion, _SQL_DISP + "WHERE d.proyecto_id = :p AND d.usuario_id = :u ORDER BY d.id", p=a.obra_id, u=a.usuario_id)


@obras.post("/{obra_id}/dispositivos", response_model=e.DispositivoOut, status_code=201)
def solicitar_dispositivo(datos: e.DispositivoIn, a: Actor = Depends(exigir("dispositivo.solicitar"))):
    """El almacenero o el SSOMA pide autorizar su celular. Queda pendiente hasta que un administrador lo autorice."""
    r = fila(
        a.sesion,
        "INSERT INTO obra.dispositivo (proyecto_id, usuario_id, identificador) VALUES (:p, :u, :i) RETURNING id",
        p=a.obra_id, u=a.usuario_id, i=datos.identificador.strip(),
    )
    eventos.publicar(
        a.sesion, "dispositivo.solicitado", a.obra_id,
        eventos.destinatarios_con_rol(a.sesion, a.obra_id, ["administrador"], excepto=a.usuario_id),
        "Dispositivo por autorizar",
        f"{a.nombre} pidió autorizar su celular en {eventos.nombre_obra(a.sesion, a.obra_id)}.",
        {"dispositivo_id": r["id"], "usuario_id": a.usuario_id},
    )
    return fila(a.sesion, _SQL_DISP + "WHERE d.id = :i", i=r["id"])


def _dispositivo_de_la_obra(a: Actor, dispositivo_id: int) -> dict:
    d = fila(a.sesion, "SELECT * FROM obra.dispositivo WHERE id = :i AND proyecto_id = :p", i=dispositivo_id, p=a.obra_id)
    if d is None:
        raise ErrorSigro(404, "no_existe", "Ese dispositivo no existe en esta obra.")
    return d


@obras.post("/{obra_id}/dispositivos/{dispositivo_id}/autorizar", response_model=e.DispositivoOut)
def autorizar_dispositivo(dispositivo_id: int, a: Actor = Depends(exigir("dispositivo.autorizar"))):
    """La regla de «un dispositivo por responsable, máximo dos por obra» la sostiene el motor (disparador + bloqueo)."""
    d = _dispositivo_de_la_obra(a, dispositivo_id)
    if d["estado"] != "pendiente":
        raise ErrorSigro(409, "estado_invalido", f"Solo se autoriza un dispositivo pendiente (este está {d['estado']}).")
    a.sesion.execute(
        text("UPDATE obra.dispositivo SET estado = 'activo', autorizado_por = :a, fecha_autorizacion = now() WHERE id = :i"),
        {"a": a.usuario_id, "i": dispositivo_id},
    )
    eventos.publicar(
        a.sesion, "dispositivo.autorizado", a.obra_id, [d["usuario_id"]], "Tu dispositivo fue autorizado",
        f"Ya puedes registrar desde tu celular en {eventos.nombre_obra(a.sesion, a.obra_id)}.", {"dispositivo_id": dispositivo_id},
    )
    return fila(a.sesion, _SQL_DISP + "WHERE d.id = :i", i=dispositivo_id)


@obras.post("/{obra_id}/dispositivos/{dispositivo_id}/revocar", response_model=e.DispositivoOut)
def revocar_dispositivo(dispositivo_id: int, datos: e.RevocarIn, a: Actor = Depends(exigir("dispositivo.autorizar"))):
    d = _dispositivo_de_la_obra(a, dispositivo_id)
    if d["estado"] == "revocado":
        raise ErrorSigro(409, "estado_invalido", "Ese dispositivo ya estaba revocado.")
    a.sesion.execute(
        text(
            "UPDATE obra.dispositivo SET estado = 'revocado', revocado_por = :a, fecha_revocacion = now(), "
            "motivo_revocacion = :m WHERE id = :i"
        ),
        {"a": a.usuario_id, "m": datos.motivo.strip(), "i": dispositivo_id},
    )
    eventos.publicar(
        a.sesion, "dispositivo.revocado", a.obra_id, [d["usuario_id"]], "Tu dispositivo fue revocado",
        f"Motivo: {datos.motivo.strip()}", {"dispositivo_id": dispositivo_id},
    )
    return fila(a.sesion, _SQL_DISP + "WHERE d.id = :i", i=dispositivo_id)


routers = [auth, usuarios, obras]
