package pe.sevens.sigro.comun;

import java.math.BigDecimal;
import java.util.*;

/** Salidas: elige y ordena los campos de una fila como lo hacía el response_model de cada ruta. */
public final class R {
    private R() {}

    public static Map<String, Object> de(Map<String, Object> fila, String... campos) {
        if (fila == null) return null;
        var m = new LinkedHashMap<String, Object>();
        for (String c : campos) {
            int i = c.indexOf('=');
            if (i > 0) { String n = c.substring(0, i); m.put(n, fila.containsKey(n) && fila.get(n) != null ? fila.get(n) : c.substring(i + 1)); }
            else m.put(c, fila.get(c));
        }
        return m;
    }

    public static List<Map<String, Object>> todos(List<Map<String, Object>> filas, String... campos) {
        var l = new ArrayList<Map<String, Object>>();
        for (var f : filas) l.add(de(f, campos));
        return l;
    }

    public static Map<String, Object> mapa(Object... pares) { return P.mapa(pares); }

    /** Para campos `float` de Pydantic: número, no texto. */
    public static Double flotante(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        return Double.valueOf(o.toString());
    }

    public static BigDecimal dec(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(o.toString());
    }

    public static Long lng(Object o) { return o == null ? null : ((Number) o).longValue(); }
    public static Integer in(Object o) { return o == null ? null : ((Number) o).intValue(); }
}
