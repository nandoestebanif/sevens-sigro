#!/usr/bin/env bash
# Mide una ruta del gateway en Render, UNA petición cada 5 s (más rápido, Cloudflare bloquea la IP).
#
#   SIGRO_TOKEN=<token de tu sesión> bash api/docs/rendimiento/medir_render.sh obras /api/v1/obras 6
#
# El token se lee de SIGRO_TOKEN y nunca se imprime. Sin token, las rutas protegidas responden 401 (sirve para
# medir el salto gateway → servicio sin la base). Para medir EN FRÍO: no tocar ningún servicio 16 min y pedir 1.
# Cada línea: código, tiempos de curl (dns, tcp, tls, primer byte, total), CF-Ray y la cabecera Server-Timing.
BASE=${SIGRO_BASE:-https://sigro-gateway.onrender.com}
etq=${1:?etiqueta}; ruta=${2:?ruta}; n=${3:-6}
for i in $(seq 1 "$n"); do
  aut=(); [ -n "$SIGRO_TOKEN" ] && aut=(-H "Authorization: Bearer $SIGRO_TOKEN")
  h=$(mktemp)
  w=$(curl -s -o /dev/null -D "$h" "${aut[@]}" --max-time 120 \
      -w '%{http_code} dns=%{time_namelookup} tcp=%{time_connect} tls=%{time_appconnect} ttfb=%{time_starttransfer} total=%{time_total}' \
      "$BASE$ruta")
  ray=$(grep -i '^cf-ray:' "$h" | tr -d '\r' | cut -d' ' -f2)
  st=$(grep -i '^server-timing:' "$h" | tr -d '\r' | cut -d' ' -f2-)
  echo "$(date +%H:%M:%S) $etq #$i $w ray=$ray st=[$st]"
  rm -f "$h"
  [ "$i" -lt "$n" ] && sleep 5
done
