"""Web de oficina SIGRO en estilo vidrio (mismo lenguaje que la app). Páginas fluidas de 1440 de ancho."""
import json, math, os, sys

ROOT = "/tmp/claude-0/-home-claude/715198d8-3788-59ed-8400-9a086962d6e7/scratchpad/web-canvas"
LOGO = "/_blob/8e33877d36f8bc75df9452182ed5a38d"
LOGO_BLANCO = "/_blob/58ea577653ffe19342a62be9c52e1c0f"

HELMET = """<helmet>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&amp;family=Sora:wght@600;700&amp;display=swap">
<style>
body{margin:0}
a{color:#C2410C}a:hover{color:#9A3412}
.vidrio{background:rgba(255,255,255,0.58);-webkit-backdrop-filter:blur(18px) saturate(160%);backdrop-filter:blur(18px) saturate(160%);border:1px solid rgba(255,255,255,0.75);box-shadow:0 8px 28px rgba(15,23,42,0.10)}
.vidrio-fuerte{background:rgba(255,255,255,0.78);-webkit-backdrop-filter:blur(22px) saturate(160%);backdrop-filter:blur(22px) saturate(160%);border:1px solid rgba(255,255,255,0.85)}
.campo{box-sizing:border-box;height:48px;border-radius:14px;border:1px solid rgba(17,24,39,0.12);background:rgba(255,255,255,0.8);padding:0 14px;font:15px Inter,sans-serif;color:#111827;width:100%}
.campo:focus,button:focus-visible,a:focus-visible{outline:2px solid #C2410C;outline-offset:2px}
.campo::placeholder{color:#6B7280}
.tabla{width:100%;border-collapse:collapse;font-size:14px;min-width:760px}
.tabla th{padding:12px 14px;font-size:12px;font-weight:700;letter-spacing:0.04em;text-transform:uppercase;color:#4B5563;text-align:left;border-bottom:1px solid rgba(17,24,39,0.10)}
.tabla td{padding:12px 14px;border-bottom:1px solid rgba(17,24,39,0.06);vertical-align:middle}
.tabla .n{text-align:right;font-variant-numeric:tabular-nums}
.tabla tr:last-child td{border-bottom:0}
</style>
</helmet>"""

BOTON = ("min-height: 48px; padding: 0 22px; border: 0; border-radius: 24px; background: #111827; color: #FFFFFF; display: inline-flex; "
         "align-items: center; justify-content: center; gap: 8px; font: 600 15px Inter, sans-serif; text-decoration: none; cursor: pointer; "
         "box-shadow: 0 10px 22px rgba(17,24,39,0.25)")
BOTON2 = ("min-height: 44px; padding: 0 18px; border: 1px solid rgba(17,24,39,0.12); border-radius: 22px; background: rgba(255,255,255,0.85); color: #111827; "
          "display: inline-flex; align-items: center; justify-content: center; gap: 8px; font: 600 14px Inter, sans-serif; text-decoration: none; cursor: pointer")
TARJETA = "border-radius: 26px; padding: 20px 22px"
H2 = "margin: 0; font-size: 18px; font-weight: 800; letter-spacing: -0.01em"
CHIP = "display: inline-flex; align-items: center; gap: 6px; border-radius: 999px; padding: 4px 11px; font-size: 12px; font-weight: 700; white-space: nowrap"
TONO = {"bien": ("#DCFCE7", "#166534"), "aviso": ("#FEF3C7", "#92400E"), "grave": ("#FEE2E2", "#991B1B"),
        "info": ("#DBEAFE", "#1E40AF"), "neutro": ("#E5E7EB", "#374151"), "marca": ("#FFEDD5", "#9A3412")}


def chip(texto, tono="neutro"):
    bg, fg = TONO[tono]
    return f'<span style="{CHIP}; background: {bg}; color: {fg}">{texto}</span>'


def soles(v, dec=0):
    s = f"{v:,.{dec}f}".replace(",", " ")
    return s


ICONO = {
    "obra": '<path d="M3 21h18M5 21V9l7-5 7 5v12M9 21v-6h6v6"></path>',
    "importar": '<path d="M12 3v12M7 10l5 5 5-5M4 19h16"></path>',
    "tablero": '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"></path>',
    "reposicion": '<path d="M3 12a9 9 0 0 1 15-6.7L21 8M21 3v5h-5M21 12a9 9 0 0 1-15 6.7L3 16M3 21v-5h5"></path>',
    "compras": '<path d="M6 2h12v20l-3-2-3 2-3-2-3 2zM9 7h6M9 11h6M9 15h4"></path>',
    "proveedores": '<path d="M3 7h13v10H3zM16 10h3l2 3v4h-5M7 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM18 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4z"></path>',
    "movimientos": '<path d="M7 4v16M3 8l4-4 4 4M17 20V4M13 16l4 4 4-4"></path>',
    "catalogo": '<circle cx="11" cy="11" r="7"></circle><path d="M20 20l-4-4"></path>',
    "accesos": '<path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"></path><path d="M9 12l2 2 4-4"></path>',
}


def icono(nombre, color="#374151", tam=20):
    return (f'<svg aria-hidden="true" width="{tam}" height="{tam}" viewBox="0 0 24 24" fill="none" stroke="{color}" stroke-width="2" '
            f'stroke-linecap="round" stroke-linejoin="round">{ICONO[nombre]}</svg>')


MENU = [
    ("Preparar la obra", [("obra", "Crear la obra", "Importar.dc.html", "obra"), ("importar", "Importar del S10", "Importar.dc.html", "importar")]),
    ("Control de la obra", [("tablero", "Gasto en recursos", "Panel.dc.html", "tablero"), ("reposicion", "Reposición", "Reposicion.dc.html", "reposicion")]),
    ("Abastecimiento", [("compras", "Compras", "Compras.dc.html", "compras"), ("proveedores", "Proveedores", "Compras.dc.html", "proveedores")]),
    ("Almacén", [("movimientos", "Ingresos y salidas", "Movimientos.dc.html", "movimientos")]),
    ("Catálogo", [("catalogo", "Catálogo de insumos", "Catalogo.dc.html", "catalogo")]),
    ("Accesos", [("accesos", "Usuarios y celulares", "Accesos.dc.html", "accesos")]),
]


def barra_lateral(activo):
    s = f'''<nav aria-label="Menú principal" class="vidrio-fuerte" style="flex: 1 1 240px; max-width: 264px; box-sizing: border-box; border-radius: 30px; padding: 20px 14px 16px; display: flex; flex-direction: column; gap: 16px; box-shadow: 0 14px 40px rgba(15,23,42,0.12)">
<a href="Panel.dc.html" style="display: flex; justify-content: center; padding: 2px 0 4px"><img src="{LOGO}" alt="SEVEN'S Ingenieros SAC" style="height: 58px; width: auto"></a>
<a href="Panel.dc.html" class="vidrio" style="border-radius: 18px; padding: 10px 12px; display: flex; align-items: center; gap: 10px; text-decoration: none; color: #111827; box-shadow: none">
<span style="width: 36px; height: 36px; border-radius: 12px; background: #FFEDD5; display: flex; align-items: center; justify-content: center; flex: none">{icono("obra", "#C2410C", 18)}</span>
<span style="flex: 1; min-width: 0; display: flex; flex-direction: column"><span style="font-size: 11px; font-weight: 700; letter-spacing: 0.06em; color: #6B7280">OBRA ACTIVA</span><span style="font-size: 14px; font-weight: 800">Multifamiliar Flores</span></span>
<svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#4B5563" stroke-width="2.4" stroke-linecap="round"><path d="M6 9l6 6 6-6"></path></svg></a>
'''
    for grupo, items in MENU:
        s += f'<div style="display: flex; flex-direction: column; gap: 3px"><span style="padding: 0 10px 4px; font-size: 11px; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: #6B7280">{grupo}</span>\n'
        for clave, nombre, href, ic in items:
            if clave == activo:
                s += (f'<a href="{href}" aria-current="page" style="min-height: 44px; padding: 0 12px; border-radius: 15px; background: #C2410C; color: #FFFFFF; display: flex; align-items: center; gap: 10px; '
                      f'font-size: 14px; font-weight: 700; text-decoration: none; box-shadow: 0 8px 18px rgba(194,65,12,0.32)">{icono(ic, "#FFFFFF", 19)}{nombre}</a>\n')
            else:
                s += (f'<a href="{href}" style="min-height: 44px; padding: 0 12px; border-radius: 15px; color: #374151; display: flex; align-items: center; gap: 10px; '
                      f'font-size: 14px; font-weight: 600; text-decoration: none">{icono(ic, "#4B5563", 19)}{nombre}</a>\n')
        s += "</div>\n"
    s += '''<div style="margin-top: auto; border-top: 1px solid rgba(17,24,39,0.08); padding: 14px 6px 0; display: flex; align-items: center; gap: 10px">
<span style="width: 40px; height: 40px; border-radius: 20px; background: #FEF3C7; display: flex; align-items: center; justify-content: center; flex: none">
<svg aria-hidden="true" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#92400E" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M6.5 8.6a5.5 5.5 0 0 1 11 0z" fill="#FCD34D"></path><path d="M4.6 8.6h14.8M12 3.2v2.4"></path><path d="M8.2 10.6a3.8 3.8 0 0 0 7.6 0"></path><path d="M4.5 21c1-3.6 3.8-5.6 7.5-5.6s6.5 2 7.5 5.6"></path></svg></span>
<span style="flex: 1; min-width: 0; display: flex; flex-direction: column"><span style="font-size: 14px; font-weight: 700">[Nombre del usuario]</span><span style="font-size: 12px; color: #4B5563">Residente de obra</span></span>
<a href="Main.dc.html" aria-label="Cerrar sesión" style="width: 40px; height: 40px; border-radius: 20px; display: flex; align-items: center; justify-content: center"><svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#4B5563" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 4h4v16h-4M10 17l5-5-5-5M15 12H3"></path></svg></a>
</div>
</nav>
'''
    return s


def fondo(p, colores, manchas, dibujo):
    c0, c1, c2 = colores
    m = "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="url(#{p}-m{t})"></circle>' for x, y, r, t in manchas)
    return f'''<svg aria-hidden="true" width="100%" height="100%" viewBox="0 0 1440 900" preserveAspectRatio="xMidYMid slice" style="position: absolute; left: 0; top: 0">
<defs>
<linearGradient id="{p}-f" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{c0}"></stop><stop offset="0.55" stop-color="{c1}"></stop><stop offset="1" stop-color="{c2}"></stop></linearGradient>
<pattern id="{p}-c" width="24" height="24" patternUnits="userSpaceOnUse"><path d="M24 0H0V24" fill="none" stroke="#94A3B8" stroke-width="0.5" opacity="0.4"></path></pattern>
<pattern id="{p}-C" width="120" height="120" patternUnits="userSpaceOnUse"><path d="M120 0H0V120" fill="none" stroke="#64748B" stroke-width="0.8" opacity="0.3"></path></pattern>
<radialGradient id="{p}-mn" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FB923C" stop-opacity="0.45"></stop><stop offset="1" stop-color="#FB923C" stop-opacity="0"></stop></radialGradient>
<radialGradient id="{p}-ma" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#60A5FA" stop-opacity="0.38"></stop><stop offset="1" stop-color="#60A5FA" stop-opacity="0"></stop></radialGradient>
<radialGradient id="{p}-mt" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#2DD4BF" stop-opacity="0.3"></stop><stop offset="1" stop-color="#2DD4BF" stop-opacity="0"></stop></radialGradient>
</defs>
<rect width="1440" height="900" fill="url(#{p}-f)"></rect><rect width="1440" height="900" fill="url(#{p}-c)"></rect><rect width="1440" height="900" fill="url(#{p}-C)"></rect>
{m}
{dibujo}
</svg>
'''


def cabeza(titulo):
    return ('<!doctype html>\n<html lang="es">\n<head>\n<meta charset="utf-8">\n'
            f'<title>{titulo}</title>\n<script src="./support.js"></script>\n</head>\n<body>\n<x-dc>\n{HELMET}\n')


def raiz(minh):
    return (f'<div style="min-height: {minh}px; box-sizing: border-box; font-family: Inter, system-ui, sans-serif; color: #111827; '
            'position: relative; overflow: hidden; background: #EDF1F6">\n')


def marco(activo, contenido):
    return ('<div style="position: relative; max-width: 1440px; margin: 0 auto; box-sizing: border-box; padding: 20px; '
            'display: flex; flex-wrap: wrap; gap: 20px; align-items: stretch">\n' + barra_lateral(activo) +
            '<main style="flex: 999 1 560px; min-width: 0; display: flex; flex-direction: column; gap: 18px">\n' + contenido + '</main>\n</div>\n')


def encabezado(titulo, sub, acciones=""):
    return f'''<header style="display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 14px; padding: 8px 4px 0">
<div style="display: flex; flex-direction: column; gap: 4px; min-width: 0">
<h1 style="margin: 0; font-size: 34px; font-weight: 800; letter-spacing: -0.03em">{titulo}</h1>
<p style="margin: 0; font-size: 15px; color: #4B5563">{sub}</p>
</div>
<div style="display: flex; flex-wrap: wrap; align-items: center; gap: 10px">
<button aria-label="Avisos: 3 sin leer" class="vidrio" style="width: 48px; height: 48px; border-radius: 24px; display: flex; align-items: center; justify-content: center; position: relative; cursor: pointer; box-shadow: none">
<svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#111827" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10 21h4"></path></svg>
<span style="position: absolute; top: 6px; right: 6px; min-width: 18px; height: 18px; border-radius: 9px; background: #C2410C; color: #FFFFFF; font-size: 11px; font-weight: 800; display: flex; align-items: center; justify-content: center; border: 2px solid #FFFFFF; box-sizing: border-box">3</span></button>
{acciones}
</div>
</header>
'''


def pie(script, h):
    return ("</div>\n</x-dc>\n"
            f"<script type=\"text/x-dc\" data-dc-script data-props='{{\"$preview\":{{\"width\":1440,\"height\":{h}}}}}'>\n"
            + script.strip() + "\n</script>\n</body>\n</html>\n")


VACIO = "class Component extends DCLogic { renderVals() { return {}; } }"
EJEMPLO = chip("Consumo y compras: datos de ejemplo · lo planificado es el S10 real", "info")


def kpi(rotulo, valor, pie_txt, color="#111827", prefijo=""):
    pre = f'<span style="font-size: 15px; font-weight: 700; color: #4B5563; margin-right: 4px">{prefijo}</span>' if prefijo else ""
    return f'''<div class="vidrio" style="{TARJETA}; flex: 1 1 200px; display: flex; flex-direction: column; gap: 6px">
<span style="font-size: 13px; font-weight: 600; color: #4B5563">{rotulo}</span>
<span style="display: flex; align-items: baseline">{pre}<span style="font-size: 30px; font-weight: 800; letter-spacing: -0.03em; color: {color}">{valor}</span></span>
<span style="font-size: 12.5px; color: #4B5563">{pie_txt}</span></div>
'''
