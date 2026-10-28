"""Fondos de plano únicos por pantalla (SVG decorativo, aria-hidden)."""
import math

def base(p, c0, c1, c2, manchas):
    m = ''.join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="url(#{p}-m{t})"></circle>' for x, y, r, t in manchas)
    return f'''<svg aria-hidden="true" width="390" height="844" viewBox="0 0 390 844" style="position: absolute; left: 0; top: 0">
    <defs>
      <linearGradient id="{p}-fondo" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{c0}"></stop><stop offset="0.55" stop-color="{c1}"></stop><stop offset="1" stop-color="{c2}"></stop></linearGradient>
      <pattern id="{p}-c" width="24" height="24" patternUnits="userSpaceOnUse"><path d="M24 0H0V24" fill="none" stroke="#94A3B8" stroke-width="0.5" opacity="0.4"></path></pattern>
      <pattern id="{p}-C" width="120" height="120" patternUnits="userSpaceOnUse"><path d="M120 0H0V120" fill="none" stroke="#64748B" stroke-width="0.8" opacity="0.3"></path></pattern>
      <radialGradient id="{p}-mn" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FB923C" stop-opacity="0.5"></stop><stop offset="1" stop-color="#FB923C" stop-opacity="0"></stop></radialGradient>
      <radialGradient id="{p}-ma" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#60A5FA" stop-opacity="0.4"></stop><stop offset="1" stop-color="#60A5FA" stop-opacity="0"></stop></radialGradient>
      <radialGradient id="{p}-mt" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#2DD4BF" stop-opacity="0.32"></stop><stop offset="1" stop-color="#2DD4BF" stop-opacity="0"></stop></radialGradient>
    </defs>
    <rect width="390" height="844" fill="url(#{p}-fondo)"></rect>
    <rect width="390" height="844" fill="url(#{p}-c)"></rect>
    <rect width="390" height="844" fill="url(#{p}-C)"></rect>
    {m}
'''

T = 'font-family="Inter, sans-serif"'
GR = 'fill="none" stroke="#475569"'

def planta():  # Entrar: planta arquitectónica con ejes
    s = base('fe', '#EEF1F6', '#F2F0EE', '#F5EAE1', [(320, 520, 200, 'n'), (60, 780, 200, 'a'), (360, 820, 120, 'n')])
    s += f'''<g {GR} stroke-width="0.9" opacity="0.45" stroke-dasharray="6 4">
      <path d="M50 450v380M195 450v380M340 450v380M20 500h350M20 650h350M20 800h350"></path></g>
    <g {GR} stroke-width="1.2" opacity="0.6">
      <circle cx="50" cy="440" r="10"></circle><circle cx="195" cy="440" r="10"></circle><circle cx="340" cy="440" r="10"></circle>
      <circle cx="12" cy="500" r="9"></circle><circle cx="12" cy="650" r="9"></circle><circle cx="12" cy="800" r="9"></circle></g>
    <g fill="#475569" opacity="0.65" {T} font-size="10" font-weight="700" text-anchor="middle">
      <text x="50" y="444">A</text><text x="195" y="444">B</text><text x="340" y="444">C</text>
      <text x="12" y="504">1</text><text x="12" y="654">2</text><text x="12" y="804">3</text></g>
    <g {GR} stroke-width="3.2" opacity="0.5" stroke-linecap="square">
      <path d="M50 500h100M175 500h165v130M340 670v130H230M200 800H50V700M50 670V500"></path>
      <path d="M195 500v100M195 630v170M50 650h110M230 650h110"></path></g>
    <g {GR} stroke-width="1" opacity="0.5">
      <path d="M150 500a25 25 0 0 1 25 25M150 500v25" ></path>
      <path d="M50 700a30 30 0 0 0 30 -30M50 670h30"></path>
      <path d="M230 800a30 30 0 0 1 -30 -30M200 800v-30"></path>
      <path d="M340 630h0M336 640v20M344 640v20"></path></g>
    <g fill="#475569" opacity="0.5">
      <rect x="47" y="497" width="7" height="7"></rect><rect x="192" y="497" width="7" height="7"></rect><rect x="337" y="497" width="7" height="7"></rect>
      <rect x="47" y="647" width="7" height="7"></rect><rect x="192" y="647" width="7" height="7"></rect><rect x="337" y="647" width="7" height="7"></rect>
      <rect x="47" y="797" width="7" height="7"></rect><rect x="192" y="797" width="7" height="7"></rect><rect x="337" y="797" width="7" height="7"></rect></g>
    <g fill="#475569" opacity="0.55" {T} font-size="10" letter-spacing="0.06em">
      <text x="72" y="585">ALMACÉN</text><text x="230" y="585">OFICINA</text><text x="232" y="735">VESTUARIO</text><text x="78" y="735">PATIO</text></g>
    <g fill="none" stroke="#C2410C" stroke-width="1.1" opacity="0.6"><path d="M50 822h290M50 816v12M340 816v12"></path></g>
    <text x="178" y="836" {T} font-size="10" fill="#C2410C" opacity="0.75">12.40 m</text>
  </svg>'''
    return s

def topografia():  # Elegir obra: curvas de nivel y lote
    s = base('fo', '#E9F0F4', '#EEF2F0', '#F4ECE4', [(330, 470, 210, 'a'), (40, 700, 200, 't'), (320, 830, 150, 'n')])
    curvas = ''
    for k in range(9):
        rx, ry = 70 + k * 26, 44 + k * 19
        cx, cy = 205 + k * 4, 600 - k * 3
        pts = []
        for a in range(0, 360, 6):
            t = math.radians(a)
            w = 1 + 0.08 * math.sin(3 * t + k) + 0.05 * math.cos(5 * t - k)
            pts.append((cx + rx * w * math.cos(t), cy + ry * w * math.sin(t)))
        n = len(pts)
        d = f'M{pts[0][0]:.1f} {pts[0][1]:.1f}'
        for i in range(n):  # Catmull-Rom → Bézier: curvas suaves
            p0, p1, p2, p3 = pts[i-1], pts[i], pts[(i+1)%n], pts[(i+2)%n]
            c1 = (p1[0]+(p2[0]-p0[0])/6, p1[1]+(p2[1]-p0[1])/6)
            c2 = (p2[0]-(p3[0]-p1[0])/6, p2[1]-(p3[1]-p1[1])/6)
            d += f' C{c1[0]:.1f} {c1[1]:.1f} {c2[0]:.1f} {c2[1]:.1f} {p2[0]:.1f} {p2[1]:.1f}'
        d += 'Z'
        op = 0.55 if k % 4 == 0 else 0.32
        sw = 1.4 if k % 4 == 0 else 0.9
        curvas += f'<path d="{d}" fill="none" stroke="#475569" stroke-width="{sw}" opacity="{op}" stroke-linejoin="round"></path>'
    s += curvas
    s += f'''<g fill="#475569" opacity="0.6" {T} font-size="9"><text x="208" y="604">+108.0</text><text x="300" y="560">+106.0</text><text x="96" y="690">+104.0</text></g>
    <path d="M120 470L300 455L330 640L150 700Z" fill="none" stroke="#C2410C" stroke-width="1.6" stroke-dasharray="8 5" opacity="0.65"></path>
    <g fill="#C2410C" opacity="0.7"><circle cx="120" cy="470" r="3"></circle><circle cx="300" cy="455" r="3"></circle><circle cx="330" cy="640" r="3"></circle><circle cx="150" cy="700" r="3"></circle></g>
    <text x="230" y="452" {T} font-size="9" fill="#C2410C" opacity="0.75">LOTE</text>
    <g transform="translate(345 770)" opacity="0.6"><circle r="20" fill="none" stroke="#475569" stroke-width="1"></circle><path d="M0 -18L7 8L0 3L-7 8Z" fill="#475569"></path><text y="-25" text-anchor="middle" {T} font-size="10" font-weight="700" fill="#475569">N</text></g>
  </svg>'''
    return s

def estructura():  # Mi celular: detalle de columna, zapata y señal
    s = base('fc', '#EEF0F6', '#F3F0EE', '#F6E8DE', [(70, 160, 190, 'n'), (340, 520, 200, 'a'), (90, 800, 170, 'n')])
    s += f'''<g fill="none" stroke="#C2410C" stroke-width="1.6" opacity="0.4" stroke-linecap="round">
      <path d="M300 150a40 40 0 0 1 40 40"></path><path d="M300 120a70 70 0 0 1 70 70"></path><path d="M300 90a100 100 0 0 1 100 100"></path></g>
    <circle cx="300" cy="190" r="5" fill="#C2410C" opacity="0.45"></circle>
    <g transform="translate(220 560)" opacity="0.6">
      <rect x="0" y="0" width="120" height="120" fill="none" stroke="#475569" stroke-width="1.6"></rect>
      <rect x="12" y="12" width="96" height="96" rx="6" fill="none" stroke="#475569" stroke-width="1.2"></rect>
      <path d="M12 26l14 -14" stroke="#475569" stroke-width="1.2"></path>
      <g fill="#475569"><circle cx="22" cy="22" r="5"></circle><circle cx="98" cy="22" r="5"></circle><circle cx="22" cy="98" r="5"></circle><circle cx="98" cy="98" r="5"></circle><circle cx="60" cy="22" r="4"></circle><circle cx="60" cy="98" r="4"></circle><circle cx="22" cy="60" r="4"></circle><circle cx="98" cy="60" r="4"></circle></g>
      <text x="0" y="140" {T} font-size="10" fill="#475569">C-1 · 30×30 · 8 Ø 5/8"</text>
      <text x="0" y="154" {T} font-size="9" fill="#64748B">□ Ø 3/8": 1@.05, 5@.10, R@.25</text>
    </g>
    <g transform="translate(30 640)" opacity="0.55" fill="none" stroke="#475569">
      <path d="M60 0v80M90 0v80" stroke-width="1.4"></path>
      <path d="M0 80h150v50H0z" stroke-width="1.6"></path>
      <path d="M10 120h130M10 120v-8M140 120v-8" stroke-width="1"></path>
      <path d="M68 10v105M82 10v105" stroke-width="0.9" stroke-dasharray="1 0"></path>
      <path d="M-10 130h170" stroke-width="0.8" stroke-dasharray="4 4"></path>
      <g stroke-width="0.8"><path d="M20 133l-8 8M40 133l-8 8M60 133l-8 8M80 133l-8 8M100 133l-8 8M120 133l-8 8M140 133l-8 8"></path></g>
    </g>
    <g fill="none" stroke="#C2410C" stroke-width="1.1" opacity="0.6"><path d="M30 790h150M30 784v12M180 784v12"></path></g>
    <text x="88" y="806" {T} font-size="10" fill="#C2410C" opacity="0.75">1.50 m</text>
  </svg>'''
    return s
