# Diagramas de secuencia por HU · arquitectura vigente (26/11/2026)

Uno por historia (37). Reemplazan a los del 19/11, que dibujaban el backend como gateway + microservicios Python.

- **App:** MVVM (Vista Compose → ViewModel → dominio → Repositorio → Room / Ktor, WorkManager para enviar sin señal).
- **Web:** Vista (página React) → estado y manejadores (ViewModel) → cliente http (Modelo) → `pedir()`.
- **Backend (ADR-12):** Filtros (`CabecerasSeguridad`, `BitacoraFiltro`) → `@RestController` del módulo →
  `Acceso.exigir(acción, obra)` → servicio/reglas del módulo → repositorio JDBC → PostgreSQL con el rol del módulo.

Los nombres de controladores y métodos salen de los `@…Mapping` del código de `backend-spring/`: si una ruta del
diagrama no existe en el código, el generador falla. Para ver la imagen: PlantUML (`java -jar plantuml.jar *.puml`)
o la extensión PlantUML de VS Code.
