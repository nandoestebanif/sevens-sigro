# Diseño de la app del almacenero (aprobado por pantalla)

Fuente visual: el lienzo de Claude Design **«SIGRO Almacén · App (Android + iOS)»**
(privado de Fernando: https://claude.ai/artifact/KTkoYe5QpwdimW6T7AuQFk).
Esta carpeta es la copia en el repositorio para programar sin depender del lienzo.

| Carpeta | Qué hay |
|---|---|
| `lienzo/*.dc.html` | Cada pantalla del lienzo (HTML + lógica de la maqueta). Son **referencia**: medidas, textos, colores y comportamiento. No se ejecutan fuera del lienzo. |
| `lienzo/canvas.json` | Índice: páginas, título de cada pantalla y su HU. |
| `recursos/` | Logo de SEVEN'S en color (`sevens-logo.png`) y en blanco para fondos oscuros (`sevens-logo-blanco.png`). Aprobado por el cliente para la app. |
| `fondos/fondos.py` | Generador de los fondos de plano (SVG) de cada pantalla. |

## Decisiones tomadas (28/10)

1. **Estilo B «claro moderno» + vidrio (glassmorphism)** sobre un **fondo de ingeniería civil distinto por pantalla**.
   El estilo blanco anterior y las propuestas A y C quedaron descartadas.
2. **Inicio de sesión:** foto de obra arriba (hoy ilustración; `foto` reemplazable), tarjeta de vidrio, botón negro.
   **Animación aprobada: «el plano se dibuja»** (reemplaza al telón, que se veía antiguo).
3. **Elegir obra:** imagen de fondo, **perfil del almacenero** antes de entrar, **buscador al centro**,
   y en cada obra el estado del celular: **la autorización es por celular y por obra** (regla 5).
4. **Inicio = «Almacén»:** resumen del día, accesos Salida · Entrada · Stock · **Más** (hoja con ingreso sin compra,
   traslado, conteo, cerrar obra, mi celular), últimos registros, botón flotante «Registrar salida», barra inferior de vidrio.
5. **Catálogo (3), Herramientas (6) y Mano de obra (3) diseñadas el 28/10** en estilo vidrio; se entra desde «Más › Otros recursos». Las de Materiales se revisan una por una; las de Materiales
   ya existen en estilo B **sin** vidrio todavía (pasarlas al vidrio con su propio fondo al revisarlas).

## Fichas técnicas (tokens)

| Token | Valor |
|---|---|
| Tipografía | **Inter** 400/500/600/700/800 (títulos 28–36 sp, 800, tracking −0.03em; cuerpo 15–17 sp) · **Sora** solo para la marca «SIGRO» en la entrada (700, 58 sp, tracking +0.06em, degradado blanco → `#FED7AA` → `#FB923C`) y su bajada «ALMACÉN DE OBRA» (600, 13 sp, tracking +0.34em, entre dos filetes naranja) |
| Texto | `#111827` principal · `#374151`/`#4B5563` secundario |
| Acento | naranja `#C2410C` (botones de acción, íconos) · claro `#FFEDD5` · logo `#F26522` |
| Botón principal | negro `#111827`, píldora 58 dp de alto, radio 29 |
| Vidrio | blanco 58 % + desenfoque 18 px + saturación 160 % + borde blanco 75 % + sombra suave · «vidrio fuerte»: 78 % y 22 px |
| Radios | tarjetas 24–28 · accesos 22 · chips 999 |
| Estados (siempre ícono + palabra + color) | por enviar `#92400E` / `#FEF3C7` · enviado `#166534` / `#DCFCE7` · rechazado `#991B1B` / `#FEE2E2` |
| Fondo base | degradado `#E8EEF6 → #F3F1EE → #F6E9DF` + cuadrícula 24 px (fina) y 120 px (gruesa) + manchas difusas naranja `#FB923C`, azul `#60A5FA`, verde agua `#2DD4BF` |
| Áreas táctiles | ≥ 44 dp (botones principales 58–62) |

## Fondos por pantalla

| Pantalla | Fondo |
|---|---|
| Entrar | Planta arquitectónica: ejes A·B·C / 1·2·3, muros, puertas, ambientes, cota |
| Elegir obra | Topografía: curvas de nivel suaves, lote punteado, norte (más azul / verde agua) |
| Inicio | Obra en marcha: grúa torre, edificio isométrico, viga en I, cota |
| Mi celular | Detalle estructural: columna con 8 fierros y estribos, zapata, ondas de señal |
| Catálogo | Estante isométrico (buscar) · sección de varillas Ø (ficha) · etiqueta con código y saco de 42.5 kg (nuevo) |
| Herramientas | Andamio (lista) · mezcladora (ficha) · caja de herramientas (ingreso) · carretilla (entrega) · andamio y flecha de retorno (devolución) · engranaje y calendario (mantenimiento o baja) |
| Mano de obra | Cascos y cuadrilla (trabajadores) · documento de identidad (nuevo) · cronograma semanal (asistencia) |
| (por hacer) | Materiales: pasarlas al vidrio con estanterías de almacén |

Son decorativos: **nunca** coordenadas, áreas ni datos que parezcan reales de la obra.

## Animaciones

| Dónde | Qué | Duración |
|---|---|---|
| Entrar → Elegir obra | **El plano se dibuja**: capa de plano; 8 grupos de trazos se dibujan en cascada (terreno, columnas, vigas, techo, grúa, puertas, ventanas, cota); aparece el logo con «Preparando tu obra…»; la capa se desvanece y crece 6 % mostrando la pantalla siguiente | trazos ~2,3 s; el logo **dura lo que tarda el servidor**; salida 0,6 s |
| Inicio (propuestas) | tarjetas en cascada, números que cuentan, check que se dibuja al guardar, chip que cambia a «enviado», tarjeta de obra que crece hasta la pantalla (*shared element*), vibración corta al guardar o al rechazo | < 0,5 s cada una |

Reglas: ninguna animación hace esperar de más; con «reducir movimiento» del sistema se apagan.

## Cómo llevarlo a Compose Multiplatform

- **Fuente Inter y logos:** `composeResources/font` y `composeResources/drawable` (plugin de recursos de Compose).
- **Vidrio:** `Modifier.blur` desenfoca el propio contenido, no lo de atrás. Para el efecto real usar la librería
  **Haze** (`dev.chrisbanes.haze`, soporta Android e iOS) o, como respaldo, blanco 70–80 % sin desenfoque
  (Android < 12 o equipos lentos).
- **Fondos:** convertir el SVG de `fondos.py` a `ImageVector` o dibujarlo en `Canvas` (líneas, círculos, `Brush.radialGradient`).
- **Plano que se dibuja:** cada trazo como `Path` + `PathMeasure.getSegment(0, largo × progreso)` animado con
  `Animatable`, con retraso escalonado; el logo con `AnimatedVisibility`.
- La lógica que ya existe (`ui/pantallas/*ViewModel`, repositorios, cola sin señal) **no cambia**: solo cambia la capa visual.
