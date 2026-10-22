# Arranque · Nicolás Pachamora

Diagrama de actividades del negocio, y después la app Android.

## Lo primero

1. Clona el repositorio y lee **`CLAUDE.md`**.
2. Lee **`docs/flujos.md`**. Ahí están los nueve procesos del negocio contados paso a paso,
   con el código de historia de cada paso. **Ese documento es el guion de tu diagrama**: no
   tienes que inventar el proceso, tienes que dibujarlo.
3. Lee tu archivo `equipo/nicolas.md` para lo de Android.

## El diagrama de actividades

Un diagrama de actividades del negocio muestra **quién hace qué y en qué orden**, no
pantallas ni tablas. Tres decisiones antes de empezar:

**1. Qué procesos dibujar.** No dibujes los nueve en una sola hoja: queda ilegible y el
profesor no puede evaluarlo. Lo que se defiende bien son tres diagramas:

| Diagrama | De dónde sale |
|---|---|
| Abastecer la obra | `docs/flujos.md`, secciones 2, 3 y 5 — cargar el requerimiento, comprar, y el aviso de reposición |
| Mover el material en almacén | sección 4 — recibir, ingresar, sacar, corregir, consultar |
| Controlar la mano de obra | sección 7 — registrar al trabajador, tomar asistencia, ver horas y costo |

**2. Carriles (swimlanes) por rol**, no por módulo. Los roles del sistema son cinco y están
en `docs/permisos.md`: almacenero, ingeniero SSOMA, residente de obra, administrador y
gerente. Agrega un carril para **el sistema**, porque hay pasos que no los hace nadie: el
aviso de reposición salta solo, y los periodos semanales se generan solos.

**3. Dónde están las decisiones.** Un diagrama de actividades sin rombos de decisión es una
lista disfrazada. Los que de verdad existen en este negocio:

- ¿Hay saldo suficiente para la salida? → si no, se rechaza
- ¿Hay señal? → si no, se encola y se envía después
- ¿Lo recibido supera lo comprado? → avisa, pero deja continuar
- ¿El saldo cruzó el punto de reposición? → dispara el aviso
- ¿El insumo está en el catálogo? → **esta no está resuelta**, es una de las preguntas
  abiertas al cliente

## Lo que el diagrama tiene que reflejar sí o sí

Son las reglas del dominio, y si el diagrama las contradice, contradice al sistema:

- Un movimiento **no se corrige editando**: se corrige con un movimiento inverso. En el
  diagrama es una actividad propia, no una flecha de vuelta.
- El registro **sin señal no es un error**: es un camino normal que termina en el mismo
  lugar, solo que más tarde.
- **Registrar la compra no mueve el stock.** El stock se mueve con la recepción. Son dos
  actividades separadas, de dos roles distintos, en momentos distintos.
- El aviso de reposición **lo hace el sistema**, no una persona.

## Al final del diagrama

Incluye el **final alterno**: qué pasa cuando el servidor rechaza un movimiento. Está
diseñado en la pantalla 8 del lienzo y casi nadie lo dibuja, así que suma.

## Después: la app

Tu parte de código está en `equipo/nicolas.md` y `docs/plan-sprint-1.md`. Lo primero es el
proyecto Android con la cola local, antes que cualquier pantalla bonita. Las pantallas ya
están diseñadas: pídele a Fernando el enlace del lienzo.
