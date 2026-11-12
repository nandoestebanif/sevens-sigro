"""Mide las consultas más usadas con datos REALES del cliente (y volumen de ejemplo rotulado) · docs/rendimiento-web-api.md

    cd api && SIGRO_PERF_SERVIDOR=sigro_owner:CLAVE@localhost:5434 python docs/rendimiento/medir_consultas.py sembrar
    ... python docs/rendimiento/medir_consultas.py explicar [--indices]     # EXPLAIN (ANALYZE, BUFFERS) de las 10 consultas
    ... python docs/rendimiento/medir_consultas.py contar                   # sentencias SQL por petición (N+1)

`sembrar` recrea la base `sigro_perf`, la migra y carga:
  REAL   · el catálogo normalizado del cliente (2 243, semillas/catalogo-cliente.json)
  REAL   · los 626 proveedores del cliente (semillas/proveedores-cliente.json)
  REAL   · la Relación de insumos del S10 (284) por la API: POST /catalogo/insumos/importar-s10 y
           POST /obras/{id}/requerimiento/importar, leída del PDF con integraciones/s10.py
  EJEMPLO· (no existe todavía en la realidad) 1 200 compras con 3 a 6 líneas, 300 solicitudes, 4 obras más,
           80 trabajadores con 12 meses de jornadas. Todo lo de ejemplo lleva «EJEMPLO» en su texto.

`contar` corre las peticiones de la web contra los servicios EN PROCESO (mismo enrutado que el gateway, cada servicio
con su rol de base) y cuenta las sentencias que llegan a PostgreSQL. Sirve para comparar antes/después: córrelo con el
código de main y con el de la rama (la base se siembra una vez).
"""
from __future__ import annotations

import gzip
import json
import os
import re
import sys
import time
from datetime import date
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]          # api/
REPO = RAIZ.parent
# SIGRO_PERF_APP: carpeta api/ de OTRA versión del código (p. ej. main exportado) para medir el «antes»
sys.path.insert(0, os.environ.get("SIGRO_PERF_APP", str(RAIZ)))
SERVIDOR = os.environ.get("SIGRO_PERF_SERVIDOR", "sigro_owner:sigro_owner_dev@localhost:5434")
BASE = os.environ.get("SIGRO_PERF_BASE", "sigro_perf")
URL = f"postgresql+psycopg://{SERVIDOR}/{BASE}"
os.environ.setdefault("SIGRO_APP_DATABASE_URL", URL)
os.environ.setdefault("SIGRO_DATABASE_URL", URL)
PDF_S10 = REPO / "insumos-cliente" / "5.3. Relacion de insumos.pdf"
_INFRA = re.compile(r"^\s*(SET ROLE|SAVEPOINT|RELEASE SAVEPOINT|ROLLBACK TO SAVEPOINT|SELECT current_user|RESET ROLE)", re.I)


# ───────────────────────────────────────────── servicios en proceso, con commit real
class Servicios:
    def __init__(self):
        from fastapi.testclient import TestClient
        from sqlalchemy import create_engine, event, text
        from sqlalchemy.orm import Session

        from app import internos
        from app.db import obtener_sesion
        from app.servicios import fabricas
        from pruebas.conftest import GatewayEnProceso

        self.motor = create_engine(URL)
        self.sentencias: list[str] = []
        event.listen(self.motor, "before_cursor_execute",
                     lambda c, cur, sql, p, ctx, many: None if _INFRA.match(sql) else self.sentencias.append(sql))

        def sesion_como(rol):
            def dep():
                with Session(self.motor) as s:
                    s.execute(text(f"SET ROLE {rol}"))
                    try:
                        yield s
                        s.commit()
                    except BaseException:
                        s.rollback()
                        raise
            return dep

        apps = {}
        for nombre, fabrica in fabricas().items():
            app = fabrica()
            app.dependency_overrides[obtener_sesion] = sesion_como(f"sigro_svc_{nombre}")
            apps[nombre] = app
        for n, a in apps.items():
            internos.registrar_cliente_de_prueba(n, TestClient(a, raise_server_exceptions=True))
        self.http = TestClient(GatewayEnProceso(apps), raise_server_exceptions=True)

    def pedir(self, metodo: str, ruta: str, uid: int, **kw):
        from app.seguridad import crear_token
        self.sentencias.clear()
        t = time.perf_counter()
        r = self.http.request(metodo, "/api/v1" + ruta, headers={"Authorization": f"Bearer {crear_token(uid)[0]}"}, **kw)
        ms = (time.perf_counter() - t) * 1000
        assert r.status_code < 400, (ruta, r.status_code, r.text[:300])
        return r, len(self.sentencias), ms


def _ids():
    from sqlalchemy import create_engine, text
    with create_engine(URL).connect() as c:
        uid = c.execute(text("SELECT id FROM obra.usuario WHERE usuario = 'perf.residente'")).scalar()
        pid = c.execute(text("SELECT id FROM obra.proyecto WHERE nombre = 'Multifamiliar Flores'")).scalar()
    return uid, pid


# ───────────────────────────────────────────── sembrar
def sembrar() -> None:
    import psycopg
    from sqlalchemy import create_engine, text

    with psycopg.connect(f"postgresql://{SERVIDOR}/postgres", autocommit=True) as c:
        c.execute(f"DROP DATABASE IF EXISTS {BASE} WITH (FORCE)")
        c.execute(f"CREATE DATABASE {BASE}")
    from pruebas.conftest import alembic
    alembic(BASE, "upgrade", "head")

    from app.seguridad import hashear
    motor = create_engine(URL)
    with motor.begin() as c:
        uid = c.execute(text("INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES ('Residente (medición)', 'perf.residente', :h) "
                             "RETURNING id"), {"h": hashear("Medicion-2026!")}).scalar()
        pid = c.execute(text("INSERT INTO obra.proyecto (nombre, fecha_inicio, fecha_fin, creado_por) "
                             "VALUES ('Multifamiliar Flores', '2026-01-12', '2027-01-31', :u) RETURNING id"), {"u": uid}).scalar()
        c.execute(text("INSERT INTO obra.asignacion (usuario_id, proyecto_id, rol, asignado_por) VALUES (:u, :p, 'residente', :u)"),
                  {"u": uid, "p": pid})

    from semillas import cargar_catalogo, cargar_proveedores
    cargar_catalogo.main([str(RAIZ / "semillas" / "catalogo-cliente.json"), "--url", URL])
    cargar_proveedores.main([str(RAIZ / "semillas" / "proveedores-cliente.json"), "--url", URL])

    # S10 real por la API (lo mismo que hace la web en «Importar del S10»)
    sys.path.insert(0, str(REPO / "integraciones"))
    import s10
    lectura = s10.leer_relacion_de_insumos(PDF_S10.read_bytes())
    filas = [{"codigo": i.codigo, "descripcion": i.descripcion, "unidad": i.unidad, "tipo": i.tipo} for i in lectura.insumos]
    sv = Servicios()
    r, n, ms = sv.pedir("POST", "/catalogo/insumos/importar-s10", uid, json={"insumos": filas})
    print(f"S10 → catálogo: {r.json()['creados']} creados · {n} sentencias · {ms:.0f} ms")
    req = [{"insumo_codigo": i.codigo, "cantidad": str(round(i.cantidad, 4)), "precio": str(round(i.precio, 4))}
           for i in lectura.insumos if i.cantidad > 0]
    r, n, ms = sv.pedir("POST", f"/obras/{pid}/requerimiento/importar", uid, json={"filas": req})
    print(f"S10 → requerimiento: {r.json()['creados']} creados · {n} sentencias · {ms:.0f} ms")

    # ── VOLUMEN DE EJEMPLO (rotulado): lo que el sistema tendría tras un año de uso
    with motor.begin() as c:
        for k in range(4):
            otra = c.execute(text("INSERT INTO obra.proyecto (nombre, fecha_inicio, fecha_fin, creado_por) "
                                  "VALUES (:n, '2026-01-12', '2027-01-31', :u) RETURNING id"), {"n": f"EJEMPLO obra {k + 2}", "u": uid}).scalar()
            c.execute(text("INSERT INTO obra.asignacion (usuario_id, proyecto_id, rol, asignado_por) VALUES (:u, :p, 'residente', :u)"),
                      {"u": uid, "p": otra})
        c.execute(text("""
            INSERT INTO compras.compra (proyecto_id, proveedor_id, tipo_comprobante, numero_comprobante, fecha, moneda,
                                        base_imponible, igv, total, registrado_por)
            SELECT p.id, pv.id, 'factura', 'E' || lpad(g::text, 3, '0') || '-' || g, date '2026-01-12' + (g % 290), 'PEN',
                   100.00, 18.00, 118.00, :u
              FROM generate_series(1, 1200) g
              JOIN LATERAL (SELECT id FROM obra.proyecto ORDER BY id OFFSET (CASE WHEN g % 10 < 6 THEN 0 ELSE g % 5 END) LIMIT 1) p ON true
              JOIN LATERAL (SELECT id FROM compras.proveedor WHERE estado = 'activo' ORDER BY id OFFSET (g * 7) % 600 LIMIT 1) pv ON true
        """), {"u": uid})
        c.execute(text("""
            INSERT INTO compras.compra_detalle (compra_id, insumo_id, cantidad, unidad, precio_unitario)
            SELECT co.id, i.id, 1 + (co.id * k) % 40, i.unidad_compra, 5 + (co.id * k) % 90
              FROM compras.compra co
              CROSS JOIN generate_series(1, 6) k
              JOIN LATERAL (SELECT id, unidad_compra FROM catalogo.insumo ORDER BY id OFFSET (co.id * 13 + k * 101) % 2500 LIMIT 1) i ON true
             WHERE k <= 3 + co.id % 4
            ON CONFLICT DO NOTHING
        """))
        c.execute(text("""
            INSERT INTO compras.solicitud (proyecto_id, emitida_por)
            SELECT p.id, :u FROM generate_series(1, 300) g
              JOIN LATERAL (SELECT id FROM obra.proyecto ORDER BY id OFFSET g % 5 LIMIT 1) p ON true
        """), {"u": uid})
        c.execute(text("""
            INSERT INTO compras.solicitud_linea (solicitud_id, insumo_id, descripcion_libre, cantidad, unidad_compra)
            SELECT s.id, CASE WHEN k = 4 THEN NULL ELSE i.id END, CASE WHEN k = 4 THEN 'EJEMPLO material sin código' END, 10, 'und'
              FROM compras.solicitud s CROSS JOIN generate_series(1, 4) k
              JOIN LATERAL (SELECT id FROM catalogo.insumo ORDER BY id OFFSET (s.id * 17 + k * 211) % 2500 LIMIT 1) i ON true
            ON CONFLICT DO NOTHING
        """))
        c.execute(text("""
            INSERT INTO tareo.trabajador (proyecto_id, nombre, documento, categoria, cuadrilla, fecha_ingreso, registrado_por)
            SELECT :p, 'EJEMPLO trabajador ' || g, (40000000 + g)::text,
                   (ARRAY['peon','oficial','operario'])[1 + g % 3], 'Cuadrilla ' || (1 + g % 8), '2026-01-12', :u
              FROM generate_series(1, 80) g
        """), {"p": pid, "u": uid})
        c.execute(text("""
            INSERT INTO tareo.jornada (proyecto_id, trabajador_id, fecha, estado, horas_normales, horas_extra, registrado_por)
            SELECT :p, t.id, d::date, CASE WHEN (t.id + extract(doy FROM d)::int) % 17 = 0 THEN 'ausente' ELSE 'presente' END,
                   CASE WHEN (t.id + extract(doy FROM d)::int) % 17 = 0 THEN 0 ELSE 8 END, 0, :u
              FROM tareo.trabajador t CROSS JOIN generate_series(date '2026-01-12', date '2026-10-31', interval '1 day') d
             WHERE t.proyecto_id = :p AND extract(isodow FROM d) < 7
        """), {"p": pid, "u": uid})
    with motor.connect() as c:
        c.execution_options(isolation_level="AUTOCOMMIT").execute(text("VACUUM ANALYZE"))
        for t in ("catalogo.insumo", "compras.proveedor", "compras.compra", "compras.compra_detalle", "compras.solicitud",
                  "compras.solicitud_linea", "compras.requerimiento", "tareo.trabajador", "tareo.jornada"):
            print(f"{t}: {c.execute(text(f'SELECT count(*) FROM {t}')).scalar()} filas")


# ───────────────────────────────────────────── las 10 consultas
def consultas(uid: int, pid: int, ids_req: list[int]) -> list[tuple[str, str, str, dict]]:
    """(id, quién la dispara, SQL tal como la manda el servicio, parámetros)."""
    from app.modulos.compras.rutas import _SQL_COMPRA, _SQL_PROV
    return [
        ("Q1", "TODA petición a catálogo/compras/tareo: obras POST /interno/autorizar (rol vigente en la obra)",
         "SELECT rol FROM obra.asignacion WHERE usuario_id = :u AND proyecto_id = :p AND fecha_retiro IS NULL", {"u": uid, "p": pid}),
        ("Q2", "web al arrancar y al cambiar de obra: GET /obras (consulta única de esta rama)",
         "SELECT p.id, p.nombre, p.fecha_inicio, p.fecha_fin, p.dia_corte, p.estado, a.rol, coalesce(per.total, 0) AS semanas_totales, "
         "per.actual AS semana FROM obra.asignacion a JOIN obra.proyecto p ON p.id = a.proyecto_id LEFT JOIN LATERAL ("
         "SELECT count(*) AS total, coalesce(max(numero) FILTER (WHERE current_date BETWEEN fecha_inicio AND fecha_fin), "
         "CASE WHEN current_date < min(fecha_inicio) THEN 0 ELSE count(*) END) AS actual FROM obra.periodo WHERE proyecto_id = p.id"
         ") per ON true WHERE a.usuario_id = :u AND a.fecha_retiro IS NULL ORDER BY p.nombre", {"u": uid}),
        ("Q3", "web y app: GET /catalogo/insumos (Catálogo, buscador del almacenero)",
         "SELECT id, codigo, descripcion, unidad_s10, unidad_compra, factor_conversion, tipo, activo, verificado "
         "FROM catalogo.insumo WHERE true ORDER BY codigo LIMIT 3000", {}),
        ("Q4", "app y web: GET /catalogo/insumos?q=cemento (búsqueda por texto)",
         "SELECT id, codigo, descripcion, unidad_s10, unidad_compra, factor_conversion, tipo, activo, verificado "
         "FROM catalogo.insumo WHERE true AND (codigo ILIKE :q OR descripcion ILIKE :q) ORDER BY codigo LIMIT 3000", {"q": "%cemento%"}),
        ("Q5", "compras → catálogo POST /interno/insumos/buscar por códigos (registrar compra, solicitud, requerimiento)",
         "SELECT id, codigo, descripcion, unidad_s10, unidad_compra, factor_conversion, tipo, activo, verificado FROM catalogo.insumo "
         "WHERE (id = ANY(:ids) OR upper(codigo) = ANY(:cods)) AND (NOT :act OR activo) ORDER BY codigo",
         {"ids": [], "cods": ["0221000000", "0203020004", "0230460056", "0238000002", "0204000003"], "act": True}),
        ("Q6", "web Compras/Proveedores: GET /proveedores?q=ferre (búsqueda por razón social)",
         _SQL_PROV + "WHERE (:q = '' OR lower(p.razon_social) LIKE '%' || lower(:q) || '%' OR p.ruc LIKE :q || '%') "
         "AND (NOT :c OR p.estado = 'activo') ORDER BY p.razon_social", {"q": "ferre", "c": False}),
        ("Q7", "web Compras (nueva compra): GET /proveedores?solo_para_comprar=true",
         _SQL_PROV + "WHERE (:q = '' OR lower(p.razon_social) LIKE '%' || lower(:q) || '%' OR p.ruc LIKE :q || '%') "
         "AND (NOT :c OR p.estado = 'activo') ORDER BY p.razon_social", {"q": "", "c": True}),
        ("Q8", "web Reposición: GET /obras/{id}/saldo-por-comprar (vista requerido − comprado)",
         "SELECT s.insumo_id, round(s.requerido, 4) AS requerido, round(s.comprado, 4) AS comprado, s.saldo AS saldo_exacto, "
         "round(s.saldo, 4) AS saldo, round(s.exceso, 4) AS exceso FROM compras.saldo_por_comprar s WHERE s.proyecto_id = :p", {"p": pid}),
        ("Q9", "web Compras: GET /obras/{id}/compras (cabeceras) — volumen de EJEMPLO",
         _SQL_COMPRA + "WHERE c.proyecto_id = :p AND (CAST(:e AS text) IS NULL OR c.estado = :e) "
         "AND (CAST(:pv AS bigint) IS NULL OR c.proveedor_id = :pv) ORDER BY c.fecha DESC, c.id DESC", {"p": pid, "e": None, "pv": None}),
        ("Q10", "web Compras: GET /obras/{id}/solicitudes (líneas con avance derivado) — volumen de EJEMPLO",
         "SELECT l.solicitud_id, l.insumo_id, l.descripcion_libre, l.unidad_compra, l.cantidad AS cantidad_solicitada, a.cantidad_comprada, "
         "a.cantidad_pendiente FROM compras.solicitud_linea l JOIN compras.solicitud_linea_avance a ON a.linea_id = l.id "
         "WHERE l.solicitud_id = ANY(:ids) ORDER BY l.id", {"ids": ids_req}),
        ("Q11", "app tareo: GET /obras/{id}/jornadas?fecha=… (jornada vigente del día) — volumen de EJEMPLO",
         "SELECT id, trabajador_id, fecha, estado FROM tareo.jornada_vigente WHERE proyecto_id = :p "
         "AND (CAST(:f AS date) IS NULL OR fecha = :f) AND (CAST(:t AS bigint) IS NULL OR trabajador_id = :t) ORDER BY fecha DESC, trabajador_id",
         {"p": pid, "f": date(2026, 10, 20), "t": None}),
    ]


# Se midieron los 7; los trigram (pg_trgm) no los usó el planificador y quedaron fuera de la propuesta 0011.
INDICES = [
    "CREATE INDEX IF NOT EXISTS ix_insumo_codigo_upper ON catalogo.insumo (upper(codigo))",
    "CREATE EXTENSION IF NOT EXISTS pg_trgm",
    "CREATE INDEX IF NOT EXISTS ix_insumo_descripcion_trgm ON catalogo.insumo USING gin (descripcion gin_trgm_ops)",
    "CREATE INDEX IF NOT EXISTS ix_insumo_codigo_trgm ON catalogo.insumo USING gin (codigo gin_trgm_ops)",
    "CREATE INDEX IF NOT EXISTS ix_proveedor_razon_social_trgm ON compras.proveedor USING gin (lower(razon_social) gin_trgm_ops)",
    "CREATE INDEX IF NOT EXISTS ix_compra_proveedor_registrada ON compras.compra (proveedor_id, fecha) WHERE estado = 'registrada'",
    "CREATE INDEX IF NOT EXISTS ix_solicitud_linea_solicitud_id ON compras.solicitud_linea (solicitud_id)",
]


def explicar(con_indices: bool) -> None:
    from sqlalchemy import create_engine, text
    uid, pid = _ids()
    motor = create_engine(URL)
    with motor.connect() as c:
        if con_indices:
            for s in INDICES:
                c.execute(text(s))
            c.commit()
            c.execution_options(isolation_level="AUTOCOMMIT").execute(text("ANALYZE"))
        ids = [r[0] for r in c.execute(text("SELECT id FROM compras.solicitud WHERE proyecto_id = :p"), {"p": pid})]
        salida = []
        for qid, quien, sql, p in consultas(uid, pid, ids):
            for _ in range(3):      # calentar caché: se mide el caso habitual, no el primer acceso a disco
                c.execute(text(sql), p).fetchall()
            tiempos, plan = [], None
            for _ in range(5):
                plan = [r[0] for r in c.execute(text("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql), p)][0][0]
                tiempos.append(plan["Execution Time"])
            filas = plan["Plan"].get("Actual Rows")
            nodos = sorted({m.group(1) for m in re.finditer(r"'Node Type': '([^']+)'", str(plan))})
            idx = sorted({m.group(1) for m in re.finditer(r"'Index Name': '([^']+)'", str(plan))})
            hits = plan["Plan"].get("Shared Hit Blocks", 0) + plan["Plan"].get("Shared Read Blocks", 0)
            tiempos.sort()
            salida.append({"id": qid, "quien": quien, "ms_mediana": round(tiempos[2], 3), "filas": filas, "bloques": hits,
                           "nodos": nodos, "indices": idx, "planificacion_ms": round(plan["Planning Time"], 3)})
            texto = "\n".join(r[0] for r in c.execute(text("EXPLAIN (ANALYZE, BUFFERS) " + sql), p))
            (Path(os.environ.get("SIGRO_PERF_SALIDA", ".")) / f"plan-{qid}-{'con' if con_indices else 'sin'}-indices.txt").write_text(texto, encoding="utf-8")
        print(json.dumps(salida, ensure_ascii=False, indent=1, default=str))


def contar() -> None:
    uid, pid = _ids()
    sv = Servicios()
    filas_s10 = None
    sys.path.insert(0, str(REPO / "integraciones"))
    import s10
    lectura = s10.leer_relacion_de_insumos(PDF_S10.read_bytes())
    filas_s10 = [{"codigo": i.codigo, "descripcion": i.descripcion, "unidad": i.unidad, "tipo": i.tipo} for i in lectura.insumos]
    req = [{"insumo_codigo": i.codigo, "cantidad": str(round(i.cantidad, 4)), "precio": str(round(i.precio, 4))}
           for i in lectura.insumos if i.cantidad > 0]
    pruebas = [
        ("GET", "/obras", {}),
        ("GET", "/catalogo/insumos", {}),
        ("GET", "/proveedores?solo_para_comprar=true", {}),
        ("GET", f"/obras/{pid}/saldo-por-comprar", {}),
        ("GET", f"/obras/{pid}/compras", {}),
        ("GET", f"/obras/{pid}/solicitudes", {}),
        ("GET", f"/obras/{pid}/jornadas", {}),
        ("GET", f"/obras/{pid}/jornadas?limite=200", {}),          # paginación opcional (solo en la rama)
        ("GET", f"/obras/{pid}/compras?limite=50", {}),
        ("GET", "/catalogo/insumos?limite=100", {}),
        ("POST", "/catalogo/insumos/importar-s10", {"json": {"insumos": filas_s10}}),          # 2.ª vez: 284 sin cambios
        ("POST", f"/obras/{pid}/requerimiento/importar", {"json": {"filas": req}}),             # 2.ª vez: 284 sin cambios
    ]
    out = []
    for m, ruta, kw in pruebas:
        r, n, ms = sv.pedir(m, ruta, uid, **kw)
        r, n2, ms2 = sv.pedir(m, ruta, uid, **kw) if m == "GET" else (r, n, ms)
        out.append({"peticion": f"{m} {ruta.replace(f'/{pid}/', '/{id}/')}", "sentencias": n2, "ms": round(min(ms, ms2)),
                    "bytes": len(r.content), "gzip": len(gzip.compress(r.content, 6)),
                    "elementos": len(r.json()) if isinstance(r.json(), list) else None})
    print(json.dumps(out, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    orden = sys.argv[1] if len(sys.argv) > 1 else "explicar"
    if orden == "sembrar":
        sembrar()
    elif orden == "explicar":
        explicar("--indices" in sys.argv)
    elif orden == "contar":
        contar()
    else:
        raise SystemExit(__doc__)
