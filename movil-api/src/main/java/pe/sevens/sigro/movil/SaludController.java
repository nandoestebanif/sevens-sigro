package pe.sevens.sigro.movil;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SaludController {
    @GetMapping("/salud")
    public Map<String, String> salud() {
        return Map.of("estado", "ok", "servicio", "movil-api", "plataforma", "Spring Boot 3 · Java 17");
    }
}
