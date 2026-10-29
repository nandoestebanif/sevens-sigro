# Ambiente de pruebas (HT-01) · Render + Neon, gratis

Para qué: que alguien distinto del autor pruebe cada HU en un lugar público (T8 de la DoD) y que la app del
emulador o del celular tenga a qué servidor conectarse. Responsable: Fernando. Lo puede ejecutar Claude Code.

## Lo que solo Fernando puede hacer (5 minutos)

1. **Neon** (https://neon.tech): crear cuenta con GitHub → proyecto `sigro` → región más cercana (US East) →
   copiar la *connection string* del dueño (`postgresql://...neon.tech/neondb?sslmode=require`).
2. **Render** (https://render.com): crear cuenta con GitHub y darle acceso al repositorio `xteban-sp/sevens-sigro`.
3. Pasarle a Claude Code la cadena de Neon **por variable de entorno**, nunca en un archivo del repositorio.

## Lo que hace Claude Code (o cualquiera del equipo)

```bash
# 1) Migrar y crear un usuario por servicio en Neon (desde la carpeta del repo)
export SIGRO_DATABASE_URL="postgresql+psycopg://<dueño>@<host-neon>/neondb?sslmode=require"
export SIGRO_OWNER_DSN="postgresql://<dueño>@<host-neon>/neondb?sslmode=require"
export SIGRO_CLAVE_OBRAS=... SIGRO_CLAVE_CATALOGO=... SIGRO_CLAVE_COMPRAS=... SIGRO_CLAVE_TAREO=... SIGRO_CLAVE_RELEVO=...
cd api && sh ../infra/migrar.sh

# 2) Datos reales de prueba (HT-07) y primer administrador
python -m semillas.cargar_proveedores semillas/proveedores-cliente.json
python -m semillas.cargar_catalogo semillas/catalogo-cliente.json
SIGRO_ADMIN_CLAVE='<clave>' python -m app.cli primer-admin --usuario <usuario> --nombre "<nombre>" \
    --obra "Multifamiliar Flores" --inicio 2026-10-19 --fin 2027-10-17
```

3. En Render: **New → Blueprint** → el repositorio → rama `main` (o la del PR) → Render lee `render.yaml`.
   Pide los valores marcados `sync: false`:
   - `SIGRO_APP_DATABASE_URL` de cada servicio: `postgresql+psycopg://sigro_<servicio>:<su clave>@<host-neon>/neondb?sslmode=require`
   - `FACTILIZA_TOKEN` de Integraciones (el de Fernando; nunca en el chat ni en el repo).
4. Si Render cambia algún nombre (sufijo), corregir las `SIGRO_URL_*` y `SIGRO_CORS` del grupo `sigro-comun`.
5. Comprobar: `https://sigro-gateway.onrender.com/salud` → `{"estado":"ok"}` y entrar a `https://sigro-web.onrender.com`.

## La app contra el ambiente

En la pantalla de entrada, «Servidor» → `https://sigro-gateway.onrender.com`. La primera vez tarda ~1 min (los
servicios gratuitos se duermen). La salida de material todavía no tiene endpoint (servicio almacén, propuesta 0010):
la app la guarda en la cola sin señal y la envía cuando exista.

## Límites conocidos del plan gratuito

- Los servicios se duermen a los 15 min sin uso.
- Relevo y Notificaciones no se despliegan aún: los avisos quedan en `outbox.evento` sin perderse.
- Integraciones queda sin token mientras la web llame directo para el S10; se cierra al pasar la importación por el gateway.
