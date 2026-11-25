"""Convierte el reporte XML de Kover (formato JaCoCo) al formato genérico de cobertura de SonarQube.

Por qué: en Kotlin Multiplatform el mismo paquete vive en commonMain, androidMain, desktopMain e iosMain, y el
importador JaCoCo de Sonar no siempre sabe a cuál archivo corresponde cada línea (la app salía con 0 %).
Aquí cada archivo se resuelve a su ruta real dentro de composeApp/src/*/kotlin, prefiriendo commonMain.

Uso (desde android/):  python3 scripts/kover_a_sonar.py composeApp/build/reports/kover/reportDebug.xml build/sonar-cobertura.xml
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ORDEN = ["commonMain", "androidMain", "desktopMain", "iosMain"]


def resolver(paquete: str, nombre: str, raiz: Path) -> str | None:
    for conjunto in ORDEN:
        ruta = raiz / "composeApp" / "src" / conjunto / "kotlin" / paquete / nombre
        if ruta.is_file():
            return ruta.relative_to(raiz).as_posix()
    return None


def convertir(entrada: Path, salida: Path, raiz: Path) -> tuple[int, int]:
    origen = ET.parse(entrada).getroot()
    cobertura = ET.Element("coverage", version="1")
    archivos = sin_ubicar = 0
    for paquete in origen.iter("package"):
        for fuente in paquete.iter("sourcefile"):
            ruta = resolver(paquete.get("name", ""), fuente.get("name", ""), raiz)
            if ruta is None:
                sin_ubicar += 1
                continue
            archivo = ET.SubElement(cobertura, "file", path=ruta)
            for linea in fuente.iter("line"):
                mi, ci = int(linea.get("mi", 0)), int(linea.get("ci", 0))
                mb, cb = int(linea.get("mb", 0)), int(linea.get("cb", 0))
                attrs = {"lineNumber": linea.get("nr"), "covered": "true" if ci > 0 else "false"}
                if mb + cb > 0:
                    attrs.update(branchesToCover=str(mb + cb), coveredBranches=str(cb))
                if mi + ci > 0:
                    ET.SubElement(archivo, "lineToCover", attrs)
            archivos += 1
    salida.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(cobertura).write(salida, encoding="utf-8", xml_declaration=True)
    return archivos, sin_ubicar


if __name__ == "__main__":
    hechos, perdidos = convertir(Path(sys.argv[1]), Path(sys.argv[2]), Path.cwd())
    print(f"cobertura: {hechos} archivos convertidos, {perdidos} sin ubicar")
    sys.exit(1 if hechos == 0 else 0)
