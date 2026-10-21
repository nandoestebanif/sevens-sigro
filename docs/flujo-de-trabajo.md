# Cómo trabajamos en el repositorio

## El ciclo de una historia

```bash
git checkout main
git pull                              # SIEMPRE antes de empezar, nunca después
git checkout -b hu-39-salida-material # una rama por historia, con su código
# ... trabajas, commiteas las veces que quieras ...
git push -u origin hu-39-salida-material
```

Abres el Pull Request, **otro lo aprueba**, y recién ahí entra a `main`.

## Reglas

| Regla | Por qué |
|---|---|
| Nadie trabaja directo sobre `main` | Si main se rompe, se rompe para los cuatro |
| PR aprobado por alguien distinto del autor, sin excepciones | Es criterio de la Definición de Terminado |
| El PR dice qué historia cubre y qué criterios de aceptación | Para que el revisor sepa qué está aprobando |
| **Solo Henry crea migraciones** | Dos migraciones simultáneas chocan. Si necesitas una tabla, pídesela |
| Cada quien toca su carpeta | Los conflictos son dos personas editando el mismo archivo |
| Rama de máximo dos días | Una rama larga acumula conflictos. Si no cierra en dos días, se parte la historia |
| Al cerrar sprint se etiqueta (`sprint-1`) | Esa etiqueta es lo que se demuestra y se entrega |

## Si hay conflicto

No es una catástrofe y no se arregla borrando la carpeta. Desde tu rama:

```bash
git pull origin main    # traes lo nuevo de main a tu rama
# git te marca los archivos en conflicto; los abres y decides qué queda
git add <archivo>
git commit
```

Si el conflicto es en una migración, para y habla con Henry.

## Niveles de prueba

| Nivel | Qué debe quedar cubierto sí o sí |
|---|---|
| Unitaria de dominio | Saldo a partir de movimientos · costo promedio ponderado tras varios ingresos a precios distintos · generación de periodos · factor de conversión del acero |
| Integración de la API | Que un reenvío con el mismo identificador no cree un segundo movimiento · que no se pueda sacar más de lo disponible · que un rol sin permiso reciba rechazo del servidor · que un movimiento no admita modificación ni borrado |
| Instrumentada Android | Registrar en modo avión, cerrar la app, recuperar señal y comprobar que llegó **una sola vez** |
| Aceptación | Cada criterio de la historia, recorrido por el Product Owner con los datos reales |

## Defectos

Van al tablero de incidencias del repositorio, **nunca al chat del grupo**. Un defecto
lleva: qué se hizo, qué se esperaba, qué pasó y en qué ambiente. Sin pasos para
reproducirlo, se devuelve. Ninguna historia cierra con un defecto crítico o bloqueante
abierto.
