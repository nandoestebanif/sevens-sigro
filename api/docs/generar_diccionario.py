"""Diccionario de datos por microservicio, generado DESDE la base migrada.

Uso:  SIGRO_DB=sigro_dev python docs/generar_diccionario.py
Salida: docs/diccionario-datos.md

El significado de negocio de cada columna sale del modelo lógico (generar_er.E), que ya se verifica contra la
base; lo técnico (tipo, nulo, defecto, claves, CHECK, índices, permisos) se lee de la base. Nada se escribe a mano.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generar_er as ER  # noqa: E402
from introspeccion import ESQUEMAS, QUE_HACE, SERVICIO_DE, Modelo, Tabla, leer_modelo  # noqa: E402

SALIDA = Path(__file__).resolve().parent / "diccionario-datos.md"
TECNICAS = {"id": "Identificador interno (lo asigna la base)", "creado_en": "Momento en que se registró (hora del servidor)",
            "uuid_cliente": "Identificador generado en el dispositivo: clave de idempotencia (regla 3)",
            "fecha_servidor": "Hora del servidor al recibir el registro"}


def md(t: str | None) -> str:
    return (t or "").replace("|", "\\|").replace("\n", " ")


def significado(m: Modelo, t: Tabla, col: str) -> str:
    logico = ER.E.get(t.clave)
    if logico:
        for atributo, columna, _ in logico[1]:
            if columna == col:
                return atributo
    if col in TECNICAS:
        return TECNICAS[col]
    for f in m.fks:
        if f.origen == t.clave and col in f.cols:
            nombre = ER.E.get(f.destino, (f.destino,))[0]
            return f"Referencia a {nombre} ({f.destino})"
    return ""


def tipo_tabla(t: Tabla) -> str:
    if t.append_only:
        return "libro (solo se agrega)"
    if t.clave == "outbox.evento":
        return "bandeja de salida (eventos)"
    if t.clave == "compras.solicitud_correlativo":
        return "técnica (contador)"
    return "maestra / transaccional (no se borra)" if t.sin_borrar else "transaccional"


def tabla_md(m: Modelo, t: Tabla) -> list[str]:
    nombre, _, regla = ER.E.get(t.clave, ("—", [], ""))
    fk_cols = m.fk_cols(t.clave)
    cruzan = {c for f in m.fks if f.origen == t.clave and f.entre_servicios for c in f.cols}
    L = [f"### `{t.clave}` · {nombre}", "",
         f"- **Tipo:** {tipo_tabla(t)}" + (f" · **Regla:** {md(regla)}" if regla else ""),
         "- **Permisos:** " + (", ".join(f"`{r}` {p}" for r, p in t.privilegios.items()) or
                               "ningún rol escribe directo; solo su función `SECURITY DEFINER`"),
         ""]
    L += ["| Columna | Tipo | Nulo | Por defecto | Clave | Significado |", "|---|---|---|---|---|---|"]
    for c in t.columnas:
        clave = " ".join(x for x, ok in (("PK", c.nombre in t.pk), ("FK*" if c.nombre in cruzan else "FK", c.nombre in fk_cols)) if ok)
        defecto = "identidad" if c.identidad else (c.defecto or "")
        L.append(f"| `{c.nombre}` | {md(c.tipo)} | {'no' if c.no_nulo else 'sí'} | {md(defecto)} | {clave} | {md(c.comentario or significado(m, t, c.nombre))} |")
    L.append("")
    if t.unicos:
        L.append("**Únicos:** " + " · ".join(f"`({md(e)})`" + (f" si `{md(d)}`" if d else "") for e, d in t.unicos))
    if t.checks:
        L.append("")
        L.append("**CHECK:**")
        L += [f"- `{n}`: `{md(e)}`" for n, e in t.checks]
    fks = [f for f in m.fks if f.origen == t.clave]
    if fks:
        L.append("")
        L.append("**Claves foráneas:**")
        L += [f"- `({', '.join(f.cols)})` → `{f.destino}({', '.join(f.dcols)})` · al borrar: {f.al_borrar}"
              + (" · **cruza al servicio " + SERVICIO_DE[f.destino.split('.')[0]] + " (fase 1)**" if f.entre_servicios else "")
              for f in fks]
    if t.indices:
        L.append("")
        L.append("**Índices:** " + " · ".join(f"`{md(i)}`" for i in t.indices))
    if t.disparadores:
        L.append("")
        L.append("**Disparadores:** " + ", ".join(f"`{d}`" for d in t.disparadores))
    L.append("")
    return L


def main() -> None:
    m = leer_modelo()
    n_cols = sum(len(t.columnas) for t in m.tablas.values())
    L = ["# Diccionario de datos · SIGRO (por microservicio)", "",
         f"> Generado por `api/docs/generar_diccionario.py` desde la base migrada (PostgreSQL {m.version_pg}, migración "
         f"`{m.migracion}`). **No editar a mano:** se regenera. {len(m.tablas)} tablas, {n_cols} columnas, "
         f"{len(m.fks)} claves foráneas ({len(m.fks_entre_servicios)} entre servicios), {len(m.vistas)} vistas.", "",
         "Convenciones: **FK\\*** = clave foránea que apunta a la tabla de otro servicio (deuda de la fase 1, ver al final). "
         "Permisos: S select · I insert · U update · D delete. Ningún servicio tiene D: nada se borra (reglas 1 y 10).", "",
         "## Resumen por servicio", "",
         "| Microservicio | Esquema | Usuario → rol | Tablas | FK hacia otros servicios | Qué guarda |", "|---|---|---|---|---|---|"]
    for e in ESQUEMAS:
        s = SERVICIO_DE[e]
        L.append(f"| **{s}** | `{e}` | `sigro_{s}` → `sigro_svc_{s}` | {len(m.de(e))} | "
                 f"{sum(1 for f in m.fks_entre_servicios if f.origen.startswith(e + '.'))} | {QUE_HACE[s]} |")
    L.append("| **almacen** · **costo** | `almacen` · `costo` | por crear | — | — | movimientos, saldo, kárdex, costo promedio (por construir) |")
    L.append("")
    for e in ESQUEMAS:
        s = SERVICIO_DE[e]
        L += [f"## Microservicio `{s}` · esquema `{e}`", "", f"{QUE_HACE[s].capitalize()}.", ""]
        for t in m.de(e):
            L += tabla_md(m, t)
    L += ["## Vistas", ""] + [f"- `{v}`" for v in m.vistas] + [""]
    L += ["## Funciones `SECURITY DEFINER`", "",
          "Son las únicas que escriben periodos, plazos y correlativos, o validan contra el esquema de otro servicio "
          "sin darle a ese servicio permiso de lectura.", ""] + [f"- `{f}`" for f in m.funciones_definer] + [""]
    L += ["## Claves foráneas entre servicios (deuda de la fase 1)", "",
          "Hoy se conservan para no perder integridad mientras todos los esquemas viven en la misma instancia. El servicio "
          "que tiene la FK **no puede leer** la tabla a la que apunta (no tiene permiso): solo la referencia. "
          "En la fase 2 cada una se reemplaza por una réplica local alimentada por eventos y la FK se elimina (ADR-09).", "",
          "| Desde (servicio) | Columnas | Hacia (servicio) |", "|---|---|---|"]
    for f in sorted(m.fks_entre_servicios, key=lambda f: (f.origen, f.destino)):
        L.append(f"| `{f.origen}` ({SERVICIO_DE[f.origen.split('.')[0]]}) | `{', '.join(f.cols)}` | "
                 f"`{f.destino}` ({SERVICIO_DE[f.destino.split('.')[0]]}) |")
    L.append("")
    SALIDA.write_text("\n".join(L), encoding="utf-8")
    print(f"diccionario: {len(m.tablas)} tablas, {n_cols} columnas -> {SALIDA}")


if __name__ == "__main__":
    main()
