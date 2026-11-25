package pe.sevens.sigro.integraciones;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Lector de la «Relación de insumos» del S10 (HU-07), port de integraciones/s10.py. Solo lee y normaliza: no toca la base.
 * Una fila que parece de insumo pero no se entiende va a «fallidos» con su motivo: nunca se descarta en silencio.
 */
public final class LectorS10 {
    private LectorS10() {}

    static final Map<String, String> SECCIONES = Map.of("MANO DE OBRA", "mano_obra", "MATERIALES", "material", "EQUIPOS", "equipo", "SUBCONTRATOS", "subcontrato");
    static final Pattern FILA = Pattern.compile("^(\\d{10})\\s+(.+?)\\s+(\\S{1,8})\\s+([\\d,]+\\.\\d{4})\\s+([\\d,]+\\.\\d{2})\\s+([\\d,]+\\.\\d{2})$");
    static final Pattern ENCABEZADO = Pattern.compile("^(S10|Página|Obra|Subpresupuesto|Fecha|Lugar|Código\\s+Recurso)");

    public record Insumo(String codigo, String descripcion, String unidad, String tipo, double cantidad, double precio, double parcial, String advertencia) {}
    public static final class Lectura {
        public String obra, subpresupuesto, fecha;
        public final List<Insumo> insumos = new ArrayList<>();
        public final List<Map<String, Object>> fallidos = new ArrayList<>();
    }

    static double numero(String t) { return Double.parseDouble(t.replace(",", "")); }

    public static Lectura leer(byte[] pdf) throws java.io.IOException {
        var l = new Lectura();
        String tipo = null;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            var st = new PDFTextStripper();
            st.setSortByPosition(true);
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                st.setStartPage(p);
                st.setEndPage(p);
                for (String cruda : st.getText(doc).split("\\R")) {
                    String linea = cruda.strip().replaceAll("[ \\t]+", " ");
                    if (linea.isEmpty()) continue;
                    Matcher m;
                    if (l.obra == null && (m = Pattern.compile("^Obra\\s+(\\d+)\\s+(.*)$").matcher(linea)).find()) { l.obra = m.group(1) + " " + m.group(2).strip(); continue; }
                    if (l.subpresupuesto == null && (m = Pattern.compile("^Subpresupuesto\\s+(\\d+)\\s+(.*)$").matcher(linea)).find()) { l.subpresupuesto = m.group(1) + " " + m.group(2).strip(); continue; }
                    if (l.fecha == null && (m = Pattern.compile("^Fecha\\s+(\\d{2}/\\d{2}/\\d{4})").matcher(linea)).find()) { l.fecha = m.group(1); continue; }
                    if (SECCIONES.containsKey(linea)) { tipo = SECCIONES.get(linea); continue; }
                    if (ENCABEZADO.matcher(linea).find() || linea.startsWith("Total")) continue;
                    if (linea.matches("[\\d,]+\\.\\d{2}")) continue;
                    m = FILA.matcher(linea);
                    if (m.find()) {
                        if (tipo == null) {
                            l.fallidos.add(new LinkedHashMap<>(Map.of("texto", linea.substring(0, Math.min(90, linea.length())), "error", "Fila de insumo antes de la primera sección.")));
                            continue;
                        }
                        double c = numero(m.group(4)), pr = numero(m.group(5)), pa = numero(m.group(6));
                        String adv = Math.abs(c * pr - pa) > Math.max(1.0, pa * 0.01)
                                ? "cantidad × precio = " + String.format(Locale.US, "%,.2f", c * pr) + " no cuadra con el parcial" : null;
                        l.insumos.add(new Insumo(m.group(1), m.group(2).strip(), m.group(3), tipo, c, pr, pa, adv));
                        continue;
                    }
                    if (linea.matches("^\\d{10}\\s.*"))
                        l.fallidos.add(new LinkedHashMap<>(Map.of("texto", linea.substring(0, Math.min(90, linea.length())),
                                "error", "La fila no calza con el formato código/descripción/unidad/cantidad/precio/parcial.")));
                }
            }
        }
        return l;
    }
}
