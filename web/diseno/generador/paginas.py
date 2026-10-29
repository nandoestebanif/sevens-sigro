from web import *

G = '#475569'


def guardar(nombre, html):
    os.makedirs(f"{ROOT}/project", exist_ok=True)
    open(f"{ROOT}/project/{nombre}.dc.html", "w", encoding="utf-8").write(html)


# ═════════════════════════ W1 · Entrar
def entrar():
    h = 900
    dib = f'''<g fill="none" stroke="{G}" stroke-width="1" opacity="0.4" stroke-dasharray="8 6"><path d="M80 120v720M440 120v720M800 120v720M1160 120v720M40 200h1360M40 520h1360M40 820h1360"></path></g>
<g fill="none" stroke="{G}" stroke-width="1.3" opacity="0.55"><circle cx="80" cy="104" r="14"></circle><circle cx="440" cy="104" r="14"></circle><circle cx="800" cy="104" r="14"></circle><circle cx="1160" cy="104" r="14"></circle></g>
<g fill="{G}" opacity="0.6" font-family="Inter, sans-serif" font-size="12" font-weight="700" text-anchor="middle"><text x="80" y="108">A</text><text x="440" y="108">B</text><text x="800" y="108">C</text><text x="1160" y="108">D</text></g>
<g fill="none" stroke="{G}" stroke-width="4" opacity="0.4"><path d="M80 200h300M500 200h660v320M1160 600v220H860M760 820H80V600M80 520V200M800 200v200M800 480v340M80 520h300M520 520h640"></path></g>
<g fill="none" stroke="#C2410C" stroke-width="1.2" opacity="0.55"><path d="M80 860h1080M80 852v16M1160 852v16"></path></g>
<text x="590" y="884" font-family="Inter, sans-serif" font-size="12" fill="#C2410C" opacity="0.75">32.40 m</text>'''
    s = cabeza("Entrar · Oficina de obra") + raiz(h) + fondo("we", ("#EEF1F6", "#F2F0EE", "#F5EAE1"), [(1150, 640, 380, "n"), (220, 820, 340, "a"), (700, 120, 260, "t")], dib)
    s += f'''<div style="position: relative; max-width: 1240px; min-height: 900px; margin: 0 auto; box-sizing: border-box; padding: 40px 24px; display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 28px">
<section style="flex: 1 1 520px; max-width: 700px; min-height: 640px; box-sizing: border-box; border-radius: 40px; background: #0F172A; color: #FFFFFF; position: relative; overflow: hidden; padding: 48px; display: flex; flex-direction: column; justify-content: space-between; gap: 28px; box-shadow: 0 30px 60px rgba(15,23,42,0.30)">
<svg aria-hidden="true" width="100%" height="100%" viewBox="0 0 700 640" preserveAspectRatio="xMidYMid slice" style="position: absolute; left: 0; top: 0">
<defs><radialGradient id="we-g" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FB923C" stop-opacity="0.45"></stop><stop offset="1" stop-color="#FB923C" stop-opacity="0"></stop></radialGradient>
<pattern id="we-gc" width="28" height="28" patternUnits="userSpaceOnUse"><path d="M28 0H0V28" fill="none" stroke="#334155" stroke-width="0.6"></path></pattern></defs>
<rect width="700" height="640" fill="url(#we-gc)" opacity="0.6"></rect>
<circle cx="600" cy="560" r="300" fill="url(#we-g)"></circle>
<g fill="none" stroke="#64748B" stroke-width="2" opacity="0.7"><path d="M360 640V300h160v340M360 340h160M360 380h160M360 420h160M360 460h160M360 500h160M360 540h160M360 580h160M413 300v340M466 300v340"></path>
<path d="M540 640V220h120v420M540 260h120M540 300h120M540 340h120M540 380h120M540 420h120M540 460h120M540 500h120M540 540h120M540 580h120M600 220v420"></path>
<path d="M630 220V90M630 90H470M630 90h50M470 90v30M455 120h30v14h-30zM630 110l-20 20M630 140l-20 20M630 170l-20 20"></path></g>
</svg>
<img src="{LOGO_BLANCO}" alt="SEVEN'S Ingenieros SAC" style="position: relative; height: 120px; width: auto; align-self: flex-start">
<div style="position: relative; display: flex; flex-direction: column; gap: 18px; max-width: 420px">
<span style="display: flex; align-items: center; gap: 12px"><span style="width: 28px; height: 1.5px; background: #FDBA74"></span>
<span style="font-family: Sora, Inter, sans-serif; font-size: 18px; font-weight: 600; letter-spacing: 0.26em; text-transform: uppercase">Oficina de obra</span></span>
<h1 style="margin: 0; font-size: 40px; font-weight: 800; line-height: 1.12; letter-spacing: -0.03em">Lo que pide el S10, lo que se compra y lo que se gasta. En un solo lugar.</h1>
<ul style="margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 12px; font-size: 16px; color: #E2E8F0">
<li style="display: flex; gap: 10px; align-items: flex-start"><span style="width: 24px; height: 24px; border-radius: 12px; background: rgba(251,146,60,0.2); display: flex; align-items: center; justify-content: center; flex: none">{CHK}</span>El presupuesto del S10 se importa tal cual: 284 insumos, al céntimo.</li>
<li style="display: flex; gap: 10px; align-items: flex-start"><span style="width: 24px; height: 24px; border-radius: 12px; background: rgba(251,146,60,0.2); display: flex; align-items: center; justify-content: center; flex: none">{CHK}</span>El almacén registra ingresos y salidas desde la obra, aunque no haya señal.</li>
<li style="display: flex; gap: 10px; align-items: flex-start"><span style="width: 24px; height: 24px; border-radius: 12px; background: rgba(251,146,60,0.2); display: flex; align-items: center; justify-content: center; flex: none">{CHK}</span>El gasto en recursos se compara con lo planificado, insumo por insumo.</li>
</ul></div>
<span style="position: relative; font-size: 13px; color: #94A3B8">SEVENS INGENIEROS SAC · SIGRO</span>
</section>
<section class="vidrio-fuerte" style="flex: 1 1 360px; max-width: 440px; box-sizing: border-box; border-radius: 34px; padding: 36px 32px; display: flex; flex-direction: column; gap: 16px; box-shadow: 0 24px 50px rgba(15,23,42,0.16)">
<div style="display: flex; flex-direction: column; gap: 6px"><h2 style="margin: 0; font-size: 30px; font-weight: 800; letter-spacing: -0.03em">Ingresar</h2>
<p style="margin: 0; font-size: 15px; color: #4B5563">Con tu usuario de SIGRO. Verás solo las obras donde estás asignado.</p></div>
<label style="display: flex; flex-direction: column; gap: 6px; font-size: 14px; font-weight: 600; color: #374151">Usuario
<span style="position: relative; display: flex; align-items: center"><input class="campo" placeholder="Tu usuario" autocomplete="username" style="padding-right: 46px">
<svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke-linecap="round" stroke-linejoin="round" style="position: absolute; right: 14px"><path d="M6.5 8.6a5.5 5.5 0 0 1 11 0z" fill="#F59E0B" stroke="#B45309" stroke-width="1.6"></path><path d="M4.6 8.6h14.8M12 3.2v2.4" stroke="#B45309" stroke-width="1.8"></path><path d="M8.2 10.6a3.8 3.8 0 0 0 7.6 0M4.5 21c1-3.6 3.8-5.6 7.5-5.6s6.5 2 7.5 5.6" stroke="#64748B" stroke-width="2"></path></svg></span></label>
<label style="display: flex; flex-direction: column; gap: 6px; font-size: 14px; font-weight: 600; color: #374151">Clave
<span style="position: relative; display: flex; align-items: center"><input class="campo" type="{{{{tipoClave}}}}" placeholder="Tu clave" autocomplete="current-password" style="padding-right: 52px">
<button onClick="{{{{alternar}}}}" aria-label="{{{{textoOjo}}}}" style="position: absolute; right: 4px; width: 44px; height: 44px; border: 0; background: transparent; border-radius: 22px; display: flex; align-items: center; justify-content: center; cursor: pointer">
<svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#4B5563" stroke-width="2" stroke-linecap="round"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"></path><circle cx="12" cy="12" r="3"></circle></svg></button></span></label>
<label style="display: flex; align-items: center; gap: 10px; font-size: 14px; color: #374151; min-height: 44px"><input type="checkbox" style="width: 20px; height: 20px; accent-color: #C2410C">Recordar mi usuario en este equipo</label>
<a href="Panel.dc.html" style="{BOTON}; min-height: 56px; border-radius: 28px; font-size: 16px">Entrar</a>
<p style="margin: 0; text-align: center; font-size: 14px; color: #4B5563">¿No tienes usuario? <span style="color: #C2410C; font-weight: 700">Pídelo al administrador de la obra</span></p>
<span style="align-self: center; font-size: 13px; color: #4B5563; display: flex; align-items: center; gap: 6px; min-height: 32px">
<svg aria-hidden="true" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#4B5563" stroke-width="2"><rect x="3" y="4" width="18" height="6" rx="1.5"></rect><rect x="3" y="14" width="18" height="6" rx="1.5"></rect></svg>Servidor: pruebas.sigro</span>
</section>
</div>
'''
    script = '''class Component extends DCLogic {
  constructor(p) { super(p); this.state = { ver: false }; }
  renderVals() { const v = this.state.ver;
    return { tipoClave: v ? 'text' : 'password', textoOjo: v ? 'Ocultar clave' : 'Mostrar clave', alternar: () => this.setState({ ver: !v }) }; }
}'''
    guardar("Main", s + pie(script, h))


CHK = '<svg aria-hidden="true" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#FDBA74" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"></path></svg>'


# ═════════════════════════ W2 · Panel (gasto en recursos)
def panel():
    h = 1180
    dib = f'''<g fill="none" stroke="{G}" stroke-width="1.6" opacity="0.4"><path d="M1240 900V260M1240 260H1010M1240 260h120M1010 260v50M990 310h40v20h-40zM1240 300l-30 30M1240 360l-30 30M1240 420l-30 30M1240 480l-30 30M1210 300v600"></path>
<path d="M1300 900V560h110v340M1300 610h110M1300 660h110M1300 710h110M1300 760h110M1300 810h110M1355 560v340"></path></g>
<g fill="none" stroke="#C2410C" stroke-width="1.2" opacity="0.5"><path d="M1440 560v340M1432 560h16M1432 900h16"></path></g>'''
    fam = [("Materiales", 768310.00, 186900, "#C2410C"), ("Mano de obra", 542997.71, 102400, "#1D4ED8"), ("Equipos", 33310.39, 9150, "#0F766E")]
    barras = ""
    for n, plan, gas, col in fam:
        pct = gas / plan * 100
        barras += f'''<div style="display: flex; flex-direction: column; gap: 8px">
<div style="display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; font-size: 14px"><span style="font-weight: 700">{n}</span>
<span style="color: #374151"><b style="color: #111827">S/ {soles(gas)}</b> de S/ {soles(plan)} · {pct:.1f} %</span></div>
<div style="height: 14px; border-radius: 7px; background: rgba(17,24,39,0.08); position: relative; overflow: hidden">
<span style="position: absolute; left: 0; top: 0; bottom: 0; width: {pct:.1f}%; background: {col}; border-radius: 7px"></span>
<span title="Avance del plazo" style="position: absolute; left: 20.4%; top: -3px; bottom: -3px; width: 2px; background: #111827"></span></div></div>
'''
    filas = [("ACERO CORRUGADO FY= 4200 Kg/cm2", "kg", 36357.42, 9820, "grave"), ("CEMENTO PORTLAND TIPO I (42.50 KG.)", "bls", 3492.36, 845, "aviso"),
             ("ARENA GRUESA", "m3", 186.18, 44, "aviso"), ("ALAMBRE NEGRO RECOCIDO # 16", "kg", 1385.27, 260, "bien"),
             ("LADRILLO K.K. 9x13x24 (18 Huecos)", "und", 21684.93, 3900, "bien")]
    js_filas = []
    for n, u, plan, cons, tono in filas:
        p = cons / plan * 100
        d = p - 20.4
        js_filas.append(dict(n=n, u=u, plan=soles(plan, 0), cons=soles(cons), pct=f"{p:.1f} %", desv=f"{d:+.1f} pts",
                             bg=TONO[tono][0], fg=TONO[tono][1], desviado=d > 1))
    s = cabeza("Gasto en recursos") + raiz(h) + fondo("wp", ("#EDF1F6", "#F2F1EE", "#F5EAE0"), [(1300, 160, 360, "n"), (200, 900, 380, "a"), (760, 1100, 300, "t")], dib)
    cont = encabezado("Gasto en recursos", "Multifamiliar Flores · presupuesto 2701006 del S10 · corte de la semana [n]",
                      f'<a href="Compras.dc.html" style="{BOTON2}">Exportar a Excel</a><a href="Compras.dc.html" style="{BOTON}">+ Registrar compra</a>')
    cont += f'<div style="display: flex; flex-wrap: wrap; gap: 8px; align-items: center">{EJEMPLO}{chip("Avance del plazo: 20.4 %", "neutro")}</div>\n'
    cont += '<div style="display: flex; flex-wrap: wrap; gap: 14px">\n'
    cont += kpi("Planificado en recursos", "1 344 618", "Materiales, mano de obra y equipos del S10", prefijo="S/")
    cont += kpi("Gastado a la fecha", "298 450", "22.2 % de lo planificado", prefijo="S/")
    cont += kpi("Desviación del gasto", "+1.8 pts", "Gasto 22.2 % frente a 20.4 % del plazo", color="#B45309")
    cont += kpi("Bajo el punto de reposición", "4 insumos", "Revisar en Reposición", color="#B91C1C")
    cont += '</div>\n<div style="display: flex; flex-wrap: wrap; gap: 18px; align-items: flex-start">\n'
    cont += f'''<div style="flex: 999 1 520px; min-width: 0; display: flex; flex-direction: column; gap: 18px">
<section class="vidrio" style="{TARJETA}; display: flex; flex-direction: column; gap: 18px"><div style="display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; align-items: baseline"><h2 style="{H2}">Por familia de recurso</h2><span style="font-size: 13px; color: #4B5563">La línea negra marca el avance del plazo</span></div>
{barras}<p style="margin: 0; font-size: 13px; color: #4B5563">Subcontratos (S/ 134 720) quedan fuera: SIGRO informa el gasto en recursos, no el costo total de la obra.</p></section>
<section class="vidrio" style="{TARJETA}; display: flex; flex-direction: column; gap: 12px">
<div style="display: flex; flex-wrap: wrap; justify-content: space-between; gap: 10px; align-items: center"><h2 style="{H2}">Insumos y su desviación</h2>
<div role="group" aria-label="Filtro" style="display: flex; gap: 4px; background: rgba(255,255,255,0.7); border-radius: 22px; padding: 3px">
<sc-for list="{{{{filtros}}}}" as="f" hint-placeholder-count="2"><button onClick="{{{{f.elegir}}}}" aria-pressed="{{{{f.on}}}}" style="min-height: 38px; padding: 0 14px; border: 0; border-radius: 19px; background: {{{{f.bg}}}}; color: {{{{f.fg}}}}; font: 700 13px Inter, sans-serif; cursor: pointer">{{{{f.t}}}}</button></sc-for></div></div>
<div style="overflow-x: auto"><table class="tabla"><thead><tr><th>Insumo</th><th>Und.</th><th class="n">Planificado</th><th class="n">Consumido</th><th class="n">% consumido</th><th>Desviación</th></tr></thead><tbody>
<sc-for list="{{{{filas}}}}" as="r" hint-placeholder-count="5"><tr><td style="font-weight: 600">{{{{r.n}}}}</td><td>{{{{r.u}}}}</td><td class="n">{{{{r.plan}}}}</td><td class="n">{{{{r.cons}}}}</td><td class="n">{{{{r.pct}}}}</td>
<td><span style="{CHIP}; background: {{{{r.bg}}}}; color: {{{{r.fg}}}}">{{{{r.desv}}}}</span></td></tr></sc-for></tbody></table></div>
<p style="margin: 0; font-size: 13px; color: #4B5563">Lo planificado incluye desperdicio: pasarse avisa, no bloquea.</p></section></div>
<aside style="flex: 1 1 280px; display: flex; flex-direction: column; gap: 18px">
<section class="vidrio" style="{TARJETA}; display: flex; flex-direction: column; gap: 12px"><h2 style="{H2}">Avisos</h2>
{aviso_fila("grave", "Cemento tipo I bajo el punto de reposición", "Quedan 92 bls · reponer 250", "Reposicion.dc.html")}
{aviso_fila("aviso", "Acero: consumo 6.6 pts sobre el plazo", "Revisar el uso en losas", "Movimientos.dc.html")}
{aviso_fila("info", "Recepción parcial pendiente", "F001-[n] · faltan 40 varillas", "Compras.dc.html")}
</section>
<section class="vidrio" style="{TARJETA}; display: flex; flex-direction: column; gap: 10px"><h2 style="{H2}">Plazo de la obra</h2>
<div style="height: 12px; border-radius: 6px; background: rgba(17,24,39,0.08); overflow: hidden"><span style="display: block; width: 20.4%; height: 12px; background: #111827; border-radius: 6px"></span></div>
<span style="font-size: 14px; color: #374151">Semana [n] de [total] · fin previsto [fecha de fin]</span></section>
</aside></div>
'''
    s += marco("tablero", cont)
    script = '''const FILAS = ''' + json.dumps(js_filas, ensure_ascii=False) + ''';
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { solo: false }; }
  renderVals() { const s = this.state.solo;
    return { filas: s ? FILAS.filter((f) => f.desviado) : FILAS,
      filtros: [['Todos', false], ['Solo desviados', true]].map(([t, v]) => ({ t, on: s === v, elegir: () => this.setState({ solo: v }),
        bg: s === v ? '#111827' : 'transparent', fg: s === v ? '#FFFFFF' : '#111827' })) }; }
}'''
    guardar("Panel", s + pie(script, h))


def aviso_fila(tono, t, d, href):
    bg, fg = TONO[tono]
    return (f'<a href="{href}" style="border-radius: 16px; background: rgba(255,255,255,0.8); padding: 12px 14px; display: flex; gap: 10px; align-items: flex-start; text-decoration: none; color: #111827">'
            f'<span style="width: 10px; height: 10px; border-radius: 5px; background: {fg}; margin-top: 5px; flex: none"></span>'
            f'<span style="display: flex; flex-direction: column; gap: 2px"><span style="font-size: 14px; font-weight: 700">{t}</span><span style="font-size: 13px; color: #4B5563">{d}</span></span></a>')


# ═════════════════════════ W3 · Importar del S10
S10 = {
 "material": [("0203020004", "ACERO CORRUGADO FY= 4200 Kg/cm2", "kg", 36357.4207, 4.54), ("0221000000", "CEMENTO PORTLAND TIPO I (42.50 KG.)", "bls", 3492.36, 36.0),
              ("0217080008", "LADRILLO K.K. 9x13x24 (18 Huecos)", "und", 21684.9322, 1.18), ("0205010033", "ARENA GRUESA", "m3", 186.1846, 80.0),
              ("0204000000", "ARENA FINA", "m3", 54.7941, 95.0), ("0202000007", "ALAMBRE NEGRO RECOCIDO # 16", "kg", 1385.265, 6.0)],
 "mano_obra": [("0147010002", "OPERARIO", "hh", 10108.089, 29.99), ("0147010003", "OFICIAL", "hh", 4702.0131, 23.6), ("0147010004", "PEON", "hh", 5886.7734, 21.36),
               ("0147000022", "OPERARIO DE EQUIPO LIVIANO", "hh", 1.8448, 30.2)],
 "equipo": [("0348010011", "MEZCLADORA DE CONCRETO DE 9 -11P3", "hm", 161.126, 15.0), ("0348090011", "ANDAMIO METAL TABLAS-ALQUILER", "HE", 630.9573, 3.75),
            ("0348960005", "CIZALLA P/CORTE DE FIERRO", "hm", 314.8985, 5.0), ("0384010002", "PISON DE MANO", "und", 665.2, 20.0)],
 "subcontrato": [("0401010020", "VENTANA METALICA DE 1.30X0.30M SEGUN DISEÑO", "und", 95.71, 250.0), ("0401030192", "BAÑOS QUIMICOS INCLUYE ACCESORIOS", "und", 8.0, 500.0),
                 ("0401030201", "PUERTA DE MADERA SOLIDA P5 DE (0.95x2.10M)", "und", 6.0, 2300.0)],
}


def importar():
    h = 1120
    dib = f'''<g fill="none" stroke="{G}" stroke-width="1.3" opacity="0.45"><path d="M1180 140h200v260h-200z"></path><path d="M1180 180h200M1180 220h200M1180 260h200M1180 300h200M1180 340h200M1240 140v260M1320 140v260"></path>
<path d="M1150 170h200v260h-200" stroke-dasharray="4 4"></path></g><text x="1180" y="426" font-family="Inter, sans-serif" font-size="12" fill="{G}" opacity="0.7">RELACIÓN DE INSUMOS</text>'''
    tipos = [("material", "Materiales", 237, 768310.00), ("mano_obra", "Mano de obra", 6, 542997.71), ("equipo", "Equipos", 18, 33310.39), ("subcontrato", "Subcontratos", 23, 134720.22)]
    js = {k: [dict(c=c, d=d, u=u, cant=f"{q:,.2f}".replace(",", " "), p=f"{p:,.2f}", parc=f"{q*p:,.2f}".replace(",", " ")) for c, d, u, q, p in v] for k, v in S10.items()}
    s = cabeza("Importar del S10") + raiz(h) + fondo("wi", ("#EEF1F7", "#F3F1EE", "#F6EBE0"), [(1250, 260, 340, "a"), (180, 820, 360, "n"), (900, 1080, 300, "t")], dib)
    pasos = "".join(f'<li style="display: flex; align-items: center; gap: 10px; font-size: 14px; font-weight: 700; color: {c}"><span style="width: 30px; height: 30px; border-radius: 15px; background: {b}; color: {tc}; display: flex; align-items: center; justify-content: center; font-size: 13px">{n}</span>{t}</li>'
                    for n, t, c, b, tc in [("1", "Subir el PDF", "#111827", "#166534", "#FFFFFF"), ("2", "Revisar lo leído", "#111827", "#C2410C", "#FFFFFF"), ("3", "Confirmar", "#4B5563", "rgba(17,24,39,0.10)", "#374151")])
    cont = encabezado("Importar del S10", "Relación de insumos del presupuesto: la base de la obra, sin escribir nada a mano",
                      f'<a href="Catalogo.dc.html" style="{BOTON2}">Ver catálogo</a>')
    cont += f'<ol style="margin: 0; padding: 0 4px; list-style: none; display: flex; flex-wrap: wrap; gap: 22px">{pasos}</ol>\n'
    cont += f'''<div style="display: flex; flex-wrap: wrap; gap: 18px; align-items: stretch">
<section class="vidrio" style="{TARJETA}; flex: 1 1 340px; display: flex; flex-direction: column; gap: 14px">
<div style="border: 2px dashed #C2410C; border-radius: 22px; background: rgba(255,237,213,0.5); padding: 22px; display: flex; align-items: center; gap: 14px">
<span style="width: 52px; height: 60px; border-radius: 10px; background: #FFFFFF; border: 1px solid rgba(17,24,39,0.12); display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 800; color: #B91C1C; flex: none">PDF</span>
<span style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px"><span style="font-size: 15px; font-weight: 800">5.3. Relación de insumos.pdf</span><span style="font-size: 13px; color: #4B5563">Presupuesto 2701006 · subpresupuesto 002 «Multifamiliar Flores»</span></span>
<button style="{BOTON2}">Cambiar</button></div>
<ul style="margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 8px; font-size: 14px">
<li style="display: flex; gap: 8px; align-items: center">{ok_ico()}284 de 284 filas leídas, 0 fallidas</li>
<li style="display: flex; gap: 8px; align-items: center">{ok_ico()}El total cuadra al céntimo con el reporte</li>
<li style="display: flex; gap: 8px; align-items: center">{ok_ico()}Primera importación: los 284 se crean · al repetirla, 0 nuevos</li></ul></section>
<section class="vidrio" style="{TARJETA}; flex: 1 1 340px; display: flex; flex-direction: column; gap: 12px">
<span style="font-size: 13px; font-weight: 600; color: #4B5563">Total del reporte</span>
<span style="display: flex; align-items: baseline; gap: 6px"><span style="font-size: 16px; font-weight: 700; color: #4B5563">S/</span><span style="font-size: 38px; font-weight: 800; letter-spacing: -0.03em">1 479 338.33</span></span>
<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px">
''' + "".join(f'<div style="background: rgba(255,255,255,0.8); border-radius: 16px; padding: 10px 12px; display: flex; flex-direction: column"><span style="font-size: 13px; color: #4B5563">{n}</span><span style="font-size: 18px; font-weight: 800">{c}</span><span style="font-size: 12.5px; color: #374151">S/ {soles(t, 2)}</span></div>' for _, n, c, t in tipos) + '''
</div></section></div>
'''
    cont += f'''<section class="vidrio" style="{TARJETA}; display: flex; flex-direction: column; gap: 12px">
<div style="display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 10px"><h2 style="{H2}">Lo que se va a importar</h2>
<div role="tablist" style="display: flex; flex-wrap: wrap; gap: 6px"><sc-for list="{{{{pestanas}}}}" as="t" hint-placeholder-count="4"><button role="tab" aria-selected="{{{{t.on}}}}" onClick="{{{{t.elegir}}}}" style="min-height: 40px; padding: 0 14px; border: 0; border-radius: 20px; background: {{{{t.bg}}}}; color: {{{{t.fg}}}}; font: 700 13px Inter, sans-serif; cursor: pointer">{{{{t.texto}}}}</button></sc-for></div></div>
<div style="overflow-x: auto"><table class="tabla"><thead><tr><th>Código S10</th><th>Descripción</th><th>Und.</th><th class="n">Cantidad</th><th class="n">Precio S/</th><th class="n">Parcial S/</th><th>Acción</th></tr></thead><tbody>
<sc-for list="{{{{filas}}}}" as="r" hint-placeholder-count="5"><tr><td style="font-variant-numeric: tabular-nums; color: #4B5563">{{{{r.c}}}}</td><td style="font-weight: 600">{{{{r.d}}}}</td><td>{{{{r.u}}}}</td><td class="n">{{{{r.cant}}}}</td><td class="n">{{{{r.p}}}}</td><td class="n">{{{{r.parc}}}}</td><td>{chip("Crear", "bien")}</td></tr></sc-for></tbody></table></div>
<span style="font-size: 13px; color: #4B5563">{{{{nota}}}}</span></section>
<div style="display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px"><a href="Catalogo.dc.html" style="{BOTON2}">Cancelar</a><a href="Catalogo.dc.html" style="{BOTON}">Confirmar e importar 284 insumos</a></div>
'''
    s += marco("importar", cont)
    script = '''const DATOS = ''' + json.dumps(js, ensure_ascii=False) + ''';
const TIPOS = [['material', 'Materiales · 237'], ['mano_obra', 'Mano de obra · 6'], ['equipo', 'Equipos · 18'], ['subcontrato', 'Subcontratos · 23']];
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { t: 'material' }; }
  renderVals() { const t = this.state.t;
    return { filas: DATOS[t], nota: 'Vista previa: primeras filas de ' + TIPOS.find((x) => x[0] === t)[1].toLowerCase() + '. Los datos son los del reporte real del cliente.',
      pestanas: TIPOS.map(([k, n]) => ({ texto: n, on: t === k, elegir: () => this.setState({ t: k }), bg: t === k ? '#111827' : 'rgba(255,255,255,0.8)', fg: t === k ? '#FFFFFF' : '#111827' })) }; }
}'''
    guardar("Importar", s + pie(script, h))


def ok_ico():
    return '<svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#166534" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10" fill="#DCFCE7" stroke="none"></circle><path d="M7.5 12.5l3 3 6-6.5"></path></svg>'


# ═════════════════════════ W4 · Catálogo
CAT = [
 dict(c="1151", n="CEMENTO PORTLAND TIPO I - 42.5 KG", u="bolsas", m="Pacasmayo · Sol", s10="0221000000", f="1 bolsa = 1 bls", e="Emparejado", t="bien", p="29.23"),
 dict(c="1147", n='FIERRO CORRUGADO DE 1/2" x 9m - ASTM A615/A706 - GRADO 60', u="unidades", m="Arequipa", s10="0203020004 (kg)", f="1 varilla = [factor] kg", e="Falta el factor", t="aviso", p="41.27"),
 dict(c="1146", n='FIERRO CORRUGADO DE 3/8" x 9m - ASTM A615/A706 - GRADO 60', u="unidades", m="Arequipa", s10="0203020004 (kg)", f="1 varilla = [factor] kg", e="Falta el factor", t="aviso", p="22.17"),
 dict(c="2544", n="LADRILLO KING KONG 18 H - UND - 9X13X24 - INKA", u="unidades", m="—", s10="0217080008", f="1 = 1", e="Emparejado", t="bien", p="0.63"),
 dict(c="423", n="ARENA GRUESA", u="metro cúbico", m="—", s10="0205010033", f="1 = 1", e="Emparejado", t="bien", p="48.36"),
 dict(c="1432", n="ALAMBRE RECOCIDO N°16", u="kilogramos", m="Prodac", s10="0202000007", f="1 = 1", e="Emparejado", t="bien", p="5.39"),
 dict(c="1154", n="CEMENTO PORTLAND TIPO IP - 42.5 KG", u="bolsas", m="Sol", s10="—", f="—", e="Por emparejar", t="neutro", p="26.88"),
 dict(c="1288", n='CLAVO 1 1/2" ESTRIADO PARA CONCRETO', u="kilogramos", m="—", s10="—", f="—", e="Por emparejar", t="neutro", p="—"),
]


def catalogo():
    h = 1000
    dib = f'''<g fill="none" stroke="{G}" stroke-width="1.4" opacity="0.42" stroke-linejoin="round"><path d="M1150 600l200-100 50 25v320l-200 100-50-25z"></path><path d="M1150 600l50 25 200-100M1200 625v320"></path>
<path d="M1150 700l50 25 200-100M1150 800l50 25 200-100"></path><path d="M1230 660l40-20v28l-40 20zM1290 630l40-20v28l-40 20zM1240 760l50-25v26l-50 25z"></path></g>'''
    for r in CAT:
        r["bg"], r["fg"] = TONO[r["t"]]
    s = cabeza("Catálogo de insumos") + raiz(h) + fondo("wc", ("#EEF1F6", "#F2F1EE", "#F5EBE2"), [(1260, 200, 340, "n"), (200, 760, 360, "a"), (900, 960, 300, "t")], dib)
    cont = encabezado("Catálogo de insumos", "Un mismo insumo lleva el mismo código en todas las obras",
                      f'<a href="Importar.dc.html" style="{BOTON2}">Importar del S10</a><button style="{BOTON}">+ Registrar insumo</button>')
    cont += f'<div style="display: flex; flex-wrap: wrap; gap: 8px">{chip("2 243 del catálogo del cliente", "marca")}{chip("284 del S10 de esta obra", "info")}{chip("Emparejamiento S10 ↔ catálogo en curso", "aviso")}</div>\n'
    cont += f'''<div style="display: flex; flex-wrap: wrap; gap: 18px; align-items: flex-start">
<section class="vidrio" style="{TARJETA}; flex: 999 1 560px; min-width: 0; display: flex; flex-direction: column; gap: 12px">
<label class="vidrio-fuerte" style="height: 52px; border-radius: 26px; padding: 0 18px; display: flex; align-items: center; gap: 10px">
<svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#C2410C" stroke-width="2.4" stroke-linecap="round"><circle cx="11" cy="11" r="7"></circle><path d="M20 20l-4-4"></path></svg>
<input value="{{{{q}}}}" onChange="{{{{buscar}}}}" placeholder="Buscar por nombre, código del cliente o código del S10" aria-label="Buscar insumo" style="flex: 1; min-width: 0; border: 0; background: transparent; font: 15px Inter, sans-serif; color: #111827; outline: none"></label>
<div style="overflow-x: auto"><table class="tabla"><thead><tr><th>Código</th><th>Insumo</th><th>Unidad de compra</th><th>Código S10</th><th>Estado</th><th></th></tr></thead><tbody>
<sc-for list="{{{{filas}}}}" as="r" hint-placeholder-count="6"><tr style="background: {{{{r.fondo}}}}"><td style="font-weight: 800; color: #9A3412">{{{{r.c}}}}</td><td style="font-weight: 600; max-width: 320px">{{{{r.n}}}}</td><td>{{{{r.u}}}}</td><td style="font-variant-numeric: tabular-nums">{{{{r.s10}}}}</td>
<td><span style="{CHIP}; background: {{{{r.bg}}}}; color: {{{{r.fg}}}}">{{{{r.e}}}}</span></td><td><button onClick="{{{{r.ver}}}}" style="{BOTON2}; min-height: 38px; padding: 0 14px">Ver</button></td></tr></sc-for></tbody></table></div>
<span style="font-size: 13px; color: #4B5563">{{{{resumen}}}}</span></section>
<aside class="vidrio-fuerte" style="{TARJETA}; flex: 1 1 300px; display: flex; flex-direction: column; gap: 12px; box-shadow: 0 14px 34px rgba(15,23,42,0.12)">
<span style="font-size: 12px; font-weight: 700; letter-spacing: 0.06em; color: #6B7280">FICHA DEL INSUMO · {{{{sel.c}}}}</span>
<h2 style="{H2}; line-height: 1.3">{{{{sel.n}}}}</h2>
<span style="{CHIP}; align-self: flex-start; background: {{{{sel.bg}}}}; color: {{{{sel.fg}}}}">{{{{sel.e}}}}</span>
<dl style="margin: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 14px; font-size: 14px">
<div><dt style="font-size: 12px; color: #4B5563">Unidad de compra</dt><dd style="margin: 0; font-weight: 700">{{{{sel.u}}}}</dd></div>
<div><dt style="font-size: 12px; color: #4B5563">Marca</dt><dd style="margin: 0; font-weight: 700">{{{{sel.m}}}}</dd></div>
<div><dt style="font-size: 12px; color: #4B5563">Código del S10</dt><dd style="margin: 0; font-weight: 700">{{{{sel.s10}}}}</dd></div>
<div><dt style="font-size: 12px; color: #4B5563">Precio promedio del cliente</dt><dd style="margin: 0; font-weight: 700">S/ {{{{sel.p}}}}</dd></div></dl>
<div style="background: rgba(255,255,255,0.85); border-radius: 16px; padding: 12px 14px; display: flex; flex-direction: column; gap: 4px"><span style="font-size: 12px; color: #4B5563">Factor de conversión</span><span style="font-size: 17px; font-weight: 800">{{{{sel.f}}}}</span>
<span style="font-size: 12.5px; color: #4B5563">Compara lo requerido, comprado y consumido en la misma unidad. Cada cambio queda con su fecha y autor.</span></div>
<button style="{BOTON}">Declarar factor</button></aside></div>
'''
    s += marco("catalogo", cont)
    script = '''const CAT = ''' + json.dumps(CAT, ensure_ascii=False) + ''';
const norm = (x) => (x || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').toLowerCase().trim();
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { q: '', sel: '1147' }; }
  renderVals() { const q = norm(this.state.q);
    const f = CAT.filter((r) => !q || norm(r.n + ' ' + r.c + ' ' + r.s10).includes(q));
    return { q: this.state.q, buscar: (e) => this.setState({ q: e.target.value }),
      filas: f.map((r) => ({ ...r, fondo: r.c === this.state.sel ? 'rgba(255,237,213,0.55)' : 'transparent', ver: () => this.setState({ sel: r.c }) })),
      sel: CAT.find((r) => r.c === this.state.sel),
      resumen: f.length + ' de ' + CAT.length + ' insumos de muestra · códigos, nombres, unidades, marcas y precios reales del cliente' }; }
}'''
    guardar("Catalogo", s + pie(script, h))


# ═════════════════════════ W5 · Reposición
REPO = [  # insumo, und, requerido S10 (real), comprado (ej.), stock (ej.), punto (ej.), seg (ej.), dias (ej.)
 ("CEMENTO PORTLAND TIPO I (42.50 KG.)", "bls", 3492.36, 1100, 92, 180, 80, 1),
 ("ACERO CORRUGADO FY= 4200 Kg/cm2", "kg", 36357.42, 14200, 2350, 2000, 900, 2),
 ("LADRILLO K.K. 9x13x24 (18 Huecos)", "und", 21684.93, 6000, 1450, 1200, 500, 3),
 ("ARENA GRUESA", "m3", 186.18, 60, 9, 12, 5, 1),
 ("ALAMBRE NEGRO RECOCIDO # 16", "kg", 1385.27, 400, 140, 90, 40, 1),
 ("ARENA FINA", "m3", 54.79, 18, 6, 4, 2, 1),
]


def reposicion():
    h = 1000
    # «diente de sierra» del inventario: punto de reposición y llegada del pedido
    pts = []
    x, y = 960, 200
    for i in range(4):
        pts += [(x, y), (x + 100, y + 160)]
        x += 100; y = 200
    sierra = "M" + " L".join(f"{a} {b}" for a, b in pts)
    dib = f'''<g fill="none" stroke="{G}" stroke-width="1.6" opacity="0.5"><path d="{sierra}"></path><path d="M960 200v160M1060 200v160M1160 200v160M1260 200v160" stroke-dasharray="3 5" stroke-width="1"></path><path d="M940 380h420M940 380V170"></path></g>
<g fill="none" stroke="#C2410C" stroke-width="1.4" opacity="0.6" stroke-dasharray="8 5"><path d="M940 300h420"></path></g>
<text x="1270" y="292" font-family="Inter, sans-serif" font-size="12" fill="#C2410C" opacity="0.8">punto de reposición</text>'''
    filas = []
    for n, u, req, comp, st, pr, seg, dias in REPO:
        saldo = max(req - comp, 0)
        est, tono = ("Reponer ya", "grave") if st < pr else (("Cerca del punto", "aviso") if st < pr * 1.3 else ("Bien", "bien"))
        filas.append(dict(n=n, u=u, req=soles(req), comp=soles(comp), saldo=soles(saldo), st=soles(st), pr=soles(pr), seg=soles(seg), dias=str(dias),
                          est=est, bg=TONO[tono][0], fg=TONO[tono][1]))
    s = cabeza("Reposición") + raiz(h) + fondo("wr", ("#EDF1F7", "#F2F0EE", "#F5E9DF"), [(1200, 300, 340, "n"), (240, 780, 360, "t"), (820, 980, 300, "a")], dib)
    cont = encabezado("Reposición", "Qué falta comprar de lo planificado y qué está por acabarse en la obra",
                      f'<button style="{BOTON2}">Exportar a Excel</button><a href="Compras.dc.html" style="{BOTON}">+ Solicitar materiales</a>')
    cont += f'<div style="display: flex; flex-wrap: wrap; gap: 8px">{EJEMPLO}{chip("El requerimiento avisa, no bloquea", "neutro")}</div>\n'
    cont += f'''<section class="vidrio" style="{TARJETA}; display: flex; flex-direction: column; gap: 14px">
<div role="tablist" style="display: flex; flex-wrap: wrap; gap: 6px; background: rgba(255,255,255,0.6); border-radius: 24px; padding: 4px; align-self: flex-start">
<sc-for list="{{{{pestanas}}}}" as="t" hint-placeholder-count="2"><button role="tab" aria-selected="{{{{t.on}}}}" onClick="{{{{t.elegir}}}}" style="min-height: 42px; padding: 0 18px; border: 0; border-radius: 21px; background: {{{{t.bg}}}}; color: {{{{t.fg}}}}; font: 700 14px Inter, sans-serif; cursor: pointer">{{{{t.texto}}}}</button></sc-for></div>
<sc-if value="{{{{verSaldo}}}}" hint-placeholder-val="{{{{ true }}}}">
<div style="overflow-x: auto"><table class="tabla"><thead><tr><th>Insumo</th><th>Und.</th><th class="n">Requerido (S10)</th><th class="n">Comprado</th><th class="n">Saldo por comprar</th><th class="n">Stock en obra</th><th class="n">Punto de reposición</th><th>Estado</th></tr></thead><tbody>
<sc-for list="{{{{filas}}}}" as="r" hint-placeholder-count="6"><tr><td style="font-weight: 600">{{{{r.n}}}}</td><td>{{{{r.u}}}}</td><td class="n">{{{{r.req}}}}</td><td class="n">{{{{r.comp}}}}</td><td class="n" style="font-weight: 800">{{{{r.saldo}}}}</td><td class="n">{{{{r.st}}}}</td><td class="n">{{{{r.pr}}}}</td>
<td><span style="{CHIP}; background: {{{{r.bg}}}}; color: {{{{r.fg}}}}">{{{{r.est}}}}</span></td></tr></sc-for></tbody></table></div>
</sc-if>
<sc-if value="{{{{verParam}}}}" hint-placeholder-val="{{{{ false }}}}">
<div style="overflow-x: auto"><table class="tabla"><thead><tr><th>Insumo</th><th>Und.</th><th class="n">Stock de seguridad</th><th class="n">Punto de reposición</th><th class="n">Días de entrega</th><th>Vigente desde</th></tr></thead><tbody>
<sc-for list="{{{{filas}}}}" as="r" hint-placeholder-count="6"><tr><td style="font-weight: 600">{{{{r.n}}}}</td><td>{{{{r.u}}}}</td>
<td class="n"><input class="campo" aria-label="Stock de seguridad" value="{{{{r.seg}}}}" style="width: 110px; height: 40px; text-align: right"></td>
<td class="n"><input class="campo" aria-label="Punto de reposición" value="{{{{r.pr}}}}" style="width: 110px; height: 40px; text-align: right"></td>
<td class="n"><input class="campo" aria-label="Días de entrega" value="{{{{r.dias}}}}" style="width: 80px; height: 40px; text-align: right"></td><td style="color: #4B5563">[fecha]</td></tr></sc-for></tbody></table></div>
<div style="display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 10px"><span style="font-size: 13px; color: #4B5563">Cada cambio crea una versión nueva con su fecha: la anterior se conserva.</span><button style="{BOTON}">Guardar parámetros</button></div>
</sc-if></section>
'''
    s += marco("reposicion", cont)
    script = '''const FILAS = ''' + json.dumps(filas, ensure_ascii=False) + ''';
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { t: 0 }; }
  renderVals() { const t = this.state.t;
    return { filas: FILAS, verSaldo: t === 0, verParam: t === 1,
      pestanas: ['Saldo por comprar', 'Parámetros de reposición'].map((x, i) => ({ texto: x, on: t === i, elegir: () => this.setState({ t: i }),
        bg: t === i ? '#111827' : 'transparent', fg: t === i ? '#FFFFFF' : '#111827' })) }; }
}'''
    guardar("Reposicion", s + pie(script, h))


# ═════════════════════════ W6 · Compras
def compras():
    h = 1120
    dib = f'''<g fill="none" stroke="{G}" stroke-width="1.4" opacity="0.45"><path d="M1200 120h160v240l-20-14-20 14-20-14-20 14-20-14-20 14-20-14-20 14z"></path><path d="M1225 170h110M1225 200h110M1225 230h80M1225 290h110"></path></g>
<g fill="none" stroke="{G}" stroke-width="1.4" opacity="0.4"><path d="M120 760h260v120H120zM380 800h80l40 50v30H380M170 900a20 20 0 1 0 0 1M430 900a20 20 0 1 0 0 1"></path></g>'''
    lista = [("F001-[n]", "ACERO COMERCIAL S.R.L.", 4620.00, "Parcial", "aviso", "Registrada", "bien"),
             ("F002-[n]", "CENTRAL DEL ACERO S.R.L.", 2216.00, "Completa", "bien", "Registrada", "bien"),
             ("F001-[n]", "COMERCIAL FERRETERO MAGER S.A.C.", 584.60, "Pendiente", "neutro", "Registrada", "bien"),
             ("F003-[n]", "CONSORCIO FERRETERO PLAST CENTER SOCIEDAD ANONIMA CERRADA", 1169.20, "—", "neutro", "Anulada", "grave")]
    filas = ""
    for comp, prov, base, rec, tr, est, te in lista:
        igv = base * 0.18
        filas += (f'<tr><td>[fecha]</td><td style="font-weight: 700">{comp}</td><td style="max-width: 260px">{prov}</td><td class="n">{base:,.2f}</td><td class="n">{igv:,.2f}</td>'
                  f'<td class="n" style="font-weight: 800">{base+igv:,.2f}</td><td>{chip(rec, tr)}</td><td>{chip(est, te)}</td></tr>\n')
    lineas = [('FIERRO CORRUGADO DE 1/2" x 9m - G60', "120 und", 41.27), ("ALAMBRE RECOCIDO N°16", "50 kg", 5.39)]
    base = 120 * 41.27 + 50 * 5.39
    lin = "".join(f'<div style="display: flex; justify-content: space-between; gap: 10px; padding: 10px 0; border-bottom: 1px solid rgba(17,24,39,0.07); font-size: 14px"><span style="display: flex; flex-direction: column"><span style="font-weight: 700">{n}</span><span style="color: #4B5563; font-size: 13px">{c} × S/ {p:.2f}</span></span>'
                  f'<span style="font-weight: 700; font-variant-numeric: tabular-nums">{float(c.split()[0])*p:,.2f}</span></div>' for n, c, p in lineas)
    s = cabeza("Compras") + raiz(h) + fondo("wq", ("#EEF0F6", "#F2F1EE", "#F6EADF"), [(1260, 220, 340, "a"), (260, 760, 360, "n"), (860, 1080, 300, "t")], dib)
    cont = encabezado("Compras", "Lo que el residente compra a sus proveedores · la compra no mueve el stock: lo mueve la recepción en almacén",
                      f'<a href="Reposicion.dc.html" style="{BOTON2}">Ver saldo por comprar</a>')
    cont += f'<div style="display: flex; flex-wrap: wrap; gap: 8px">{EJEMPLO}{chip("Proveedores: los 626 reales del cliente", "marca")}</div>\n'
    cont += f'''<div style="display: flex; flex-wrap: wrap; gap: 18px; align-items: flex-start">
<section class="vidrio" style="{TARJETA}; flex: 999 1 560px; min-width: 0; display: flex; flex-direction: column; gap: 12px"><h2 style="{H2}">Compras de la obra</h2>
<div style="overflow-x: auto"><table class="tabla"><thead><tr><th>Fecha</th><th>Comprobante</th><th>Proveedor</th><th class="n">Base S/</th><th class="n">IGV S/</th><th class="n">Total S/</th><th>Recepción</th><th>Estado</th></tr></thead><tbody>
{filas}</tbody></table></div>
<span style="font-size: 13px; color: #4B5563">Una compra no se edita: se anula con su motivo y queda en la lista. El IGV se guarda aparte porque no es costo del insumo (NIC 2).</span></section>
<aside class="vidrio-fuerte" style="{TARJETA}; flex: 1 1 340px; display: flex; flex-direction: column; gap: 14px; box-shadow: 0 14px 34px rgba(15,23,42,0.12)">
<h2 style="{H2}">Registrar compra</h2>
<label style="display: flex; flex-direction: column; gap: 6px; font-size: 14px; font-weight: 600; color: #374151">Proveedor
<span class="campo" style="height: auto; min-height: 56px; padding: 8px 14px; display: flex; flex-direction: column; justify-content: center"><span style="font-weight: 700; color: #111827">ACERO COMERCIAL S.R.L.</span><span style="font-size: 12.5px; color: #4B5563; font-weight: 500">RUC 20446460481 · {chip("Validado en SUNAT", "bien")}</span></span></label>
<div style="display: flex; flex-wrap: wrap; gap: 10px">
<label style="flex: 1 1 120px; display: flex; flex-direction: column; gap: 6px; font-size: 14px; font-weight: 600; color: #374151">Tipo<select class="campo"><option>Factura</option><option>Boleta</option></select></label>
<label style="flex: 1 1 140px; display: flex; flex-direction: column; gap: 6px; font-size: 14px; font-weight: 600; color: #374151">Número<input class="campo" value="{{{{num}}}}" onChange="{{{{eNum}}}}" placeholder="F001-000123"></label></div>
<sc-if value="{{{{duplicado}}}}" hint-placeholder-val="{{{{ false }}}}"><div role="alert" style="background: #FEE2E2; color: #991B1B; border-radius: 14px; padding: 10px 12px; font-size: 13px; font-weight: 600">Ese comprobante ya está registrado para este proveedor.</div></sc-if>
<div style="display: flex; flex-direction: column">{lin}</div>
<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; font-size: 13px">
<div style="display: flex; flex-direction: column"><span style="color: #4B5563">Base</span><b style="font-size: 16px">{base:,.2f}</b></div>
<div style="display: flex; flex-direction: column"><span style="color: #4B5563">IGV 18 %</span><b style="font-size: 16px">{base*0.18:,.2f}</b></div>
<div style="display: flex; flex-direction: column"><span style="color: #4B5563">Total S/</span><b style="font-size: 16px">{base*1.18:,.2f}</b></div></div>
<button disabled="{{{{duplicado}}}}" style="{{{{estiloBoton}}}}">Registrar compra</button>
<span style="font-size: 12.5px; color: #4B5563">Prueba: escribe F001-000123 para ver el aviso de duplicado.</span></aside></div>
'''
    s += marco("compras", cont)
    script = '''const B = "''' + BOTON + '''";
class Component extends DCLogic {
  constructor(p) { super(p); this.state = { num: '' }; }
  renderVals() { const d = this.state.num.trim().toUpperCase() === 'F001-000123';
    return { num: this.state.num, eNum: (e) => this.setState({ num: e.target.value }), duplicado: d,
      estiloBoton: d ? B.replace('#111827', '#9CA3AF').replace('cursor: pointer', 'cursor: not-allowed') : B }; }
}'''
    guardar("Compras", s + pie(script, h))


# ═════════════════════════ W7 · Ingresos y salidas
def movimientos():
    h = 1160
    dib = f'''<g fill="none" stroke="{G}" stroke-width="1.6" opacity="0.45" stroke-linecap="round" stroke-linejoin="round"><path d="M1160 360V200l120-60 120 60v160z"></path><path d="M1230 360v-80h100v80"></path>
<path d="M1060 260h80M1120 240l20 20-20 20" stroke="#166534"></path><path d="M1420 300h-0M1400 300h70M1450 280l20 20-20 20" stroke="#B91C1C"></path></g>'''
    stock = [("CEMENTO PORTLAND TIPO I (42.50 KG.)", "Central", "230 bls", "36.5556", "8 407.78"),
             ("ACERO CORRUGADO FY= 4200 Kg/cm2", "Central", "2 350 kg", "4.4800", "10 528.00"),
             ("LADRILLO K.K. 9x13x24 (18 Huecos)", "Frente A", "1 450 und", "0.6300", "913.50"),
             ("ARENA GRUESA", "Frente A", "9 m3", "48.3600", "435.24")]
    st = "".join(f'<tr><td style="font-weight: 600">{a}</td><td>{b}</td><td class="n" style="font-weight: 800">{c}</td><td class="n">{d}</td><td class="n">{e}</td></tr>' for a, b, c, d, e in stock)
    kard = [("#1", "Ingreso", "bien", "Recepción de F001-[n]", "200", "", "200", "36.0000", "36.0000", ""),
            ("#2", "Salida", "marca", "Vaciado de losa · retira [capataz]", "", "30", "170", "36.0000", "36.0000", ""),
            ("#3", "Salida", "marca", "Muros del 2.º piso · retira [capataz]", "", "25", "145", "36.0000", "36.0000", "Revertido por #4"),
            ("#4", "Reverso de #3", "neutro", "Se registró en el almacén equivocado", "25", "", "170", "36.0000", "36.0000", ""),
            ("#5", "Ingreso", "bien", "Recepción de F002-[n]", "100", "", "270", "37.5000", "36.5556", ""),
            ("#6", "Salida", "marca", "Columnas del 3.er piso · retira [capataz]", "", "40", "230", "36.5556", "36.5556", "")]
    kd = ""
    for n, tipo, tono, det, ent, sal, saldo, cu, prom, nota in kard:
        extra = f'<br><span style="font-size: 12px; color: #92400E; font-weight: 700">{nota}</span>' if nota else ""
        kd += (f'<tr><td style="color: #4B5563">{n}</td><td>{chip(tipo, tono)}</td><td>{det}{extra}</td><td class="n" style="color: #166534; font-weight: 700">{ent}</td>'
               f'<td class="n" style="color: #B91C1C; font-weight: 700">{sal}</td><td class="n" style="font-weight: 800">{saldo}</td><td class="n">{cu}</td><td class="n">{prom}</td></tr>\n')
    s = cabeza("Ingresos y salidas") + raiz(h) + fondo("wm", ("#EDF0F6", "#F2F0EE", "#F5E9DF"), [(1280, 260, 340, "t"), (200, 820, 360, "n"), (860, 1100, 300, "a")], dib)
    cont = encabezado("Ingresos y salidas", "El libro del almacén: nada se borra; una corrección es un reverso que apunta al original",
                      f'<button style="{BOTON2}">Exportar kárdex</button><button style="{BOTON}">Corregir con reverso</button>')
    cont += f'<div style="display: flex; flex-wrap: wrap; gap: 8px">{EJEMPLO}{chip("El saldo lo calcula el servidor", "neutro")}{chip("Costo promedio ponderado (NIC 2)", "neutro")}</div>\n'
    cont += f'''<section class="vidrio" style="{TARJETA}; display: flex; flex-direction: column; gap: 12px"><h2 style="{H2}">Stock por almacén</h2>
<div style="overflow-x: auto"><table class="tabla"><thead><tr><th>Insumo</th><th>Almacén</th><th class="n">Saldo</th><th class="n">Costo promedio S/</th><th class="n">Valor S/</th></tr></thead><tbody>{st}</tbody></table></div></section>
<section class="vidrio" style="{TARJETA}; display: flex; flex-direction: column; gap: 12px">
<div style="display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; align-items: baseline"><h2 style="{H2}">Kárdex · CEMENTO PORTLAND TIPO I (42.50 KG.) · almacén central</h2><span style="font-size: 13px; color: #4B5563">Cada salida congela el promedio de ese momento</span></div>
<div style="overflow-x: auto"><table class="tabla"><thead><tr><th>N.º</th><th>Movimiento</th><th>Detalle</th><th class="n">Entra</th><th class="n">Sale</th><th class="n">Saldo</th><th class="n">Costo unit. S/</th><th class="n">Promedio S/</th></tr></thead><tbody>
{kd}</tbody></table></div>
<span style="font-size: 13px; color: #4B5563">El ingreso #5 a S/ 37.50 sube el promedio a 36.5556; la salida #2, hecha antes, sigue valorizada a 36.0000.</span></section>
'''
    s += marco("movimientos", cont)
    guardar("Movimientos", s + pie(VACIO, h))


# ═════════════════════════ W8 · Usuarios y celulares
def accesos():
    h = 1000
    dib = f'''<g fill="none" stroke="{G}" stroke-width="1.5" opacity="0.45" stroke-linecap="round" stroke-linejoin="round"><path d="M1220 260a80 80 0 0 1 160 0z"></path><path d="M1190 260h220M1300 160v30"></path>
<path d="M1330 420a40 40 0 1 0 0 1M1360 420h80l-10 14 10 14"></path></g>'''
    usuarios = [("[usuario]", "[Nombre y apellido]", "Residente de obra", "marca"), ("[usuario]", "[Nombre y apellido]", "Almacenero", "info"),
                ("[usuario]", "[Nombre y apellido]", "Ingeniero SSOMA", "info"), ("[usuario]", "[Nombre y apellido]", "Administrador", "neutro"),
                ("[usuario]", "[Nombre y apellido]", "Gerente", "neutro")]
    us = "".join(f'<tr><td style="font-weight: 700">{a}</td><td>{b}</td><td>{chip(c, t)}</td><td>{chip("Activo", "bien")}</td><td>desde [fecha]</td>'
                 f'<td><button style="{BOTON2}; min-height: 38px; padding: 0 14px">Cambiar</button></td></tr>' for a, b, c, t in usuarios)
    s = cabeza("Usuarios y celulares") + raiz(h) + fondo("wa", ("#EEF1F7", "#F3F1EE", "#F6EBE0"), [(1260, 260, 340, "a"), (220, 780, 360, "n"), (860, 960, 300, "t")], dib)
    cont = encabezado("Usuarios y celulares", "Quién entra, con qué rol, en qué obra y desde qué celular",
                      f'<button style="{BOTON}">+ Agregar usuario</button>')
    cont += f'''<div style="display: flex; flex-wrap: wrap; gap: 18px; align-items: flex-start">
<section class="vidrio" style="{TARJETA}; flex: 999 1 560px; min-width: 0; display: flex; flex-direction: column; gap: 12px"><h2 style="{H2}">Asignados a Multifamiliar Flores</h2>
<div style="overflow-x: auto"><table class="tabla"><thead><tr><th>Usuario</th><th>Nombre</th><th>Rol en esta obra</th><th>Estado</th><th>Asignado</th><th></th></tr></thead><tbody>{us}</tbody></table></div>
<span style="font-size: 13px; color: #4B5563">El rol vale solo en esta obra y lo verifica el servidor en cada pedido. Quien deja la obra se retira con su fecha: no se borra.</span></section>
<aside class="vidrio-fuerte" style="{TARJETA}; flex: 1 1 320px; display: flex; flex-direction: column; gap: 12px; box-shadow: 0 14px 34px rgba(15,23,42,0.12)">
<div style="display: flex; justify-content: space-between; align-items: baseline; gap: 8px"><h2 style="{H2}">Celulares autorizados</h2><span style="font-size: 14px; font-weight: 800">{{{{cuenta}}}} de 2</span></div>
<div style="background: rgba(255,255,255,0.85); border-radius: 16px; padding: 12px 14px; display: flex; flex-direction: column; gap: 6px">
<span style="display: flex; justify-content: space-between; gap: 8px; font-size: 14px; font-weight: 700">Almacenero{chip("Activo", "bien")}</span>
<span style="font-family: ui-monospace, monospace; font-size: 13px; color: #374151; word-break: break-all">sigro-android-[código]</span>
<button style="{BOTON2}; min-height: 38px; align-self: flex-start">Revocar con motivo</button></div>
<div style="background: rgba(255,255,255,0.85); border-radius: 16px; padding: 12px 14px; display: flex; flex-direction: column; gap: 8px">
<span style="display: flex; justify-content: space-between; gap: 8px; font-size: 14px; font-weight: 700">Ingeniero SSOMA<span style="{CHIP}; background: {{{{chipBg}}}}; color: {{{{chipFg}}}}">{{{{estado}}}}</span></span>
<span style="font-family: ui-monospace, monospace; font-size: 13px; color: #374151; word-break: break-all">sigro-ios-[código]</span>
<sc-if value="{{{{pendiente}}}}" hint-placeholder-val="{{{{ true }}}}"><div style="display: flex; gap: 8px"><button onClick="{{{{autorizar}}}}" style="{BOTON}; min-height: 42px">Autorizar</button><button style="{BOTON2}; min-height: 42px">Rechazar</button></div></sc-if></div>
<span style="font-size: 13px; color: #4B5563; line-height: 1.45">Máximo dos por obra: el del almacenero y el del ingeniero SSOMA. Un registro que llegue desde otro celular lo rechaza el servidor.</span></aside></div>
'''
    s += marco("accesos", cont)
    script = '''class Component extends DCLogic {
  constructor(p) { super(p); this.state = { ok: false }; }
  renderVals() { const ok = this.state.ok;
    return { pendiente: !ok, cuenta: ok ? 2 : 1, estado: ok ? 'Activo' : 'Pendiente', chipBg: ok ? '#DCFCE7' : '#FEF3C7', chipFg: ok ? '#166534' : '#92400E',
      autorizar: () => this.setState({ ok: true }) }; }
}'''
    guardar("Accesos", s + pie(script, h))


PAGINAS = [("Main", entrar, 900, "W1 · Entrar"), ("Panel", panel, 1180, "W2 · Gasto en recursos (HU-23, HU-57, HU-58)"),
           ("Importar", importar, 1120, "W3 · Importar del S10 (HU-07, HU-19)"), ("Catalogo", catalogo, 1000, "W4 · Catálogo de insumos (HU-08, HU-10, HU-17)"),
           ("Reposicion", reposicion, 1000, "W5 · Reposición: saldo por comprar y parámetros (HU-24, HU-25)"), ("Compras", compras, 1120, "W6 · Compras (HU-29 a HU-34)"),
           ("Movimientos", movimientos, 1160, "W7 · Ingresos y salidas: stock y kárdex (HU-43, HU-45, HU-55)"), ("Accesos", accesos, 1000, "W8 · Usuarios y celulares (HU-13, HU-14, HU-15)")]

if __name__ == "__main__":
    import datetime
    for nombre, f, h, t in PAGINAS:
        f()
    boards, order = {}, []
    x = y = 0
    for i, (nombre, f, h, t) in enumerate(PAGINAS):
        col, fila = i % 2, i // 2
        boards[f"{nombre}.dc.html"] = {"x": col * 1520, "y": fila * 1400, "w": 1440, "h": h, "title": t, "expand": "fill", "is_interactive": True, "radius": 24}
        order.append(f"{nombre}.dc.html")
    canvas = {"v": 3, "createdOnFiles": {"v": 1, "at": datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")},
              "title": "SIGRO Oficina · Web (estilo vidrio)", "launch": {"view": "canvas"}, "pages": [],
              "boards": boards, "order": order,
              "notes": {"titulo": {"x": 0, "y": -300, "text": "Web de oficina · mismo vidrio que la app", "kind": "title1", "maxW": 2960},
                        "leyenda": {"x": 3080, "y": 0, "w": 560, "size": "m", "fill": "orange",
                                    "text": "Estilo de la app llevado a escritorio: Inter, vidrio sobre un fondo de plano distinto en cada página, acento naranja C2410C, botón negro en píldora y menú lateral con la sección activa en naranja.\n\nDatos: lo planificado, los insumos, el catálogo y los proveedores son REALES del cliente. Consumos, compras y stock son de ejemplo y cada página lo dice. Los nombres de personas van como [marcador]."}},
              "designSystems": []}
    os.makedirs(f"{ROOT}/project", exist_ok=True)
    json.dump(canvas, open(f"{ROOT}/project/canvas.json", "w"), ensure_ascii=False, indent=2)
    print("ok", len(PAGINAS))
