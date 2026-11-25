# movil-api · backend de la app del almacenero (Spring Boot 3 + Java 17)

La app Android habla con este backend. Expone el mismo contrato `/api/v1` que ya usa el celular, así que la app no
cambia: solo se le pone la dirección de este servidor en *Entrar → Servidor*.

## Qué hace

| Pieza | Dónde | Qué resuelve |
|---|---|---|
| Sesión | `seguridad/FiltroSesion`, `seguridad/VerificadorJwt` | Toda ruta `/api/v1` exige Bearer HS256 válido (firma, algoritmo y vencimiento); si no, 401 `{codigo, mensaje}` |
| Registrar salida (HU-40) | `salidas/SalidaController`, `SalidaRequest`, `SalidaService` | Bean Validation del JSON de la app (cantidad > 0, hasta 4 decimales, uuid, celular…) antes de tocar el libro |
| Reenviar no duplica (regla 3) | `salidas/RegistroIdempotente` | El mismo `uuid_cliente` con el mismo contenido devuelve la respuesta original (24 h, tope 5 000) sin volver a escribir |
| Entrar (HU-12) | `auth/AuthController` | Valida usuario y clave; la clave se verifica contra su hash argon2 en el servicio de identidad |
| Demás llamadas de la app | `reenvio/ReenvioController` | Lista blanca de las rutas que usa la app; pasan a su servicio dueño con el mismo Bearer |
| Errores | `errores/ManejadorErrores` | Todo sale como `{codigo, mensaje}`: 422 validación, 503 servidor dormido o caído |

Las reglas del libro (stock no negativo, costo promedio, reverso) siguen en los servicios de dominio y en la base de
datos: este backend no las duplica, se las pide.

## Correr

```bash
cd movil-api
mvn test                      # 17 pruebas (JUnit 5 + MockMvc, con un servidor falso de dominio)
SIGRO_JWT_SECRET=<el mismo del servicio> SIGRO_API_URL=https://sigro-api.onrender.com mvn spring-boot:run
curl localhost:8080/salud
```

Variables: `SIGRO_API_URL` (servicios de dominio), `SIGRO_JWT_SECRET` (el mismo secreto con que se firman las
sesiones; nunca se escribe en el repositorio), `PORT` (Render lo pone solo).

## Render (plan gratuito)

New → Web Service → este repositorio → **Root Directory** `movil-api` → **Runtime** Docker → **Health Check Path**
`/salud` → variables `SIGRO_API_URL` y `SIGRO_JWT_SECRET`.
