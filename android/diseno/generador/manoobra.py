"""5 · Mano de obra (HU-62, HU-63). Personas y documentos: datos de ejemplo."""
import json
from comun import *
from herramientas import OPC, CHIPS, TRAB

AVATAR = {"Operario": ("#FFEDD5", "#9A3412"), "Oficial": ("#DBEAFE", "#1E40AF"), "Peón": ("#DCFCE7", "#166534")}


def fila_trabajador(t, n):
    bg, fg = AVATAR[t["cat"]]
    return f'''<div style="padding: 12px 16px; display: flex; align-items: center; gap: 12px; border-bottom: 1px solid rgba(17,24,39,0.07)">
<span style="width: 42px; height: 42px; border-radius: 21px; background: {bg}; color: {fg}; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 800; flex: none">T{n}</span>
<span style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px"><span style="font-size: 15px; font-weight: 700">{t["n"]}</span><span style="font-size: 13px; color: #4B5563">DNI [documento] · desde el inicio de obra</span></span>
<span style="background: {bg}; color: {fg}; border-radius: 999px; padding: 4px 10px; font-size: 12px; font-weight: 700">{t["cat"]}</span>
</div>
'''


# ───────────────────────────── 5.1 Trabajadores
def trabajadores():
    h = 1000
    dib = '''<g fill="none" stroke="#475569" stroke-width="1.4" opacity="0.5" stroke-linecap="round">
<path d="M250 200a40 34 0 0 1 80 0M244 200h92M290 166v-10"></path><path d="M300 270a30 26 0 0 1 60 0M296 270h68"></path>
<circle cx="240" cy="760" r="16"></circle><circle cx="290" cy="760" r="16"></circle><circle cx="340" cy="760" r="16"></circle>
<path d="M216 820c4-24 44-24 48 0M266 820c4-24 44-24 48 0M316 820c4-24 44-24 48 0"></path></g>
<text x="250" y="850" font-family="Inter, sans-serif" font-size="10" fill="#475569" opacity="0.7">CUADRILLA A</text>
<g fill="none" stroke="#C2410C" stroke-width="1.1" opacity="0.55"><path d="M216 866h150M216 860v12M366 860v12"></path></g>'''
    s = cabeza("Trabajadores de la obra") + raiz(h) + fondo("mt", h, ("#EDF1F6", "#F2F1EE", "#F6EADF"), [(320, 150, 200, "n"), (40, 520, 210, "t"), (330, 820, 160, "a")], dib)
    s += encabezado("Inicio.dc.html", "Trabajadores", "Multifamiliar Flores · 6 activos")
    s += '''<div style="position: relative; padding: 8px 16px 0; display: flex; flex-direction: column; gap: 12px">
<a href="MOAsistencia.dc.html" style="border-radius: 24px; padding: 14px 16px; background: #C2410C; color: #FFFFFF; display: flex; align-items: center; gap: 12px; text-decoration: none; box-shadow: 0 10px 24px rgba(194,65,12,0.30)">
<svg aria-hidden="true" width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex: none"><rect x="3" y="5" width="18" height="16" rx="2"></rect><path d="M3 10h18M8 3v4M16 3v4M8 15l2 2 4-4"></path></svg>
<span style="flex: 1; display: flex; flex-direction: column"><span style="font-size: 16px; font-weight: 800">Asistencia de hoy</span><span style="font-size: 13px; opacity: 0.92">Falta registrar · 6 trabajadores</span></span>
<svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round"><path d="M9 6l6 6-6 6"></path></svg></a>
<div class="vidrio" style="border-radius: 22px; padding: 12px 16px; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px">
<div style="display: flex; flex-direction: column"><span style="font-size: 24px; font-weight: 800">2</span><span style="font-size: 12px; color: #374151">Operarios</span></div>
<div style="display: flex; flex-direction: column; border-left: 1px solid rgba(17,24,39,0.10); padding-left: 12px"><span style="font-size: 24px; font-weight: 800">2</span><span style="font-size: 12px; color: #374151">Oficiales</span></div>
<div style="display: flex; flex-direction: column; border-left: 1px solid rgba(17,24,39,0.10); padding-left: 12px"><span style="font-size: 24px; font-weight: 800">2</span><span style="font-size: 12px; color: #374151">Peones</span></div>
</div>
'''
    for cu in ("A", "B"):
        s += f'<h3 style="{SECCION}">Cuadrilla {cu}</h3>\n<div class="vidrio" style="border-radius: 24px; overflow: hidden">\n'
        for i, t in enumerate(TRAB):
            if t["cu"] == cu:
                s += fila_trabajador(t, i + 1)
        if cu == "B":
            s += '''<div style="padding: 12px 16px; display: flex; align-items: center; gap: 12px; opacity: 0.7">
<span style="width: 42px; height: 42px; border-radius: 21px; background: #E5E7EB; color: #374151; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 800; flex: none">T7</span>
<span style="flex: 1; display: flex; flex-direction: column; gap: 2px"><span style="font-size: 15px; font-weight: 700">Trabajador 07</span><span style="font-size: 13px; color: #4B5563">Salió el [fecha] · sus jornadas se conservan</span></span>
<span style="background: #E5E7EB; color: #374151; border-radius: 999px; padding: 4px 10px; font-size: 12px; font-weight: 700">De baja</span></div>
'''
        s += "</div>\n"
    s += '''</div>
<a href="MONuevo.dc.html" style="position: absolute; right: 18px; bottom: 24px; height: 58px; padding: 0 22px 0 18px; border-radius: 29px; background: #111827; color: #FFFFFF; display: flex; align-items: center; gap: 10px; font-size: 16px; font-weight: 700; text-decoration: none; box-shadow: 0 12px 28px rgba(17,24,39,0.32)">
<svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#FB923C" stroke-width="2.6" stroke-linecap="round"><path d="M12 5v14M5 12h14"></path></svg>Registrar trabajador</a>
'''
    return s + pie("class Component extends DCLogic { renderVals() { return {}; } }", h)


# ───────────────────────────── 5.2 Registrar trabajador
def nuevo():
    h = 1000
    dib = '''<g fill="none" stroke="#475569" stroke-width="1.4" opacity="0.5" stroke-linejoin="round">
<rect x="226" y="120" width="150" height="96" rx="10"></rect><circle cx="262" cy="160" r="16"></circle><path d="M242 196c4-14 36-14 40 0"></path>
<path d="M298 150h60M298 166h44M298 182h52"></path></g>
<g fill="none" stroke="#475569" stroke-width="1.2" opacity="0.4" stroke-linecap="round"><path d="M70 800c0-30 50-30 50 0v30M80 800c0-18 30-18 30 0v40M90 800v44M60 805v20M130 805v20"></path></g>
<text x="232" y="238" font-family="Inter, sans-serif" font-size="9" fill="#475569" opacity="0.7">DOCUMENTO DE IDENTIDAD</text>'''
    s = cabeza("Registrar trabajador") + raiz(h) + fondo("mn", h, ("#EEF1F7", "#F3F1EE", "#F6EBE0"), [(330, 160, 200, "a"), (40, 560, 200, "n"), (330, 900, 170, "t")], dib)
    s += encabezado("MOTrabajadores.dc.html", "Nuevo trabajador", "Se incorpora a la obra con su fecha")
    s += '''<div style="position: relative; padding: 8px 16px 24px; display: flex; flex-direction: column; gap: 14px">
<div class="vidrio" style="border-radius: 26px; padding: 18px; display: flex; flex-direction: column; gap: 14px">
<label style="''' + ETIQUETA + '''">DNI *
<div style="display: flex; gap: 8px">
<input class="campo" value="{{dni}}" onChange="{{eDni}}" inputmode="numeric" maxlength="8" placeholder="8 dígitos" style="flex: 1; letter-spacing: 0.08em">
<button onClick="{{consultar}}" disabled="{{noConsulta}}" style="flex: none; height: 52px; padding: 0 16px; border-radius: 16px; border: 0; background: {{consultaFondo}}; color: #FFFFFF; font: 700 14px Inter, sans-serif; cursor: pointer">Consultar RENIEC</button>
</div></label>
<sc-if value="{{repetido}}" hint-placeholder-val="{{ false }}">
<div role="alert" style="background: #FEE2E2; color: #991B1B; border-radius: 14px; padding: 10px 12px; font-size: 13px; font-weight: 600">Ese DNI ya está en esta obra (Trabajador 03). Puede estar en otra obra, pero no dos veces en la misma.</div>
</sc-if>
<sc-if value="{{encontrado}}" hint-placeholder-val="{{ false }}">
<div style="background: #DCFCE7; border-radius: 16px; padding: 12px 14px; display: flex; align-items: center; gap: 10px">
<svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#166534" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" style="flex: none"><path d="M5 12.5l4.5 4.5L19 7.5"></path></svg>
<span style="display: flex; flex-direction: column"><span style="font-size: 12px; color: #166534; font-weight: 700">SEGÚN RENIEC</span><span style="font-size: 15px; font-weight: 800; color: #14532D">[Nombres y apellidos]</span></span></div>
</sc-if>
<sc-if value="{{manual}}" hint-placeholder-val="{{ false }}">
<label style="''' + ETIQUETA + '''">Nombre completo *<input class="campo" value="{{nombre}}" onChange="{{eNombre}}" placeholder="Como figura en su DNI"></label>
</sc-if>
<button onClick="{{aMano}}" style="align-self: flex-start; border: 0; background: transparent; padding: 0; font: 600 13px Inter, sans-serif; color: #C2410C; cursor: pointer; text-decoration: underline">{{textoManual}}</button>
<div style="''' + ETIQUETA + '''">Categoría *''' + CHIPS % "categorias" + '''<span style="font-size: 12px; font-weight: 500; color: #4B5563">Define el costo por hora que se aplica después.</span></div>
<div style="''' + ETIQUETA + '''">Cuadrilla *''' + CHIPS % "cuadrillas" + '''</div>
<label style="''' + ETIQUETA + '''">Fecha de ingreso *<input class="campo" type="date" value="{{hoy}}"></label>
</div>
<button disabled="{{bloqueado}}" style="{{estiloBoton}}">''' + CHECK + '''Registrar trabajador</button>
<span style="text-align: center; font-size: 13px; color: #4B5563; line-height: 1.4">Si se retira, se da de baja con su fecha de salida: no se borra y sus jornadas se conservan.</span>
</div>
'''
    script = OPC + '''
const BOTON = "''' + BOTON + '''";
const BOTON_OFF = "''' + BOTON_OFF + '''";
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { dni: '', encontrado: false, manual: false, nombre: '', cat: -1, cu: 0 }; }
  renderVals() {
    const st = this.state;
    const ocho = /^\\d{8}$/.test(st.dni);
    const repetido = st.dni === '12345678';
    const tieneNombre = st.encontrado || (st.manual && st.nombre.trim());
    const bloqueado = !ocho || repetido || !tieneNombre || st.cat < 0;
    return {
      dni: st.dni, eDni: (e) => this.setState({ dni: e.target.value.replace(/\\D/g, '').slice(0, 8), encontrado: false }),
      noConsulta: !ocho || repetido, consultaFondo: (!ocho || repetido) ? '#9CA3AF' : '#C2410C',
      consultar: () => this.setState({ encontrado: true, manual: false }),
      repetido, encontrado: st.encontrado, manual: st.manual,
      nombre: st.nombre, eNombre: (e) => this.setState({ nombre: e.target.value }),
      aMano: () => this.setState({ manual: !st.manual, encontrado: false }),
      textoManual: st.manual ? 'Volver a consultar en RENIEC' : '¿RENIEC no responde? Escríbelo a mano',
      categorias: opciones(['Operario', 'Oficial', 'Peón'], st.cat, (i) => this.setState({ cat: i }), '#C2410C'),
      cuadrillas: opciones(['Cuadrilla A', 'Cuadrilla B', '+ Nueva'], st.cu, (i) => this.setState({ cu: i })),
      hoy: new Date().toISOString().slice(0, 10),
      bloqueado, estiloBoton: bloqueado ? BOTON_OFF : BOTON
    };
  }
}
'''
    return s + pie(script, h)


# ───────────────────────────── 5.3 Asistencia del día
def asistencia():
    h = 1200
    barras = "".join(f'<rect x="{30 + x}" y="{y}" width="{w}" height="10" rx="5"></rect>'
                     for x, y, w in [(0, 930, 120), (60, 950, 150), (120, 970, 110), (190, 990, 140), (40, 1010, 90)])
    dib = f'''<g fill="none" stroke="#475569" stroke-width="0.9" opacity="0.4"><path d="M30 910v120M80 910v120M130 910v120M180 910v120M230 910v120M280 910v120M330 910v120M30 910h300"></path></g>
<g font-family="Inter, sans-serif" font-size="10" fill="#475569" opacity="0.6"><text x="50" y="904">L</text><text x="100" y="904">M</text><text x="150" y="904">M</text><text x="200" y="904">J</text><text x="250" y="904">V</text><text x="300" y="904">S</text></g>
<g fill="#C2410C" opacity="0.28">{barras}</g>
<g fill="none" stroke="#475569" stroke-width="1.3" opacity="0.45"><circle cx="320" cy="170" r="50"></circle><path d="M320 130v40l26 16"></path></g>'''
    s = cabeza("Asistencia del día") + raiz(h) + fondo("ma", h, ("#EDF0F6", "#F2F0EE", "#F5E9DF"), [(330, 160, 200, "t"), (40, 560, 210, "a"), (330, 980, 170, "n")], dib)
    s += encabezado("MOTrabajadores.dc.html", "Asistencia", "Un registro por trabajador y día")
    s += '''<div style="position: relative; padding: 8px 16px 24px; display: flex; flex-direction: column; gap: 12px">
<div class="vidrio" style="border-radius: 22px; padding: 8px; display: flex; align-items: center; gap: 8px">
<button aria-label="Día anterior" style="width: 44px; height: 44px; border-radius: 22px; border: 0; background: rgba(255,255,255,0.85); display: flex; align-items: center; justify-content: center; cursor: pointer"><svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#111827" stroke-width="2.4" stroke-linecap="round"><path d="M15 6l-6 6 6 6"></path></svg></button>
<span style="flex: 1; display: flex; flex-direction: column; align-items: center"><span style="font-size: 16px; font-weight: 800">Hoy</span><span style="font-size: 12px; color: #4B5563">Periodo abierto · dentro del plazo de la obra</span></span>
<button aria-label="Día siguiente" disabled="{{ true }}" style="width: 44px; height: 44px; border-radius: 22px; border: 0; background: rgba(255,255,255,0.5); display: flex; align-items: center; justify-content: center; opacity: 0.5"><svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#111827" stroke-width="2.4" stroke-linecap="round"><path d="M9 6l6 6-6 6"></path></svg></button>
</div>
<div class="vidrio" style="border-radius: 22px; padding: 12px 16px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 6px">
<div style="display: flex; flex-direction: column"><span style="font-size: 22px; font-weight: 800; color: #166534">{{nP}}</span><span style="font-size: 12px; color: #374151">Presentes</span></div>
<div style="display: flex; flex-direction: column"><span style="font-size: 22px; font-weight: 800; color: #991B1B">{{nA}}</span><span style="font-size: 12px; color: #374151">Ausentes</span></div>
<div style="display: flex; flex-direction: column"><span style="font-size: 22px; font-weight: 800; color: #92400E">{{nL}}</span><span style="font-size: 12px; color: #374151">Permiso</span></div>
<div style="display: flex; flex-direction: column"><span style="font-size: 22px; font-weight: 800">{{horas}}</span><span style="font-size: 12px; color: #374151">Horas · {{extras}} extra</span></div>
</div>
<sc-for list="{{filas}}" as="t" hint-placeholder-count="6">
<div class="vidrio" style="border-radius: 22px; padding: 12px 14px; display: flex; flex-direction: column; gap: 10px">
<div style="display: flex; align-items: center; gap: 10px">
<span style="flex: 1; display: flex; flex-direction: column"><span style="font-size: 15px; font-weight: 700">{{t.n}}</span><span style="font-size: 12px; color: #4B5563">{{t.cat}} · Cuadrilla {{t.cu}}</span></span>
<div role="group" aria-label="Asistencia de {{t.n}}" style="display: flex; gap: 4px; background: rgba(255,255,255,0.7); border-radius: 22px; padding: 3px">
<sc-for list="{{t.estados}}" as="e" hint-placeholder-count="3">
<button onClick="{{e.elegir}}" aria-pressed="{{e.on}}" style="min-width: 44px; height: 38px; padding: 0 10px; border-radius: 19px; border: 0; background: {{e.bg}}; color: {{e.fg}}; font: 700 13px Inter, sans-serif; cursor: pointer">{{e.texto}}</button>
</sc-for>
</div>
</div>
<sc-if value="{{t.presente}}" hint-placeholder-val="{{ true }}">
<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px">
<div style="background: rgba(255,255,255,0.8); border-radius: 16px; padding: 6px 6px 6px 12px; display: flex; align-items: center; gap: 6px">
<span style="flex: 1; font-size: 12px; color: #374151">Normales</span>
<button onClick="{{t.menosN}}" aria-label="Menos horas normales" style="width: 34px; height: 34px; border-radius: 17px; border: 0; background: #F3F4F6; font: 700 18px Inter; cursor: pointer">−</button>
<span style="min-width: 22px; text-align: center; font-size: 16px; font-weight: 800">{{t.hn}}</span>
<button onClick="{{t.masN}}" aria-label="Más horas normales" style="width: 34px; height: 34px; border-radius: 17px; border: 0; background: #F3F4F6; font: 700 18px Inter; cursor: pointer">+</button></div>
<div style="background: rgba(255,255,255,0.8); border-radius: 16px; padding: 6px 6px 6px 12px; display: flex; align-items: center; gap: 6px">
<span style="flex: 1; font-size: 12px; color: #374151">Extra</span>
<button onClick="{{t.menosE}}" aria-label="Menos horas extra" style="width: 34px; height: 34px; border-radius: 17px; border: 0; background: #F3F4F6; font: 700 18px Inter; cursor: pointer">−</button>
<span style="min-width: 22px; text-align: center; font-size: 16px; font-weight: 800; color: #C2410C">{{t.he}}</span>
<button onClick="{{t.masE}}" aria-label="Más horas extra" style="width: 34px; height: 34px; border-radius: 17px; border: 0; background: #F3F4F6; font: 700 18px Inter; cursor: pointer">+</button></div>
</div>
</sc-if>
</div>
</sc-for>
<button style="''' + BOTON + '''">''' + CHECK + '''Guardar asistencia de hoy</button>
<span style="text-align: center; font-size: 13px; color: #4B5563; line-height: 1.45">Sin señal se guarda en el celular y se envía después, como las salidas. Un día ya guardado se corrige con un registro nuevo que deja constancia del anterior.</span>
</div>
'''
    script = '''
const TRAB = ''' + json.dumps(TRAB, ensure_ascii=False) + ''';
const EST = [['P', '#166534'], ['A', '#991B1B'], ['Permiso', '#B45309']];
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { f: TRAB.map((_, i) => ({ e: i === 3 ? 1 : 0, hn: 8, he: i === 0 ? 2 : 0 })) }; }
  fijar(i, cambio) { const f = this.state.f.map((x, j) => (j === i ? { ...x, ...cambio } : x)); this.setState({ f }); }
  renderVals() {
    const f = this.state.f;
    const pres = f.filter((x) => x.e === 0);
    return {
      nP: pres.length, nA: f.filter((x) => x.e === 1).length, nL: f.filter((x) => x.e === 2).length,
      horas: pres.reduce((s, x) => s + x.hn + x.he, 0), extras: pres.reduce((s, x) => s + x.he, 0),
      filas: TRAB.map((t, i) => ({ ...t, presente: f[i].e === 0, hn: f[i].hn, he: f[i].he,
        estados: EST.map(([txt, c], k) => ({ texto: txt, on: f[i].e === k, elegir: () => this.fijar(i, { e: k }),
          bg: f[i].e === k ? c : 'transparent', fg: f[i].e === k ? '#FFFFFF' : '#374151' })),
        menosN: () => this.fijar(i, { hn: Math.max(0, f[i].hn - 1) }), masN: () => this.fijar(i, { hn: Math.min(8, f[i].hn + 1) }),
        menosE: () => this.fijar(i, { he: Math.max(0, f[i].he - 1) }), masE: () => this.fijar(i, { he: Math.min(6, f[i].he + 1) }) }))
    };
  }
}
'''
    return s + pie(script, h)


if __name__ == "__main__":
    for nombre, f in (("MOTrabajadores", trabajadores), ("MONuevo", nuevo), ("MOAsistencia", asistencia)):
        open(f"{R}/project/{nombre}.dc.html", "w", encoding="utf-8").write(f())
        print(nombre)
