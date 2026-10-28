# Decisiones de arquitectura

Cada decisión se registra con el contexto que la obligó, las alternativas evaluadas y las
consecuencias que se aceptan. Una decisión sin alternativas descartadas no es una
decisión: es una costumbre.

## ADR-01 · Monolito modular + microservicio de integraciones
**Contexto.** Seis dominios, dos integraciones con terceros (S10, SUNAT), cuatro personas
con dedicación parcial y cuatro semanas.
**Decisión.** Una sola API con módulos por dominio, sin consultas cruzadas directas, y un
servicio independiente para las integraciones.
**Descartado.** Microservicios por dominio (el despliegue y la coordinación cuestan más de
lo que el equipo puede pagar). Monolito sin separar integraciones (una importación de 237
insumos bloquearía al almacenero en obra).
**Consecuencia.** La disciplina de no cruzar módulos depende del equipo, no del
compilador: se vigila en la revisión del PR.

## ADR-02 · Android nativo, no aplicación web
**Contexto.** El registro ocurre en obra, sin señal, y debe sobrevivir a que el sistema
cierre la aplicación.
**Decisión.** Kotlin, Compose, Room y WorkManager.
**Descartado.** Aplicación web instalable (el almacenamiento del navegador puede ser
descartado por el sistema y el envío en segundo plano no está garantizado). React Native
(añade una capa entre la app y las garantías de persistencia que justamente se necesitan).
**Consecuencia.** Dos bases de código, app y web. Se asume porque atienden necesidades
distintas.

## ADR-03 · Libro de movimientos sin borrado y saldo derivado
**Contexto.** El problema de origen es que nadie puede reconstruir qué pasó con el
material. Un saldo en columna editable se corrige sin dejar rastro.
**Decisión.** Nada se modifica ni se elimina; corregir es registrar el inverso. El stock se
calcula desde los movimientos.
**Descartado.** Guardar el saldo y actualizarlo en cada operación (más rápido de consultar,
pierde la trazabilidad).
**Consecuencia.** La consulta cuesta más: se resuelve con índices y, si hiciera falta, con
un saldo precalculado que se reconstruye desde los movimientos y nunca se edita.

## ADR-04 · Identificador generado en el dispositivo
**Contexto.** Un movimiento registrado sin señal se reenvía al volver la cobertura; un
envío doble sacaría el material dos veces.
**Decisión.** El dispositivo genera un identificador único antes de encolar; el servidor lo
usa como clave de idempotencia y devuelve el resultado del original.
**Descartado.** Deduplicar comparando campos (dos salidas legítimas e iguales el mismo día
serían indistinguibles de un duplicado).
**Consecuencia.** El reintento deja de ser peligroso. Obliga a conservar los
identificadores recibidos; se purgan con el cierre de la obra.

## ADR-05 · Costo promedio ponderado congelado en el movimiento
**Contexto.** El mismo insumo entra a precios distintos a lo largo de la obra.
**Decisión.** Cada ingreso recalcula el promedio; cada salida toma el vigente y lo guarda.
**Descartado.** PEPS (exige seguir lotes y el almacenero no los distingue al despachar).
Precio del presupuesto (no refleja lo que se pagó).
**Consecuencia.** El promedio suaviza las variaciones: el reporte lo declara para que nadie
lo lea como precio de compra.

## ADR-06 · Despliegue en nube gestionada
**Contexto.** La Definición de Terminado exige que otro ejecute la historia en un ambiente
de pruebas, y el presupuesto es cero.
**Decisión.** Nivel gratuito con PostgreSQL administrado; APK por enlace.
**Descartado.** Servidor contratado (cuesta dinero del equipo). Solo local (incumple la DoD
y deja al cliente sin forma de probar).
**Consecuencia.** Los niveles gratuitos suspenden por inactividad y la primera petición
tarda: se advierte antes de cada demostración.

## ADR-07 · Kotlin Multiplatform para la app: Android e iOS con un solo código
**Contexto.** El 28/10 el equipo decidió que la app funcione en Android y en iPhone, para no depender del celular que tenga cada almacenero o ingeniero SSOMA (a confirmar con el Product Owner). ADR-02 eligió Android nativo por
las garantías de persistencia y de envío en segundo plano; esas garantías se tienen que conservar.
**Decisión.** Kotlin Multiplatform con Compose Multiplatform, Koin, Ktor y Room KMP. La interfaz, el
dominio, la base local y el cliente de la API se escriben una vez (`commonMain`); cada plataforma aporta
solo el motor HTTP, la ruta de la base y el programador de envío. En Android se sigue usando WorkManager.
**Descartado.** Dos apps nativas, Kotlin y Swift (el doble de trabajo con una persona en la app).
Flutter o React Native (otro lenguaje y otra capa sobre la persistencia, que es lo que ADR-02 quiso evitar).
Aplicación web instalable (mismas razones que en ADR-02).
**Consecuencia.** ADR-02 se mantiene en Android (Room y WorkManager reales) y se amplía a iOS. Se aceptan
tres costos: el iPhone solo se compila en una Mac con Xcode; iOS no garantiza el momento del envío en
segundo plano como WorkManager, así que en iOS la app envía al registrar, al abrirse y con «Enviar ahora»
hasta registrar la tarea de fondo; y Room KMP exige que las consultas sean `suspend` o `Flow`.

