"""Invariantes que valen para TODO el esquema y permisos del usuario de la aplicación (R1, R2, R9, R10).

Estas pruebas no miran una tabla: miran el catálogo de PostgreSQL. Si mañana alguien agrega una
tabla sin protegerla contra el borrado, la suite falla aunque nadie haya escrito una prueba
para esa tabla.
"""
from datetime import date

import pytest

from pruebas.conftest import RESTRICT, SIN_PERMISO, como_aplicacion, rechaza

ESQUEMAS = ("obra", "catalogo", "compras", "tareo", "outbox")
# Libros: se inserta y se lee. Nunca se actualiza.
LIBROS = (
    "obra.proyecto_plazo_historial", "tareo.jornada", "compras.compra_detalle",
    "compras.compra_adjunto", "compras.solicitud_linea", "compras.solicitud_correlativo",
)


def tablas(db):
    return [f"{r['table_schema']}.{r['table_name']}" for r in db.execute(
        "SELECT table_schema, table_name FROM information_schema.tables "
        "WHERE table_type = 'BASE TABLE' AND table_schema = ANY(%s) ORDER BY 1, 2", (list(ESQUEMAS),))]


# ============================================================ invariantes del esquema entero
class TestInvariantes:
    def test_hay_tablas_que_revisar(self, db):
        assert len(tablas(db)) >= 17

    def test_toda_tabla_rechaza_delete_y_truncate_con_un_disparador(self, db):
        """R10: ninguna tabla admite DELETE ni TRUNCATE, ni siquiera el dueño."""
        sin_delete, sin_truncate = [], []
        for t in tablas(db):
            fila = db.execute(
                "SELECT bool_or(tgtype & 1 = 1 AND tgtype & 8 = 8) AS d, bool_or(tgtype & 32 = 32) AS t "
                "FROM pg_trigger WHERE tgrelid = %s::regclass AND NOT tgisinternal", (t,)).fetchone()
            if not fila["d"]:
                sin_delete.append(t)
            if not fila["t"]:
                sin_truncate.append(t)
        assert sin_delete == [], f"tablas que admiten DELETE: {sin_delete}"
        assert sin_truncate == [], f"tablas que admiten TRUNCATE: {sin_truncate}"

    def test_los_libros_rechazan_tambien_update(self, db):
        for t in LIBROS:
            if t == "compras.solicitud_correlativo":
                continue    # es un contador: lo actualiza solo su función; se prueba en test_permisos
            fila = db.execute(
                "SELECT bool_or(tgtype & 1 = 1 AND tgtype & 16 = 16) AS u FROM pg_trigger "
                "WHERE tgrelid = %s::regclass AND NOT tgisinternal", (t,)).fetchone()
            assert fila["u"], f"{t} es un libro y admite UPDATE"

    def test_ninguna_tabla_guarda_un_saldo(self, db):
        """R2: el saldo se deriva de los movimientos; no existe como columna."""
        cols = db.execute(
            "SELECT table_schema, table_name, column_name FROM information_schema.columns "
            "WHERE table_schema = ANY(%s) AND column_name ILIKE '%%saldo%%'", (list(ESQUEMAS),)).fetchall()
        assert cols == []

    def test_toda_tabla_tiene_clave_primaria(self, db):
        for t in tablas(db):
            esquema, nombre = t.split(".")
            pk = db.execute(
                "SELECT count(*) AS n FROM information_schema.table_constraints "
                "WHERE table_schema=%s AND table_name=%s AND constraint_type='PRIMARY KEY'", (esquema, nombre)).fetchone()["n"]
            assert pk == 1, f"{t} no tiene clave primaria"

    def test_ninguna_contrasena_en_texto_plano_es_posible(self, db):
        fila = db.execute(
            "SELECT pg_get_constraintdef(oid) AS d FROM pg_constraint WHERE conname = 'ck_usuario_hash_clave'").fetchone()
        assert "argon2" in fila["d"]

    def test_las_claves_foraneas_cruzan_solo_hacia_obra_o_catalogo(self, db):
        """Sentido de las dependencias entre módulos: nada en obra depende de compras o tareo."""
        filas = db.execute(
            "SELECT DISTINCT cn.nspname AS desde, rn.nspname AS hacia "
            "FROM pg_constraint c JOIN pg_class cc ON cc.oid = c.conrelid JOIN pg_namespace cn ON cn.oid = cc.relnamespace "
            "JOIN pg_class rc ON rc.oid = c.confrelid JOIN pg_namespace rn ON rn.oid = rc.relnamespace "
            "WHERE c.contype = 'f' AND cn.nspname = ANY(%s) AND rn.nspname <> cn.nspname", (list(ESQUEMAS),)).fetchall()
        dependencias = {(r["desde"], r["hacia"]) for r in filas}
        permitidas = {("catalogo", "obra"), ("tareo", "obra"), ("compras", "obra"), ("compras", "catalogo"), ("outbox", "obra")}
        assert dependencias <= permitidas, f"dependencias no previstas: {dependencias - permitidas}"
        assert not any(desde == "obra" for desde, _ in dependencias)


# ============================================================== permisos del usuario de la aplicación
class TestPermisosDeLaAplicacion:
    def test_el_usuario_de_la_aplicacion_no_puede_borrar_ni_vaciar_nada(self, db):
        for t in tablas(db):
            for priv in ("DELETE", "TRUNCATE"):
                assert not db.execute("SELECT has_table_privilege('sigro_app', %s, %s) AS p", (t, priv)).fetchone()["p"], f"{t} {priv}"

    @pytest.mark.parametrize("tabla", LIBROS)
    def test_en_un_libro_la_aplicacion_no_puede_actualizar_ninguna_columna(self, db, tabla):
        esquema, nombre = tabla.split(".")
        columnas = [r["column_name"] for r in db.execute(
            "SELECT column_name FROM information_schema.columns WHERE table_schema=%s AND table_name=%s", (esquema, nombre))]
        for col in columnas:
            assert not db.execute("SELECT has_column_privilege('sigro_app', %s, %s, 'UPDATE') AS p", (tabla, col)).fetchone()["p"], f"{tabla}.{col}"

    @pytest.mark.parametrize("tabla,columna", [
        ("obra.proyecto", "fecha_fin"), ("obra.proyecto", "fecha_inicio"), ("obra.periodo", "fecha_fin"),
        ("obra.asignacion", "rol"), ("obra.dispositivo", "identificador"), ("catalogo.insumo", "codigo"),
        ("compras.compra", "total"), ("compras.compra", "base_imponible"), ("compras.proveedor", "ruc"),
        ("compras.solicitud", "numero"), ("tareo.trabajador", "documento"), ("tareo.trabajador", "fecha_ingreso"),
    ])
    def test_columnas_que_la_aplicacion_nunca_edita(self, db, tabla, columna):
        assert not db.execute("SELECT has_column_privilege('sigro_app', %s, %s, 'UPDATE') AS p", (tabla, columna)).fetchone()["p"]

    @pytest.mark.parametrize("sql", [
        "UPDATE obra.proyecto SET fecha_fin = '2030-01-01'",
        "INSERT INTO obra.periodo (proyecto_id, numero, fecha_inicio, fecha_fin) VALUES (1, 99, '2030-01-01', '2030-01-07')",
        "INSERT INTO obra.proyecto_plazo_historial (proyecto_id, fecha_fin_anterior, fecha_fin_nueva, motivo, autor) VALUES (1, '2030-01-01', '2030-02-01', 'x', 1)",
        "INSERT INTO compras.solicitud_correlativo (proyecto_id, ultimo) VALUES (1, 50)",
        "UPDATE compras.solicitud_correlativo SET ultimo = 0",
        "UPDATE tareo.jornada SET horas_normales = 0",
        "DELETE FROM compras.compra",
        "UPDATE catalogo.insumo SET codigo = 'otro'",
        "UPDATE compras.proveedor SET ruc = '20606456892'",
    ])
    def test_estas_operaciones_se_rechazan_por_falta_de_permiso(self, db, sql):
        como_aplicacion(db)
        with rechaza(db, SIN_PERMISO):
            db.execute(sql)


class TestFlujoCompletoConElUsuarioDeLaAplicacion:
    """Si los permisos mínimos fueran demasiado mínimos, la API no podría trabajar. Se comprueba."""

    def test_una_obra_de_punta_a_punta_sin_privilegios_de_dueno(self, db, f):
        como_aplicacion(db)
        c = db.execute
        adm = c("INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES ('Adm', 'adm_app', %s) RETURNING id",
                ("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA",)).fetchone()["id"]
        res, alm = (c("INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES (%s, %s, %s) RETURNING id",
                      (n, n.lower() + "_app", "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA")).fetchone()["id"] for n in ("Res", "Alm"))
        p = c("INSERT INTO obra.proyecto (nombre, fecha_inicio, fecha_fin, creado_por) VALUES ('Obra App', '2026-10-19', '2026-11-29', %s) RETURNING id",
              (adm,)).fetchone()["id"]
        assert c("SELECT count(*) AS n FROM obra.periodo WHERE proyecto_id=%s", (p,)).fetchone()["n"] == 6   # el trigger los generó
        c("INSERT INTO obra.asignacion (usuario_id, proyecto_id, rol, asignado_por) VALUES (%s,%s,'residente',%s)", (res, p, adm))
        c("INSERT INTO obra.asignacion (usuario_id, proyecto_id, rol, asignado_por) VALUES (%s,%s,'almacenero',%s)", (alm, p, adm))
        d = c("INSERT INTO obra.dispositivo (proyecto_id, usuario_id, identificador) VALUES (%s,%s,'android-app-0001') RETURNING id", (p, alm)).fetchone()["id"]
        c("UPDATE obra.dispositivo SET estado='activo', autorizado_por=%s, fecha_autorizacion=now() WHERE id=%s", (adm, d))
        c("INSERT INTO obra.almacen (proyecto_id, nombre, tipo) VALUES (%s, 'Almacén central', 'central')", (p,))

        t = c("INSERT INTO tareo.trabajador (proyecto_id, nombre, documento, categoria, cuadrilla, fecha_ingreso, registrado_por) "
              "VALUES (%s,'Juan Quispe','45678912','oficial','C1','2026-10-19',%s) RETURNING id", (p, alm)).fetchone()["id"]
        j = c("INSERT INTO tareo.jornada (proyecto_id, trabajador_id, fecha, estado, horas_normales, categoria, periodo_id, registrado_por) "
              "VALUES (%s,%s,'2026-10-20','presente',8,'peon',1,%s) RETURNING id", (p, t, alm)).fetchone()["id"]
        c("INSERT INTO tareo.jornada (proyecto_id, trabajador_id, fecha, estado, horas_normales, categoria, periodo_id, registrado_por, corrige_a_id, motivo_correccion) "
          "VALUES (%s,%s,'2026-10-20','presente',6,'peon',1,%s,%s,'salió temprano')", (p, t, alm, j))
        assert c("SELECT horas_normales FROM tareo.jornada_vigente WHERE trabajador_id=%s", (t,)).fetchone()["horas_normales"] == 6
        c("UPDATE tareo.trabajador SET fecha_salida = '2026-10-30' WHERE id = %s", (t,))

        prov = c("INSERT INTO compras.proveedor (ruc, razon_social) VALUES ('20606456892', 'SEVENS SAC') RETURNING id").fetchone()["id"]
        ins = c("INSERT INTO catalogo.insumo (codigo, descripcion, unidad_s10, unidad_compra, tipo, verificado) "
                "VALUES ('0201010001', 'Cemento Portland Tipo I', 'bol', 'bol', 'material', true) RETURNING id").fetchone()["id"]
        s = c("INSERT INTO compras.solicitud (proyecto_id, emitida_por) VALUES (%s, %s) RETURNING id, numero", (p, res)).fetchone()
        assert s["numero"] == 1
        c("INSERT INTO compras.solicitud_linea (solicitud_id, insumo_id, cantidad, unidad_compra) VALUES (%s,%s,200,'bol')", (s["id"], ins))
        co = c("INSERT INTO compras.compra (proyecto_id, proveedor_id, tipo_comprobante, numero_comprobante, fecha, base_imponible, igv, total, registrado_por, solicitud_id) "
               "VALUES (%s,%s,'factura','F001-4512','2026-10-21',1000,180,1180,%s,%s) RETURNING id", (p, prov, res, s["id"])).fetchone()["id"]
        c("INSERT INTO compras.compra_detalle (compra_id, insumo_id, cantidad, unidad, precio_unitario) VALUES (%s,%s,200,'bol',5)", (co, ins))
        assert c("SELECT estado_derivado FROM compras.solicitud_estado WHERE solicitud_id=%s", (s["id"],)).fetchone()["estado_derivado"] == "comprada"
        c("UPDATE compras.compra SET estado='anulada', motivo_anulacion='duplicada', anulada_por=%s, anulada_en=now() WHERE id=%s", (res, co))

        assert c("SELECT obra.ampliar_plazo(%s, '2026-12-13', 'adicional de obra', %s) AS n", (p, res)).fetchone()["n"] >= 1
        c("UPDATE obra.periodo SET estado='cerrado', cerrado_en=now(), cerrado_por=%s WHERE proyecto_id=%s AND numero=1", (res, p))
        c("UPDATE obra.asignacion SET fecha_retiro=now(), retirado_por=%s WHERE usuario_id=%s AND proyecto_id=%s", (adm, alm, p))
        assert c("SELECT estado FROM obra.dispositivo WHERE id=%s", (d,)).fetchone()["estado"] == "revocado"
