# sevens-sgr · SIGRO

Sistema de Gestión de Recursos de Obra para SEVENS INGENIEROS SAC.
Curso Ingeniería de Software I · UPeU · 2026-2.

- **Sprint 1** · 15 al 28 de octubre de 2026
- **Sprint 2** · 29 de octubre al 11 de noviembre de 2026
- **Sustentación** · 26 de noviembre de 2026

## Empieza por aquí

1. Lee `CLAUDE.md`. Es el contexto completo: problema, reglas de dominio, arquitectura y
   forma de trabajo. Si usas Claude Code, lo lee solo.
2. Lee tu archivo en `equipo/`.
3. Lee tus historias en `docs/historias-sprint-1.md`.

## Estructura (vigente al 26/11, ADR-12)

```
backend-spring/  · API en producción: Spring Boot 3 + Java 17, monolito modular (un paquete por dominio)
api/             · backend Python (referencia), migraciones Alembic (fuente del esquema) y pruebas de la API
android/         · app del almacenero y del SSOMA (Kotlin Multiplatform, MVVM, offline-first)
web/             · web de oficina (React + TypeScript + Bootstrap)
integraciones/   · lectura del S10 y padrón SUNAT/RENIEC (hoy también dentro de backend-spring)
infra/           · scripts (obras de demostración con datos de prueba)
docs/            · historias, permisos, modelo de datos, decisiones (ADR-01 a ADR-12), diagramas
equipo/          · qué hace cada uno
```

Las pruebas de la API (`api/pruebas`) se corren también contra Spring por HTTP (`SIGRO_SPRING_URL`): así se
comprueba que los dos backends cumplen el mismo contrato.

## Equipo

| | Rol Scrum | Construye |
|---|---|---|
| Fernando Istaña Flores | Scrum Master | Experiencia (UX), web, integraciones y documentación |
| Víctor Adiel Luján Marallano | Product Owner | Backend |
| Henry Ticona Flores | Developer | Web |
| Nicolás Pachamora Villanueva | Developer | App Android |

## Reglas que no se discuten

Nada se borra · el saldo lo calcula el servidor · un reenvío no duplica · el costo se
congela al salir · el permiso se verifica en el servidor.

El detalle está en `CLAUDE.md`.
