"""3 · Catálogo de insumos: buscar (HU-10, HU-09), ficha (HU-11, HU-09, HU-17), registrar nuevo (HU-08)."""
import json
from comun import *

# Insumos REALES del catálogo del cliente (api/semillas/catalogo-cliente.json): código, nombre, unidad, marca.
# Los nombres comerciales son ejemplos de cómo los llaman en obra (se registran con HU-09).
CAT = [
    {"id": 1151, "nombre": "CEMENTO PORTLAND TIPO I - 42.5 KG", "um": "bolsas", "marca": "Pacasmayo · Sol", "alias": ["cemento sol", "cemento pacasmayo"]},
    {"id": 1154, "nombre": "CEMENTO PORTLAND TIPO IP - 42.5 KG", "um": "bolsas", "marca": "Sol", "alias": ["cemento ip"]},
    {"id": 1146, "nombre": "FIERRO CORRUGADO DE 3/8\" x 9m - ASTM A615/A706 - GRADO 60", "um": "unidades", "marca": "Arequipa", "alias": ["fierro de tres octavos"]},
    {"id": 1147, "nombre": "FIERRO CORRUGADO DE 1/2\" x 9m - ASTM A615/A706 - GRADO 60", "um": "unidades", "marca": "Arequipa", "alias": ["fierro de media", "varilla de media"]},
    {"id": 1149, "nombre": "FIERRO CORRUGADO DE 3/4\" x 9m - ASTM A615/A706 - GRADO 60", "um": "unidades", "marca": "Arequipa", "alias": ["fierro de tres cuartos"]},
    {"id": 1288, "nombre": "CLAVO 1 1/2\" ESTRIADO PARA CONCRETO", "um": "kilogramos", "marca": "", "alias": ["clavo de acero"]},
    {"id": 1432, "nombre": "ALAMBRE RECOCIDO N°16", "um": "kilogramos", "marca": "Prodac", "alias": ["alambre negro"]},
    {"id": 2544, "nombre": "LADRILLO KING KONG 18 H - UND - 9X13X24 - INKA", "um": "unidades", "marca": "", "alias": ["king kong"]},
    {"id": 423, "nombre": "ARENA GRUESA", "um": "metro cúbico", "marca": "", "alias": []},
]
CAT_JS = json.dumps(CAT, ensure_ascii=False)
NORM = r"const norm = (s) => (s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/\s+/g, ' ').trim();"


# ───────────────────────────── 3.1 Buscar insumo
def buscar():
    h = 844
    dib = '''<g fill="none" stroke="#475569" stroke-width="1.3" opacity="0.5" stroke-linejoin="round">
<path d="M228 560l120-60 30 15v190l-120 60-30-15z"></path><path d="M228 560l30 15 120-60M258 575v190"></path>
<path d="M228 620l30 15 120-60M228 680l30 15 120-60M228 740l30 15 120-60"></path>
<path d="M268 600l22-11 0 16-22 11zM300 584l22-11 0 16-22 11zM270 662l30-15 0 14-30 15zM312 641l26-13 0 14-26 13z"></path>
<path d="M276 715c8-6 22-6 30 0v14c-8 6-22 6-30 0z"></path></g>
<g fill="none" stroke="#C2410C" stroke-width="1.1" opacity="0.55"><path d="M228 800h150M228 794v12M378 794v12"></path></g>
<text x="276" y="818" font-family="Inter, sans-serif" font-size="10" fill="#C2410C" opacity="0.75">ESTANTE E-2</text>
<g fill="none" stroke="#475569" stroke-width="1.1" opacity="0.4"><circle cx="60" cy="210" r="34"></circle><path d="M84 234l26 26" stroke-width="3" stroke-linecap="round"></path></g>'''
    s = cabeza("Buscar insumo") + raiz(h) + fondo("cb", h, ("#EEF1F6", "#F2F1EE", "#F5EBE2"), [(330, 120, 190, "n"), (40, 470, 200, "a"), (320, 760, 170, "t")], dib)
    s += encabezado("Inicio.dc.html", "Catálogo", "Código único en todas las obras")
    s += '''<div style="position: relative; padding: 8px 16px 0; display: flex; flex-direction: column; gap: 14px">
<label class="vidrio-fuerte" style="height: 58px; border-radius: 29px; padding: 0 8px 0 18px; display: flex; align-items: center; gap: 10px; box-shadow: 0 10px 28px rgba(15,23,42,0.12)">
<svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#C2410C" stroke-width="2.4" stroke-linecap="round"><circle cx="11" cy="11" r="7"></circle><path d="M20 20l-4-4"></path></svg>
<input value="{{q}}" onChange="{{escribir}}" placeholder="Nombre, código o como le dicen en obra" aria-label="Buscar insumo" style="flex: 1; min-width: 0; border: 0; background: transparent; font: 16px Inter, sans-serif; color: #111827; outline: none">
<sc-if value="{{hayTexto}}" hint-placeholder-val="{{ true }}">
<button onClick="{{limpiar}}" aria-label="Borrar búsqueda" style="width: 44px; height: 44px; border-radius: 22px; border: 0; background: rgba(17,24,39,0.06); display: flex; align-items: center; justify-content: center; cursor: pointer"><svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#111827" stroke-width="2.4" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"></path></svg></button>
</sc-if>
</label>
<div style="display: flex; flex-wrap: wrap; gap: 8px">
<sc-for list="{{sugerencias}}" as="s" hint-placeholder-count="3">
<button onClick="{{s.usar}}" class="vidrio" style="min-height: 36px; padding: 0 14px; border-radius: 18px; font: 600 13px Inter, sans-serif; color: #374151; cursor: pointer">{{s.texto}}</button>
</sc-for>
</div>
<p style="margin: 0 4px; font-size: 13px; color: #4B5563">{{resumen}}</p>
<div class="vidrio" style="border-radius: 26px; display: flex; flex-direction: column; overflow: hidden">
<sc-for list="{{resultados}}" as="r" hint-placeholder-count="3">
<a href="CatFicha.dc.html" style="padding: 13px 16px; display: flex; align-items: center; gap: 12px; border-bottom: 1px solid rgba(17,24,39,0.07); text-decoration: none; color: #111827">
<span style="width: 44px; height: 44px; border-radius: 14px; background: #FFEDD5; color: #9A3412; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 800; flex: none">{{r.id}}</span>
<span style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px">
<span style="font-size: 14px; font-weight: 700; line-height: 1.3">{{r.nombre}}</span>
<span style="font-size: 13px; color: #4B5563">{{r.um}}{{r.marcaTxt}}</span>
<sc-if value="{{r.porAlias}}" hint-placeholder-val="{{ false }}">
<span style="align-self: flex-start; background: #FFEDD5; color: #9A3412; border-radius: 999px; padding: 3px 10px; font-size: 12px; font-weight: 700">le dicen «{{r.alias}}»</span>
</sc-if>
</span>
<svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#6B7280" stroke-width="2.2" stroke-linecap="round"><path d="M9 6l6 6-6 6"></path></svg>
</a>
</sc-for>
<sc-if value="{{vacio}}" hint-placeholder-val="{{ false }}">
<div style="padding: 22px 18px; display: flex; flex-direction: column; gap: 10px; align-items: flex-start">
<span style="font-size: 16px; font-weight: 700">No está en el catálogo</span>
<span style="font-size: 14px; color: #4B5563; line-height: 1.45">Revisa cómo lo escribiste. Si de verdad es nuevo, regístralo: el sistema le da su código.</span>
</div>
</sc-if>
</div>
<a href="CatNuevo.dc.html" class="vidrio" style="min-height: 52px; border-radius: 26px; display: flex; align-items: center; justify-content: center; gap: 8px; font-size: 15px; font-weight: 700; color: #111827; text-decoration: none">
<svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#C2410C" stroke-width="2.4" stroke-linecap="round"><path d="M12 5v14M5 12h14"></path></svg>Registrar insumo nuevo</a>
</div>
'''
    script = '''
const CAT = ''' + CAT_JS + ''';
''' + NORM + '''
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { q: 'fierro de media' }; }
  renderVals() {
    const q = norm(this.state.q);
    const res = !q ? CAT : CAT.map((c) => {
      const alias = c.alias.find((a) => norm(a).includes(q));
      const directo = norm(c.nombre).includes(q) || String(c.id) === q;
      return directo || alias ? { ...c, porAlias: !directo && !!alias, alias: alias || '' } : null;
    }).filter(Boolean);
    const sug = ['cemento sol', 'king kong', '1432', 'alambre negro'];
    return {
      q: this.state.q,
      hayTexto: q.length > 0,
      escribir: (e) => this.setState({ q: e.target.value }),
      limpiar: () => this.setState({ q: '' }),
      sugerencias: sug.map((t) => ({ texto: t, usar: () => this.setState({ q: t }) })),
      resultados: res.map((r) => ({ porAlias: false, alias: '', ...r, marcaTxt: r.marca ? ' · ' + r.marca : '' })),
      vacio: res.length === 0,
      resumen: !q ? CAT.length + ' insumos de ejemplo · busca sin tildes ni mayúsculas'
                  : res.length + (res.length === 1 ? ' coincidencia' : ' coincidencias') + ' por nombre, código o nombre comercial'
    };
  }
}
'''
    return s + pie(script, h)


# ───────────────────────────── 3.2 Ficha del insumo
def ficha():
    h = 1080
    dib = '''<g fill="none" stroke="#475569" opacity="0.5">
<circle cx="320" cy="150" r="18" stroke-width="1.4"></circle><circle cx="320" cy="150" r="24" stroke-width="0.8" stroke-dasharray="3 3"></circle>
<circle cx="260" cy="200" r="13" stroke-width="1.4"></circle><circle cx="352" cy="226" r="28" stroke-width="1.4"></circle>
<path d="M30 880h240M30 896h240" stroke-width="1.4"></path>
<path d="M44 880l-8 16M64 880l-8 16M84 880l-8 16M104 880l-8 16M124 880l-8 16M144 880l-8 16M164 880l-8 16M184 880l-8 16M204 880l-8 16M224 880l-8 16M244 880l-8 16M264 880l-8 16" stroke-width="1"></path></g>
<g font-family="Inter, sans-serif" font-size="10" fill="#475569" opacity="0.7"><text x="304" y="186">Ø 1/2"</text><text x="236" y="226">Ø 3/8"</text><text x="334" y="270">Ø 3/4"</text></g>
<g fill="none" stroke="#C2410C" stroke-width="1.1" opacity="0.6"><path d="M30 920h240M30 914v12M270 914v12"></path></g>
<text x="128" y="938" font-family="Inter, sans-serif" font-size="10" fill="#C2410C" opacity="0.8">9.00 m</text>'''
    s = cabeza("Ficha del insumo") + raiz(h) + fondo("cf", h, ("#EDF0F6", "#F2F0EE", "#F5EAE0"), [(340, 160, 200, "a"), (40, 600, 200, "n"), (330, 980, 180, "t")], dib)
    s += encabezado("CatBuscar.dc.html", "Insumo", "Código 1147 · el mismo en todas las obras")
    s += '''<div style="position: relative; padding: 8px 16px 24px; display: flex; flex-direction: column; gap: 14px">
<div class="vidrio" style="border-radius: 28px; overflow: hidden">
<div style="height: 168px; position: relative; background: linear-gradient(135deg, #E2E8F0, #F1F5F9)">
<svg role="img" aria-label="Espacio para la foto del insumo" width="358" height="168" viewBox="0 0 358 168" style="position: absolute; left: 0; top: 0">
<g stroke="#64748B" stroke-width="7" stroke-linecap="round" opacity="0.55"><path d="M40 120L318 60"></path><path d="M40 138L318 78"></path><path d="M40 102L318 42"></path></g>
<g stroke="#F1F5F9" stroke-width="2" opacity="0.9"><path d="M80 112l4 -14M120 103l4-14M160 94l4-14M200 86l4-14M240 77l4-14M280 69l4-14"></path></g>
<text x="18" y="156" font-family="Inter, sans-serif" font-size="11" fill="#475569">[foto real del insumo en almacén]</text>
</svg>
<button class="vidrio-fuerte" style="position: absolute; right: 12px; bottom: 12px; min-height: 40px; padding: 0 14px; border-radius: 20px; display: flex; align-items: center; gap: 8px; font: 700 13px Inter, sans-serif; color: #111827; cursor: pointer">
<svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#C2410C" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8h3l2-3h6l2 3h3v11H4z"></path><circle cx="12" cy="13" r="3.5"></circle></svg>Cambiar foto</button>
</div>
<div style="padding: 16px 18px; display: flex; flex-direction: column; gap: 8px">
<h2 style="margin: 0; font-size: 19px; font-weight: 800; line-height: 1.3">FIERRO CORRUGADO DE 1/2" x 9m - ASTM A615/A706 - GRADO 60</h2>
<div style="display: flex; flex-wrap: wrap; gap: 6px">
<span style="background: #FFEDD5; color: #9A3412; border-radius: 999px; padding: 4px 10px; font-size: 12px; font-weight: 700">Material</span>
<span style="background: #DCFCE7; color: #166534; border-radius: 999px; padding: 4px 10px; font-size: 12px; font-weight: 700">Activo</span>
<span style="background: #DBEAFE; color: #1E40AF; border-radius: 999px; padding: 4px 10px; font-size: 12px; font-weight: 700">Verificado</span>
</div>
<span style="font-size: 12px; color: #4B5563">Foto: una por insumo (JPG o PNG) · subida por [usuario] el [fecha]</span>
</div>
</div>

<div class="vidrio" style="border-radius: 24px; padding: 6px 18px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 14px">
<div style="padding: 10px 0; display: flex; flex-direction: column; gap: 2px; border-bottom: 1px solid rgba(17,24,39,0.07)"><span style="font-size: 12px; color: #4B5563">Unidad de compra</span><span style="font-size: 15px; font-weight: 700">varilla (unidades)</span></div>
<div style="padding: 10px 0; display: flex; flex-direction: column; gap: 2px; border-bottom: 1px solid rgba(17,24,39,0.07)"><span style="font-size: 12px; color: #4B5563">Medida</span><span style="font-size: 15px; font-weight: 700">1/2" × 9 m</span></div>
<div style="padding: 10px 0; display: flex; flex-direction: column; gap: 2px"><span style="font-size: 12px; color: #4B5563">Norma</span><span style="font-size: 15px; font-weight: 700">ASTM A615/A706 · G60</span></div>
<div style="padding: 10px 0; display: flex; flex-direction: column; gap: 2px"><span style="font-size: 12px; color: #4B5563">Marca</span><span style="font-size: 15px; font-weight: 700">Arequipa</span></div>
</div>

<h3 style="''' + SECCION + '''">Factor de conversión</h3>
<div class="vidrio" style="border-radius: 24px; padding: 16px 18px; display: flex; flex-direction: column; gap: 10px">
<div style="display: flex; align-items: center; gap: 10px">
<span style="font-size: 26px; font-weight: 800; letter-spacing: -0.02em">1 varilla</span>
<svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#C2410C" stroke-width="2.4" stroke-linecap="round"><path d="M5 9h14M5 15h14"></path></svg>
<span style="font-size: 26px; font-weight: 800; letter-spacing: -0.02em; color: #9A3412">[factor] kg</span>
</div>
<span style="font-size: 14px; color: #374151; line-height: 1.45">El S10 trae el acero como un solo recurso en kilos (fy = 4200). Se compra por varilla y diámetro: con este factor el sistema compara requerido, comprado y consumido en la misma unidad.</span>
<span style="font-size: 12px; color: #4B5563">Declarado por [residente] el [fecha] · cada cambio queda en su historial</span>
</div>

<h3 style="''' + SECCION + '''">Nombres comerciales</h3>
<div class="vidrio" style="border-radius: 24px; padding: 16px 18px; display: flex; flex-direction: column; gap: 12px">
<div style="display: flex; flex-wrap: wrap; gap: 8px">
<sc-for list="{{alias}}" as="a" hint-placeholder-count="2">
<span style="background: #FFFFFF; border: 1px solid rgba(17,24,39,0.10); border-radius: 999px; padding: 7px 12px; font-size: 14px; font-weight: 600">{{a}}</span>
</sc-for>
</div>
<div style="display: flex; gap: 8px">
<input class="campo" value="{{nuevo}}" onChange="{{escribir}}" placeholder="Otro nombre, p. ej. «cemento sol»" aria-label="Nuevo nombre comercial" style="flex: 1">
<button onClick="{{agregar}}" disabled="{{noSePuede}}" aria-label="Agregar nombre" style="width: 52px; height: 52px; flex: none; border: 0; border-radius: 16px; background: {{botonFondo}}; display: flex; align-items: center; justify-content: center; cursor: pointer"><svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#FFFFFF" stroke-width="2.6" stroke-linecap="round"><path d="M12 5v14M5 12h14"></path></svg></button>
</div>
<sc-if value="{{hayAviso}}" hint-placeholder-val="{{ false }}">
<div role="alert" style="background: #FEE2E2; color: #991B1B; border-radius: 14px; padding: 10px 12px; font-size: 13px; font-weight: 600; line-height: 1.4">{{aviso}}</div>
</sc-if>
</div>

<a href="Stock.dc.html" style="''' + BOTON + '''">Ver stock en esta obra</a>
</div>
'''
    script = '''
const CAT = ''' + CAT_JS + ''';
''' + NORM + '''
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { alias: ['fierro de media', 'varilla de media'], nuevo: '' }; }
  renderVals() {
    const n = norm(this.state.nuevo);
    const otro = n ? CAT.find((c) => c.id !== 1147 && c.alias.some((a) => norm(a) === n)) : null;
    const propio = n && this.state.alias.some((a) => norm(a) === n);
    const aviso = otro ? '«' + this.state.nuevo.trim() + '» ya es nombre de ' + otro.nombre + ' (' + otro.id + '). Un nombre no puede apuntar a dos insumos.'
                : propio ? 'Ese nombre ya está registrado para este insumo.' : '';
    const noSePuede = !n || !!aviso;
    return {
      alias: this.state.alias,
      nuevo: this.state.nuevo,
      escribir: (e) => this.setState({ nuevo: e.target.value }),
      agregar: () => { if (!noSePuede) this.setState({ alias: [...this.state.alias, this.state.nuevo.trim()], nuevo: '' }); },
      noSePuede,
      botonFondo: noSePuede ? '#9CA3AF' : '#111827',
      hayAviso: !!aviso,
      aviso
    };
  }
}
'''
    return s + pie(script, h)


# ───────────────────────────── 3.3 Registrar insumo nuevo
def nuevo():
    h = 1120
    dib = '''<g fill="none" stroke="#475569" stroke-width="1.3" opacity="0.5" stroke-linejoin="round">
<path d="M262 120h96l12 12v120h-108z"></path><path d="M358 120v12h12"></path>
<path d="M276 150h70M276 166h52"></path>
<path d="M278 196v34M284 196v34M288 196v34M296 196v34M300 196v34M306 196v34M314 196v34M318 196v34M326 196v34M332 196v34M338 196v34" stroke-width="1.6"></path></g>
<g fill="none" stroke="#475569" stroke-width="1.3" opacity="0.45"><path d="M40 900c0-20 20-28 60-28s60 8 60 28v110c0 14-20 20-60 20s-60-6-60-20z"></path><path d="M40 930h120M40 990h120" stroke-dasharray="4 4"></path></g>
<text x="64" y="964" font-family="Inter, sans-serif" font-size="11" fill="#475569" opacity="0.7">42.5 KG</text>
<text x="274" y="246" font-family="Inter, sans-serif" font-size="9" fill="#475569" opacity="0.7">CÓDIGO ÚNICO</text>'''
    s = cabeza("Registrar insumo") + raiz(h) + fondo("cn", h, ("#EEF1F7", "#F3F1EE", "#F6EADF"), [(330, 180, 200, "n"), (60, 620, 210, "a"), (320, 1040, 170, "t")], dib)
    s += encabezado("CatBuscar.dc.html", "Insumo nuevo", "Solo si no existe en el catálogo")
    tipos = "".join(
        f'<button onClick="{{{{t{i}}}}}" aria-pressed="{{{{t{i}on}}}}" style="min-height: 44px; padding: 0 16px; border-radius: 22px; border: 0; background: {{{{t{i}bg}}}}; color: {{{{t{i}fg}}}}; font: 700 14px Inter, sans-serif; cursor: pointer">{t}</button>'
        for i, t in enumerate(["Material", "Equipo"]))
    unidades = "".join(
        f'<button onClick="{{{{u{i}}}}}" aria-pressed="{{{{u{i}on}}}}" style="min-height: 40px; padding: 0 14px; border-radius: 20px; border: 0; background: {{{{u{i}bg}}}}; color: {{{{u{i}fg}}}}; font: 600 14px Inter, sans-serif; cursor: pointer">{u}</button>'
        for i, u in enumerate(["bolsas", "unidades", "kilogramos", "metro cúbico", "rollo"]))
    s += '''<div style="position: relative; padding: 8px 16px 24px; display: flex; flex-direction: column; gap: 14px">
<div class="vidrio" style="border-radius: 26px; padding: 18px; display: flex; flex-direction: column; gap: 14px">
<div style="''' + ETIQUETA + '''">Tipo<div style="display: flex; gap: 8px">''' + tipos + '''</div></div>
<label style="''' + ETIQUETA + '''">Material *<input class="campo" value="{{material}}" onChange="{{eMaterial}}" placeholder="p. ej. Fierro corrugado"></label>
<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px">
<label style="''' + ETIQUETA + '''">Medida<input class="campo" value="{{medida}}" onChange="{{eMedida}}" placeholder="1/2&quot; x 9m"></label>
<label style="''' + ETIQUETA + '''">Marca<input class="campo" value="{{marca}}" onChange="{{eMarca}}" placeholder="Opcional"></label>
</div>
<label style="''' + ETIQUETA + '''">Norma<input class="campo" value="{{norma}}" onChange="{{eNorma}}" placeholder="Si el tipo la exige"></label>
<div style="''' + ETIQUETA + '''">Unidad *<div style="display: flex; flex-wrap: wrap; gap: 8px">''' + unidades + '''</div></div>
</div>

<sc-if value="{{hayParecidos}}" hint-placeholder-val="{{ true }}">
<div class="vidrio-fuerte" style="border-radius: 24px; padding: 16px; display: flex; flex-direction: column; gap: 10px; border: 1px solid #FDBA74">
<span style="display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 800; color: #9A3412">
<svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#C2410C" stroke-width="2.2" stroke-linecap="round"><circle cx="12" cy="12" r="9"></circle><path d="M12 7v6M12 16.5v.5"></path></svg>
¿Es alguno de estos? Ya están en el catálogo</span>
<sc-for list="{{parecidos}}" as="p" hint-placeholder-count="2">
<a href="CatFicha.dc.html" style="background: #FFFFFF; border-radius: 16px; padding: 10px 12px; display: flex; align-items: center; gap: 10px; text-decoration: none; color: #111827">
<span style="font-size: 12px; font-weight: 800; color: #9A3412; flex: none">{{p.id}}</span>
<span style="flex: 1; min-width: 0; font-size: 13px; font-weight: 600; line-height: 1.35">{{p.nombre}}</span>
<span style="font-size: 13px; font-weight: 700; color: #C2410C; flex: none">Usar</span>
</a>
</sc-for>
</div>
</sc-if>

<sc-if value="{{duplicado}}" hint-placeholder-val="{{ false }}">
<div role="alert" style="background: #FEE2E2; color: #991B1B; border-radius: 16px; padding: 12px 14px; font-size: 14px; font-weight: 600; line-height: 1.45">Ya existe un insumo con el mismo material, medida y marca. Usa ese: un insumo no se registra dos veces.</div>
</sc-if>

<div class="vidrio" style="border-radius: 20px; padding: 12px 14px; display: flex; align-items: center; gap: 10px; font-size: 13px; color: #374151; line-height: 1.4">
<svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#C2410C" stroke-width="2" stroke-linecap="round" style="flex: none"><path d="M4 7V5h2M18 5h2v2M20 17v2h-2M6 19H4v-2M8 8v8M11 8v8M14 8v8M17 8v8"></path></svg>
El código lo genera el sistema al guardar: único e irrepetible, el mismo en todas las obras.</div>

<button disabled="{{bloqueado}}" style="{{estiloBoton}}">Registrar insumo</button>
</div>
'''
    script = '''
const CAT = ''' + CAT_JS + ''';
''' + NORM + '''
const TIPOS = ['Material', 'Equipo'];
const UNIDADES = ['bolsas', 'unidades', 'kilogramos', 'metro cúbico', 'rollo'];
const BOTON = "''' + BOTON + '''";
const BOTON_OFF = "''' + BOTON_OFF + '''";
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { tipo: 0, material: 'Fierro corrugado', medida: '1/2', marca: 'Arequipa', norma: '', unidad: 1 }; }
  renderVals() {
    const st = this.state;
    const palabras = norm(st.material).split(' ').filter((w) => w.length > 2);
    const parecidos = palabras.length === 0 ? [] : CAT.filter((c) => palabras.every((w) => norm(c.nombre).includes(w))
      && (!norm(st.medida) || norm(c.nombre).includes(norm(st.medida))));
    const duplicado = parecidos.some((c) => norm(st.medida) && norm(st.marca) && norm(c.marca).includes(norm(st.marca)));
    const bloqueado = !norm(st.material) || duplicado;
    const v = {
      material: st.material, medida: st.medida, marca: st.marca, norma: st.norma,
      eMaterial: (e) => this.setState({ material: e.target.value }),
      eMedida: (e) => this.setState({ medida: e.target.value }),
      eMarca: (e) => this.setState({ marca: e.target.value }),
      eNorma: (e) => this.setState({ norma: e.target.value }),
      parecidos: parecidos.slice(0, 3), hayParecidos: parecidos.length > 0, duplicado,
      bloqueado, estiloBoton: bloqueado ? BOTON_OFF : BOTON
    };
    TIPOS.forEach((_, i) => { const on = st.tipo === i; Object.assign(v, { ['t' + i]: () => this.setState({ tipo: i }), ['t' + i + 'on']: on, ['t' + i + 'bg']: on ? '#111827' : 'rgba(255,255,255,0.8)', ['t' + i + 'fg']: on ? '#FFFFFF' : '#111827' }); });
    UNIDADES.forEach((_, i) => { const on = st.unidad === i; Object.assign(v, { ['u' + i]: () => this.setState({ unidad: i }), ['u' + i + 'on']: on, ['u' + i + 'bg']: on ? '#C2410C' : 'rgba(255,255,255,0.8)', ['u' + i + 'fg']: on ? '#FFFFFF' : '#111827' }); });
    return v;
  }
}
'''
    return s + pie(script, h)


if __name__ == "__main__":
    for nombre, f in (("CatBuscar", buscar), ("CatFicha", ficha), ("CatNuevo", nuevo)):
        open(f"{R}/project/{nombre}.dc.html", "w", encoding="utf-8").write(f())
        print(nombre)
