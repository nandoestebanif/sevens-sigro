package pe.sevens.sigro.integraciones;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Contra el PDF REAL del cliente: 284 insumos y S/ 1 479 338.33, igual que el lector original. */
class LectorS10Test {
    static final Path PDF = Path.of("../insumos-cliente/5.3. Relacion de insumos.pdf");

    @Test
    void elPdfRealDa284InsumosYElTotalDelPresupuesto() throws Exception {
        Assumptions.assumeTrue(Files.exists(PDF), "sin el PDF del cliente");
        var l = LectorS10.leer(Files.readAllBytes(PDF));
        assertEquals(284, l.insumos.size());
        double total = l.insumos.stream().mapToDouble(LectorS10.Insumo::parcial).sum();
        assertEquals(1479338.33, Math.round(total * 100) / 100.0, 0.001);
        var sb = new StringBuilder();
        for (var i : l.insumos) sb.append(i.codigo()).append('|').append(i.descripcion()).append('|').append(i.unidad()).append('|').append(i.tipo()).append('|')
                .append(i.cantidad()).append('|').append(i.precio()).append('|').append(i.parcial()).append('|').append(i.advertencia()).append('\n');
        sb.append("OBRA|").append(l.obra).append("|SUB|").append(l.subpresupuesto).append("|FECHA|").append(l.fecha).append("|FALLIDOS|").append(l.fallidos.size()).append('\n');
        Files.writeString(Path.of("target/s10-java.txt"), sb.toString());
    }
}
