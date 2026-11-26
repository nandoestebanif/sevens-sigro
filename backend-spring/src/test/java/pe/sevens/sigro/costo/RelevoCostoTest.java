package pe.sevens.sigro.costo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RelevoCostoTest {
    @Test
    void laEsperaCreceHastaUnaHora() {
        assertEquals(5, RelevoCosto.espera(1));
        assertEquals(10, RelevoCosto.espera(2));
        assertEquals(40, RelevoCosto.espera(4));
        assertEquals(3600, RelevoCosto.espera(20));
    }

    @Test
    void laToleranciaCubreElRedondeoDeCadaHecho() {
        assertEquals(0, new BigDecimal("0.01").compareTo(CostoController.toleranciaRedondeo(0)));
        assertEquals(0, new BigDecimal("0.01").compareTo(CostoController.toleranciaRedondeo(2)));
        assertEquals(0, new BigDecimal("1.500").compareTo(CostoController.toleranciaRedondeo(300)));
    }
}
