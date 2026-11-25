package pe.sevens.sigro.comun;

import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo con SIGRO_PRUEBAS=1: la prueba de paridad clona una base nueva por prueba y pide renovar las conexiones. */
@RestController
@ConditionalOnProperty(name = "SIGRO_PRUEBAS", havingValue = "1")
public class SoloPruebas {
    private final List<Modulo> modulos;

    public SoloPruebas(List<Modulo> modulos) { this.modulos = modulos; }

    @PostMapping("/interno/pruebas/reiniciar")
    public Map<String, Object> reiniciar() {
        for (Modulo m : modulos) if (m.ds() instanceof HikariDataSource h && h.getHikariPoolMXBean() != null) h.getHikariPoolMXBean().softEvictConnections();
        return P.mapa("ok", true);
    }
}
