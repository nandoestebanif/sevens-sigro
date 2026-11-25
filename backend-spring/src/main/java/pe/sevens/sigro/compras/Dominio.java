package pe.sevens.sigro.compras;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Pattern;

/** Lógica de dominio de compras, sin base de datos (port de app/modulos/compras/dominio.py). */
public final class Dominio {
    private Dominio() {}

    /** El contexto decimal por defecto de Python: 28 dígitos, redondeo al par. */
    public static final MathContext PY = new MathContext(28, RoundingMode.HALF_EVEN);
    static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    static final Pattern FORMATO = Pattern.compile("^(10|15|16|17|20)[0-9]{9}$");
    public static final int MAX_COMPROBANTE_BYTES = 10 * 1024 * 1024;

    public static int digitoVerificador(String diez) {
        if (!diez.matches("[0-9]{10}")) throw new IllegalArgumentException("se esperan exactamente 10 dígitos");
        int suma = 0;
        for (int i = 0; i < 10; i++) suma += (diez.charAt(i) - '0') * PESOS[i];
        return (11 - suma % 11) % 10;
    }

    public static boolean rucValido(String ruc) {
        if (!FORMATO.matcher(ruc).find()) return false;
        return digitoVerificador(ruc.substring(0, 10)) == ruc.charAt(10) - '0';
    }

    /** HU-33: promedio por unidad del S10 × factor = precio habitual por unidad de compra (6 decimales). */
    public static BigDecimal referenciaEnUnidadDeCompra(BigDecimal promedioS10, BigDecimal factor) {
        if (promedioS10 == null) return null;
        return promedioS10.multiply(factor, PY).setScale(6, RoundingMode.HALF_UP);
    }

    public static BigDecimal desviacionPct(BigDecimal precioSinIgv, BigDecimal referencia) {
        if (referencia == null || referencia.signum() <= 0) return null;
        return precioSinIgv.divide(referencia, PY).subtract(BigDecimal.ONE, PY).multiply(BigDecimal.valueOf(100), PY).setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean fueraDeLoHabitual(BigDecimal desviacion, BigDecimal limitePct) {
        return desviacion != null && desviacion.abs().compareTo(limitePct) > 0;
    }

    public record Clasificacion(String estado, String alerta, String advertencia) {}

    /** HU-31: qué dice SUNAT del proveedor. Avisa, no bloquea (D12). */
    public static Clasificacion clasificar(String ruc, pe.sevens.sigro.comun.Integraciones.RucSunat r) {
        if (ruc.length() == 8) return new Clasificacion("sin_validar", null, "Es un DNI (persona natural): queda sin validar; solo los RUC se validan en SUNAT.");
        if (!rucValido(ruc)) return new Clasificacion("sin_validar", null, "El dígito verificador del RUC no coincide: revisa que esté bien escrito.");
        if (r == null) return new Clasificacion("sin_validar", null, "No se pudo consultar a SUNAT; el proveedor queda por validar y se puede reintentar.");
        if (r.aptoParaComprar()) return new Clasificacion("validado", null, null);
        String alerta = r.estadoSunat() + " / " + r.condicion();
        return new Clasificacion("con_alerta_sunat", alerta, "SUNAT lo informa " + alerta + ": puedes comprarle, pero la compra queda marcada para el residente.");
    }

    /** El tipo lo dicen los primeros bytes, no la extensión: un .exe renombrado a .pdf no pasa. */
    public static String tipoReal(byte[] c) {
        if (empieza(c, new byte[]{'%', 'P', 'D', 'F', '-'})) return "application/pdf";
        if (empieza(c, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) return "image/jpeg";
        if (empieza(c, new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'})) return "image/png";
        return null;
    }

    static boolean empieza(byte[] c, byte[] f) {
        if (c.length < f.length) return false;
        for (int i = 0; i < f.length; i++) if (c[i] != f[i]) return false;
        return true;
    }

    /** Solo el nombre (sin rutas), sin caracteres de control ni <>:"|?*, con un máximo de 150. */
    public static String nombreSeguro(String nombre, String mime) {
        String base = (nombre == null ? "" : nombre).replace("\\", "/");
        base = base.substring(base.lastIndexOf('/') + 1);
        var sb = new StringBuilder();
        base.codePoints().forEach(cp -> {
            if (imprimible(cp) && "<>:\"|?*".indexOf(cp) < 0) sb.appendCodePoint(cp);
        });
        base = sb.toString().replaceAll("^[ .]+|[ .]+$", "");
        if (base.isEmpty()) base = "comprobante" + switch (mime) { case "application/pdf" -> ".pdf"; case "image/jpeg" -> ".jpg"; default -> ".png"; };
        if (base.codePointCount(0, base.length()) > 150) {
            int p = base.lastIndexOf('.');
            String ext = p >= 0 ? base.substring(p + 1) : "";
            if (p >= 0 && ext.length() <= 5) base = base.substring(0, 150 - ext.length() - 1) + "." + ext;
            else base = base.substring(0, 150);
        }
        return base;
    }

    /** str.isprintable() de Python: nada de controles, separadores salvo el espacio, ni sin asignar. */
    static boolean imprimible(int cp) {
        if (cp == ' ') return true;
        int t = Character.getType(cp);
        return !List.of((int) Character.CONTROL, (int) Character.FORMAT, (int) Character.SURROGATE, (int) Character.PRIVATE_USE,
                (int) Character.UNASSIGNED, (int) Character.LINE_SEPARATOR, (int) Character.PARAGRAPH_SEPARATOR, (int) Character.SPACE_SEPARATOR).contains(t);
    }
}
