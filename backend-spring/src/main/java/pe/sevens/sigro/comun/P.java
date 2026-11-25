package pe.sevens.sigro.comun;

import java.sql.Types;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Parámetros con nombre para SQL: P.de("u", 1, "p", 2). */
public final class P {
    private P() {}

    public static MapSqlParameterSource de(Object... pares) {
        var m = new MapSqlParameterSource();
        for (int i = 0; i < pares.length; i += 2) m.addValue((String) pares[i], pares[i + 1]);
        return m;
    }

    public static Map<String, Object> mapa(Object... pares) {
        var m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pares.length; i += 2) m.put((String) pares[i], pares[i + 1]);
        return m;
    }

    /** Arreglo de PostgreSQL para `= ANY(:x)` (no se expande como lista IN). */
    public static SqlParameterValue enteros(Collection<? extends Number> c) {
        Long[] a = c.stream().map(Number::longValue).toArray(Long[]::new);
        return new SqlParameterValue(Types.ARRAY, a);
    }

    public static SqlParameterValue textos(Collection<String> c) {
        return new SqlParameterValue(Types.ARRAY, c.toArray(String[]::new));
    }

    /** jsonb a partir de un objeto Java. */
    public static String json(Object o) { return Json.texto(o); }
}
