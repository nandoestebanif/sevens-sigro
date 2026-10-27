# Arranque · Víctor Adiel Luján

> **Archivo del 21/10, solo como historia.** Lo vigente para el Sprint 1 está en `equipo/LEEME.md` y en el archivo de cada uno.

Base de datos, microservicios y backend. Este archivo es lo que necesitas para empezar sin
preguntarle nada a nadie.

## Lo primero, en orden

1. Clona el repositorio y lee **`CLAUDE.md`** entero. Son diez reglas; las diez condicionan
   el modelo de datos.
2. Lee **`docs/modelo-datos.md`**. Ahí está el modelo completo con sus restricciones y el
   porqué de cada una.
3. Lee **`docs/decisiones-arquitectura.md`**, ADR-01 y ADR-03. Son las dos que te tocan.
4. Abre Claude Code en la carpeta del repositorio. Lee el `CLAUDE.md` solo.

## Lo que tienes que producir

| Entregable | Qué debe mostrar |
|---|---|
| **Diagrama físico de la base de datos** | Tablas, claves, y sobre todo los **índices únicos** que sostienen las reglas: `uuid_cliente`, `insumo.codigo`, `(trabajador_id, fecha)`, `proveedor.ruc`. Con una nota en `movimiento`: append-only, sin columna de saldo |
| **Esquema de microservicios** | Dos piezas: la API monolito modular con sus seis módulos, y el microservicio de integraciones. La frontera entre ambos y cómo se hablan |
| **Backend** | Los módulos `obra`, `compras` y `tareo` son tuyos. `almacen` y `costo` son de Henry |

## Las trampas de este modelo

Son cuatro y las cuatro son fáciles de romper sin darse cuenta:

1. **No pongas una columna `saldo`.** Es lo primero que uno quiere hacer y mata la
   trazabilidad, que es el problema que el sistema existe para resolver. El saldo se suma
   desde los movimientos.
2. **No permitas `UPDATE` ni `DELETE` sobre `movimiento`.** No basta con no escribirlo en
   el código: quítale el permiso al usuario de la aplicación en la base.
3. **`uuid_cliente` único es lo que impide duplicar.** Si lo dejas sin índice único, un
   reenvío sin señal saca el material dos veces del almacén.
4. **El rol no va en `usuario`.** Va en `asignacion`, junto al proyecto: la misma persona
   puede ser almacenero en una obra y nada en otra.

## Tu primer prompt a Claude Code

Pégalo tal cual, estando dentro de la carpeta del repositorio:

> Lee CLAUDE.md y docs/modelo-datos.md. Genera el esquema físico de PostgreSQL con las
> migraciones de Alembic para los módulos obra, compras y tareo, respetando: movimiento
> append-only sin columna de saldo, uuid_cliente con índice único, el rol en la tabla
> asignacion junto al proyecto, y la jornada única por trabajador y fecha. Cada migración
> tiene que correr sobre base limpia y sobre base con datos, y declarar su vuelta atrás.
> No toques los módulos almacen ni costo: son de otro integrante.

## Tus historias del Sprint 1

Están en `equipo/victor.md` con sus puntos y dependencias, y los criterios de aceptación de
cada una en `docs/historias-sprint-1.md`.

## Reglas de trabajo

- Rama por historia: `hu-12-iniciar-sesion`. Nunca directo sobre `main`.
- Pull Request aprobado por otro, siempre.
- **Solo Henry crea migraciones** una vez que el esqueleto esté en pie. Mientras levantas
  el esquema inicial, coordínalo con él para no chocar.
- Como Product Owner tienes además un trabajo que no es código: marcar los criterios en las
  tarjetas y aceptar o rechazar cada historia.
