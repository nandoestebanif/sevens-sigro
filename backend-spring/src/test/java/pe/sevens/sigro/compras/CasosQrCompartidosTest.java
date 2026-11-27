package pe.sevens.sigro.compras;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * HU-73 · Los casos COMPARTIDOS con la app ({@code docs/contratos/hu-73-qr-casos.json}). Se lee el archivo del contrato,
 * no una copia: si alguien agrega un caso allí, lo prueban el parser y el cotejo de Java y los de Kotlin, y los dos lados
 * no pueden interpretar distinto el mismo QR.
 */
class CasosQrCompartidosTest {
    static final String ARCHIVO = "docs/contratos/hu-73-qr-casos.json";
    static final Pattern REF = Pattern.compile("^validos\\[(\\d+)]$");
    static JsonNode casos;

    @BeforeAll
    static void leer() throws IOException {
        casos = new ObjectMapper().readTree(Files.readString(ubicar()));
    }

    /** Maven corre las pruebas desde backend-spring/: el contrato está en la raíz del repositorio. */
    static Path ubicar() {
        for (Path d = Path.of("").toAbsolutePath(); d != null; d = d.getParent()) {
            Path p = d.resolve(ARCHIVO);
            if (Files.isRegularFile(p)) return p;
        }
        throw new IllegalStateException("No se encontró " + ARCHIVO + " subiendo desde " + Path.of("").toAbsolutePath());
    }

    static Stream<JsonNode> lista(String nombre) {
        var l = new ArrayList<JsonNode>();
        casos.get(nombre).forEach(l::add);
        return l.stream();
    }

    @TestFactory
    Stream<DynamicTest> losValidosSeLeenComoDiceElContrato() {
        return lista("validos").map(c -> dynamicTest(c.get("nombre").asText(), () -> {
            QrFacturaSunat qr = QrFacturaSunat.leer(c.get("qr").asText());
            assertNotNull(qr, "debía reconocerse");
            var esperado = new LinkedHashMap<String, String>();
            c.get("esperado").fields().forEachRemaining(e -> esperado.put(e.getKey(), e.getValue().asText()));
            assertEquals(esperado, qr.aMapa(), "mismo bloque «comprobante» que en la sección 3.1");
        }));
    }

    @TestFactory
    Stream<DynamicTest> losNoReconocidosNoSeAdivinan() {
        return lista("no_reconocidos").map(c -> dynamicTest(c.get("nombre").asText(),
                () -> assertNull(QrFacturaSunat.leer(c.get("qr").asText()), "debía ser qr_no_reconocido")));
    }

    @TestFactory
    Stream<DynamicTest> elCotejoDaElMismoResultadoQueLaApp() {
        String rucSevens = casos.get("ruc_sevens").asText();
        return lista("cotejo").map(c -> dynamicTest(c.get("nombre").asText(), () -> {
            QrFacturaSunat qr = QrFacturaSunat.leer(qrDe(c.get("qr").asText()));
            assertNotNull(qr);
            var compras = new ArrayList<CotejoQr.Compra>();
            if (c.hasNonNull("compras")) c.get("compras").forEach(j -> compras.add(compra(j)));
            else if (c.hasNonNull("compra")) compras.add(compra(c.get("compra")));
            var cotejo = CotejoQr.cotejar(qr, compras, rucSevens);
            assertEquals(c.get("resultado").asText(), cotejo.resultado().valor());
            if (c.has("avisos")) {
                var esperados = new ArrayList<String>();
                c.get("avisos").forEach(a -> esperados.add(a.asText()));
                assertEquals(esperados, cotejo.avisos(), "mismos textos y orden que la app (contrato v2)");
            }
            if (c.has("compra_id")) assertEquals(c.get("compra_id").asLong(), cotejo.compra().id(), "compra elegida");
            boolean conAvisos = cotejo.resultado() == CotejoQr.Resultado.RECIBIR_CON_AVISOS;
            if (cotejo.resultado() == CotejoQr.Resultado.RECIBIR || conAvisos)
                assertEquals(conAvisos, !cotejo.avisos().isEmpty(), "recibir ⇔ sin avisos; recibir_con_avisos ⇔ con avisos");
        }));
    }

    @Test
    void losCasosCubrenLosSeisResultadosYNoSeRepiten() {
        var vistos = new TreeSet<String>();
        lista("cotejo").forEach(c -> vistos.add(c.get("resultado").asText()));
        var todos = new TreeSet<String>();
        for (var r : CotejoQr.Resultado.values()) todos.add(r.valor());
        assertEquals(todos, vistos, "cada resultado del contrato tiene al menos un caso compartido");

        var nombres = new ArrayList<String>();
        for (String g : List.of("validos", "no_reconocidos", "cotejo")) lista(g).forEach(c -> nombres.add(g + "/" + c.get("nombre").asText()));
        assertEquals(new HashSet<>(nombres).size(), nombres.size(), "nombres de caso únicos");
    }

    @Test
    void losRucDeEmisorDelArchivoSonSinteticosPeroValidos() {
        lista("validos").forEach(c -> {
            String ruc = c.get("esperado").get("ruc_emisor").asText();
            assertTrue(Dominio.rucValido(ruc), ruc);
            assertTrue(ruc.startsWith("20999") || ruc.startsWith("20888"), "RUC sintético, no de un proveedor real: " + ruc);
        });
    }

    /** «validos[0]» → el QR de ese caso válido. */
    static String qrDe(String ref) {
        Matcher m = REF.matcher(ref);
        assertTrue(m.matches(), "referencia de QR desconocida: " + ref);
        return casos.get("validos").get(Integer.parseInt(m.group(1))).get("qr").asText();
    }

    static CotejoQr.Compra compra(JsonNode j) {
        return new CotejoQr.Compra(j.hasNonNull("id") ? j.get("id").asLong() : 1L, j.get("proveedor_ruc").asText(), "Proveedor de prueba S.A.C.",
                j.get("tipo_comprobante").asText(), j.get("numero_comprobante").asText(), new BigDecimal(j.get("total").asText()),
                new BigDecimal(j.get("igv").asText()), j.hasNonNull("fecha") ? java.time.LocalDate.parse(j.get("fecha").asText()) : null,
                j.hasNonNull("moneda") ? j.get("moneda").asText() : "PEN", j.get("estado").asText(),
                j.hasNonNull("motivo_anulacion") ? j.get("motivo_anulacion").asText() : null, j.get("queda_por_recibir").asBoolean());
    }
}
