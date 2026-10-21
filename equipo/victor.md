# Víctor Luján · Product Owner · Web

Tienes los dos sombreros: decides qué significa «hecho» para cada historia y construyes las pantallas de oficina, donde el residente y el gerente ven lo que el almacén produjo.

Tu carpeta es `web/`. Fuera de ahí, solo con Pull Request y aviso.

## Por dónde empiezas

1. Como Product Owner: marca los criterios en las 9 tarjetas y prepara las preguntas pendientes para Pool (EPP del SSOMA, stock del SSOMA, cuántos SSOMA por obra).
2. Proyecto React con Vite y TypeScript, y la pantalla de lista de obras.
3. La pantalla de reposición con la sugerencia de compra, que es la que el residente va a mirar todos los días.

## Lo que no puedes perder de vista

- Los criterios de aceptación los escribes tú y la aceptación de cada historia la firmas tú. Si no la aceptas, vuelve al Product Backlog con el motivo anotado.
- Las alertas se muestran con su explicación: cuánto queda, cuál es el punto de reposición y cuánto demora el proveedor. Una alerta sin motivo se ignora a la segunda semana.
- El requerimiento planificado es un horizonte, no un tope: el sistema avisa cuando se excede, nunca bloquea.
- Las pantallas están diseñadas: pídele a Fernando el enlace de los mockups.

## Tus historias del Sprint 1 — 7 historias, 37 puntos

| Código | Historia | Prioridad | Puntos | Depende de |
|---|---|---|:--:|---|
| HU-32 | Registrar compra | Crítica | 8 | HU-29, HU-30 |
| HU-35 | Registrar recepción de compra | Crítica | 8 | HU-32 |
| HU-57 | Comparar consumo contra lo planificado | Crítica | 8 | HU-19, HU-55 |
| HU-23 | Consultar avance de compra y consumo | Alta | 5 | HU-19, HU-35, HU-40 |
| HU-24 | Consultar saldo por comprar | Alta | 3 | HU-23 |
| HU-29 | Consultar proveedores | Alta | 2 | — |
| HU-30 | Registrar proveedor | Media | 3 | HU-29 |

Los criterios de aceptación de cada una están en `docs/historias-sprint-1.md`.

En el Sprint 2 te tocan 9 historias más, 30 puntos: `docs/historias-sprint-2.md`.
