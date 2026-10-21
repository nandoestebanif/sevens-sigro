# Henry Ticona · Developer · API

Construyes el corazón del sistema. Todo lo que el almacenero registra en el celular y todo lo que el gerente consulta en la web pasa por tu API, y el modelo de datos que armes decide si el resto se puede construir o no.

Tu carpeta es `api/`. Fuera de ahí, solo con Pull Request y aviso.

## Por dónde empiezas

1. Levanta el esqueleto: FastAPI, PostgreSQL, Alembic, los seis módulos vacíos, y **despliega a la nube aunque devuelva «hola»**. El ambiente de pruebas tiene que existir desde el día uno porque la Definición de Terminado lo exige en cada historia.
2. Modela el libro de movimientos antes que nada: append-only, `uuid_cliente` para la idempotencia, saldo derivado. Que los cuatro lo revisen antes de construir encima.
3. HU-12, HU-13 y HU-14 (sesión, roles y asignación). Sin usuarios no se puede probar nada con permisos, y `docs/permisos.md` ya te dice exactamente qué verificar en cada endpoint.

## Lo que no puedes perder de vista

- No hay `UPDATE` ni `DELETE` sobre movimientos. Si tu modelo los permite, el modelo está mal.
- El saldo no es una columna. Si aparece una, bórrala antes de que alguien la edite.
- El costo promedio se congela en la salida: un ingreso posterior no puede cambiar una salida ya registrada.
- Cada endpoint verifica rol y obra en el servidor. Esconder el botón no es control de acceso.
- Eres el único que crea migraciones. Si alguien necesita una tabla, te la pide.

## Tus historias del Sprint 1 — 14 historias, 63 puntos

| Código | Historia | Prioridad | Puntos | Depende de |
|---|---|---|:--:|---|
| HU-01 | Crear proyecto | Crítica | 3 | — |
| HU-03 | Dividir la obra en periodos semanales | Crítica | 3 | HU-01 |
| HU-08 | Registrar insumo estandarizado | Crítica | 5 | — |
| HU-12 | Iniciar sesión | Crítica | 5 | — |
| HU-13 | Asignar rol a usuario | Crítica | 5 | HU-12 |
| HU-14 | Asignar usuarios a proyectos | Crítica | 3 | HU-13 |
| HU-17 | Registrar factor de conversión | Crítica | 3 | HU-08 |
| HU-25 | Definir parámetros de reposición | Crítica | 5 | HU-08 |
| HU-26 | Avisar punto de reposición | Crítica | 5 | HU-25, HU-43 |
| HU-27 | Sugerir cantidad a comprar | Crítica | 5 | HU-24, HU-26 |
| HU-43 | Consultar stock de insumos | Crítica | 5 | HU-40 |
| HU-45 | Corregir movimiento con reverso | Crítica | 5 | HU-40 |
| HU-55 | Valorizar salida de material | Crítica | 8 | HU-35, HU-40 |
| HU-62 | Registrar trabajador de la obra | Alta | 3 | — |

Los criterios de aceptación de cada una están en `docs/historias-sprint-1.md`.

En el Sprint 2 te tocan 15 historias más, 48 puntos: `docs/historias-sprint-2.md`.
