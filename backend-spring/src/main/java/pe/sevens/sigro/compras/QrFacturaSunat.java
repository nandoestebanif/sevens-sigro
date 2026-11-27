package pe.sevens.sigro.compras;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import pe.sevens.sigro.comun.R;

/**
 * HU-73 · El QR de la factura electrónica de SUNAT, leído según la sección 2 de
 * {@code docs/contratos/hu-73-escanear-factura.md}. El mismo QR debe dar lo mismo aquí y en la app
 * ({@code QrFactura.kt}): los dos lados se prueban con {@code docs/contratos/hu-73-qr-casos.json}.
 *
 * <p>Campos separados por {@code |}: RUC del emisor, tipo, serie, número, IGV, total, fecha de emisión, tipo de documento
 * del adquirente, documento del adquirente y, desde el décimo, resumen o firma (se ignoran). Lo que no cumple el
 * formato no se adivina: {@link #leer} devuelve {@code null}.
 */
public record QrFacturaSunat(String rucEmisor, String tipoComprobante, String numeroComprobante, BigDecimal igv, BigDecimal total,
                             LocalDate fecha, String adquirenteTipo, String adquirenteDocumento) {

    /** Los nueve campos que trae todo comprobante; del décimo en adelante no se leen. */
    static final int CAMPOS = 9;

    static final Pattern RUC = Pattern.compile("^[0-9]{11}$");
    static final Pattern SERIE = Pattern.compile("^[A-Za-z0-9]{4}$");
    static final Pattern NUMERO = Pattern.compile("^[0-9]{1,8}$");
    static final Pattern MONTO = Pattern.compile("^[0-9]+(\\.[0-9]+)?$");
    static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    /** Catálogo 01 de SUNAT: solo factura y boleta se reciben (las notas 07/08 no son una compra). */
    static final Map<String, String> TIPOS = Map.of("01", "factura", "03", "boleta");

    /** Tipo de documento del adquirente que es un RUC (catálogo 06 de SUNAT). */
    public static final String ADQUIRENTE_RUC = "6";

    /** El QR leído, o {@code null} si no es un QR de comprobante que se pueda recibir (→ 422 qr_no_reconocido). */
    public static QrFacturaSunat leer(String texto) {
        if (texto == null) return null;
        String[] c = texto.split("\\|", -1);
        if (c.length < CAMPOS) return null;
        for (int i = 0; i < c.length; i++) c[i] = c[i].strip();

        String ruc = c[0];
        if (!RUC.matcher(ruc).matches() || !Dominio.rucValido(ruc)) return null;
        String tipo = TIPOS.get(c[1]);
        if (tipo == null) return null;
        if (!SERIE.matcher(c[2]).matches() || !NUMERO.matcher(c[3]).matches()) return null;
        BigDecimal igv = monto(c[4]), total = monto(c[5]);
        if (igv == null || total == null) return null;
        LocalDate fecha = fecha(c[6]);
        if (fecha == null) return null;
        return new QrFacturaSunat(ruc, tipo, numeroComprobante(c[2], c[3]), igv, total, fecha, c[7], c[8]);
    }

    /** {@code F001} + {@code 00001234} → {@code F001-1234}: serie en mayúsculas y el número sin ceros a la izquierda. */
    public static String numeroComprobante(String serie, String numero) {
        String n = numero.replaceFirst("^0+", "");
        return serie.toUpperCase(Locale.ROOT) + "-" + (n.isEmpty() ? "0" : n);
    }

    static BigDecimal monto(String s) {
        return MONTO.matcher(s).matches() ? new BigDecimal(s) : null;
    }

    static LocalDate fecha(String s) {
        for (DateTimeFormatter f : new DateTimeFormatter[] {ISO, DMY}) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException e) {
                // se prueba el otro formato
            }
        }
        return null;
    }

    /** ¿El comprobante está emitido al RUC {@code ruc}? (adquirente tipo 6 con ese número). */
    public boolean emitidoA(String ruc) {
        return ADQUIRENTE_RUC.equals(adquirenteTipo) && ruc.equals(adquirenteDocumento);
    }

    /** El bloque {@code comprobante} de la respuesta (sección 3.1): todo como texto, la fecha en ISO. */
    public Map<String, Object> aMapa() {
        return R.mapa("ruc_emisor", rucEmisor, "tipo_comprobante", tipoComprobante, "numero_comprobante", numeroComprobante,
                "igv", igv.toPlainString(), "total", total.toPlainString(), "fecha", fecha.toString(),
                "adquirente_tipo", adquirenteTipo, "adquirente_documento", adquirenteDocumento);
    }

    /** Nunca el QR entero ni sus montos: solo lo que identifica el comprobante (criterio 8). */
    @Override
    public String toString() {
        return "QrFacturaSunat[" + rucEmisor + " " + numeroComprobante + "]";
    }
}
