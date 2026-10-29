"""Prueba la PROPUESTA 0010 (almacén y costo) sin tocar `versions/`: crea una base temporal, la migra hasta 0009,
aplica la propuesta y recorre los escenarios de las HU 35 a 47 entrando como el rol del servicio (sigro_svc_almacen).

Uso:  python docs/propuestas/probar_0010.py            (necesita PostgreSQL con el usuario dueño, como las pruebas)
Deja la base `sigro_propuesta_0010` migrada para poder generar su diagrama físico:
      SIGRO_DB=sigro_propuesta_0010 python docs/generar_diagrama.py
"""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import uuid
from decimal import Decimal as D
from pathlib import Path

import psycopg

API = Path(__file__).resolve().parents[2]
SERVIDOR = os.environ.get("SIGRO_TEST_SERVIDOR", "sigro_owner:sigro_owner_dev@localhost:5432")
BASE = "sigro_propuesta_0010"
PROPUESTA = API / "migraciones" / "propuestas" / "0010_almacen_y_costo.py"
COPIA = API / "migraciones" / "versions" / "0010_almacen_y_costo.py"

ok = 0


def caso(nombre: str):
    def deco(f):
        global ok
        f()
        ok += 1
        print(f"  ✓ {nombre}")
    return deco


def preparar() -> None:
    with psycopg.connect(f"postgresql://{SERVIDOR}/postgres", autocommit=True) as c:
        c.execute(f"DROP DATABASE IF EXISTS {BASE} WITH (FORCE)")
        c.execute(f"CREATE DATABASE {BASE}")
    env = {**os.environ, "SIGRO_DATABASE_URL": f"postgresql+psycopg://{SERVIDOR}/{BASE}"}
    shutil.copy(PROPUESTA, COPIA)
    try:
        for paso in (["upgrade", "head"], ["downgrade", "0009"], ["upgrade", "head"]):   # también se puede revertir
            subprocess.run([sys.executable, "-m", "alembic", *paso], cwd=API, env=env, check=True, capture_output=True)
    finally:
        COPIA.unlink()


def main() -> None:
    preparar()
    print("0010 aplicada, revertida y vuelta a aplicar sobre 0009. Escenarios:")
    with psycopg.connect(f"postgresql://{SERVIDOR}/{BASE}", autocommit=True) as c:
        # datos mínimos (como dueño): una obra con dos almacenes y un insumo replicados
        c.execute("INSERT INTO almacen.almacen_ref VALUES (1, 10, 'Central', 'central', 'activo'), (2, 10, 'Frente A', 'frente', 'activo'),"
                  " (3, 20, 'Obra B', 'frente', 'activo')")
        c.execute("INSERT INTO almacen.insumo_ref VALUES (100, '0201010001', 'Cemento portland tipo I', 'bol', true)")
        c.execute("SET ROLE sigro_svc_almacen")

        def mov(**k):
            k.setdefault("uuid_cliente", uuid.uuid4())
            k.setdefault("proyecto_id", 10)
            k.setdefault("insumo_id", 100)
            k.setdefault("registrado_por", 1)
            k.setdefault("costo_unitario", 0)
            cols = ", ".join(k)
            return c.execute(f"INSERT INTO almacen.movimiento ({cols}) VALUES ({', '.join(['%s'] * len(k))}) "
                             "RETURNING id, costo_unitario, sentido, cantidad", list(k.values())).fetchone()

        def saldo(almacen=1):
            return c.execute("SELECT cantidad, costo_promedio FROM almacen.saldo WHERE almacen_id = %s AND insumo_id = 100", (almacen,)).fetchone()

        def falla(sqlstate_o_texto, f):
            try:
                f()
            except psycopg.Error as e:
                assert sqlstate_o_texto in (e.sqlstate or "") or sqlstate_o_texto in str(e), e
                return
            raise AssertionError(f"debía fallar con {sqlstate_o_texto}")

        rec = c.execute("INSERT INTO almacen.recepcion (uuid_cliente, proyecto_id, almacen_id, compra_id, registrado_por) "
                        "VALUES (%s, 10, 1, 555, 1) RETURNING id", (uuid.uuid4(),)).fetchone()[0]

        @caso("HU-35 recepción: ingreso por compra con su costo")
        def _():
            mov(almacen_id=1, tipo="ingreso_compra", sentido=1, cantidad=D("100"), costo_unitario=D("20"), recepcion_id=rec)
            assert saldo() == (D("100.0000"), D("20.000000"))

        @caso("R4 costo promedio ponderado: 100 a 20 + 50 a 26 = 22")
        def _():
            mov(almacen_id=1, tipo="ingreso_compra", sentido=1, cantidad=D("50"), costo_unitario=D("26"), recepcion_id=rec)
            assert saldo() == (D("150.0000"), D("22.000000"))

        salida = {}

        @caso("HU-40 salida: el servidor ignora el costo del cliente y congela el promedio vigente")
        def _():
            r = mov(almacen_id=1, tipo="salida", sentido=-1, cantidad=D("30"), costo_unitario=D("999"), motivo="Vaciado de losa", quien_retira="Capataz")
            salida["id"] = r[0]
            assert r[1] == D("22.000000")

        @caso("R4 un ingreso posterior no cambia el costo de la salida ya registrada")
        def _():
            mov(almacen_id=1, tipo="ingreso_compra", sentido=1, cantidad=D("80"), costo_unitario=D("30"), recepcion_id=rec)
            assert c.execute("SELECT costo_unitario FROM almacen.movimiento WHERE id = %s", (salida["id"],)).fetchone()[0] == D("22")
            assert saldo() == (D("200.0000"), D("25.200000"))          # (120×22 + 80×30) / 200

        @caso("HU-40 no se saca más de lo disponible (SIGRO-A1)")
        def _():
            falla("SIGRO-A1", lambda: mov(almacen_id=1, tipo="salida", sentido=-1, cantidad=D("201"), motivo="x", quien_retira="y"))

        @caso("HU-40 la salida exige motivo y quién retira")
        def _():
            falla("ck_movimiento_por_tipo", lambda: mov(almacen_id=1, tipo="salida", sentido=-1, cantidad=D("1"), motivo=" "))

        @caso("R3 un reenvío con el mismo uuid_cliente no duplica")
        def _():
            u = uuid.uuid4()
            mov(uuid_cliente=u, almacen_id=1, tipo="salida", sentido=-1, cantidad=D("1"), motivo="m", quien_retira="q")
            falla("23505", lambda: mov(uuid_cliente=u, almacen_id=1, tipo="salida", sentido=-1, cantidad=D("1"), motivo="m", quien_retira="q"))
            assert saldo()[0] == D("199")

        @caso("R1 el libro no se modifica ni se borra (ni el servicio puede intentarlo)")
        def _():
            falla("42501", lambda: c.execute("UPDATE almacen.movimiento SET cantidad = 1"))
            falla("42501", lambda: c.execute("DELETE FROM almacen.movimiento"))

        @caso("HU-45 reverso: copia al original con el signo cambiado y el saldo vuelve")
        def _():
            antes = saldo()[0]
            r = mov(almacen_id=1, tipo="reverso", sentido=1, cantidad=D("1"), revierte_a_id=salida["id"], motivo="Se registró en el frente equivocado")
            assert (r[2], r[3], r[1]) == (1, D("30"), D("22"))
            assert saldo()[0] == antes + 30
            k = c.execute("SELECT revertido_por_id FROM almacen.kardex WHERE id = %s", (salida["id"],)).fetchone()[0]
            assert k == r[0]

        @caso("HU-45 un original se revierte una sola vez; un reverso no se revierte")
        def _():
            falla("uq_movimiento_revierte_a", lambda: mov(almacen_id=1, tipo="reverso", sentido=1, cantidad=D("1"), revierte_a_id=salida["id"], motivo="otra vez"))
            ult = c.execute("SELECT id FROM almacen.movimiento WHERE revierte_a_id = %s", (salida["id"],)).fetchone()[0]
            falla("SIGRO-A4", lambda: mov(almacen_id=1, tipo="reverso", sentido=-1, cantidad=D("1"), revierte_a_id=ult, motivo="x"))

        @caso("HU-41 ingreso sin compra: exige origen y costo; «otro» exige detalle")
        def _():
            falla("ck_movimiento_por_tipo", lambda: mov(almacen_id=2, tipo="ingreso_sin_compra", sentido=1, cantidad=D("5"), costo_unitario=D("0"), origen="sobrante"))
            falla("ck_movimiento_por_tipo", lambda: mov(almacen_id=2, tipo="ingreso_sin_compra", sentido=1, cantidad=D("5"), costo_unitario=D("21"), origen="otro"))
            mov(almacen_id=2, tipo="ingreso_sin_compra", sentido=1, cantidad=D("5"), costo_unitario=D("21"), origen="devolucion_cuadrilla")
            assert saldo(2) == (D("5"), D("21"))

        @caso("HU-42 traslado: salida en origen, ingreso en destino, el costo viaja con el material")
        def _():
            t = c.execute("INSERT INTO almacen.traslado (uuid_cliente, origen_proyecto_id, origen_almacen_id, destino_proyecto_id, destino_almacen_id, motivo, registrado_por) "
                          "VALUES (%s, 10, 1, 20, 3, 'Préstamo a obra B', 1) RETURNING id", (uuid.uuid4(),)).fetchone()[0]
            s = mov(almacen_id=1, tipo="traslado_salida", sentido=-1, cantidad=D("40"), traslado_id=t)
            i = mov(proyecto_id=20, almacen_id=3, tipo="traslado_ingreso", sentido=1, cantidad=D("999"), costo_unitario=D("1"), traslado_id=t)
            assert (i[3], i[1]) == (D("40"), s[1])
            assert saldo(3) == (D("40"), s[1])

        @caso("HU-46 conteo: el servidor pone el saldo teórico; la diferencia exige motivo; el ajuste va al promedio")
        def _():
            co = c.execute("INSERT INTO almacen.conteo (uuid_cliente, proyecto_id, almacen_id, fecha, registrado_por) VALUES (%s, 10, 2, current_date, 1) RETURNING id",
                           (uuid.uuid4(),)).fetchone()[0]
            falla("ck_conteo_linea_motivo", lambda: c.execute("INSERT INTO almacen.conteo_linea (conteo_id, insumo_id, saldo_teorico, cantidad_contada) VALUES (%s, 100, 0, 4)", (co,)))
            teorico, dif = c.execute("INSERT INTO almacen.conteo_linea (conteo_id, insumo_id, saldo_teorico, cantidad_contada, motivo) "
                                     "VALUES (%s, 100, 0, 4, 'Una bolsa rota') RETURNING saldo_teorico, diferencia", (co,)).fetchone()
            assert (teorico, dif) == (D("5"), D("-1"))
            a = mov(almacen_id=2, tipo="ajuste_conteo", sentido=-1, cantidad=D("1"), conteo_id=co, motivo="Una bolsa rota")
            assert a[1] == D("21") and saldo(2)[0] == D("4")
            c.execute("UPDATE almacen.conteo SET estado = 'confirmado', confirmado_por = 1, confirmado_en = now() WHERE id = %s", (co,))
            falla("SIGRO-A5", lambda: c.execute("UPDATE almacen.conteo SET estado = 'borrador', confirmado_por = NULL, confirmado_en = NULL WHERE id = %s", (co,)))

        @caso("ADR-09 el servicio almacén no puede leer el esquema de otro servicio")
        def _():
            falla("42501", lambda: c.execute("SELECT 1 FROM obra.usuario"))
            falla("42501", lambda: c.execute("SELECT 1 FROM compras.compra"))

        @caso("costo: modelo de lectura idempotente (un evento se procesa una vez)")
        def _():
            c.execute("RESET ROLE")
            c.execute("SET ROLE sigro_svc_costo")
            c.execute("INSERT INTO costo.evento_recibido (evento_id, tipo) VALUES (1, 'almacen.salida_registrada')")
            falla("23505", lambda: c.execute("INSERT INTO costo.evento_recibido (evento_id, tipo) VALUES (1, 'almacen.salida_registrada')"))
            c.execute("INSERT INTO costo.hecho (evento_id, proyecto_id, familia, clase, insumo_codigo, cantidad, unidad, costo_unitario, fecha, referencia) "
                      "VALUES (1, 10, 'material', 'consumo', '0201010001', 30, 'bol', 22, current_date, 'almacen.movimiento:3')")
            assert c.execute("SELECT gasto FROM costo.gasto_por_obra WHERE proyecto_id = 10").fetchone()[0] == D("660.00")
            falla("42501", lambda: c.execute("SELECT 1 FROM almacen.movimiento"))
    concurrencia()
    print(f"{ok} escenarios en verde · base {BASE} lista para el diagrama")


def concurrencia() -> None:
    """Dos celulares sacan TODO el saldo a la vez: el candado hace que el segundo vea el saldo ya descontado."""
    import threading
    global ok
    dsn = f"postgresql://{SERVIDOR}/{BASE}"
    ins = ("INSERT INTO almacen.movimiento (uuid_cliente, proyecto_id, almacen_id, insumo_id, tipo, sentido, cantidad, costo_unitario, "
           "motivo, quien_retira, registrado_por) VALUES (%s, 10, 1, 100, 'salida', -1, %s, 0, 'm', 'q', 1)")
    with psycopg.connect(dsn) as a, psycopg.connect(dsn) as b:
        a.execute("SET ROLE sigro_svc_almacen")
        b.execute("SET ROLE sigro_svc_almacen")
        disponible = a.execute("SELECT cantidad FROM almacen.saldo WHERE almacen_id = 1 AND insumo_id = 100").fetchone()[0]
        a.execute(ins, (uuid.uuid4(), disponible))                  # A toma todo y aún no confirma
        res = {}

        def segundo():
            try:
                b.execute(ins, (uuid.uuid4(), disponible))
                res["b"] = "registrada"
            except psycopg.Error as e:
                res["b"] = str(e)
        hilo = threading.Thread(target=segundo)
        hilo.start()
        hilo.join(1)
        assert hilo.is_alive(), "B debía esperar el candado de A"
        a.commit()
        hilo.join()
        assert "SIGRO-A1" in res["b"], res
    ok += 1
    print("  ✓ HU-40 dos salidas simultáneas no dejan el saldo en negativo (candado por almacén e insumo)")


if __name__ == "__main__":
    main()
