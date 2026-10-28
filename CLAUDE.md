# SIGRO · contexto del repositorio

Sistema de Gestión de Recursos de Obra para **SEVENS INGENIEROS SAC** (RUC 20606456892),
constructora de Lima. Proyecto del curso Ingeniería de Software I, UPeU, ciclo 2026-2.
Cliente real; el contacto es Pool Stiwart Briones Sánchez.

Antes de escribir código, lee este archivo entero. Las reglas de dominio no son
preferencias de estilo: si una se rompe, el sistema deja de servir para lo que fue hecho.

## El problema que resolvemos

SEVENS pierde jornadas de obra por falta de material y a la vez inmoviliza capital en
material que sobra, porque el almacén se controla entre el cuaderno del almacenero y
hojas de cálculo sueltas. Datos del propio cliente: **más de 5 interrupciones de obra al
mes**, cada una de algunas horas, con un costo de jornada detenida de **S/ 1 000 a
S/ 5 000**.

Los recursos de obra son tres familias y el sistema cubre las tres: **materiales**,
**herramientas y equipos**, y **horas hombre**.

El sistema informa el **gasto en recursos**, no el costo total de la obra: los gastos
generales, los subcontratos, los servicios y la planilla quedan fuera y el propio tablero
lo declara.

## Reglas de dominio · no negociables

1. **El movimiento es un libro que no se borra.** Ningún movimiento se modifica ni se
   elimina. Una corrección es un movimiento inverso que referencia al original y guarda
   autor, fecha y motivo. No hay `UPDATE` ni `DELETE` sobre la tabla de movimientos.
2. **El saldo no es un campo.** El stock se deriva de los movimientos y lo calcula el
   servidor. El cliente Android nunca calcula saldo: muestra lo que el servidor devolvió.
3. **Un reenvío no duplica.** Cada movimiento nace con un identificador único generado en
   el dispositivo (`uuid_cliente`). El servidor lo usa como clave de idempotencia: si
   recibe uno repetido, no crea nada y devuelve el resultado del original.
4. **Costo promedio ponderado.** Cada ingreso recalcula el promedio del insumo en ese
   almacén. Cada salida toma el promedio vigente en ese instante y **lo congela en el
   movimiento**: un ingreso posterior no cambia el costo de una salida ya registrada
   (NIC 2).
5. **Un dispositivo autorizado por responsable.** Cada obra admite hasta dos dispositivos
   autorizados: el del almacenero y el del ingeniero SSOMA. El servidor rechaza el
   registro que venga de un dispositivo no autorizado para esa obra.
6. **El control del costo es por insumo, no por partida.** El cliente pidió postergar la
   imputación a partidas; el reporte de recursos del S10 ya trae el costo por insumo.
7. **El requerimiento avisa, no bloquea.** Lo planificado incluye desperdicio y reserva:
   comprar o consumir más de lo previsto es normal en obra. El sistema advierte y deja
   continuar.
8. **Código único de insumo.** El mismo insumo lleva el mismo código en todas las obras.
9. **El alcance es la obra.** Todo permiso aplica solo a las obras donde la persona está
   asignada, y se verifica en el servidor. Que la pantalla esconda un botón no es control
   de acceso.
10. **Nada se borra, tampoco las personas.** Un trabajador que se retira se da de baja con
    su fecha de salida; sus jornadas registradas se conservan.

## Stack y arquitectura

| Pieza | Tecnología |
|---|---|
| API | Python · FastAPI · SQLAlchemy 2 · Pydantic v2 · Alembic |
| Base de datos | PostgreSQL |
| App del almacenero | Kotlin Multiplatform (Android + iOS) · Compose Multiplatform · Koin · Ktor · Room KMP · WorkManager (ADR-07) |
| Web de oficina | React 18 · TypeScript · Vite |
| Integraciones | Microservicio FastAPI aparte (importación del S10, consulta de RUC en SUNAT) |
| Despliegue | Nube gestionada, PostgreSQL administrado, APK por enlace |

**Monolito modular** para la API: seis módulos por dominio (obra, catálogo, almacén,
compras, costo, tareo), cada uno con su esquema y **sin consultas cruzadas directas**
entre módulos. Lo que depende de terceros vive en el microservicio, para que una
importación pesada no deje lenta la app del almacenero.

El detalle y las alternativas descartadas están en `docs/decisiones-arquitectura.md`.

## Estructura del repositorio

```
api/            Adiel Luján       · FastAPI, módulos de dominio, migraciones
integraciones/  Fernando Istaña   · microservicio S10 y SUNAT
android/        Nicolás Pachamora · app del almacenero (Android + iOS)
web/            Fernando y Henry  · React de oficina
docs/                             · historias, reglas, permisos, decisiones
equipo/                           · qué hace cada uno esta semana
```

**Cada quien toca su carpeta.** Los conflictos de Git casi siempre son dos personas
editando el mismo archivo; con módulos separados eso deja de pasar.

## Cómo trabajamos

- `main` siempre desplegable. **Nadie trabaja directo sobre main.**
- Una rama por historia, con su código: `hu-39-salida-material`.
- Todo entra por **Pull Request aprobado por alguien distinto del autor**, aunque el
  cambio sea de una línea. El PR dice qué historia cubre y qué criterios de aceptación.
- **Solo Adiel crea migraciones.** Si necesitas una tabla, pídesela. Dos migraciones
  simultáneas chocan.
- Una rama que vive más de dos días acumula conflictos: si la historia no cierra en dos
  días, se parte.
- Al cerrar cada sprint se etiqueta el repositorio (`sprint-1`, `sprint-2`). Esa etiqueta
  es lo que se demuestra y lo que se entrega al docente.

## Definición de Terminado

Una historia está terminada cuando, además de cumplir sus criterios de aceptación:

- entró por PR aprobado por otro;
- su migración corre sobre base limpia y sobre base con datos, y se puede revertir;
- la lógica de dominio tiene pruebas que corren sin levantar la base;
- las reglas críticas que le aplican están probadas (idempotencia, no borrado, saldo del
  servidor, dispositivo autorizado, control de acceso rechazado desde el servidor);
- las historias de Android se probaron **en modo avión y recuperando la señal después**;
- está desplegada en el ambiente de pruebas y alguien distinto del autor la ejecutó ahí;
- se demuestra desde la interfaz, no abriendo una tabla de la base;
- el endpoint quedó documentado y toda regla que la historia precisó quedó escrita aquí;
- el Product Owner (Adiel) la aceptó.

## Datos reales del cliente

Los reportes del S10 del presupuesto **2701006**, subpresupuesto **002 «Multifamiliar
Flores»** (fecha 09/01/2026) están en la carpeta de insumos del cliente y **no se editan**:

- **Relación de insumos** (5 páginas) — la base de la importación: 6 insumos de mano de
  obra por S/ 542 998 con sus horas planificadas, **237 materiales** por S/ 768 310,
  18 equipos y 23 subcontratos. Cada fila trae código, unidad, cantidad, precio y parcial.
- **Análisis de precios unitarios** (371 páginas) — partida → recurso. Solo hace falta si
  algún día se baja a nivel de partida; hoy no.
- **Presupuesto** (39 páginas) — el árbol de partidas con metrado y precio.

El **acero corrugado fy=4200** aparece como un solo insumo en kilos (36 357 kg a
S/ 4.54) y se compra en varillas por diámetro: es la única excepción del catálogo y se
resuelve con el factor de conversión, nunca a mano.

Las pruebas de importación se hacen con estos archivos reales. Una historia de importación
probada con datos inventados **no está terminada**.

## Convenciones de código

- Python: tipado en firmas públicas, nombres de dominio en español (`movimiento`,
  `almacen`, `insumo`), código en inglés solo donde el framework lo impone.
- Un endpoint declara el rol y el alcance que exige; la verificación va en el servidor.
- Toda cifra de dinero se guarda con su unidad y su momento: el costo de una salida es el
  del instante en que se registró.
- Nada de borrados lógicos improvisados: la baja es un estado con fecha.

## Dónde está cada cosa

| Archivo | Qué contiene |
|---|---|
| `docs/historias-sprint-1.md` | Las 29 historias del Sprint 1 con sus criterios de aceptación |
| `docs/historias-sprint-2.md` | Las 38 del Sprint 2: 31 originales, HU-66 y HU-67 (CR-04) y las 5 que antes iban a una siguiente iteración |
| `docs/permisos.md` | Matriz de roles y permisos |
| `docs/scrum/tablero-sprint-1.md` | Tablero del Sprint 1: tareas por HU, responsables, reglas To Do → Doing → Done |
| `docs/decisiones-arquitectura.md` | Por qué el sistema es como es |
| `docs/flujo-de-trabajo.md` | Git, ramas, PR y pruebas |
| `equipo/*.md` | Qué le toca a cada uno |
