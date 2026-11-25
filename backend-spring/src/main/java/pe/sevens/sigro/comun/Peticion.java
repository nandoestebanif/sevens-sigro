package pe.sevens.sigro.comun;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Parámetros de consulta con las reglas de FastAPI (Query): tipos, límites y el error «campo: motivo». */
public final class Peticion {
    private Peticion() {}

    public static HttpServletRequest req() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }

    public static HttpServletResponse resp() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();
    }

    static ErrorSigro error(String campo, String msg) { return new ErrorSigro(422, "datos_invalidos", campo + ": " + msg); }

    public static String texto(String n) { return req().getParameter(n); }

    public static String texto(String n, Integer min, Integer max) {
        String v = req().getParameter(n);
        if (v == null) return null;
        int l = v.codePointCount(0, v.length());
        if (min != null && l < min) throw error(n, "String should have at least " + min + " character" + (min == 1 ? "" : "s"));
        if (max != null && l > max) throw error(n, "String should have at most " + max + " character" + (max == 1 ? "" : "s"));
        return v;
    }

    public static String requerido(String n) {
        String v = req().getParameter(n);
        if (v == null) throw error(n, "Field required");
        return v;
    }

    public static Long entero(String n, Long ge, Long le) {
        String v = req().getParameter(n);
        if (v == null) return null;
        return enteroDe(n, v, ge, le);
    }

    public static Long enteroDe(String n, String v, Long ge, Long le) {
        BigInteger b = Esquema.aEntero(v);
        if (b == null) throw error(n, "Input should be a valid integer, unable to parse string as an integer");
        long x = b.longValue();
        if (ge != null && x < ge) throw error(n, "Input should be greater than or equal to " + ge);
        if (le != null && x > le) throw error(n, "Input should be less than or equal to " + le);
        return x;
    }

    public static LocalDate fecha(String n) {
        String v = req().getParameter(n);
        if (v == null) return null;
        LocalDate f = Esquema.aFecha(v);
        if (f == null) throw error(n, "Input should be a valid date or datetime, input is too short");
        return f;
    }

    public static Boolean bool(String n) {
        String v = req().getParameter(n);
        if (v == null) return null;
        Boolean b = Esquema.aBool(v);
        if (b == null) throw error(n, "Input should be a valid boolean, unable to interpret input");
        return b;
    }

    public static BigDecimal decimal(String n) {
        String v = req().getParameter(n);
        if (v == null) return null;
        var d = Esquema.aDecimal(Json.nodos().textNode(v));
        if (d == null) throw error(n, "Input should be a valid decimal");
        return d;
    }

    public static String uno(String n, String defecto, String... ops) {
        String v = req().getParameter(n);
        if (v == null) return defecto;
        if (!List.of(ops).contains(v)) {
            var q = java.util.Arrays.stream(ops).map(o -> "'" + o + "'").toList();
            String l = q.size() == 1 ? q.get(0) : String.join(", ", q.subList(0, q.size() - 1)) + " or " + q.get(q.size() - 1);
            throw error(n, "Input should be " + l);
        }
        return v;
    }

    public static void cabecera(String n, String v) { resp().setHeader(n, v); }
}
