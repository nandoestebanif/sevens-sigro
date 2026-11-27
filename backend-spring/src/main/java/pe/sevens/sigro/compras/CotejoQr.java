package pe.sevens.sigro.compras;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HU-73 · Reglas del cotejo entre el QR de la factura y las compras de la obra (sección 3.1 del contrato). Kotlin puro
 * en la app ({@code CotejoQr.kt}) y Java aquí: mismas entradas, mismo {@link Resultado}. No toca la base: recibe las
 * compras candidatas y responde. Solo consulta y compara; no registra nada.
 *
 * <p>El orden de las reglas es el del contrato: primero si el comprobante es para SEVENS (si no, ni se busca la
 * compra), luego si existe la compra, si está anulada, si ya se recibió todo y, al final, si los importes cuadran.
 */
public final class CotejoQr {
    private CotejoQr() {}

    /** Diferencias de hasta un céntimo son redondeo, no un error del comprobante. */
    public static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public enum Resultado {
        RECIBIR, RECIBIR_CON_AVISOS, YA_RECIBIDA, ANULADA, SIN_COMPRA, NO_ES_DE_SEVENS;

        /** Como viaja en el JSON: {@code recibir_con_avisos}. */
        public String valor() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Lo que hace falta de una compra para cotejarla. {@code moneda} y {@code fecha} pueden faltar (se asume PEN / no se compara). */
    public record Compra(Long id, String proveedorRuc, String proveedorRazonSocial, String tipoComprobante, String numeroComprobante,
                         BigDecimal total, BigDecimal igv, LocalDate fecha, String moneda, String estado, String motivoAnulacion,
                         boolean quedaPorRecibir) {
        public Compra conQuedaPorRecibir(boolean q) {
            return new Compra(id, proveedorRuc, proveedorRazonSocial, tipoComprobante, numeroComprobante, total, igv, fecha, moneda, estado,
                    motivoAnulacion, q);
        }

        boolean anulada() { return "anulada".equals(estado); }
    }

    /** {@code compra} es {@code null} en {@code sin_compra} y {@code no_es_de_sevens}. */
    public record Cotejo(Resultado resultado, Compra compra, List<String> avisos) {}

    /** El cotejo completo: el QR contra las compras candidatas de la obra. */
    public static Cotejo cotejar(QrFacturaSunat qr, Collection<Compra> compras, String rucEmpresa) {
        if (!qr.emitidoA(rucEmpresa)) return new Cotejo(Resultado.NO_ES_DE_SEVENS, null, List.of());
        Compra c = buscar(qr, compras).orElse(null);
        if (c == null) return new Cotejo(Resultado.SIN_COMPRA, null, List.of());
        if (c.anulada()) {
            // el número del QR ya normalizado (no el guardado, que puede traer ceros) y sin doble punto final
            String m = c.motivoAnulacion() == null ? "" : c.motivoAnulacion().strip().replaceAll("\\.+$", "").strip();
            return new Cotejo(Resultado.ANULADA, c, List.of("La compra " + qr.numeroComprobante() + " está anulada" + (m.isEmpty() ? "" : ": " + m) + "."));
        }
        if (!c.quedaPorRecibir()) return new Cotejo(Resultado.YA_RECIBIDA, c, List.of());
        List<String> avisos = diferencias(qr, c);
        return new Cotejo(avisos.isEmpty() ? Resultado.RECIBIR : Resultado.RECIBIR_CON_AVISOS, c, avisos);
    }

    /**
     * La compra de ese proveedor (RUC del emisor) con ese comprobante. El número se compara normalizado
     * ({@code F001-00001234} = {@code F001-1234}). Si hubiera más de una, gana la del mismo tipo de comprobante, luego la
     * vigente sobre la anulada y luego la más reciente.
     */
    public static Optional<Compra> buscar(QrFacturaSunat qr, Collection<Compra> compras) {
        return compras.stream()
                .filter(c -> qr.rucEmisor().equals(c.proveedorRuc()) && qr.numeroComprobante().equals(normalizar(c.numeroComprobante())))
                .min(Comparator.<Compra>comparingInt(c -> qr.tipoComprobante().equals(c.tipoComprobante()) ? 0 : 1)
                        .thenComparingInt(c -> c.anulada() ? 1 : 0)
                        .thenComparing(c -> c.id() == null ? 0L : -c.id()));
    }

    /** El número guardado en la compra, con la misma normalización del QR. Lo que no tenga forma serie-número queda tal cual. */
    static String normalizar(String numero) {
        if (numero == null) return null;
        int g = numero.indexOf('-');
        if (g < 0) return numero.strip().toUpperCase(Locale.ROOT);
        String serie = numero.substring(0, g).strip(), n = numero.substring(g + 1).strip();
        return n.matches("[0-9]+") ? QrFacturaSunat.numeroComprobante(serie, n) : numero.strip().toUpperCase(Locale.ROOT);
    }

    /** Total, IGV, fecha y tipo del QR contra lo registrado. Texto listo para mostrar (sección 3.1). */
    static List<String> diferencias(QrFacturaSunat qr, Compra c) {
        var avisos = new ArrayList<String>();
        String m = simbolo(c.moneda());
        if (difiere(qr.total(), c.total()))
            avisos.add("El total del QR (" + m + " " + soles(qr.total()) + ") difiere en " + m + " " + soles(qr.total().subtract(c.total()).abs())
                    + " del registrado en la compra (" + m + " " + soles(c.total()) + ").");
        if (difiere(qr.igv(), c.igv()))
            avisos.add("El IGV del QR (" + m + " " + soles(qr.igv()) + ") difiere en " + m + " " + soles(qr.igv().subtract(c.igv()).abs())
                    + " del registrado en la compra (" + m + " " + soles(c.igv()) + ").");
        if (c.fecha() != null && !c.fecha().equals(qr.fecha()))
            avisos.add("La fecha del QR (" + qr.fecha().format(DMY) + ") no coincide con la de la compra (" + c.fecha().format(DMY) + ").");
        // contrato v2: solo pasa si no hay compra del mismo tipo con ese número (la búsqueda prefiere el mismo tipo)
        if (!qr.tipoComprobante().equals(c.tipoComprobante()))
            avisos.add("El QR es de una " + qr.tipoComprobante() + " y la compra se registró como " + c.tipoComprobante() + ".");
        return avisos;
    }

    /** Más de S/ 0.01 de diferencia. Sin importe registrado no hay con qué comparar. */
    static boolean difiere(BigDecimal delQr, BigDecimal registrado) {
        return registrado != null && delQr.subtract(registrado).abs().compareTo(TOLERANCIA) > 0;
    }

    static String simbolo(String moneda) {
        return "USD".equals(moneda) ? "US$" : "S/";
    }

    /** {@code 1180} → {@code 1 180.00}: miles con espacio y punto decimal, como en el contrato. */
    static String soles(BigDecimal x) {
        var s = new DecimalFormatSymbols(Locale.ROOT);
        s.setGroupingSeparator(' ');
        s.setDecimalSeparator('.');
        var f = new DecimalFormat("#,##0.00", s);
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(x);
    }
}
