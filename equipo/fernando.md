# Fernando Istaña · Scrum Master · Integraciones

Eres el puente con lo que ya existe en la empresa. Todo lo que entra al sistema desde fuera pasa por tu microservicio: los reportes del S10 y la consulta de RUC en SUNAT.

Tu carpeta es `integraciones/`. Fuera de ahí, solo con Pull Request y aviso.

## Por dónde empiezas

1. Define el formato de importación contra el archivo real: la **Relación de insumos** del presupuesto 2701006 tiene 237 materiales, 6 insumos de mano de obra con horas planificadas, 18 equipos y 23 subcontratos.
2. HU-07 importar el catálogo. Destraba HU-16, HU-17 y HU-19.
3. Como Scrum Master: conseguir lo que al equipo le falta y mantener el tablero al día. Hoy lo que falta es confirmar que el repo y el entorno corren en las cuatro máquinas.

## Lo que no puedes perder de vista

- Importar dos veces el mismo archivo **no duplica** insumos: la importación es idempotente.
- Una fila mala se rechaza con su número y su motivo; la importación nunca queda a medias.
- El acero es la única excepción: el S10 lo da en kilos y se compra en varillas por diámetro. Se resuelve con el factor de conversión, no a mano.
- Los archivos del cliente no se editan. Lo que necesites derivar, se genera.

## Tus historias del Sprint 1 — 3 historias, 21 puntos

| Código | Historia | Prioridad | Puntos | Depende de |
|---|---|---|:--:|---|
| HU-07 | Importar catálogo de insumos | Crítica | 8 | — |
| HU-16 | Importar análisis de precios unitarios | Crítica | 8 | HU-07 |
| HU-19 | Importar requerimiento total de obra | Crítica | 5 | HU-07, HU-17 |

Los criterios de aceptación de cada una están en `docs/historias-sprint-1.md`.

En el Sprint 2 te tocan 1 historias más, 5 puntos: `docs/historias-sprint-2.md`.
