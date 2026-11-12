"""Convierte el fondo de plano de cada pantalla del lienzo (lienzo/*.dc.html) en código Compose.

    python diseno/generador/a_compose.py        # desde android/

Escribe composeApp/src/commonMain/kotlin/pe/sevens/sigro/ui/componentes/PlanosLienzo.kt.
Lee el primer <svg> de 390 de ancho de cada lienzo: degradado, manchas difusas y los trazos decorativos.
La cuadrícula de 24 y 120 la pone `baseLienzo` (es la misma en todas). No edites el .kt a mano: cambia
el lienzo y vuelve a correr esto.
"""
from __future__ import annotations

import os
import re
import xml.etree.ElementTree as ET

AQUI = os.path.dirname(os.path.abspath(__file__))
LIENZO = os.path.join(AQUI, "..", "lienzo")
SALIDA = os.path.join(AQUI, "..", "..", "composeApp", "src", "commonMain", "kotlin", "pe", "sevens", "sigro",
                      "ui", "componentes", "PlanosLienzo.kt")

# pantalla del lienzo → nombre de la función Kotlin
PANTALLAS = {
    "Obras": "planoObras",
    "Celular": "planoCelular",
    "CatBuscar": "planoCatBuscar",
    "CatFicha": "planoCatFicha",
    "CatNuevo": "planoCatNuevo",
    "MOTrabajadores": "planoTrabajadores",
    "MONuevo": "planoTrabajadorNuevo",
    "MOAsistencia": "planoAsistencia",
}


def num(v: str | None, defecto: float = 0.0) -> float:
    if v is None:
        return defecto
    return float(re.sub(r"[^0-9.\-]", "", v) or defecto)


def f(x: float) -> str:
    s = f"{x:.3f}".rstrip("0").rstrip(".")
    return (s if s not in ("", "-0") else "0") + "f"


def color(c: str) -> str:
    c = c.strip()
    if c.startswith("#"):
        h = c[1:]
        if len(h) == 3:
            h = "".join(ch * 2 for ch in h)
        return f"Color(0xFF{h.upper()})"
    return {"white": "Color.White", "black": "Color.Black"}.get(c, "Color(0xFF475569)")


def kstr(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


class Conversor:
    def __init__(self, nombre: str, svg: str):
        self.nombre = nombre
        self.svg = svg
        self.trazos: list[str] = []   # constantes de Path
        self.lineas: list[str] = []   # cuerpo de la función
        self.radiales: dict[str, tuple[str, float]] = {}
        self.lineal: list[str] = []
        self.manchas: list[str] = []
        self.alto = 844.0

    def ruta(self, d: str) -> str:
        n = f"{self.nombre}T{len(self.trazos)}"
        self.trazos.append(f"private val {n} by lazy {{ svg({kstr(d)}) }}")
        return n

    def convertir(self) -> str:
        raiz = ET.fromstring(self.svg)
        self.alto = num(raiz.get("height"), 844)
        for el in raiz:
            tag = el.tag
            if tag == "defs":
                for d in el:
                    if d.tag == "linearGradient":
                        self.lineal = [color(s.get("stop-color")) for s in d if s.tag == "stop"]
                    elif d.tag == "radialGradient":
                        s0 = [s for s in d if s.tag == "stop"][0]
                        self.radiales[d.get("id")] = (color(s0.get("stop-color")), num(s0.get("stop-opacity"), 1))
                continue
            fill = el.get("fill", "")
            m = re.match(r"url\(#([\w-]+)\)", fill)
            if m:
                if tag == "circle" and m.group(1) in self.radiales:
                    c, a = self.radiales[m.group(1)]
                    self.manchas.append(
                        f"Triple(Offset({f(num(el.get('cx')))}, {f(num(el.get('cy')))}), {f(num(el.get('r')))}, {c}.copy(alpha = {f(a)}))")
                continue  # degradado y cuadrículas: los pone baseLienzo
            if self.y_minima(el) > 0.6 * self.alto:
                # Lo que el lienzo pone abajo se ancla al pie de la pantalla real (el lienzo puede ser más alto).
                self.lineas.append(f"    translate(0f, alto - {f(self.alto)}) {{")
                self.elemento(el, {}, 2)
                self.lineas.append("    }")
            else:
                self.elemento(el, {}, 1)
        c0, c1, c2 = (self.lineal + ["Color(0xFFE8EEF6)", "Color(0xFFF3F1EE)", "Color(0xFFF6E9DF)"])[:3]
        cab = [
            f"fun DrawScope.{self.nombre}(m: TextMeasurer) = enLienzoPorAncho {{ alto ->",
            f"    baseLienzo(alto, {c0}, {c1}, {c2}, listOf({', '.join(self.manchas)}))",
        ]
        return "\n".join(self.trazos) + "\n\n" + "\n".join(cab + self.lineas + ["}"]) + "\n"

    def y_minima(self, el) -> float:
        tr = el.get("transform") or ""
        m = re.search(r"translate\(\s*[-\d.]+[ ,]+([-\d.]+)", tr)
        if m:
            return float(m.group(1))
        if el.tag == "g":
            ys = [self.y_minima(h) for h in el]
            return min(ys) if ys else 0.0
        if el.tag == "path":
            m = re.match(r"\s*M\s*[-\d.]+[ ,]+([-\d.]+)", el.get("d", ""))
            return float(m.group(1)) if m else 0.0
        for k in ("cy", "y", "y1"):
            if el.get(k) is not None:
                return num(el.get(k)) - num(el.get("r"), 0)
        return 0.0

    def elemento(self, el, her: dict, nivel: int):
        a = dict(her)
        for k in ("fill", "stroke", "stroke-width", "stroke-dasharray", "stroke-linecap", "font-size", "font-weight",
                  "text-anchor"):
            if el.get(k) is not None:
                a[k] = el.get(k)
        alfa = her.get("_alfa", 1.0) * num(el.get("opacity"), 1.0) * num(el.get("fill-opacity"), 1.0)
        a["_alfa"] = alfa
        ind = "    " * nivel
        tr = el.get("transform")
        cierres = 0
        if tr:
            for op, args in re.findall(r"(\w+)\(([^)]*)\)", tr):
                v = [float(x) for x in re.split(r"[ ,]+", args.strip()) if x]
                if op == "translate":
                    self.lineas.append(f"{ind}translate({f(v[0])}, {f(v[1] if len(v) > 1 else 0)}) {{")
                elif op == "rotate":
                    piv = f"Offset({f(v[1])}, {f(v[2])})" if len(v) == 3 else "Offset.Zero"
                    self.lineas.append(f"{ind}rotate({f(v[0])}, {piv}) {{")
                elif op == "scale":
                    self.lineas.append(f"{ind}scale({f(v[0])}, {f(v[1] if len(v) > 1 else v[0])}, Offset.Zero) {{")
                else:
                    continue
                cierres += 1
                ind += "    "
        tag = el.tag
        if tag == "g":
            for h in el:
                self.elemento(h, a, nivel + cierres)
        elif tag in ("path", "circle", "rect", "line", "ellipse", "polyline", "polygon"):
            self.forma(el, tag, a, ind)
        elif tag == "text":
            texto = "".join(el.itertext()).strip()
            if texto:
                col = color(a.get("fill", "#475569"))
                tam = num(a.get("font-size"), 10)
                neg = str(a.get("font-weight", "400")) in ("600", "700", "800", "bold")
                anc = a.get("text-anchor") == "middle"
                self.lineas.append(
                    f"{ind}rotulo(m, {kstr(texto)}, {f(num(el.get('x')))}, {f(num(el.get('y')))}, {f(alfa)}, {col}, "
                    f"tam = {f(tam)}, negrita = {str(neg).lower()}, centrado = {str(anc).lower()})")
        for _ in range(cierres):
            ind = ind[:-4]
            self.lineas.append(f"{ind}}}")

    def forma(self, el, tag, a, ind):
        if tag == "path":
            d = el.get("d")
        elif tag == "circle":
            cx, cy, r = num(el.get("cx")), num(el.get("cy")), num(el.get("r"))
            d = f"M{cx - r} {cy}a{r} {r} 0 1 0 {2 * r} 0a{r} {r} 0 1 0 {-2 * r} 0z"
        elif tag == "ellipse":
            cx, cy, rx, ry = num(el.get("cx")), num(el.get("cy")), num(el.get("rx")), num(el.get("ry"))
            d = f"M{cx - rx} {cy}a{rx} {ry} 0 1 0 {2 * rx} 0a{rx} {ry} 0 1 0 {-2 * rx} 0z"
        elif tag == "rect":
            x, y, w, h = num(el.get("x")), num(el.get("y")), num(el.get("width")), num(el.get("height"))
            rx = num(el.get("rx"), 0)
            if rx:
                rx = min(rx, w / 2, h / 2)
                d = (f"M{x + rx} {y}h{w - 2 * rx}a{rx} {rx} 0 0 1 {rx} {rx}v{h - 2 * rx}a{rx} {rx} 0 0 1 {-rx} {rx}"
                     f"h{-(w - 2 * rx)}a{rx} {rx} 0 0 1 {-rx} {-rx}v{-(h - 2 * rx)}a{rx} {rx} 0 0 1 {rx} {-rx}z")
            else:
                d = f"M{x} {y}h{w}v{h}h{-w}z"
        elif tag == "line":
            d = f"M{num(el.get('x1'))} {num(el.get('y1'))}L{num(el.get('x2'))} {num(el.get('y2'))}"
        else:
            pts = el.get("points", "").split()
            d = "M" + " L".join(pts) + ("z" if tag == "polygon" else "")
        n = self.ruta(d)
        alfa = a["_alfa"]
        relleno = a.get("fill", "#000000" if "stroke" not in a else "none")
        if relleno and relleno != "none" and not relleno.startswith("url"):
            self.lineas.append(f"{ind}drawPath({n}, {color(relleno)}.copy(alpha = {f(alfa)}))")
        trazo = a.get("stroke")
        if trazo and trazo != "none":
            dash = a.get("stroke-dasharray")
            tramos = [float(x) for x in re.split(r"[ ,]+", dash.strip())] if dash else []
            disc = f"floatArrayOf({', '.join(f(x) for x in tramos)})" if tramos and min(tramos) > 0 else "null"
            cuad = "true" if a.get("stroke-linecap") == "square" else "false"
            self.lineas.append(
                f"{ind}trazo({n}, {f(alfa)}, {f(num(a.get('stroke-width'), 1))}, {color(trazo)}, discontinuo = {disc}, cuadrado = {cuad})")


def main():
    partes = [
        "package pe.sevens.sigro.ui.componentes\n",
        "// GENERADO por diseno/generador/a_compose.py a partir de diseno/lienzo/*.dc.html. No editar a mano.\n",
        "import androidx.compose.ui.geometry.Offset",
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.graphics.drawscope.DrawScope",
        "import androidx.compose.ui.graphics.drawscope.rotate",
        "import androidx.compose.ui.graphics.drawscope.scale",
        "import androidx.compose.ui.graphics.drawscope.translate",
        "import androidx.compose.ui.text.TextMeasurer\n",
    ]
    for pantalla, nombre in PANTALLAS.items():
        t = open(os.path.join(LIENZO, pantalla + ".dc.html"), encoding="utf-8").read()
        m = re.search(r'<svg aria-hidden="true" width="390".*?</svg>', t, re.S)
        if not m:
            raise SystemExit(f"{pantalla}: sin fondo de plano")
        partes.append(f"// ── {pantalla}.dc.html " + "─" * 60)
        partes.append(Conversor(nombre, m.group(0)).convertir())
    with open(SALIDA, "w", encoding="utf-8", newline="\n") as s:
        s.write("\n".join(partes))
    print("escrito", os.path.relpath(SALIDA))


if __name__ == "__main__":
    main()
