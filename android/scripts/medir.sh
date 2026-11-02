#!/usr/bin/env bash
# Medición reproducible del rendimiento de la app en un emulador (docs/rendimiento-app.md).
#
#   1. python -I android/scripts/servidor-medicion.py 8765      (servidor falso, en otra terminal)
#   2. instalar el APK a medir:  adb install -r composeApp-release.apk
#   3. android/scripts/medir.sh <carpeta-de-salida> [corridas-arranque=7] [corridas-cuadros=3]
#
# CAPTURAS=<carpeta> guarda además capturas de Entrar, Inicio y Salida (paridad visual).
# Mide: arranque en frío (am start -W) sin sesión y con sesión, cuadros lentos (dumpsys gfxinfo)
# recorriendo Entrar → «el plano se dibuja» → Inicio → Registrar salida, y memoria (dumpsys meminfo).
# Todo con `adb shell input`: mismos toques, mismo orden, cada vez. Pensado para 1080 × 2400 (Pixel 8).
set -euo pipefail

ADB="${ADB:-adb}"
PKG=pe.sevens.sigro
ACT="$PKG/.MainActivity"
SERVIDOR="${SERVIDOR:-http://10.0.2.2:8765}"
OUT="${1:?carpeta de salida}"
N_ARRANQUE="${2:-7}"
N_CUADROS="${3:-3}"
mkdir -p "$OUT"

sh_() { "$ADB" shell "$@" | tr -d '\r'; }

# Centro del primer nodo cuyo text o content-desc contiene $1 (con «=X», el campo cuyo content-desc es X).
centro() {
    local intento
    for intento in 1 2 3 4 5 6 7 8 9 10; do
        local xml
        xml=$("$ADB" exec-out uiautomator dump /dev/tty 2>/dev/null || true)
        local b patron="(text|content-desc)=\"[^\"]*$1"
        [ "${1:0:1}" = "=" ] && patron="content-desc=\"${1:1}\""   # «=Etiqueta»: el campo, no su rótulo
        b=$(printf '%s' "$xml" | tr '>' '\n' | grep -E "$patron" | head -1 \
            | grep -o -E 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -o -E '[0-9]+' | tr '\n' ' ' || true)
        if [ -n "$b" ]; then
            set -- $b
            echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
            return 0
        fi
        sleep 1
    done
    echo "no encontré «$1» en pantalla" >&2
    return 1
}
tocar() { local c; c=$(centro "$1"); sh_ input tap $c; sleep "${2:-0.6}"; }
escribir() { sh_ input text "$(printf '%s' "$1" | sed 's/ /%s/g')"; sleep 0.4; }
deslizar() { sh_ input swipe 540 "$1" 540 "$2" "${3:-250}"; sleep 0.5; }

# Cierra el teclado solo si está abierto (con el teclado cerrado, «atrás» saldría de la pantalla).
cerrar_teclado() {
    local im; im=$(sh_ dumpsys input_method)
    case "$im" in *mInputShown=true*) sh_ input keyevent 4; sleep 0.6 ;; esac
}

hora() { date +%H:%M:%S; }

# Con CAPTURAS=<carpeta>, guarda Entrar, Inicio y Salida para comparar el diseño antes y después.
captura() { [ -n "${CAPTURAS:-}" ] && [ "${MODO:-}" != gfx ] && { mkdir -p "$CAPTURAS"; "$ADB" exec-out screencap -p > "$CAPTURAS/$1.png"; } || true; }

# ── Arranque en frío ──────────────────────────────────────────────────────────────────────────
arranque() { # $1 = etiqueta
    local f="$OUT/arranque-$1.txt"; : > "$f"
    for i in $(seq 1 "$N_ARRANQUE"); do
        sh_ am force-stop "$PKG"; sleep 2
        sh_ am start -W -n "$ACT" | grep -E "TotalTime|WaitTime" | tr '\n' ' ' >> "$f"; echo >> "$f"
        sleep 3
    done
    echo "[$(hora)] arranque $1 (TotalTime ms):"
    grep -o -E 'TotalTime: [0-9]+' "$f" | awk '{print $2}' | sort -n | awk '{v[NR]=$1} END {
        m = (NR % 2) ? v[(NR+1)/2] : (v[NR/2] + v[NR/2+1]) / 2
        printf "   n=%d mediana=%d min=%d max=%d  [", NR, m, v[1], v[NR]; for (i=1;i<=NR;i++) printf " %d", v[i]; print " ]" }'
}

# Recorrido Entrar → Inicio → Salida. Con $1 = "gfx" mide los cuadros de todo el recorrido.
recorrido() {
    MODO="${1:-}"
    sh_ pm clear "$PKG" > /dev/null; sleep 2
    sh_ am start -W -n "$ACT" > /dev/null; sleep 3
    case "$(sh_ dumpsys activity activities)" in *"topResumedActivity="*"$PKG/"*) ;; *) echo "la app no quedó al frente" >&2; return 1 ;; esac
    [ "${1:-}" = gfx ] && sh_ dumpsys gfxinfo "$PKG" reset > /dev/null
    centro "Servidor:" > /dev/null; captura entrar          # espera a que Entrar esté pintada
    # Entrar
    tocar "Servidor:"
    tocar "=Dirección del servidor"
    sh_ input keycombination 113 29; sh_ input keyevent 67   # Ctrl+A y borrar
    escribir "$SERVIDOR"
    tocar "=Usuario"; escribir "medicion"
    tocar "=Clave"; escribir "medicion"
    cerrar_teclado
    tocar "Entrar" 0.2
    sleep 6                                                 # el plano se dibuja (2,5 s de servidor) + transición
    # Inicio
    centro "Registrar salida" > /dev/null; sleep 1; captura inicio
    deslizar 1800 700; deslizar 700 1800; deslizar 1800 700; deslizar 700 1800
    # Salida
    tocar "Registrar salida" 1.5; captura salida
    tocar "=Buscar insumo por nombre o código" 0.5
    escribir "insumo"; sleep 1.5
    tocar "MED0" 1                                         # primer resultado
    deslizar 1800 700; deslizar 700 1800
    cerrar_teclado
    sh_ input keyevent 4; sleep 1.5                         # atrás → Inicio
    deslizar 1800 700; deslizar 700 1800
    sleep 2
}

cuadros() {
    for i in $(seq 1 "$N_CUADROS"); do
        recorrido gfx
        sh_ dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo-$i.txt"
        echo "[$(hora)] cuadros corrida $i:"; grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile" "$OUT/gfxinfo-$i.txt" | head -6
    done
}

memoria() {
    # Tras el último recorrido la app está en Inicio con sesión.
    sleep 5
    sh_ dumpsys meminfo "$PKG" > "$OUT/meminfo.txt"
    echo "[$(hora)] memoria:"; grep -E "TOTAL PSS|Java Heap|Native Heap|Graphics|TOTAL RSS" "$OUT/meminfo.txt" | head -6
}

# Estado de compilación ART conocido antes de medir, igual en cada versión:
#   verify → recién instalada desde el enlace (sin código precompilado: así llega el APK al almacenero);
#   perfil → después de que el sistema aplica el perfil de arranque (Baseline Profile) que trae el APK,
#            como lo hace Macrobenchmark con CompilationMode.Partial: reset, instalar perfil, speed-profile.
compilar() {
    case "$1" in
        verify) sh_ cmd package compile -f -m verify "$PKG" > /dev/null ;;
        perfil)
            sh_ cmd package compile --reset "$PKG" > /dev/null
            sh_ am start -W -n "$ACT" > /dev/null; sleep 2
            sh_ am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE                 "$PKG/androidx.profileinstaller.ProfileInstallReceiver" > /dev/null
            sleep 1; sh_ am force-stop "$PKG"
            sh_ cmd package compile -f -m speed-profile "$PKG" > /dev/null ;;
    esac
    echo "[$(hora)] compilación $1: $(sh_ dumpsys package dexopt | grep -A2 "\[$PKG\]" | grep -o 'status=[a-z-]*')"
}

echo "[$(hora)] inicio · $(sh_ getprop ro.product.model) API $(sh_ getprop ro.build.version.sdk)"
compilar verify
sh_ pm clear "$PKG" > /dev/null
arranque sin-sesion
cuadros
memoria
arranque con-sesion
compilar perfil
arranque perfil-con-sesion
sh_ pm clear "$PKG" > /dev/null
arranque perfil-sin-sesion
if [ -n "${CAPTURAS:-}" ]; then recorrido; echo "[$(hora)] capturas en $CAPTURAS"; fi
echo "[$(hora)] fin"
