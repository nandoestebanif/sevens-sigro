"""Piezas comunes de las pantallas del lienzo (estilo B + vidrio + fondo de plano por pantalla)."""

import os
R = os.environ.get("SIGRO_LIENZO", os.path.join(os.path.dirname(__file__), ".."))  # carpeta que contiene project/

HELMET = """<helmet>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&amp;display=swap">
<style>
body{margin:0}
a{color:#C2410C}a:hover{color:#9A3412}
.vidrio{background:rgba(255,255,255,0.58);-webkit-backdrop-filter:blur(18px) saturate(160%);backdrop-filter:blur(18px) saturate(160%);border:1px solid rgba(255,255,255,0.75);box-shadow:0 8px 28px rgba(15,23,42,0.10)}
.vidrio-fuerte{background:rgba(255,255,255,0.78);-webkit-backdrop-filter:blur(22px) saturate(160%);backdrop-filter:blur(22px) saturate(160%);border:1px solid rgba(255,255,255,0.85)}
.campo{box-sizing:border-box;height:52px;border-radius:16px;border:1px solid rgba(255,255,255,0.9);background:rgba(255,255,255,0.74);padding:0 16px;font:16px Inter,sans-serif;color:#111827;width:100%}
.campo:focus{outline:2px solid #C2410C;outline-offset:1px}
.campo::placeholder{color:#6B7280}
</style>
</helmet>"""


def cabeza(titulo: str) -> str:
    return ('<!doctype html>\n<html lang="es">\n<head>\n<meta charset="utf-8">\n'
            f"<title>{titulo}</title>\n" '<script src="./support.js"></script>\n</head>\n<body>\n<x-dc>\n' + HELMET + "\n")


def raiz(h: int) -> str:
    return (f'<div style="width: 390px; height: {h}px; box-sizing: border-box; font-family: Inter, system-ui, sans-serif; '
            'color: #111827; position: relative; overflow: hidden; background: #EDF1F6">\n')


def fondo(p: str, h: int, colores, manchas, dibujo: str) -> str:
    c0, c1, c2 = colores
    m = "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="url(#{p}-m{t})"></circle>' for x, y, r, t in manchas)
    return f'''<svg aria-hidden="true" width="390" height="{h}" viewBox="0 0 390 {h}" style="position: absolute; left: 0; top: 0">
<defs>
<linearGradient id="{p}-f" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{c0}"></stop><stop offset="0.55" stop-color="{c1}"></stop><stop offset="1" stop-color="{c2}"></stop></linearGradient>
<pattern id="{p}-c" width="24" height="24" patternUnits="userSpaceOnUse"><path d="M24 0H0V24" fill="none" stroke="#94A3B8" stroke-width="0.5" opacity="0.4"></path></pattern>
<pattern id="{p}-C" width="120" height="120" patternUnits="userSpaceOnUse"><path d="M120 0H0V120" fill="none" stroke="#64748B" stroke-width="0.8" opacity="0.3"></path></pattern>
<radialGradient id="{p}-mn" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FB923C" stop-opacity="0.5"></stop><stop offset="1" stop-color="#FB923C" stop-opacity="0"></stop></radialGradient>
<radialGradient id="{p}-ma" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#60A5FA" stop-opacity="0.4"></stop><stop offset="1" stop-color="#60A5FA" stop-opacity="0"></stop></radialGradient>
<radialGradient id="{p}-mt" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#2DD4BF" stop-opacity="0.32"></stop><stop offset="1" stop-color="#2DD4BF" stop-opacity="0"></stop></radialGradient>
</defs>
<rect width="390" height="{h}" fill="url(#{p}-f)"></rect>
<rect width="390" height="{h}" fill="url(#{p}-c)"></rect>
<rect width="390" height="{h}" fill="url(#{p}-C)"></rect>
{m}
{dibujo}
</svg>
'''


def encabezado(volver: str, titulo: str, sub: str) -> str:
    return f'''<header style="position: relative; padding: 44px 16px 8px 12px; display: flex; align-items: center; gap: 4px">
<a href="{volver}" aria-label="Volver" class="vidrio" style="width: 44px; height: 44px; border-radius: 22px; margin-right: 8px; display: flex; align-items: center; justify-content: center; flex: none">
<svg aria-hidden="true" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#111827" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"></path></svg>
</a>
<div style="display: flex; flex-direction: column; gap: 2px; min-width: 0">
<h1 style="margin: 0; font-size: 28px; font-weight: 800; letter-spacing: -0.03em">{titulo}</h1>
<p style="margin: 0; font-size: 14px; color: #4B5563">{sub}</p>
</div>
</header>
'''


def pie(script: str, h: int) -> str:
    return ("</div>\n</x-dc>\n"
            f"<script type=\"text/x-dc\" data-dc-script data-props='{{\"$preview\":{{\"width\":390,\"height\":{h}}}}}'>\n"
            + script.strip() + "\n</script>\n</body>\n</html>\n")


BOTON = ('height: 58px; border: 0; border-radius: 29px; background: #111827; color: #FFFFFF; display: flex; align-items: center; '
         'justify-content: center; gap: 10px; font: 600 17px Inter, sans-serif; text-decoration: none; cursor: pointer')
BOTON_OFF = BOTON.replace("background: #111827", "background: #9CA3AF").replace("cursor: pointer", "cursor: not-allowed")
ETIQUETA = "display: flex; flex-direction: column; gap: 6px; font-size: 14px; font-weight: 600; color: #374151"
SECCION = "margin: 6px 4px 0; font-size: 13px; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; color: #4B5563"
CHECK = '<svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#FB923C" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"></path></svg>'

# chip de estado: fondo, texto
ESTADO = {
    "disponible": ("#DCFCE7", "#166534", "Disponible"),
    "en_uso": ("#DBEAFE", "#1E40AF", "En uso"),
    "mantenimiento": ("#FEF3C7", "#92400E", "En mantenimiento"),
    "baja": ("#E5E7EB", "#374151", "Dada de baja"),
}
