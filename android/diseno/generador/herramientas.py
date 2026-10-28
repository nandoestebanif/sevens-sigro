"""4 · Herramientas y equipos (HU-48 a HU-53). Herramientas, códigos y personas: datos de ejemplo."""
import json
from comun import *

HER = [
    {"id": "H-001", "nombre": "Amoladora angular 4 1/2\"", "estado": "en_uso", "detalle": "Trabajador 03 · desde ayer", "aviso": ""},
    {"id": "H-002", "nombre": "Rotomartillo SDS Plus", "estado": "disponible", "detalle": "En almacén · estado bueno", "aviso": ""},
    {"id": "H-003", "nombre": "Mezcladora de concreto 9 p³", "estado": "mantenimiento", "detalle": "Correctivo · desde el lunes", "aviso": "Mantenimiento vencido"},
    {"id": "H-004", "nombre": "Vibradora de concreto", "estado": "en_uso", "detalle": "Trabajador 05 · desde hoy", "aviso": ""},
    {"id": "H-005", "nombre": "Cortadora de fierro", "estado": "disponible", "detalle": "En almacén · estado regular", "aviso": ""},
    {"id": "H-006", "nombre": "Nivel láser", "estado": "disponible", "detalle": "En almacén · estado nuevo", "aviso": ""},
    {"id": "H-007", "nombre": "Carretilla buggy", "estado": "baja", "detalle": "Robo · último: Trabajador 04", "aviso": ""},
]
TRAB = [{"n": "Trabajador 01", "cat": "Operario", "cu": "A"}, {"n": "Trabajador 02", "cat": "Oficial", "cu": "A"},
        {"n": "Trabajador 03", "cat": "Oficial", "cu": "A"}, {"n": "Trabajador 04", "cat": "Peón", "cu": "B"},
        {"n": "Trabajador 05", "cat": "Operario", "cu": "B"}, {"n": "Trabajador 06", "cat": "Peón", "cu": "B"}]

# opciones con estado: devuelve [{texto, elegir, bg, fg, on}]
OPC = r"""
const opciones = (lista, sel, fijar, colorOn) => lista.map((t, i) => ({
  texto: t, on: sel === i, elegir: () => fijar(i),
  bg: sel === i ? (colorOn || '#111827') : 'rgba(255,255,255,0.82)', fg: sel === i ? '#FFFFFF' : '#111827' }));
"""
CHIPS = '''<div style="display: flex; flex-wrap: wrap; gap: 8px">
<sc-for list="{{%s}}" as="o" hint-placeholder-count="3">
<button onClick="{{o.elegir}}" aria-pressed="{{o.on}}" style="min-height: 44px; padding: 0 16px; border-radius: 22px; border: 0; background: {{o.bg}}; color: {{o.fg}}; font: 700 14px Inter, sans-serif; cursor: pointer">{{o.texto}}</button>
</sc-for>
</div>'''


def chip_estado(clave):
    bg, fg, txt = ESTADO[clave]
    return f'<span style="background: {bg}; color: {fg}; border-radius: 999px; padding: 4px 10px; font-size: 12px; font-weight: 700">{txt}</span>'


def tarjeta_herramienta(nombre, codigo, estado, extra=""):
    return f'''<div class="vidrio" style="border-radius: 24px; padding: 14px 16px; display: flex; align-items: center; gap: 12px">
<span style="width: 48px; height: 48px; border-radius: 16px; background: #FFEDD5; display: flex; align-items: center; justify-content: center; flex: none">
<svg aria-hidden="true" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#C2410C" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.4-.6-.6-2.4z"></path></svg></span>
<span style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px"><span style="font-size: 16px; font-weight: 800">{nombre}</span>
<span style="font-size: 13px; color: #4B5563">{codigo}{extra}</span></span>
{chip_estado(estado)}
</div>
'''


# ───────────────────────────── 4.1 Lista
def lista():
    h = 900
    dib = '''<g fill="none" stroke="#475569" stroke-width="1.4" opacity="0.5">
<path d="M240 520v330M310 520v330M380 520v330M240 520h140M240 630h140M240 740h140M240 850h140"></path>
<path d="M240 520l70 110M310 520l70 110M240 630l70 110M310 630l70 110M240 740l70 110M310 740l70 110" stroke-width="1"></path>
<path d="M226 850h168" stroke-width="2"></path></g>
<g fill="none" stroke="#C2410C" stroke-width="1.1" opacity="0.6"><path d="M220 520v330M214 520h12M214 850h12"></path></g>
<text x="190" y="690" font-family="Inter, sans-serif" font-size="10" fill="#C2410C" opacity="0.8" transform="rotate(-90 196 690)">3 cuerpos · 1.10 m</text>'''
    s = cabeza("Herramientas de la obra") + raiz(h) + fondo("hl", h, ("#EDF1F6", "#F1F1EF", "#F5EAE0"), [(320, 140, 200, "n"), (60, 520, 200, "a"), (330, 820, 160, "t")], dib)
    s += encabezado("Inicio.dc.html", "Herramientas", "Multifamiliar Flores · quién tiene cada una")
    s += '''<div style="position: relative; padding: 8px 16px 0; display: flex; flex-direction: column; gap: 12px">
<a href="HerMantBaja.dc.html" class="vidrio-fuerte" style="border-radius: 20px; padding: 12px 14px; display: flex; align-items: center; gap: 10px; text-decoration: none; color: #92400E; border: 1px solid #FCD34D">
<svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#B45309" stroke-width="2.2" stroke-linecap="round" style="flex: none"><path d="M12 3l9 16H3z"></path><path d="M12 10v4M12 17v.5"></path></svg>
<span style="flex: 1; font-size: 14px; font-weight: 700">1 con mantenimiento vencido: Mezcladora H-003</span>
<svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#92400E" stroke-width="2.2" stroke-linecap="round"><path d="M9 6l6 6-6 6"></path></svg></a>
<div style="display: flex; gap: 8px; overflow-x: auto; padding-bottom: 2px">
<sc-for list="{{filtros}}" as="f" hint-placeholder-count="5">
<button onClick="{{f.elegir}}" aria-pressed="{{f.on}}" style="flex: none; min-height: 40px; padding: 0 14px; border-radius: 20px; border: 0; background: {{f.bg}}; color: {{f.fg}}; font: 700 13px Inter, sans-serif; cursor: pointer">{{f.texto}}</button>
</sc-for>
</div>
<div class="vidrio" style="border-radius: 26px; display: flex; flex-direction: column; overflow: hidden">
<sc-for list="{{visibles}}" as="t" hint-placeholder-count="5">
<a href="HerFicha.dc.html" style="padding: 12px 16px; display: flex; align-items: center; gap: 12px; border-bottom: 1px solid rgba(17,24,39,0.07); text-decoration: none; color: #111827; opacity: {{t.opacidad}}">
<span style="width: 42px; height: 42px; border-radius: 14px; background: {{t.bg}}; display: flex; align-items: center; justify-content: center; flex: none">
<svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="{{t.fg}}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.4-.6-.6-2.4z"></path></svg></span>
<span style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px">
<span style="font-size: 15px; font-weight: 700">{{t.nombre}}</span>
<span style="font-size: 13px; color: #4B5563">{{t.id}} · {{t.detalle}}</span>
<sc-if value="{{t.hayAviso}}" hint-placeholder-val="{{ false }}"><span style="font-size: 12px; font-weight: 700; color: #B45309">{{t.aviso}}</span></sc-if>
</span>
<span style="background: {{t.bg}}; color: {{t.fg}}; border-radius: 999px; padding: 4px 9px; font-size: 11px; font-weight: 700; flex: none">{{t.estadoTxt}}</span>
</a>
</sc-for>
</div>
</div>
<a href="HerIngreso.dc.html" style="position: absolute; right: 18px; bottom: 28px; height: 58px; padding: 0 22px 0 18px; border-radius: 29px; background: #111827; color: #FFFFFF; display: flex; align-items: center; gap: 10px; font-size: 16px; font-weight: 700; text-decoration: none; box-shadow: 0 12px 28px rgba(17,24,39,0.32)">
<svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#FB923C" stroke-width="2.6" stroke-linecap="round"><path d="M12 5v14M5 12h14"></path></svg>Ingresar herramienta</a>
'''
    script = '''
const HER = ''' + json.dumps(HER, ensure_ascii=False) + ''';
const EST = ''' + json.dumps(ESTADO, ensure_ascii=False) + ''';
const FILTROS = [['todas', 'Todas'], ['disponible', 'Disponibles'], ['en_uso', 'En uso'], ['mantenimiento', 'Mantenimiento'], ['baja', 'De baja']];
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { f: 'todas' }; }
  renderVals() {
    const f = this.state.f;
    return {
      filtros: FILTROS.map(([k, t]) => {
        const n = k === 'todas' ? HER.length : HER.filter((x) => x.estado === k).length;
        return { texto: t + ' · ' + n, on: f === k, elegir: () => this.setState({ f: k }),
                 bg: f === k ? '#111827' : 'rgba(255,255,255,0.82)', fg: f === k ? '#FFFFFF' : '#111827' };
      }),
      visibles: HER.filter((x) => f === 'todas' || x.estado === f).map((x) => ({ ...x,
        bg: EST[x.estado][0], fg: EST[x.estado][1], estadoTxt: EST[x.estado][2], hayAviso: !!x.aviso,
        opacidad: x.estado === 'baja' ? 0.7 : 1 }))
    };
  }
}
'''
    return s + pie(script, h)


# ───────────────────────────── 4.2 Ficha e historial
def ficha():
    h = 1000
    dib = '''<g fill="none" stroke="#475569" stroke-width="1.4" opacity="0.5" stroke-linejoin="round">
<ellipse cx="300" cy="170" rx="56" ry="70" transform="rotate(-25 300 170)"></ellipse>
<ellipse cx="300" cy="170" rx="30" ry="40" transform="rotate(-25 300 170)" stroke-dasharray="4 4"></ellipse>
<path d="M250 220l-30 80h150l-30-80M230 300v40M360 300v40"></path><circle cx="230" cy="350" r="12"></circle><circle cx="360" cy="350" r="12"></circle></g>
<text x="248" y="386" font-family="Inter, sans-serif" font-size="10" fill="#475569" opacity="0.7">MEZCLADORA 9 p³</text>
<g fill="none" stroke="#C2410C" stroke-width="1.1" opacity="0.55"><path d="M20 900h150M20 894v12M170 894v12"></path></g>'''
    s = cabeza("Ficha de herramienta") + raiz(h) + fondo("hf", h, ("#EEF0F6", "#F2F0EE", "#F6EADF"), [(330, 200, 210, "a"), (50, 640, 200, "n"), (330, 940, 160, "t")], dib)
    s += encabezado("HerLista.dc.html", "Herramienta", "Historial completo: nada se borra")
    s += '<div style="position: relative; padding: 8px 16px 24px; display: flex; flex-direction: column; gap: 14px">\n'
    s += tarjeta_herramienta('Amoladora angular 4 1/2"', "H-001", "en_uso", " · ingresó nueva")
    s += '''<div class="vidrio" style="border-radius: 24px; padding: 16px; display: flex; flex-direction: column; gap: 12px">
<span style="font-size: 13px; font-weight: 700; color: #4B5563">LA TIENE AHORA</span>
<div style="display: flex; align-items: center; gap: 12px">
<span style="width: 48px; height: 48px; border-radius: 24px; background: #DBEAFE; color: #1E40AF; display: flex; align-items: center; justify-content: center; font-size: 15px; font-weight: 800; flex: none">T3</span>
<span style="display: flex; flex-direction: column; gap: 2px"><span style="font-size: 16px; font-weight: 800">Trabajador 03</span><span style="font-size: 13px; color: #4B5563">Oficial · Cuadrilla A · desde ayer 07:40 · la recibió en estado bueno</span></span>
</div>
<a href="HerDevolver.dc.html" style="''' + BOTON + '''">Registrar devolución</a>
<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px">
<a href="HerMantBaja.dc.html" style="min-height: 50px; border-radius: 25px; background: rgba(255,255,255,0.85); display: flex; align-items: center; justify-content: center; gap: 8px; font-size: 14px; font-weight: 700; color: #111827; text-decoration: none">
<svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#C2410C" stroke-width="2.2" stroke-linecap="round"><circle cx="12" cy="12" r="3"></circle><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2"></path></svg>Mantenimiento</a>
<a href="HerMantBaja.dc.html" style="min-height: 50px; border-radius: 25px; background: rgba(255,255,255,0.85); display: flex; align-items: center; justify-content: center; gap: 8px; font-size: 14px; font-weight: 700; color: #991B1B; text-decoration: none">
<svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#B91C1C" stroke-width="2.2" stroke-linecap="round"><circle cx="12" cy="12" r="9"></circle><path d="M5.6 5.6l12.8 12.8"></path></svg>Dar de baja</a>
</div>
<span style="font-size: 12px; color: #4B5563">Mientras esté en uso no puede entregarse a otra persona.</span>
</div>
<h3 style="''' + SECCION + '''">Historial</h3>
<ol class="vidrio" style="list-style: none; margin: 0; border-radius: 24px; padding: 16px 18px; display: flex; flex-direction: column; gap: 0">
'''
    eventos = [
        ("#DBEAFE", "#1E40AF", "Entregada a Trabajador 03", "Ayer 07:40 · estado bueno · registró: almacenero"),
        ("#DCFCE7", "#166534", "Devuelta por Trabajador 01", "Hace 3 días · de nueva a buena"),
        ("#FEF3C7", "#92400E", "Mantenimiento preventivo", "Hace 9 días · cambio de escobillas · S/ [costo]"),
        ("#DBEAFE", "#1E40AF", "Entregada a Trabajador 01", "Hace 12 días · estado nuevo"),
        ("#FFEDD5", "#9A3412", "Ingreso a la obra", "Hace 15 días · nueva · 2 fotos"),
    ]
    for i, (bg, fg, t, d) in enumerate(eventos):
        linea = '' if i == len(eventos) - 1 else '<span style="position: absolute; left: 11px; top: 26px; bottom: -4px; width: 2px; background: rgba(17,24,39,0.10)"></span>'
        s += f'''<li style="position: relative; padding: 0 0 16px 36px; display: flex; flex-direction: column; gap: 2px">{linea}
<span style="position: absolute; left: 0; top: 0; width: 24px; height: 24px; border-radius: 12px; background: {bg}; display: flex; align-items: center; justify-content: center"><span style="width: 8px; height: 8px; border-radius: 4px; background: {fg}"></span></span>
<span style="font-size: 15px; font-weight: 700">{t}</span><span style="font-size: 13px; color: #4B5563">{d}</span></li>
'''
    s += "</ol>\n</div>\n"
    script = "class Component extends DCLogic { renderVals() { return {}; } }"
    return s + pie(script, h)


# ───────────────────────────── 4.3 Ingreso
def ingreso():
    h = 1000
    dib = '''<g fill="none" stroke="#475569" stroke-width="1.4" opacity="0.5" stroke-linejoin="round">
<path d="M220 160h150v90H220z"></path><path d="M220 190h150"></path><path d="M270 160v-18h50v18"></path><path d="M285 190v14h20v-14"></path>
<path d="M40 860l60-60M92 792l16 16-20 6zM40 860l12 12"></path><path d="M150 880h80l10-20h-100z"></path></g>
<text x="232" y="276" font-family="Inter, sans-serif" font-size="10" fill="#475569" opacity="0.7">CAJA · INVENTARIO</text>
<g fill="none" stroke="#C2410C" stroke-width="1.1" opacity="0.55"><path d="M220 296h150M220 290v12M370 290v12"></path></g>'''
    s = cabeza("Ingreso de herramienta") + raiz(h) + fondo("hi", h, ("#EEF1F6", "#F3F1EE", "#F6EBE0"), [(320, 180, 200, "n"), (40, 560, 210, "t"), (330, 900, 170, "a")], dib)
    s += encabezado("HerLista.dc.html", "Ingresar herramienta", "Una por una, con su código y estado")
    s += '''<div style="position: relative; padding: 8px 16px 24px; display: flex; flex-direction: column; gap: 14px">
<div class="vidrio" style="border-radius: 26px; padding: 18px; display: flex; flex-direction: column; gap: 14px">
<label style="''' + ETIQUETA + '''">Descripción *<input class="campo" value="{{desc}}" onChange="{{eDesc}}" placeholder="p. ej. Amoladora angular 4 1/2&quot;"></label>
<label style="''' + ETIQUETA + '''">Código *<input class="campo" value="{{codigo}}" onChange="{{eCodigo}}" placeholder="El que lleva pintado o grabado"></label>
<div style="''' + ETIQUETA + '''">Estado al ingresar *''' + CHIPS % "estados" + '''</div>
<div style="''' + ETIQUETA + '''">Fotos del estado (respaldo)
<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px">
<button onClick="{{foto}}" style="aspect-ratio: 1; border-radius: 18px; border: 2px dashed #C2410C; background: rgba(255,237,213,0.6); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; font: 700 12px Inter, sans-serif; color: #9A3412; cursor: pointer">
<svg aria-hidden="true" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#C2410C" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8h3l2-3h6l2 3h3v11H4z"></path><circle cx="12" cy="13" r="3.5"></circle></svg>Tomar foto</button>
<sc-for list="{{fotos}}" as="f" hint-placeholder-count="1">
<span role="img" aria-label="Foto {{f}}" style="aspect-ratio: 1; border-radius: 18px; background: linear-gradient(135deg, #CBD5E1, #E2E8F0); display: flex; align-items: flex-end; padding: 8px; box-sizing: border-box; font-size: 11px; font-weight: 700; color: #334155">Foto {{f}}</span>
</sc-for>
</div></div>
</div>
<div class="vidrio" style="border-radius: 20px; padding: 12px 14px; font-size: 13px; color: #374151; line-height: 1.45">Cada herramienta se registra por separado: si llegan tres amoladoras, son tres ingresos, cada uno con su código. Así se sabe quién tiene cuál.</div>
<button disabled="{{bloqueado}}" style="{{estiloBoton}}">''' + CHECK + '''Registrar ingreso</button>
</div>
'''
    script = OPC + '''
const BOTON = "''' + BOTON + '''";
const BOTON_OFF = "''' + BOTON_OFF + '''";
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { desc: 'Rotomartillo SDS Plus', codigo: '', estado: 1, fotos: [1] }; }
  renderVals() {
    const st = this.state;
    const bloqueado = !st.desc.trim() || !st.codigo.trim() || st.estado < 0;
    return {
      desc: st.desc, codigo: st.codigo,
      eDesc: (e) => this.setState({ desc: e.target.value }),
      eCodigo: (e) => this.setState({ codigo: e.target.value }),
      estados: opciones(['Nueva', 'Buena', 'Regular', 'Deteriorada'], st.estado, (i) => this.setState({ estado: i }), '#C2410C'),
      fotos: st.fotos,
      foto: () => { if (st.fotos.length < 5) this.setState({ fotos: [...st.fotos, st.fotos.length + 1] }); },
      bloqueado, estiloBoton: bloqueado ? BOTON_OFF : BOTON
    };
  }
}
'''
    return s + pie(script, h)


# ───────────────────────────── 4.4 Entregar
def entregar():
    h = 900
    dib = '''<g fill="none" stroke="#475569" stroke-width="1.4" opacity="0.5" stroke-linejoin="round" stroke-linecap="round">
<path d="M220 700h110l30-50H200z"></path><path d="M330 700l50-90h20"></path><circle cx="250" cy="730" r="22"></circle><circle cx="250" cy="730" r="6"></circle>
<path d="M300 700l-10 50h-20"></path></g>
<g fill="none" stroke="#C2410C" stroke-width="1.2" opacity="0.55" stroke-linecap="round"><path d="M40 170h120M140 150l20 20-20 20"></path></g>
<text x="40" y="200" font-family="Inter, sans-serif" font-size="10" fill="#C2410C" opacity="0.75">ENTREGA</text>'''
    s = cabeza("Entregar herramienta") + raiz(h) + fondo("he", h, ("#EDF1F7", "#F2F1EE", "#F5E9DF"), [(320, 160, 200, "a"), (50, 520, 200, "n"), (330, 820, 160, "t")], dib)
    s += encabezado("HerFicha.dc.html", "Entregar", "Queda a nombre de quien la recibe")
    s += '<div style="position: relative; padding: 8px 16px 24px; display: flex; flex-direction: column; gap: 14px">\n'
    s += tarjeta_herramienta("Rotomartillo SDS Plus", "H-002", "disponible", " · estado bueno")
    s += '''<div class="vidrio" style="border-radius: 26px; padding: 16px; display: flex; flex-direction: column; gap: 12px">
<span style="font-size: 14px; font-weight: 700; color: #374151">¿Quién la recibe? *</span>
<label style="height: 50px; border-radius: 25px; background: rgba(255,255,255,0.85); padding: 0 16px; display: flex; align-items: center; gap: 8px">
<svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#6B7280" stroke-width="2.4" stroke-linecap="round"><circle cx="11" cy="11" r="7"></circle><path d="M20 20l-4-4"></path></svg>
<input value="{{q}}" onChange="{{buscar}}" placeholder="Buscar trabajador" aria-label="Buscar trabajador" style="flex: 1; min-width: 0; border: 0; background: transparent; font: 16px Inter, sans-serif; color: #111827; outline: none"></label>
<div style="display: flex; flex-direction: column; gap: 6px">
<sc-for list="{{personas}}" as="p" hint-placeholder-count="4">
<button onClick="{{p.elegir}}" aria-pressed="{{p.on}}" style="min-height: 56px; border-radius: 18px; border: 2px solid {{p.borde}}; background: rgba(255,255,255,0.85); padding: 0 12px; display: flex; align-items: center; gap: 12px; font: 15px Inter, sans-serif; color: #111827; text-align: left; cursor: pointer">
<span style="width: 22px; height: 22px; border-radius: 11px; border: 2px solid {{p.borde}}; display: flex; align-items: center; justify-content: center; flex: none"><span style="width: 10px; height: 10px; border-radius: 5px; background: {{p.punto}}"></span></span>
<span style="flex: 1; display: flex; flex-direction: column"><span style="font-weight: 700">{{p.n}}</span><span style="font-size: 13px; color: #4B5563">{{p.cat}} · Cuadrilla {{p.cu}}</span></span>
</button>
</sc-for>
</div>
<span style="font-size: 12px; color: #4B5563">Solo aparecen los trabajadores activos de esta obra (Mano de obra).</span>
</div>
<label class="vidrio" style="border-radius: 20px; padding: 12px 16px; ''' + ETIQUETA + '''">Fecha de entrega *<input class="campo" type="date" value="{{fecha}}" onChange="{{eFecha}}"></label>
<button disabled="{{bloqueado}}" style="{{estiloBoton}}">''' + CHECK + '''{{textoBoton}}</button>
</div>
'''
    script = '''
const TRAB = ''' + json.dumps(TRAB, ensure_ascii=False) + ''';
const BOTON = "''' + BOTON + '''";
const BOTON_OFF = "''' + BOTON_OFF + '''";
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { q: '', sel: -1, fecha: new Date().toISOString().slice(0, 10) }; }
  renderVals() {
    const st = this.state;
    const q = st.q.toLowerCase().trim();
    const bloqueado = st.sel < 0 || !st.fecha;
    return {
      q: st.q, buscar: (e) => this.setState({ q: e.target.value }),
      personas: TRAB.map((t, i) => ({ ...t, i })).filter((t) => !q || (t.n + ' ' + t.cat).toLowerCase().includes(q)).map((t) => ({ ...t,
        on: st.sel === t.i, elegir: () => this.setState({ sel: t.i }),
        borde: st.sel === t.i ? '#C2410C' : 'rgba(17,24,39,0.12)', punto: st.sel === t.i ? '#C2410C' : 'transparent' })),
      fecha: st.fecha, eFecha: (e) => this.setState({ fecha: e.target.value }),
      bloqueado, estiloBoton: bloqueado ? BOTON_OFF : BOTON,
      textoBoton: st.sel < 0 ? 'Elige quién la recibe' : 'Entregar a ' + TRAB[st.sel].n
    };
  }
}
'''
    return s + pie(script, h)


# ───────────────────────────── 4.5 Devolución
def devolver():
    h = 1000
    dib = '''<g fill="none" stroke="#475569" stroke-width="1.4" opacity="0.5">
<path d="M250 600v300M330 600v300M250 600h80M250 700h80M250 800h80M250 600l80 100M250 700l80 100M250 800l80 100"></path></g>
<g fill="none" stroke="#C2410C" stroke-width="1.3" opacity="0.55" stroke-linecap="round"><path d="M180 170H60M80 150l-20 20 20 20"></path></g>
<text x="60" y="200" font-family="Inter, sans-serif" font-size="10" fill="#C2410C" opacity="0.75">DEVOLUCIÓN</text>'''
    s = cabeza("Devolución de herramienta") + raiz(h) + fondo("hd", h, ("#EEF1F6", "#F2F0EE", "#F5EAE1"), [(320, 150, 200, "t"), (40, 560, 210, "n"), (330, 900, 170, "a")], dib)
    s += encabezado("HerFicha.dc.html", "Devolución", "Se compara con el estado de la entrega")
    s += '<div style="position: relative; padding: 8px 16px 24px; display: flex; flex-direction: column; gap: 14px">\n'
    s += tarjeta_herramienta('Amoladora angular 4 1/2"', "H-001", "en_uso", " · Trabajador 03")
    s += '''<div class="vidrio" style="border-radius: 26px; padding: 18px; display: flex; flex-direction: column; gap: 14px">
<div style="display: flex; align-items: center; justify-content: space-between; background: rgba(255,255,255,0.8); border-radius: 16px; padding: 12px 14px">
<span style="font-size: 14px; color: #374151">Se entregó en estado</span><span style="background: #DCFCE7; color: #166534; border-radius: 999px; padding: 4px 12px; font-size: 13px; font-weight: 800">Buena</span></div>
<div style="''' + ETIQUETA + '''">¿Quién la devuelve? *''' + CHIPS % "quien" + '''</div>
<div style="''' + ETIQUETA + '''">Estado al devolver *''' + CHIPS % "estados" + '''</div>
<sc-if value="{{empeoro}}" hint-placeholder-val="{{ true }}">
<div role="alert" style="background: #FEF3C7; color: #92400E; border-radius: 16px; padding: 12px 14px; font-size: 14px; font-weight: 600; line-height: 1.4">El estado empeoró respecto a la entrega: escribe qué pasó. Queda en el historial de la herramienta.</div>
<label style="''' + ETIQUETA + '''">Observación *<textarea value="{{obs}}" onChange="{{eObs}}" rows="3" placeholder="p. ej. disco de corte rajado" class="campo" style="height: auto; padding: 12px 16px; resize: none; font: 16px Inter, sans-serif"></textarea></label>
</sc-if>
<button onClick="{{foto}}" style="min-height: 48px; border-radius: 24px; border: 2px dashed #C2410C; background: rgba(255,237,213,0.5); display: flex; align-items: center; justify-content: center; gap: 8px; font: 700 14px Inter, sans-serif; color: #9A3412; cursor: pointer">
<svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#C2410C" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8h3l2-3h6l2 3h3v11H4z"></path><circle cx="12" cy="13" r="3.5"></circle></svg>{{textoFotos}}</button>
</div>
<button disabled="{{bloqueado}}" style="{{estiloBoton}}">''' + CHECK + '''Registrar devolución</button>
<span style="text-align: center; font-size: 13px; color: #4B5563">Vuelve a «disponible» y puede entregarse de nuevo.</span>
</div>
'''
    script = OPC + '''
const BOTON = "''' + BOTON + '''";
const BOTON_OFF = "''' + BOTON_OFF + '''";
const ENTREGA = 1; // 0 nueva · 1 buena · 2 regular · 3 deteriorada
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { quien: 0, estado: 2, obs: '', fotos: 0 }; }
  renderVals() {
    const st = this.state;
    const empeoro = st.estado > ENTREGA;
    const bloqueado = st.estado < 0 || (empeoro && !st.obs.trim());
    return {
      quien: opciones(['Trabajador 03 (la tiene)', 'Otra persona'], st.quien, (i) => this.setState({ quien: i })),
      estados: opciones(['Nueva', 'Buena', 'Regular', 'Deteriorada'], st.estado, (i) => this.setState({ estado: i }), '#C2410C'),
      empeoro, obs: st.obs, eObs: (e) => this.setState({ obs: e.target.value }),
      foto: () => this.setState({ fotos: st.fotos + 1 }),
      textoFotos: st.fotos ? st.fotos + (st.fotos === 1 ? ' foto · tomar otra' : ' fotos · tomar otra') : 'Tomar foto (opcional)',
      bloqueado, estiloBoton: bloqueado ? BOTON_OFF : BOTON
    };
  }
}
'''
    return s + pie(script, h)


# ───────────────────────────── 4.6 Mantenimiento o baja
def mant_baja():
    h = 1000
    dientes = " ".join(f"M{300 + 70 * __import__('math').cos(a):.1f} {190 + 70 * __import__('math').sin(a):.1f}L{300 + 86 * __import__('math').cos(a):.1f} {190 + 86 * __import__('math').sin(a):.1f}"
                       for a in [i * 3.14159 / 6 for i in range(12)])
    dib = f'''<g fill="none" stroke="#475569" stroke-width="1.4" opacity="0.5"><circle cx="300" cy="190" r="70"></circle><circle cx="300" cy="190" r="26"></circle>
<path d="{dientes}" stroke-width="8" stroke-linecap="round" opacity="0.6"></path></g>
<g fill="none" stroke="#475569" stroke-width="1.3" opacity="0.45"><rect x="30" y="760" width="150" height="120" rx="8"></rect><path d="M30 790h150M60 750v20M150 750v20"></path>
<path d="M50 810h20M90 810h20M130 810h20M50 840h20M90 840h20"></path></g>
<circle cx="140" cy="850" r="11" fill="none" stroke="#C2410C" stroke-width="1.6" opacity="0.6"></circle>'''
    s = cabeza("Mantenimiento o baja") + raiz(h) + fondo("hm", h, ("#EEF0F6", "#F2F1EE", "#F6EADF"), [(330, 200, 200, "n"), (40, 560, 200, "a"), (320, 920, 160, "t")], dib)
    s += encabezado("HerFicha.dc.html", "Mantenimiento o baja", "La baja no borra su historial")
    s += '<div style="position: relative; padding: 8px 16px 24px; display: flex; flex-direction: column; gap: 14px">\n'
    s += tarjeta_herramienta("Mezcladora de concreto 9 p³", "H-003", "disponible", " · última: Trabajador 05")
    s += '''<div role="tablist" class="vidrio" style="border-radius: 26px; padding: 5px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 4px">
<sc-for list="{{pestanas}}" as="t" hint-placeholder-count="2">
<button role="tab" aria-selected="{{t.on}}" onClick="{{t.elegir}}" style="min-height: 46px; border-radius: 22px; border: 0; background: {{t.bg}}; color: {{t.fg}}; font: 700 15px Inter, sans-serif; cursor: pointer">{{t.texto}}</button>
</sc-for>
</div>
<sc-if value="{{esMant}}" hint-placeholder-val="{{ true }}">
<div class="vidrio" style="border-radius: 26px; padding: 18px; display: flex; flex-direction: column; gap: 14px">
<div style="''' + ETIQUETA + '''">Tipo *''' + CHIPS % "tipos" + '''</div>
<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px">
<label style="''' + ETIQUETA + '''">Fecha *<input class="campo" type="date" value="{{hoy}}"></label>
<label style="''' + ETIQUETA + '''">Costo (S/)<input class="campo" inputmode="decimal" placeholder="Si lo hubo"></label>
</div>
<label style="''' + ETIQUETA + '''">Próximo mantenimiento<input class="campo" type="date"></label>
<span style="font-size: 13px; color: #4B5563; line-height: 1.4">Mientras dure, queda «en mantenimiento» y no puede entregarse. Si pasa la fecha del próximo, la lista lo señala.</span>
</div>
<button style="''' + BOTON + '''">''' + CHECK + '''Enviar a mantenimiento</button>
</sc-if>
<sc-if value="{{esBaja}}" hint-placeholder-val="{{ false }}">
<div class="vidrio" style="border-radius: 26px; padding: 18px; display: flex; flex-direction: column; gap: 14px">
<div style="''' + ETIQUETA + '''">Motivo *''' + CHIPS % "motivos" + '''</div>
<div style="background: rgba(255,255,255,0.8); border-radius: 16px; padding: 12px 14px; display: flex; flex-direction: column; gap: 2px">
<span style="font-size: 12px; color: #4B5563">Último responsable (se registra en la baja)</span><span style="font-size: 15px; font-weight: 700">Trabajador 05 · Operario · Cuadrilla B</span></div>
<label style="''' + ETIQUETA + '''">Detalle<textarea rows="3" placeholder="Qué pasó, cuándo y dónde" class="campo" style="height: auto; padding: 12px 16px; resize: none; font: 16px Inter, sans-serif"></textarea></label>
</div>
<div style="background: #FEE2E2; color: #991B1B; border-radius: 18px; padding: 12px 14px; font-size: 14px; font-weight: 600; line-height: 1.45">La baja queda pendiente hasta que el residente la autorice. Después ya no puede entregarse ni devolverse, pero su historial se conserva.</div>
<button disabled="{{sinMotivo}}" style="{{estiloBaja}}">Solicitar baja</button>
</sc-if>
</div>
'''
    script = OPC + '''
const BOTON = "''' + BOTON.replace("#111827", "#991B1B") + '''";
const BOTON_OFF = "''' + BOTON_OFF + '''";
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { tab: 0, tipo: 1, motivo: -1 }; }
  renderVals() {
    const st = this.state;
    return {
      pestanas: ['Mantenimiento', 'Dar de baja'].map((t, i) => ({ texto: t, on: st.tab === i, elegir: () => this.setState({ tab: i }),
        bg: st.tab === i ? '#111827' : 'transparent', fg: st.tab === i ? '#FFFFFF' : '#111827' })),
      esMant: st.tab === 0, esBaja: st.tab === 1,
      tipos: opciones(['Preventivo', 'Correctivo'], st.tipo, (i) => this.setState({ tipo: i }), '#C2410C'),
      motivos: opciones(['Pérdida', 'Deterioro irreparable', 'Robo'], st.motivo, (i) => this.setState({ motivo: i }), '#991B1B'),
      hoy: new Date().toISOString().slice(0, 10),
      sinMotivo: st.motivo < 0, estiloBaja: st.motivo < 0 ? BOTON_OFF : BOTON
    };
  }
}
'''
    return s + pie(script, h)


PANTALLAS = (("HerLista", lista), ("HerFicha", ficha), ("HerIngreso", ingreso), ("HerEntregar", entregar), ("HerDevolver", devolver), ("HerMantBaja", mant_baja))
if __name__ == "__main__":
    for nombre, f in PANTALLAS:
        open(f"{R}/project/{nombre}.dc.html", "w", encoding="utf-8").write(f())
        print(nombre)
