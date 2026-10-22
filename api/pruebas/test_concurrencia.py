"""Concurrencia real: varias conexiones, varios hilos, commits de verdad.

Lo que se protege con un bloqueo o un índice único no se puede probar con una sola conexión:
sin concurrencia, un 'contar y luego insertar' sin bloqueo también pasaría la prueba.
"""
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
from uuid import uuid4

import psycopg
from psycopg.rows import dict_row

from pruebas.conftest import dsn


def conectar(base):
    return psycopg.connect(dsn(base), row_factory=dict_row)


def correr_a_la_vez(base, tareas):
    """Cada tarea es sql + params; todas arrancan a la vez y cada una commitea por su cuenta."""
    barrera = Barrier(len(tareas))

    def trabajar(tarea):
        sql, params = tarea
        with conectar(base) as c:
            barrera.wait()
            try:
                fila = c.execute(sql, params).fetchone()
                c.commit()
                return ("ok", fila)
            except psycopg.Error as e:
                c.rollback()
                return (e.sqlstate, str(e))

    with ThreadPoolExecutor(max_workers=len(tareas)) as ex:
        return list(ex.map(trabajar, tareas))


def preparar_obra(base, almaceneros=3):
    """Datos COMMITEADOS (los hilos usan otras conexiones y no verían una transacción abierta)."""
    sufijo = uuid4().hex[:10]
    hash_ = "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"
    with conectar(base) as c:
        adm = c.execute("INSERT INTO obra.usuario (nombre,usuario,hash_clave) VALUES ('Adm',%s,%s) RETURNING id", (f"adm{sufijo}", hash_)).fetchone()["id"]
        p = c.execute("INSERT INTO obra.proyecto (nombre,fecha_inicio,fecha_fin,creado_por) VALUES (%s,'2026-10-19','2026-12-06',%s) RETURNING id",
                      (f"Obra concurrente {sufijo}", adm)).fetchone()["id"]
        alms = []
        for i in range(almaceneros):
            u = c.execute("INSERT INTO obra.usuario (nombre,usuario,hash_clave) VALUES ('Alm',%s,%s) RETURNING id", (f"alm{i}{sufijo}", hash_)).fetchone()["id"]
            c.execute("INSERT INTO obra.asignacion (usuario_id,proyecto_id,rol,asignado_por) VALUES (%s,%s,'almacenero',%s)", (u, p, adm))
            alms.append(u)
        c.commit()
    return {"admin": adm, "proyecto": p, "almaceneros": alms, "sufijo": sufijo}


def test_dos_administradores_a_la_vez_no_pueden_autorizar_el_tercer_dispositivo(base_pruebas):
    """R5. Sin el bloqueo consultivo, ambos contarían 1 activo y los dos pasarían: quedarían 3."""
    for ronda in range(8):
        o = preparar_obra(base_pruebas, almaceneros=3)
        a1, a2, a3 = o["almaceneros"]
        with conectar(base_pruebas) as c:
            ids = [c.execute("INSERT INTO obra.dispositivo (proyecto_id,usuario_id,identificador) VALUES (%s,%s,%s) RETURNING id",
                             (o["proyecto"], u, f"android-{o['sufijo']}-{i}-xxxx")).fetchone()["id"] for i, u in enumerate((a1, a2, a3))]
            c.execute("UPDATE obra.dispositivo SET estado='activo', autorizado_por=%s, fecha_autorizacion=now() WHERE id=%s", (o["admin"], ids[0]))
            c.commit()

        sql = "UPDATE obra.dispositivo SET estado='activo', autorizado_por=%s, fecha_autorizacion=now() WHERE id=%s RETURNING id"
        resultados = correr_a_la_vez(base_pruebas, [(sql, (o["admin"], ids[1])), (sql, (o["admin"], ids[2]))])

        resultado = sorted(r[0] for r in resultados)
        assert resultado == ["23514", "ok"], f"ronda {ronda}: {resultados}"
        with conectar(base_pruebas) as c:
            activos = c.execute("SELECT count(*) AS n FROM obra.dispositivo WHERE proyecto_id=%s AND estado='activo'", (o["proyecto"],)).fetchone()["n"]
        assert activos == 2, f"ronda {ronda}: hay {activos} dispositivos activos"


def test_solicitudes_simultaneas_de_una_obra_reciben_numeros_distintos_y_sin_huecos(base_pruebas):
    o = preparar_obra(base_pruebas, almaceneros=0)
    sql = "INSERT INTO compras.solicitud (proyecto_id, emitida_por) VALUES (%s, %s) RETURNING numero"
    resultados = correr_a_la_vez(base_pruebas, [(sql, (o["proyecto"], o["admin"]))] * 12)
    assert all(r[0] == "ok" for r in resultados), resultados
    assert sorted(r[1]["numero"] for r in resultados) == list(range(1, 13))


def test_dos_asistencias_simultaneas_del_mismo_trabajador_y_dia_solo_dejan_una(base_pruebas):
    o = preparar_obra(base_pruebas, almaceneros=1)
    with conectar(base_pruebas) as c:
        t = c.execute("INSERT INTO tareo.trabajador (proyecto_id,nombre,documento,categoria,cuadrilla,fecha_ingreso,registrado_por) "
                      "VALUES (%s,'Pedro','%s','peon','C1','2026-10-19',%s) RETURNING id" % ("%s", str(70000000 + int(o["sufijo"][:6], 16) % 9999999), "%s"),
                      (o["proyecto"], o["almaceneros"][0])).fetchone()["id"]
        c.commit()
    sql = ("INSERT INTO tareo.jornada (proyecto_id,trabajador_id,fecha,estado,horas_normales,categoria,periodo_id,registrado_por) "
           "VALUES (%s,%s,'2026-10-20','presente',8,'peon',1,%s) RETURNING id")
    resultados = correr_a_la_vez(base_pruebas, [(sql, (o["proyecto"], t, o["almaceneros"][0]))] * 6)
    assert sorted(r[0] for r in resultados) == ["23505"] * 5 + ["ok"]


def test_dos_correcciones_simultaneas_de_la_misma_jornada_no_bifurcan_la_historia(base_pruebas):
    o = preparar_obra(base_pruebas, almaceneros=1)
    alm = o["almaceneros"][0]
    with conectar(base_pruebas) as c:
        t = c.execute("INSERT INTO tareo.trabajador (proyecto_id,nombre,documento,categoria,cuadrilla,fecha_ingreso,registrado_por) "
                      "VALUES (%s,'Luis',%s,'peon','C1','2026-10-19',%s) RETURNING id",
                      (o["proyecto"], str(60000000 + int(o["sufijo"][:6], 16) % 9999999), alm)).fetchone()["id"]
        j = c.execute("INSERT INTO tareo.jornada (proyecto_id,trabajador_id,fecha,estado,horas_normales,categoria,periodo_id,registrado_por) "
                      "VALUES (%s,%s,'2026-10-20','presente',8,'peon',1,%s) RETURNING id", (o["proyecto"], t, alm)).fetchone()["id"]
        c.commit()
    sql = ("INSERT INTO tareo.jornada (proyecto_id,trabajador_id,fecha,estado,horas_normales,categoria,periodo_id,registrado_por,corrige_a_id,motivo_correccion) "
           "VALUES (%s,%s,'2026-10-20','presente',%s,'peon',1,%s,%s,'corrección') RETURNING id")
    resultados = correr_a_la_vez(base_pruebas, [(sql, (o["proyecto"], t, h, alm, j)) for h in (5, 6, 7)])
    assert sorted(r[0] for r in resultados) == ["23505", "23505", "ok"]
    with conectar(base_pruebas) as c:
        assert c.execute("SELECT count(*) AS n FROM tareo.jornada_vigente WHERE trabajador_id=%s", (t,)).fetchone()["n"] == 1
