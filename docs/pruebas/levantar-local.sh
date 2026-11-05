#!/usr/bin/env bash
# SIGRO · HT-06 · levanta la pila LOCAL para correr Postman y Playwright, sin Docker para los servicios.
# Solo necesita un PostgreSQL (aquí uno de Docker en el puerto 5435, para no chocar con otros).
#
#   bash docs/pruebas/levantar-local.sh            # base + migraciones + primer admin + 5 servicios + integraciones
#   bash docs/pruebas/levantar-local.sh --parar    # apaga los servicios (no el contenedor)
#
# Los secretos (JWT, token de servicio, claves de cada usuario de base y del admin) se GENERAN en cada corrida y
# quedan solo en docs/pruebas/.local/pruebas-local.env (ignorado por git, no se sube). El usuario y la clave del admin se imprimen al
# final para exportarlos como SIGRO_E2E_USUARIO / SIGRO_E2E_CLAVE o llenarlos en el ambiente de Postman.
# Variables opcionales: PY (python del venv con api/requirements-dev.txt), PY_INT (python con
# integraciones/requirements.txt), PG_PUERTO (5435), WEB_PUERTO (5174), FACTILIZA_TOKEN (si lo tienes).
set -euo pipefail
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
ESTADO="$RAIZ/docs/pruebas/.local"
mkdir -p "$ESTADO"
if [ "${1:-}" = "--parar" ]; then
  for f in "$ESTADO"/*.pid; do [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null || true; rm -f "$f"; done
  echo "Servicios detenidos."; exit 0
fi

PY="${PY:-$RAIZ/.venv/Scripts/python}"; [ -x "$PY" ] || PY="$RAIZ/.venv/bin/python"
PY_INT="${PY_INT:-$RAIZ/.venv-int/Scripts/python}"; [ -x "$PY_INT" ] || PY_INT="$RAIZ/.venv-int/bin/python"
PG_PUERTO="${PG_PUERTO:-5435}"; WEB_PUERTO="${WEB_PUERTO:-5174}"
DUENO="sigro_owner:${PG_CLAVE_DUENO:-sigro_owner_dev}@localhost:$PG_PUERTO/sigro"
aleatorio() { "$PY" -c "import secrets;print(secrets.token_urlsafe(${1:-24}))"; }

ENV="$ESTADO/pruebas-local.env"
if [ ! -f "$ENV" ]; then
  {
    echo "SIGRO_JWT_SECRET=$(aleatorio 48)"
    echo "SIGRO_SERVICIO_TOKEN=$(aleatorio 32)"
    for s in OBRAS CATALOGO COMPRAS TAREO RELEVO; do echo "SIGRO_CLAVE_$s=$(aleatorio 18)"; done
    echo "SIGRO_E2E_USUARIO=qa_admin"
    echo "SIGRO_E2E_CLAVE=Qa$(aleatorio 12)9z"
  } > "$ENV"
fi
set -a; . "$ENV"; set +a

cd "$RAIZ/api"
export SIGRO_DATABASE_URL="postgresql+psycopg://$DUENO" SIGRO_OWNER_DSN="postgresql://$DUENO"
PATH="$(dirname "$PY"):$PATH" sh ../infra/migrar.sh
SIGRO_APP_DATABASE_URL="postgresql+psycopg://sigro_obras:$SIGRO_CLAVE_OBRAS@localhost:$PG_PUERTO/sigro" \
SIGRO_ADMIN_CLAVE="$SIGRO_E2E_CLAVE" "$PY" -m app.cli primer-admin --usuario "$SIGRO_E2E_USUARIO" \
  --nombre "Administrador QA" --obra "Multifamiliar Flores" --inicio 2026-10-19 --fin 2027-10-17 || \
  echo "(primer-admin ya existía: se reutiliza)"

export SIGRO_ENTORNO=pruebas SIGRO_CORS="http://localhost:$WEB_PUERTO" \
  SIGRO_URL_OBRAS=http://127.0.0.1:8101 SIGRO_URL_CATALOGO=http://127.0.0.1:8102 \
  SIGRO_URL_COMPRAS=http://127.0.0.1:8103 SIGRO_URL_TAREO=http://127.0.0.1:8104 \
  SIGRO_INTEGRACIONES_URL=http://127.0.0.1:8105
arrancar() {  # nombre puerto [usuario-de-base]
  local nombre=$1 puerto=$2 url=""
  if [ -n "${3:-}" ]; then
    local clave_var="SIGRO_CLAVE_${3^^}"
    url="postgresql+psycopg://sigro_$3:${!clave_var}@localhost:$PG_PUERTO/sigro"
  fi
  SIGRO_APP_DATABASE_URL="$url" nohup "$PY" -m uvicorn "app.servicios.$nombre:app" --port "$puerto" \
    > "$ESTADO/$nombre.log" 2>&1 & echo $! > "$ESTADO/$nombre.pid"
}
arrancar obras 8101 obras; arrancar catalogo 8102 catalogo; arrancar compras 8103 compras; arrancar tareo 8104 tareo
arrancar gateway 8000
cd "$RAIZ/integraciones"
SERVICIO_TOKEN="$SIGRO_SERVICIO_TOKEN" FACTILIZA_TOKEN="${FACTILIZA_TOKEN:-}" nohup "$PY_INT" -m uvicorn main:app --port 8105 \
  > "$ESTADO/integraciones.log" 2>&1 & echo $! > "$ESTADO/integraciones.pid"

for p in 8101 8102 8103 8104 8105 8000; do
  for _ in $(seq 1 60); do curl -sf "http://127.0.0.1:$p/salud" >/dev/null && break; sleep 0.5; done
  curl -sf "http://127.0.0.1:$p/salud" >/dev/null || { echo "El servicio en :$p no arrancó; mira $ESTADO/*.log"; exit 1; }
done
echo "Pila local lista: gateway http://localhost:8000 · web (Playwright) http://localhost:$WEB_PUERTO"
echo "Credenciales del admin de prueba en $ENV (SIGRO_E2E_USUARIO / SIGRO_E2E_CLAVE). No se suben al repositorio."
