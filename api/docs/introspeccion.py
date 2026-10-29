"""Lee el modelo FÍSICO real de la base migrada, ordenado por microservicio (ADR-09).

Es la única fuente de los diagramas físicos y del diccionario de datos: nada se dibuja ni se escribe a mano,
así que la documentación no puede contradecir a la base. Si alguien agrega una tabla, una columna o un permiso
en una migración, basta con volver a generar.

Uso desde otros scripts:  from introspeccion import leer_modelo;  m = leer_modelo()
"""
from __future__ import annotations

import os
import re
from dataclasses import dataclass, field

import psycopg
from psycopg.rows import dict_row

BASE = os.environ.get("SIGRO_DB", "sigro_dev")
DSN = os.environ.get("SIGRO_DSN", f"postgresql://sigro_owner:sigro_owner_dev@localhost:5432/{BASE}")

# esquema -> microservicio dueño (ADR-09). outbox lo escriben obras y compras, pero lo administra el relevo.
SERVICIO_DE = {"obra": "obras", "catalogo": "catalogo", "compras": "compras", "tareo": "tareo", "outbox": "relevo"}
ESQUEMAS = list(SERVICIO_DE)
ROLES = [f"sigro_svc_{s}" for s in ("obras", "catalogo", "compras", "tareo", "relevo")]
COLOR = {  # cabecera, fondo del grupo
    "obra": ("#1F4E79", "#EAF1F8"),
    "catalogo": ("#7A4B00", "#FBF3E4"),
    "compras": ("#2E6B3A", "#EAF5EC"),
    "tareo": ("#6B2E6B", "#F6ECF6"),
    "outbox": ("#8A3B12", "#FBEDE6"),
}
QUE_HACE = {
    "obras": "identidad, sesión, usuarios, obras, periodos, almacenes, asignaciones, dispositivos; decide quién puede qué",
    "catalogo": "código único de insumo, nombres comerciales, factor de conversión, importación del S10",
    "compras": "proveedores, compras, solicitudes, requerimiento y reposición",
    "tareo": "trabajadores y asistencia (horas hombre)",
    "relevo": "bandeja de salida (outbox): entrega los eventos a Notificaciones",
}


@dataclass
class Columna:
    nombre: str
    tipo: str
    no_nulo: bool
    defecto: str | None
    comentario: str | None
    identidad: bool


@dataclass
class Fk:
    nombre: str
    origen: str           # esquema.tabla
    cols: list[str]
    destino: str
    dcols: list[str]
    al_borrar: str
    anulable: bool

    @property
    def entre_servicios(self) -> bool:
        return self.origen.split(".")[0] != self.destino.split(".")[0]

    @property
    def compuesta(self) -> bool:
        return len(self.cols) > 1


@dataclass
class Tabla:
    clave: str
    comentario: str | None
    columnas: list[Columna] = field(default_factory=list)
    pk: list[str] = field(default_factory=list)
    unicos: list[tuple[str, str | None]] = field(default_factory=list)     # (columnas o expresión, condición parcial)
    indices: list[str] = field(default_factory=list)                       # no únicos, ya formateados
    checks: list[tuple[str, str]] = field(default_factory=list)            # (nombre, expresión)
    disparadores: list[str] = field(default_factory=list)
    privilegios: dict[str, str] = field(default_factory=dict)              # rol -> "S I U D" (solo los que tiene)
    filas_estimadas: int = 0

    @property
    def esquema(self) -> str:
        return self.clave.split(".")[0]

    @property
    def nombre(self) -> str:
        return self.clave.split(".")[1]

    @property
    def append_only(self) -> bool:
        return any(d.endswith("_append_only") for d in self.disparadores)

    @property
    def sin_borrar(self) -> bool:
        return any(d.endswith("_no_borrar") for d in self.disparadores)

    @property
    def reglas(self) -> list[str]:
        return [d.removeprefix("tg_") for d in self.disparadores if not d.endswith(("_no_borrar", "_no_truncar", "_append_only"))]


@dataclass
class Modelo:
    tablas: dict[str, Tabla]
    fks: list[Fk]
    vistas: list[str]
    funciones_definer: list[str]
    migracion: str
    version_pg: str

    def de(self, esquema: str) -> list[Tabla]:
        return [t for t in self.tablas.values() if t.esquema == esquema]

    def fk_cols(self, clave: str) -> set[str]:
        return {c for f in self.fks if f.origen == clave for c in f.cols}

    @property
    def fks_entre_servicios(self) -> list[Fk]:
        return [f for f in self.fks if f.entre_servicios]


def _abreviar(tipo: str) -> str:
    return (tipo.replace("timestamp with time zone", "timestamptz").replace("character varying", "varchar")
            .replace("timestamp without time zone", "timestamp"))


def leer_modelo(dsn: str = DSN) -> Modelo:
    with psycopg.connect(dsn, row_factory=dict_row) as c:
        def q(sql, *a):
            return c.execute(sql, a).fetchall()

        tablas: dict[str, Tabla] = {}
        fks: list[Fk] = []
        for t in q("""SELECT n.nspname AS esquema, cl.relname AS tabla, cl.oid AS oid, obj_description(cl.oid, 'pg_class') AS com,
                             GREATEST(cl.reltuples, 0)::bigint AS filas
                        FROM pg_class cl JOIN pg_namespace n ON n.oid = cl.relnamespace
                       WHERE cl.relkind IN ('r', 'p') AND n.nspname = ANY(%s) ORDER BY 1, 2""", ESQUEMAS):
            k = f"{t['esquema']}.{t['tabla']}"
            tb = Tabla(k, t["com"], filas_estimadas=t["filas"])
            tb.columnas = [Columna(r["nombre"], _abreviar(r["tipo"]), r["no_nulo"], r["defecto"], r["com"], r["identidad"]) for r in q("""
                SELECT a.attname AS nombre, format_type(a.atttypid, a.atttypmod) AS tipo, a.attnotnull AS no_nulo,
                       pg_get_expr(d.adbin, d.adrelid) AS defecto, col_description(a.attrelid, a.attnum) AS com,
                       (a.attidentity <> '') AS identidad
                  FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
                 WHERE a.attrelid = %s AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum""", t["oid"])]
            for f in q("""
                SELECT i.indisprimary AS es_pk, i.indisunique AS unico, pg_get_indexdef(i.indexrelid) AS def, ic.relname AS nombre,
                       (SELECT array_agg(a.attname ORDER BY k.ord) FROM unnest(i.indkey) WITH ORDINALITY k(attnum, ord)
                          JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum WHERE k.attnum > 0) AS cols
                  FROM pg_index i JOIN pg_class ic ON ic.oid = i.indexrelid WHERE i.indrelid = %s ORDER BY ic.relname""", t["oid"]):
                if f["es_pk"]:
                    tb.pk = f["cols"]
                    continue
                donde = re.search(r"WHERE \((.*)\)$", f["def"])
                donde = re.sub(r"\)?::text", "", donde.group(1)) if donde else None
                cuerpo = re.search(r"USING \w+ \((.*?)\)(?: WHERE|$)", f["def"])
                etiqueta = cuerpo.group(1) if cuerpo else ", ".join(f["cols"] or [])
                if f["unico"]:
                    tb.unicos.append((etiqueta, donde))
                else:
                    tb.indices.append(f"{f['nombre']} ({etiqueta})" + (f" WHERE {donde}" if donde else ""))
            tb.checks = [(r["conname"], re.sub(r"^CHECK \((.*)\)$", r"\1", r["def"])) for r in q(
                "SELECT conname, pg_get_constraintdef(oid) AS def FROM pg_constraint WHERE conrelid = %s AND contype = 'c' ORDER BY conname", t["oid"])]
            tb.disparadores = [r["tgname"] for r in q(
                "SELECT tgname FROM pg_trigger WHERE tgrelid = %s AND NOT tgisinternal ORDER BY 1", t["oid"])]
            for rol in ROLES:
                p = q("""SELECT has_any_column_privilege(%s, %s::oid, 'SELECT') AS s, has_table_privilege(%s, %s::oid, 'INSERT') AS i,
                                has_any_column_privilege(%s, %s::oid, 'UPDATE') AS u, has_table_privilege(%s, %s::oid, 'DELETE') AS d""",
                      rol, t["oid"], rol, t["oid"], rol, t["oid"], rol, t["oid"])[0]
                letras = " ".join(l for l, ok in (("S", p["s"]), ("I", p["i"]), ("U", p["u"]), ("D", p["d"])) if ok)
                if letras:
                    tb.privilegios[rol] = letras
            for r in q("""SELECT conname, confrelid::regclass::text AS destino, conkey, confkey, confrelid, confdeltype
                            FROM pg_constraint WHERE conrelid = %s AND contype = 'f' ORDER BY conname""", t["oid"]):
                def nombres(oid, nums):
                    return [a["attname"] for a in q("SELECT attname FROM pg_attribute WHERE attrelid=%s AND attnum = ANY(%s) "
                                                    "ORDER BY array_position(%s, attnum)", oid, nums, nums)]
                origen = nombres(t["oid"], r["conkey"])
                destino = r["destino"] if "." in r["destino"] else f"public.{r['destino']}"
                anulable = any(not col.no_nulo for col in tb.columnas if col.nombre in origen)
                accion = {"a": "NO ACTION", "r": "RESTRICT", "c": "CASCADE", "n": "SET NULL", "d": "SET DEFAULT"}[r["confdeltype"]]
                fks.append(Fk(r["conname"], k, origen, destino, nombres(r["confrelid"], r["confkey"]), accion, anulable))
            tablas[k] = tb

        vistas = [f"{r['schemaname']}.{r['viewname']}" for r in q(
            "SELECT schemaname, viewname FROM pg_views WHERE schemaname = ANY(%s) ORDER BY 1, 2", ESQUEMAS)]
        funciones = [f"{r['nspname']}.{r['proname']}()" for r in q(
            "SELECT n.nspname, p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace "
            "WHERE n.nspname = ANY(%s) AND p.prosecdef ORDER BY 1, 2", ESQUEMAS)]
        migracion = c.execute("SELECT version_num FROM public.alembic_version").fetchone()["version_num"]
        version_pg = c.execute("SHOW server_version").fetchone()["server_version"].split(" ")[0]
    return Modelo(tablas, fks, vistas, funciones, migracion, version_pg)
