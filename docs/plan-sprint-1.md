# Plan de ataque del Sprint 1

15 al 28 de octubre de 2026 · 29 historias · 152 puntos comprometidos

Este archivo dice **quién hace qué, en qué orden y con qué pedirlo**. Si abres Claude Code
en tu carpeta, el `CLAUDE.md` de la raíz ya le da el contexto; aquí está la tarea.

## Antes que nada: los números, sin maquillaje

Un punto es esfuerzo relativo, no horas. Para planificar usamos **1 punto ≈ 2 horas** de
trabajo completo: backend, pantalla, pruebas y despliegue, no solo teclear.

| | |
|---|---|
| Comprometido | 152 puntos ≈ 304 horas |
| Capacidad del equipo | 4 personas × 8–12 h/semana × 2 semanas = **80 a 96 horas** |
| Diferencia | el compromiso es **tres veces** la capacidad |

Ese 2 h/punto asume programar a mano. Con Claude Code sobre este repositorio, un CRUD
puede salir en una fracción. Lo que **no** se acelera igual es lo difícil: la cola sin
señal con idempotencia, el costo promedio ponderado y la importación de 237 insumos.

Por eso **el día 4 se mide la velocidad real** y con ese número se decide qué se recorta,
en vez de discutirlo ahora.

## Frentes · cada uno dueño de lo suyo

| Persona | Su frente | Carpeta |
|---|---|---|
| **Henry Ticona** | Almacén y costo: movimientos, stock, corrección, valorización | `api/` (módulos almacen, costo) |
| **Fernando Istaña** | Catálogo e integraciones: S10, factor de conversión, requerimiento | `integraciones/`, `api/` (módulo catalogo) |
| **Víctor Luján** | Proyectos, usuarios y permisos, proveedores, tareo | `api/` (módulos obra, compras, tareo), `web/` |
| **Nicolás Pachamora** | Android completo | `android/` |

Tres personas tocan `api/`, así que la regla de carpetas se afina: **cada quien dentro de
su módulo**. Nadie edita un módulo ajeno sin avisar, y **solo Henry crea migraciones**.

---

## Bloque 1 · días 1 a 4 · fundaciones

Los cuatro en paralelo, sin pisarse. Al final de este bloque tiene que existir una API
desplegada, con usuarios y permisos, y una app Android que inicia sesión.

### Henry — el modelo de datos y el despliegue vacío

1. Levanta `api/`: FastAPI, SQLAlchemy 2, Pydantic v2, Alembic, PostgreSQL.
2. Crea los seis módulos vacíos: `obra`, `catalogo`, `almacen`, `compras`, `costo`, `tareo`.
3. **Modela el movimiento antes que nada.** Es la pieza de la que cuelga todo:
   - tabla append-only, sin `UPDATE` ni `DELETE`;
   - `uuid_cliente` único por movimiento, con índice único, para la idempotencia;
   - tipo de movimiento (ingreso, salida, traslado, reverso) y referencia al original
     cuando es un reverso;
   - costo unitario **congelado** en el movimiento;
   - **ninguna columna de saldo**.
4. Despliega a la nube aunque devuelva «hola». El ambiente de pruebas tiene que existir
   desde el día uno: la Definición de Terminado lo exige en cada historia.
5. Pide a los otros tres que revisen el modelo antes de que nadie construya encima.

> Qué pedirle a Claude Code: «Lee CLAUDE.md. Crea el módulo `almacen` con el modelo de
> movimiento append-only descrito en las reglas 1 a 4, su migración de Alembic reversible,
> y las pruebas unitarias del cálculo de saldo a partir de los movimientos, sin tocar la
> base de datos.»

### Víctor — sesión, roles y asignación (HU-12, HU-13, HU-14 · 13 pts)

1. Autenticación con contraseña, token y bloqueo por intentos. La contraseña nunca se
   guarda en claro.
2. Los **cinco roles** de `docs/permisos.md`: almacenero, SSOMA, residente, administrador,
   gerente.
3. Asignación de personas a obras, porque **todo permiso aplica solo a las obras donde la
   persona está asignada**.
4. Una dependencia reutilizable que verifique rol y obra, y que todos los endpoints usen.

> Qué pedirle: «Lee CLAUDE.md y docs/permisos.md. Implementa el módulo `obra` con usuarios,
> los cinco roles y la asignación a obras, más una dependencia de FastAPI que verifique rol
> y alcance de obra en el servidor, con pruebas de que un rol sin permiso recibe 403.»

### Fernando — importar el catálogo (HU-07 · 8 pts)

1. Microservicio `integraciones/` con un endpoint que reciba el reporte **Relación de
   insumos** del S10.
2. Prueba con el archivo real: presupuesto 2701006, 237 materiales, 6 insumos de mano de
   obra, 18 equipos, 23 subcontratos.
3. Idempotencia: importar dos veces el mismo archivo **no duplica**; un insumo existente se
   actualiza.
4. Una fila con unidad no reconocida se rechaza indicando su número y su motivo, y la
   importación no queda a medias.
5. Al terminar, informa cuántos se crearon, cuántos se actualizaron y cuántos fallaron.

> Qué pedirle: «Lee CLAUDE.md. Escribe el lector del reporte "Relación de insumos" del S10
> para el archivo que está en la carpeta de insumos del cliente, con detección de secciones
> (mano de obra, materiales, equipos, subcontratos) e idempotencia por código de insumo.»

### Nicolás — el esqueleto de Android

1. Proyecto con Kotlin, Compose, Room y WorkManager.
2. Pantalla de inicio de sesión contra la API de Víctor, y lista de obras asignadas.
3. Arranca HU-37: entidad de movimiento en Room y la cola de salida.
4. Las pantallas están diseñadas: pide a Fernando el enlace de los mockups.

> Qué pedirle: «Lee CLAUDE.md. Crea la entidad Room del movimiento con `uuid_cliente`
> generado en el dispositivo, la cola de envío con WorkManager y reintento, y una prueba
> instrumentada que registre en modo avión y verifique que al recuperar la señal se envía
> una sola vez.»

---

## Punto de control · día 4

En la daily del cuarto día, cada uno trae dos números: **puntos realmente terminados** —
terminados según la Definición, no «ya casi» — y **horas que le metió**.

```
velocidad real = puntos terminados ÷ horas invertidas
```

- Si sale cerca de **1 h/punto**, el sprint completo es alcanzable y se sigue.
- Si sale **2 h/punto o más**, se recorta ese mismo día, de prioridad Media hacia Crítica,
  y se avisa al Product Owner. No se cierra una historia a medias para que el número cuadre.

---

## Bloque 2 · días 5 a 10 · el núcleo

### Henry — salida y stock (HU-40, HU-43 · 13 pts)

- La salida exige almacén, insumo, cantidad, motivo y quién retira.
- No se puede sacar más de lo disponible en ese almacén.
- La salida se valoriza al costo promedio ponderado vigente y **ese valor queda congelado**.
- El stock se deriva de los movimientos, muestra saldo por almacén y consolidado, y la
  fecha del último movimiento considerado.

### Fernando — requerimiento desde el S10 (HU-16, HU-19 · 13 pts)

- Importación del análisis de precios unitarios y del requerimiento total.
- Ojo con el defecto conocido del PDF: el reporte trunca el código de partida en seis
  niveles, de modo que hay códigos repetidos con descripciones distintas. Importar por
  **código + descripción**, o pedir la exportación a Excel.
- El acero es la única excepción del catálogo: viene en kilos y se compra en varillas por
  diámetro. Se resuelve con el factor de conversión (HU-17).

### Víctor — proveedores y compra (HU-29, HU-30, HU-32 · 13 pts)

- Lista y alta rápida de proveedores, sin interrumpir el registro de la compra.
- La compra exige proveedor, comprobante y productos, y queda como constancia de lo
  adquirido. **Registrar la compra no mueve el stock**: el stock se mueve con la recepción.

### Nicolás — sincronización y pantallas (HU-38 · 5 pts + interfaz)

- Estado de cada movimiento: pendiente, sincronizado o rechazado con motivo.
- Pantallas de registro de salida y consulta de stock, sobre los mockups.
- La app **nunca** calcula el saldo: muestra lo que el servidor devolvió.

---

## Bloque 3 · lo que entre

En orden de prioridad, si la velocidad lo permite: recepción de compra (HU-35),
reposición (HU-25, HU-26, HU-27), corrección con reverso (HU-45), valorización (HU-55),
tareo (HU-62, HU-63), comparación contra lo planificado (HU-57) y dispositivo autorizado
(HU-15).

## Lo que no se negocia aunque apremie el tiempo

- Nadie trabaja directo sobre `main`; todo entra por PR aprobado por otro.
- Ninguna historia se da por terminada sin estar desplegada en el ambiente de pruebas y
  ejecutada ahí por alguien distinto del autor.
- Las reglas de dominio del `CLAUDE.md` se cumplen aunque cueste un día más. Un sistema que
  borra movimientos no resuelve el problema por el que existe.
