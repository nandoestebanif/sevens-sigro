package pe.sevens.sigro.compras;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.sevens.sigro.compras.CotejoQr.Compra;
import pe.sevens.sigro.compras.CotejoQr.Resultado;

/** HU-73 · Lo que el JSON compartido no fija al detalle: el texto de los avisos, los bordes de la tolerancia y la búsqueda. */
class CotejoQrTest {
    static final String SEVENS = "20606456892";
    static final String QR = "20999999990|01|F001|00001234|180.00|1180.00|2026-11-20|6|20606456892|";

    static Compra compra(long id, String tipo, String numero, String total, String igv, LocalDate fecha, String estado) {
        return new Compra(id, "20999999990", "Aceros del Sur S.A.C.", tipo, numero, new BigDecimal(total), new BigDecimal(igv), fecha, "PEN", estado,
                "anulada".equals(estado) ? "Se registró dos veces" : null, true);
    }

    static Compra vigente(String total, String igv) {
        return compra(12, "factura", "F001-1234", total, igv, LocalDate.of(2026, 11, 20), "registrada");
    }

    static QrFacturaSunat qr() { return QrFacturaSunat.leer(QR); }

    @Test
    void elAvisoDelTotalEsElDelContratoConLaDiferenciaEnSoles() {
        var c = CotejoQr.cotejar(qr(), List.of(vigente("1062.00", "162.00")), SEVENS);
        assertEquals(Resultado.RECIBIR_CON_AVISOS, c.resultado());
        assertEquals(List.of(
                "El total del QR (S/ 1 180.00) difiere en S/ 118.00 del registrado en la compra (S/ 1 062.00).",
                "El IGV del QR (S/ 180.00) difiere en S/ 18.00 del registrado en la compra (S/ 162.00)."), c.avisos());
    }

    @Test
    void unCentimoSeToleraYDosNo() {
        assertEquals(Resultado.RECIBIR, CotejoQr.cotejar(qr(), List.of(vigente("1179.99", "180.01")), SEVENS).resultado());
        var dos = CotejoQr.cotejar(qr(), List.of(vigente("1180.02", "180.00")), SEVENS);
        assertEquals(Resultado.RECIBIR_CON_AVISOS, dos.resultado());
        assertEquals("El total del QR (S/ 1 180.00) difiere en S/ 0.02 del registrado en la compra (S/ 1 180.02).", dos.avisos().get(0));
    }

    @Test
    void laFechaDistintaAvisaPeroDejaRecibir() {
        var c = CotejoQr.cotejar(qr(), List.of(compra(12, "factura", "F001-1234", "1180.00", "180.00", LocalDate.of(2026, 11, 19), "registrada")), SEVENS);
        assertEquals(Resultado.RECIBIR_CON_AVISOS, c.resultado());
        assertEquals(List.of("La fecha del QR (20/11/2026) no coincide con la de la compra (19/11/2026)."), c.avisos());
    }

    @Test
    void laAnuladaDiceElMotivo() {
        var c = CotejoQr.cotejar(qr(), List.of(compra(12, "factura", "F001-1234", "1180.00", "180.00", null, "anulada")), SEVENS);
        assertEquals(Resultado.ANULADA, c.resultado());
        assertEquals(List.of("La compra F001-1234 está anulada: Se registró dos veces."), c.avisos());
    }

    @Test
    void elNumeroGuardadoConCerosEsElMismoComprobante() {
        var c = CotejoQr.cotejar(qr(), List.of(compra(12, "factura", "f001-00001234", "1180.00", "180.00", null, "registrada")), SEVENS);
        assertEquals(Resultado.RECIBIR, c.resultado());
        assertEquals(12L, c.compra().id());
    }

    @Test
    void otroNumeroDelMismoProveedorNoEsLaCompra() {
        var c = CotejoQr.cotejar(qr(), List.of(compra(12, "factura", "F001-12340", "1180.00", "180.00", null, "registrada")), SEVENS);
        assertEquals(Resultado.SIN_COMPRA, c.resultado());
        assertNull(c.compra());
    }

    @Test
    void conVariasCandidatasGanaElMismoTipoLuegoLaVigente() {
        var boleta = compra(30, "boleta", "F001-1234", "1180.00", "180.00", null, "registrada");
        var anulada = compra(20, "factura", "F001-1234", "1180.00", "180.00", null, "anulada");
        var vigente = compra(10, "factura", "F001-1234", "1180.00", "180.00", null, "registrada");
        assertEquals(10L, CotejoQr.buscar(qr(), List.of(boleta, anulada, vigente)).orElseThrow().id());
        assertEquals(20L, CotejoQr.buscar(qr(), List.of(boleta, anulada)).orElseThrow().id(), "el tipo pesa más que el estado");
        assertEquals(30L, CotejoQr.buscar(qr(), List.of(boleta)).orElseThrow().id(), "si solo hay otro tipo, es esa (el contrato busca por proveedor y número)");
    }

    @Test
    void noEsDeSevensNoMiraLasCompras() {
        var aOtro = QrFacturaSunat.leer("20999999990|01|F001|00001234|180.00|1180.00|2026-11-20|6|20888888889|");
        var c = CotejoQr.cotejar(aOtro, List.of(vigente("1180.00", "180.00")), SEVENS);
        assertEquals(Resultado.NO_ES_DE_SEVENS, c.resultado());
        assertNull(c.compra(), "no busca la compra");
        var conDni = QrFacturaSunat.leer("20999999990|03|B001|77|9.00|59.00|2026-11-21|1|20606456892");
        assertEquals(Resultado.NO_ES_DE_SEVENS, CotejoQr.cotejar(conDni, List.of(), SEVENS).resultado(), "el número sin tipo 6 no es el RUC");
    }

    @Test
    void enDolaresElAvisoNoDiceSoles() {
        var usd = new Compra(12L, "20999999990", "Import S.A.C.", "factura", "F001-1234", new BigDecimal("1000.00"), new BigDecimal("180.00"), null, "USD",
                "registrada", null, true);
        assertTrue(CotejoQr.cotejar(qr(), List.of(usd), SEVENS).avisos().get(0).startsWith("El total del QR (US$ 1 180.00) difiere en US$ 180.00"));
    }

    @Test
    void elQrNoSeFiltraAlImprimirlo() {
        String s = qr().toString();
        assertFalse(s.contains("1180") || s.contains(SEVENS) || s.contains("|"), "toString sin montos, sin adquirente, sin el texto: " + s);
        assertTrue(s.contains("F001-1234"));
    }

    @Test
    void normalizacionDelNumero() {
        assertEquals("F001-1234", QrFacturaSunat.numeroComprobante("f001", "00001234"));
        assertEquals("E001-0", QrFacturaSunat.numeroComprobante("E001", "00000000"));
        assertEquals("F001-1234", CotejoQr.normalizar(" F001 - 0001234 "));
        assertEquals("SIN-GUION", CotejoQr.normalizar("sin-guion"), "lo que no es serie-número se compara tal cual");
    }

    @Test
    void otrosBordesDelFormato() {
        assertNotNull(QrFacturaSunat.leer(QR + "aGFzaA==|otro|mas"), "del décimo campo en adelante se ignora");
        assertNull(QrFacturaSunat.leer(null));
        assertNull(QrFacturaSunat.leer("20999999990|01|F01|00001234|180.00|1180.00|2026-11-20|6|20606456892"), "serie de 3");
        assertNull(QrFacturaSunat.leer("20999999990|01|F001|123456789|180.00|1180.00|2026-11-20|6|20606456892"), "número de 9 dígitos");
        assertNull(QrFacturaSunat.leer("20999999990|01|F001|1234|-18.00|1180.00|2026-11-20|6|20606456892"), "monto negativo");
        assertNull(QrFacturaSunat.leer("20999999990|01|F001|1234|180,00|1180.00|2026-11-20|6|20606456892"), "coma decimal");
        assertNull(QrFacturaSunat.leer("20999999990|08|F001|1234|180.00|1180.00|2026-11-20|6|20606456892"), "nota de débito");
        assertNull(QrFacturaSunat.leer("20999999990|01|F001|1234|180.00|1180.00|29/02/2026|6|20606456892"), "29 de febrero de un año no bisiesto");
        assertEquals(LocalDate.of(2028, 2, 29), QrFacturaSunat.leer("20999999990|01|F001|1234|180.00|1180.00|29/02/2028|6|20606456892").fecha());
    }
}
