package pe.sevens.sigro.comun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Contrato de entrada de una ruta, con las mismas reglas que los modelos Pydantic del servicio original:
 * campos extra prohibidos, textos recortados, valores por defecto y el primer error como «campo: motivo».
 */
public final class Esquema {
    public enum Tipo { UUID, TEXTO, ENTERO, DECIMAL, FECHA, BOOLEANO, LISTA, OBJETO, CUALQUIERA }

    public static final class Campo {
        final String nombre;
        final Tipo tipo;
        boolean requerido = true;
        Object defecto;
        Integer minLargo, maxLargo;
        Pattern patron;
        String patronTexto;
        BigDecimal ge, gt, le, lt;
        LocalDate fechaGe, fechaLe;
        List<String> opciones;
        Esquema item;          // para LISTA de objetos u OBJETO
        Tipo itemTipo;         // para LISTA de escalares
        Campo itemCampo;
        Integer maxDigitos, decimales;
        boolean nulable;

        Campo(String nombre, Tipo tipo) { this.nombre = nombre; this.tipo = tipo; }

        public Campo opcional() { requerido = false; nulable = true; return this; }
        public Campo defecto(Object v) { requerido = false; defecto = v; return this; }
        public Campo nulable() { nulable = true; return this; }
        public Campo min(int n) { minLargo = n; return this; }
        public Campo max(int n) { maxLargo = n; return this; }
        public Campo patron(String p) { patron = Pattern.compile(p); patronTexto = p; return this; }
        public Campo ge(Object v) { if (v instanceof LocalDate d) fechaGe = d; else ge = new BigDecimal(v.toString()); return this; }
        public Campo le(Object v) { if (v instanceof LocalDate d) fechaLe = d; else le = new BigDecimal(v.toString()); return this; }
        public Campo gt(Object v) { gt = new BigDecimal(v.toString()); return this; }
        public Campo lt(Object v) { lt = new BigDecimal(v.toString()); return this; }
        public Campo uno(String... ops) { opciones = List.of(ops); return this; }
        public Campo digitos(int max, int dec) { maxDigitos = max; decimales = dec; return this; }
    }

    public static Campo texto(String n) { return new Campo(n, Tipo.TEXTO); }
    public static Campo uuid(String n) { return new Campo(n, Tipo.UUID); }
    public static Campo entero(String n) { return new Campo(n, Tipo.ENTERO); }
    public static Campo decimal(String n) { return new Campo(n, Tipo.DECIMAL); }
    public static Campo fecha(String n) { return new Campo(n, Tipo.FECHA); }
    public static Campo booleano(String n) { return new Campo(n, Tipo.BOOLEANO); }
    public static Campo cualquiera(String n) { return new Campo(n, Tipo.CUALQUIERA); }
    public static Campo objeto(String n, Esquema e) { var c = new Campo(n, Tipo.OBJETO); c.item = e; return c; }
    public static Campo lista(String n, Esquema e) { var c = new Campo(n, Tipo.LISTA); c.item = e; return c; }
    public static Campo listaDe(String n, Campo item) { var c = new Campo(n, Tipo.LISTA); c.itemCampo = item; return c; }

    /** Error de validación con la ubicación al estilo de Pydantic (campo.0.sub). */
    public static final class Invalido extends RuntimeException {
        public final String ubicacion;
        public Invalido(String ubicacion, String mensaje) { super(mensaje); this.ubicacion = ubicacion; }
        public ErrorSigro comoError() {
            String m = (ubicacion + ": " + getMessage());
            m = m.replaceAll("^[: ]+", "").replaceAll("[: ]+$", "");
            return new ErrorSigro(422, "datos_invalidos", m);
        }
    }

    private final List<Campo> campos;
    private boolean estricto = true;
    private boolean recortar = true;
    private final List<UnaryOperator<ObjectNode>> antes = new ArrayList<>();
    private final List<Consumer<Datos>> despues = new ArrayList<>();

    private Esquema(List<Campo> campos) { this.campos = campos; }

    public static Esquema de(Campo... c) { return new Esquema(new ArrayList<>(List.of(c))); }
    public Esquema permisivo() { estricto = false; return this; }
    public Esquema sinRecortar() { recortar = false; return this; }
    /** Como model_validator(mode="before"). */
    public Esquema antes(UnaryOperator<ObjectNode> f) { antes.add(f); return this; }
    /** Como model_validator(mode="after"): lanzar {@link #error(String)}. */
    public Esquema despues(Consumer<Datos> f) { despues.add(f); return this; }
    /** Hereda los campos de otro esquema y agrega estos (como una subclase de Pydantic). */
    public Esquema mas(Campo... extra) {
        var e = new Esquema(new ArrayList<>(campos));
        e.campos.addAll(List.of(extra));
        e.estricto = estricto; e.recortar = recortar;
        e.antes.addAll(antes); e.despues.addAll(despues);
        return e;
    }

    public static Invalido error(String mensaje) { return new Invalido("", "Value error, " + mensaje); }
    public static Invalido errorEn(String campo, String mensaje) { return new Invalido(campo, "Value error, " + mensaje); }

    public Datos validar(JsonNode cuerpo) {
        try {
            return validar(cuerpo, "");
        } catch (Invalido e) {
            throw e.comoError();
        }
    }

    Datos validar(JsonNode cuerpo, String prefijo) {
        if (cuerpo == null || cuerpo.isMissingNode() || cuerpo.isNull()) {
            if (prefijo.isEmpty()) throw new Invalido("", "Field required");
        }
        if (cuerpo == null || !cuerpo.isObject()) throw new Invalido(prefijo, "Input should be a valid dictionary or object to extract fields from");
        ObjectNode obj = ((ObjectNode) cuerpo).deepCopy();
        for (var f : antes) obj = f.apply(obj);
        var valores = new LinkedHashMap<String, Object>();
        var enviados = new LinkedHashSet<String>();
        Set<String> conocidos = new HashSet<>();
        for (Campo c : campos) {
            conocidos.add(c.nombre);
            String loc = prefijo.isEmpty() ? c.nombre : prefijo + "." + c.nombre;
            JsonNode v = obj.get(c.nombre);
            if (v == null) {
                if (c.requerido) throw new Invalido(loc, "Field required");
                valores.put(c.nombre, c.defecto);
                continue;
            }
            enviados.add(c.nombre);
            valores.put(c.nombre, convertir(c, v, loc));
        }
        if (estricto) {
            var it = obj.fieldNames();
            while (it.hasNext()) {
                String n = it.next();
                if (!conocidos.contains(n)) throw new Invalido(prefijo.isEmpty() ? n : prefijo + "." + n, "Extra inputs are not permitted");
            }
        }
        var d = new Datos(valores, enviados);
        for (var f : despues) {
            try {
                f.accept(d);
            } catch (Invalido e) {
                if (prefijo.isEmpty()) throw e;
                throw new Invalido(e.ubicacion.isEmpty() ? prefijo : prefijo + "." + e.ubicacion, e.getMessage());
            }
        }
        return d;
    }

    private Object convertir(Campo c, JsonNode v, String loc) {
        if (v.isNull()) {
            if (c.nulable || c.tipo == Tipo.CUALQUIERA) return null;
            throw new Invalido(loc, mensajeTipo(c.tipo));
        }
        switch (c.tipo) {
            case CUALQUIERA:
                return Json.aJava(v);
            case UUID: {
                if (!v.isTextual()) throw new Invalido(loc, "UUID input should be a string, bytes or UUID object");
                java.util.UUID u = aUuid(v.textValue());
                if (u == null) throw new Invalido(loc, "Input should be a valid UUID, invalid character: expected an optional prefix of `urn:uuid:` followed by [0-9a-fA-F-], found `" + v.textValue() + "`");
                return u;
            }
            case TEXTO: {
                if (!v.isTextual()) throw new Invalido(loc, "Input should be a valid string");
                String s = v.textValue();
                if (recortar) s = s.strip();
                if (c.minLargo != null && s.codePointCount(0, s.length()) < c.minLargo)
                    throw new Invalido(loc, "String should have at least " + c.minLargo + " character" + (c.minLargo == 1 ? "" : "s"));
                if (c.maxLargo != null && s.codePointCount(0, s.length()) > c.maxLargo)
                    throw new Invalido(loc, "String should have at most " + c.maxLargo + " character" + (c.maxLargo == 1 ? "" : "s"));
                if (c.patron != null && !c.patron.matcher(s).find())
                    throw new Invalido(loc, "String should match pattern '" + c.patronTexto + "'");
                if (c.opciones != null && !c.opciones.contains(s)) throw new Invalido(loc, "Input should be " + literales(c.opciones));
                return s;
            }
            case ENTERO: {
                BigInteger n = aEntero(v);
                if (n == null) throw new Invalido(loc, v.isTextual() ? "Input should be a valid integer, unable to parse string as an integer" : "Input should be a valid integer");
                limites(c, new BigDecimal(n), loc);
                return n.bitLength() < 32 ? (Object) n.intValue() : (Object) n.longValue();
            }
            case DECIMAL: {
                BigDecimal d = aDecimal(v);
                if (d == null) throw new Invalido(loc, "Input should be a valid decimal");
                limites(c, d, loc);
                if (c.maxDigitos != null) {
                    BigDecimal n = d.stripTrailingZeros();
                    int dec = Math.max(n.scale(), 0);
                    int digitos = n.precision() - n.scale() + dec;
                    if (n.scale() < 0) digitos = n.precision() - n.scale();
                    if (dec > c.decimales) throw new Invalido(loc, "Decimal input should have no more than " + c.decimales + " decimal place" + (c.decimales == 1 ? "" : "s"));
                    if (digitos > c.maxDigitos) throw new Invalido(loc, "Decimal input should have no more than " + c.maxDigitos + " digit" + (c.maxDigitos == 1 ? "" : "s") + " in total");
                    if (digitos - dec > c.maxDigitos - c.decimales) throw new Invalido(loc, "Decimal input should have no more than " + (c.maxDigitos - c.decimales) + " digit" + (c.maxDigitos - c.decimales == 1 ? "" : "s") + " before the decimal point");
                }
                return d;
            }
            case FECHA: {
                LocalDate f = aFecha(v);
                if (f == null) throw new Invalido(loc, v.isTextual() ? "Input should be a valid date or datetime, input is too short" : "Input should be a valid date");
                if (c.fechaGe != null && f.isBefore(c.fechaGe)) throw new Invalido(loc, "Input should be greater than or equal to " + c.fechaGe);
                if (c.fechaLe != null && f.isAfter(c.fechaLe)) throw new Invalido(loc, "Input should be less than or equal to " + c.fechaLe);
                return f;
            }
            case BOOLEANO: {
                Boolean b = aBool(v);
                if (b == null) throw new Invalido(loc, "Input should be a valid boolean");
                return b;
            }
            case OBJETO:
                return c.item.validar(v, loc);
            case LISTA: {
                if (!v.isArray()) throw new Invalido(loc, "Input should be a valid list");
                if (c.minLargo != null && v.size() < c.minLargo)
                    throw new Invalido(loc, "List should have at least " + c.minLargo + " item" + (c.minLargo == 1 ? "" : "s") + " after validation, not " + v.size());
                if (c.maxLargo != null && v.size() > c.maxLargo)
                    throw new Invalido(loc, "List should have at most " + c.maxLargo + " item" + (c.maxLargo == 1 ? "" : "s") + " after validation, not " + v.size());
                var l = new ArrayList<Object>();
                for (int i = 0; i < v.size(); i++) {
                    String li = loc + "." + i;
                    l.add(c.item != null ? c.item.validar(v.get(i), li) : convertir(c.itemCampo, v.get(i), li));
                }
                return l;
            }
        }
        throw new IllegalStateException();
    }

    private static String literales(List<String> ops) {
        var q = ops.stream().map(o -> "'" + o + "'").toList();
        if (q.size() == 1) return q.get(0);
        return String.join(", ", q.subList(0, q.size() - 1)) + " or " + q.get(q.size() - 1);
    }

    private static String mensajeTipo(Tipo t) {
        return switch (t) {
            case TEXTO -> "Input should be a valid string";
            case ENTERO -> "Input should be a valid integer";
            case DECIMAL -> "Decimal input should be an integer, float, string or Decimal object";
            case FECHA -> "Input should be a valid date";
            case BOOLEANO -> "Input should be a valid boolean";
            case LISTA -> "Input should be a valid list";
            default -> "Input should be a valid dictionary or object to extract fields from";
        };
    }

    private static void limites(Campo c, BigDecimal n, String loc) {
        if (c.ge != null && n.compareTo(c.ge) < 0) throw new Invalido(loc, "Input should be greater than or equal to " + num(c.ge));
        if (c.gt != null && n.compareTo(c.gt) <= 0) throw new Invalido(loc, "Input should be greater than " + num(c.gt));
        if (c.le != null && n.compareTo(c.le) > 0) throw new Invalido(loc, "Input should be less than or equal to " + num(c.le));
        if (c.lt != null && n.compareTo(c.lt) >= 0) throw new Invalido(loc, "Input should be less than " + num(c.lt));
    }

    private static String num(BigDecimal d) { return d.stripTrailingZeros().scale() <= 0 ? d.toBigInteger().toString() : d.toPlainString(); }

    // ------------------------------------------------------------ conversiones «lax» como Pydantic
    public static BigInteger aEntero(JsonNode v) {
        if (v.isIntegralNumber()) return v.bigIntegerValue();
        if (v.isFloatingPointNumber()) {
            BigDecimal d = v.decimalValue();
            return d.stripTrailingZeros().scale() <= 0 ? d.toBigIntegerExact() : null;
        }
        if (v.isTextual()) return aEntero(v.textValue());
        return null;
    }

    public static BigInteger aEntero(String s) {
        String t = s.strip().replace("_", "");
        if (!t.matches("[+-]?\\d+")) return null;
        return new BigInteger(t);
    }

    public static BigDecimal aDecimal(JsonNode v) {
        if (v.isFloatingPointNumber()) return Interno.deFlotante(v.doubleValue());   // json de Python: float → Decimal(str(f))
        if (v.isNumber()) return v.decimalValue();
        if (v.isTextual()) {
            try {
                String t = v.textValue().strip();
                if (t.isEmpty() || !t.matches("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")) return null;
                return new BigDecimal(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static java.util.UUID aUuid(String s) {
        String t = s.strip();
        if (t.toLowerCase(Locale.ROOT).startsWith("urn:uuid:")) t = t.substring(9);
        if (t.startsWith("{") && t.endsWith("}")) t = t.substring(1, t.length() - 1);
        String h = t.replace("-", "");
        if (!h.matches("[0-9a-fA-F]{32}")) return null;
        if (t.contains("-") && !t.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) return null;
        return java.util.UUID.fromString(h.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }

    public static LocalDate aFecha(JsonNode v) {
        if (!v.isTextual()) return null;
        return aFecha(v.textValue());
    }

    public static LocalDate aFecha(String s) {
        try {
            if (s.length() == 10) return LocalDate.parse(s);
            return null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static Boolean aBool(JsonNode v) {
        if (v.isBoolean()) return v.booleanValue();
        if (v.isIntegralNumber()) { long n = v.longValue(); return n == 1 ? Boolean.TRUE : n == 0 ? Boolean.FALSE : null; }
        if (v.isTextual()) return aBool(v.textValue());
        return null;
    }

    public static Boolean aBool(String s) {
        return switch (s.strip().toLowerCase(Locale.ROOT)) {
            case "1", "on", "t", "true", "y", "yes" -> Boolean.TRUE;
            case "0", "off", "f", "false", "n", "no" -> Boolean.FALSE;
            default -> null;
        };
    }
}
