package pe.sevens.sigro.comun;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Lo que viaja entre módulos tiene el mismo tipo que tenía entre los microservicios originales, que se hablaban en
 * JSON (jsonable_encoder de FastAPI): un decimal con parte fraccionaria llega como número de punto flotante, uno
 * entero como entero, y las fechas como texto ISO. Mantenerlo igual conserva los mismos redondeos y resultados.
 */
public final class Interno {
    private Interno() {}

    private static final DateTimeFormatter SEG = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @SuppressWarnings("unchecked")
    public static <T> T json(T o) {
        return (T) conv(o);
    }

    private static Object conv(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal d) return d.scale() <= 0 ? (Object) d.toBigInteger().longValue() : (Object) d.doubleValue();
        if (o instanceof Instant i) {
            var t = i.atOffset(ZoneOffset.UTC);
            int micros = t.getNano() / 1000;
            return t.format(SEG) + (micros == 0 ? "" : String.format(".%06d", micros)) + "+00:00";
        }
        if (o instanceof LocalDate d) return d.toString();
        if (o instanceof UUID u) return u.toString();
        if (o instanceof Map<?, ?> m) {
            var r = new LinkedHashMap<String, Object>();
            m.forEach((k, v) -> r.put(String.valueOf(k), conv(v)));
            return r;
        }
        if (o instanceof Collection<?> c) {
            var r = new ArrayList<Object>();
            for (Object x : c) r.add(conv(x));
            return r;
        }
        return o;
    }

    /** Decimal(str(x)) de Python para un valor que llegó por JSON (entero o flotante). */
    public static BigDecimal dec(Object x) {
        if (x == null) return null;
        if (x instanceof BigDecimal b) return b;
        if (x instanceof Long || x instanceof Integer) return BigDecimal.valueOf(((Number) x).longValue());
        if (x instanceof Double d) return deFlotante(d);
        if (x instanceof Number n) return deFlotante(n.doubleValue());
        return new BigDecimal(x.toString());
    }

    /** str(float) de Python: dígitos mínimos; «12.0» para los enteros, sin ceros de más en el resto. */
    public static BigDecimal deFlotante(double d) {
        BigDecimal b = new BigDecimal(Double.toString(d));
        BigDecimal s = b.stripTrailingZeros();
        if (s.scale() <= 0) return s.setScale(1);
        return s;
    }
}
