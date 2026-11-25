# SIGRO · backend en Spring Boot 3 + Java 17

Port completo del servicio de SIGRO: mismas rutas `/api/v1`, mismo JSON, mismas reglas. Un solo desplegable con un
paquete por servicio de dominio (`obra`, `catalogo`, `compras`, `tareo`, `almacen`, `costo`). Cada módulo usa su propio
pool y su propio rol de base (`SET ROLE sigro_svc_<servicio>`): ninguno lee el esquema de otro.

El esquema es el mismo (las 23 migraciones de `api/migraciones`); este backend no crea tablas.

## Variables de entorno

| Variable | Para qué |
|---|---|
| `SIGRO_DB_URL` | `postgresql://usuario:clave@host/base?sslmode=require` (un usuario que pueda hacer `SET ROLE sigro_svc_*`) |
| `SIGRO_JWT_SECRET` | el mismo secreto del servicio actual (los tokens siguen valiendo) |
| `SIGRO_ENTORNO` | `produccion` fuera de la PC (exige secreto y token de servicio) |
| `SIGRO_SERVICIO_TOKEN` | token del relevo para `/interno/eventos` |
| `SIGRO_INTEGRACIONES_URL`, `SIGRO_NOTIFICACIONES_URL` | servicios externos (opcionales) |
| `SIGRO_CORS` | orígenes de la web, separados por coma |
| `PORT` | puerto (Render lo pone) |

## Prueba de paridad

Las mismas pruebas de la API (`api/pruebas`) corren por HTTP contra este backend, con una base nueva por prueba
(ver `api/pruebas/paridad_spring.py`):

```bash
SIGRO_PRUEBAS=1 PORT=8080 SIGRO_DB_URL=postgresql://sigro_owner:...@localhost:5432/sigro_spring \
  SIGRO_INTEGRACIONES_URL=http://127.0.0.1:8099 SIGRO_NOTIFICACIONES_URL=http://127.0.0.1:8099 \
  SIGRO_SERVICIO_TOKEN=token-de-servicio-de-pruebas-0123456789 java -jar target/backend-spring-1.0.0.jar &
cd ../api && SIGRO_SPRING_URL=http://localhost:8080 python -m pytest pruebas/test_api_*.py pruebas/test_almacen_costo_0010.py pruebas/test_hallazgos_fuzzing.py
```

Resultado al 25/11/2026: **372 pasan, 3 no aplican por HTTP** (simulan la caída de un servicio interno o cambian
variables del proceso).
