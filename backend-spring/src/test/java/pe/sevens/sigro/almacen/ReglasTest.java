package pe.sevens.sigro.almacen;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import pe.sevens.sigro.comun.ErrorSigro;

/** Reglas puras de almacén: se prueban sin base de datos ni Spring. */
class ReglasTest {
    static Map<String, Object> periodo(long id, String ini, String fin, String estado) {
        return Map.of("id", id, "numero", id, "fecha_inicio", LocalDate.parse(ini), "fecha_fin", LocalDate.parse(fin), "estado", estado);
    }

    @Test
    void uuidDerivadoEsElMismoQueUuid5DePython() {
        // uuid.uuid5(UUID('12345678-1234-5678-1234-567812345678'), 'recepcion:7') en Python
        var base = UUID.fromString("12345678-1234-5678-1234-567812345678");
        assertEquals(UUID.fromString("4b6bdad8-2b1b-5d89-8897-9e46353a7c0c"), Reglas.uuidDerivado(base, "recepcion", 7));
        assertEquals(Reglas.uuidDerivado(base, "recepcion", 7), Reglas.uuidDerivado(base, "recepcion", 7), "un reenvío produce el mismo id");
    }

    @Test
    void convierteDeUnidadDeCompraAUnidadS10() {
        var ins = Map.<String, Object>of("codigo", "0201", "unidad_s10", "kg", "unidad_compra", "bls", "factor_conversion", "42.5");
        assertEquals(new BigDecimal("85.0000"), Reglas.aUnidadS10(new BigDecimal("2"), "bls", ins));
        assertEquals(new BigDecimal("2"), Reglas.aUnidadS10(new BigDecimal("2"), "kg", ins));
        var e = assertThrows(ErrorSigro.class, () -> Reglas.aUnidadS10(BigDecimal.ONE, "m3", ins));
        assertEquals("unidad_invalida", e.codigo());
    }

    @Test
    void excesoSoloCuandoSePasaDeLoRequerido() {
        assertEquals(new BigDecimal("0.0000"), Reglas.excesoSobreRequerido(new BigDecimal("10"), new BigDecimal("4"), new BigDecimal("6")));
        assertEquals(new BigDecimal("2.0000"), Reglas.excesoSobreRequerido(new BigDecimal("10"), new BigDecimal("4"), new BigDecimal("8")));
        assertNull(Reglas.excesoSobreRequerido(null, BigDecimal.ONE, BigDecimal.ONE), "sin requerimiento no hay exceso que medir");
    }

    @Test
    void periodoCerradoSoloAdmiteReversos() {
        var periodos = List.of(periodo(1, "2026-01-05", "2026-01-11", "cerrado"));
        var dia = LocalDate.parse("2026-01-07");
        assertEquals("periodo_cerrado", assertThrows(ErrorSigro.class, () -> Reglas.resolverPeriodo(periodos, dia, false)).codigo());
        assertEquals(1L, Reglas.resolverPeriodo(periodos, dia, true).get("id"));
    }

    @Test
    void fechaFueraDelPlazoDeLaObra() {
        var periodos = List.of(periodo(1, "2026-01-05", "2026-01-11", "abierto"));
        var e = assertThrows(ErrorSigro.class, () -> Reglas.resolverPeriodo(periodos, LocalDate.parse("2026-02-01"), false));
        assertEquals(422, e.estado());
        assertEquals("fuera_de_plazo", e.codigo());
    }

    @Test
    void disponibleParaPrestarEsSaldoMenosPrestado() {
        assertEquals(new BigDecimal("2"), Reglas.disponibleParaPrestar(new BigDecimal("5"), new BigDecimal("3")));
        assertEquals(BigDecimal.ZERO, Reglas.disponibleParaPrestar(new BigDecimal("1"), new BigDecimal("3")), "nunca negativo");
    }
}
