package pe.sevens.sigro.almacen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import pe.sevens.sigro.comun.ErrorSigro;
import pe.sevens.sigro.comun.Interno;
import pe.sevens.sigro.compras.Dominio;

/** Reglas de almacén que se prueban sin base (port de app/modulos/almacen/dominio.py). */
public final class Reglas {
    private Reglas() {}

    public static final ZoneOffset LIMA = ZoneOffset.ofHours(-5);       // Perú no tiene horario de verano
    public static final Map<String, String> CLASE = Map.of("ingreso_compra", "ingreso", "ingreso_sin_compra", "ingreso", "salida", "consumo",
            "traslado_salida", "traslado", "traslado_ingreso", "traslado", "ajuste_conteo", "ajuste");
    public static final Map<String, String> FAMILIAS = Map.of("material", "material", "equipo", "equipo");
    public static final String[] ORIGENES = {"saldo_otra_obra", "devolucion_cuadrilla", "prestamo", "sobrante", "otro"};

    public static BigDecimal q4(BigDecimal x) { return x.setScale(4, RoundingMode.HALF_UP); }
    /** quantize sin redondeo indicado: el del contexto de Python (al par). */
    public static BigDecimal q4par(BigDecimal x) { return x.setScale(4, RoundingMode.HALF_EVEN); }

    public static LocalDate fechaDelHecho(Long horaMs, Instant ahora) {
        Instant momento = ahora;
        if (horaMs != null) {
            Instant disp = Instant.ofEpochMilli(horaMs);
            momento = disp.isBefore(ahora) ? disp : ahora;
        }
        return momento.atOffset(LIMA).toLocalDate();
    }

    public static LocalDate hoyLima() { return LocalDate.now(ZoneId.of("America/Lima")); }

    static LocalDate aFecha(Object v) { return v instanceof LocalDate d ? d : LocalDate.parse(String.valueOf(v).substring(0, 10)); }

    public static Map<String, Object> periodoDe(List<Map<String, Object>> periodos, LocalDate dia) {
        for (var p : periodos) if (!aFecha(p.get("fecha_inicio")).isAfter(dia) && !dia.isAfter(aFecha(p.get("fecha_fin")))) return p;
        return null;
    }

    public static Map<String, Object> resolverPeriodo(List<Map<String, Object>> periodos, LocalDate dia, boolean esReverso) {
        if (periodos.isEmpty()) throw new ErrorSigro(422, "fuera_de_plazo", "La obra no tiene periodos configurados.");
        var p = periodoDe(periodos, dia);
        if (p == null) {
            LocalDate ini = periodos.stream().map(x -> aFecha(x.get("fecha_inicio"))).min(Comparator.naturalOrder()).get();
            LocalDate fin = periodos.stream().map(x -> aFecha(x.get("fecha_fin"))).max(Comparator.naturalOrder()).get();
            throw new ErrorSigro(422, "fuera_de_plazo", "La fecha del movimiento (" + dia + ") está fuera del plazo de la obra (" + ini + " al " + fin + ").");
        }
        if ("cerrado".equals(p.get("estado")) && !esReverso)
            throw new ErrorSigro(409, "periodo_cerrado", "El periodo " + p.get("numero") + " (" + p.get("fecha_inicio") + " al " + p.get("fecha_fin")
                    + ") está cerrado: no admite movimientos nuevos, solo correcciones con reverso.");
        if (dia.isAfter(hoyLima()))
            throw new ErrorSigro(422, "fecha_futura", "La fecha del movimiento (" + dia + ") es futura: un ingreso o salida se registra cuando ocurre.");
        return p;
    }

    static ErrorSigro unidadInvalida(Map<String, Object> ins) {
        return new ErrorSigro(422, "unidad_invalida", ins.get("codigo") + " se registra en " + ins.get("unidad_s10") + " (o en " + ins.get("unidad_compra") + ", su unidad de compra).");
    }

    public static BigDecimal aUnidadS10(BigDecimal cantidad, String unidad, Map<String, Object> ins) {
        if (unidad == null || unidad.equals(ins.get("unidad_s10"))) return cantidad;
        if (unidad.equals(ins.get("unidad_compra"))) return q4(cantidad.multiply(Interno.dec(ins.get("factor_conversion")), Dominio.PY));
        throw unidadInvalida(ins);
    }

    public static BigDecimal aCostoS10(BigDecimal costo, String unidad, Map<String, Object> ins) {
        if (unidad == null || unidad.equals(ins.get("unidad_s10"))) return costo;
        if (unidad.equals(ins.get("unidad_compra"))) return costo.divide(Interno.dec(ins.get("factor_conversion")), Dominio.PY).setScale(6, RoundingMode.HALF_UP);
        throw unidadInvalida(ins);
    }

    public static BigDecimal costoRecepcion(BigDecimal precio, BigDecimal factorSinIgv, BigDecimal factorConversion) {
        return precio.multiply(factorSinIgv, Dominio.PY).divide(factorConversion, Dominio.PY).setScale(6, RoundingMode.HALF_UP);
    }

    /** uuid.uuid5(base, "a:b:c") de Python: el mismo identificador en cada reenvío (regla 3). */
    public static UUID uuidDerivado(UUID base, Object... partes) {
        var nombre = new StringJoiner(":");
        for (Object p : partes) nombre.add(String.valueOf(p));
        try {
            var sha = MessageDigest.getInstance("SHA-1");
            var bb = java.nio.ByteBuffer.wrap(new byte[16]);
            bb.putLong(base.getMostSignificantBits()).putLong(base.getLeastSignificantBits());
            sha.update(bb.array());
            sha.update(nombre.toString().getBytes(StandardCharsets.UTF_8));
            byte[] h = Arrays.copyOf(sha.digest(), 16);
            h[6] = (byte) ((h[6] & 0x0f) | 0x50);
            h[8] = (byte) ((h[8] & 0x3f) | 0x80);
            var r = java.nio.ByteBuffer.wrap(h);
            return new UUID(r.getLong(), r.getLong());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static BigDecimal excesoSobreRequerido(BigDecimal requerido, BigDecimal yaRecibido, BigDecimal conEsta) {
        if (requerido == null) return null;
        BigDecimal x = yaRecibido.add(conEsta, Dominio.PY).subtract(requerido, Dominio.PY);
        return q4par(x.signum() > 0 ? x : BigDecimal.ZERO);
    }

    /** Herramientas: lo que se puede prestar = saldo del almacén − lo prestado sin devolver (nunca negativo). */
    public static BigDecimal disponibleParaPrestar(BigDecimal saldo, BigDecimal prestadoSinDevolver) {
        BigDecimal d = saldo.subtract(prestadoSinDevolver);
        return d.signum() > 0 ? d : BigDecimal.ZERO;
    }

    /** str.capitalize() de Python: primera letra en mayúscula y el resto en minúscula. */
    public static String capitalizar(String s) {
        if (s.isEmpty()) return s;
        int p = s.offsetByCodePoints(0, 1);
        return s.substring(0, p).toUpperCase(Locale.ROOT) + s.substring(p).toLowerCase(Locale.ROOT);
    }
}
