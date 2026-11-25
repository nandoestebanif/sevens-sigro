package pe.sevens.sigro.comun;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** /salud, /salud/servicios y CORS (GET y POST, cabeceras Authorization y Content-Type). */
@RestController
@Configuration
public class Sistema {
    private final List<Modulo> modulos;

    public Sistema(List<Modulo> modulos) { this.modulos = modulos; }

    @GetMapping({"/salud", "/api/v1/salud"})
    public Map<String, Object> salud() {
        return P.mapa("estado", "ok", "servicio", "spring");
    }

    /** Cada módulo responde con SU conexión: 200 si todos están listos, 503 si falta alguno (como el gateway). */
    @GetMapping("/salud/servicios")
    public org.springframework.http.ResponseEntity<Map<String, Object>> servicios() {
        var l = new ArrayList<Map<String, Object>>();
        boolean todos = true;
        for (Modulo m : modulos) {
            long t = System.nanoTime();
            String estado;
            try {
                m.en(() -> m.escalar("SELECT 1", P.de()));
                estado = "ok";
            } catch (RuntimeException e) {
                estado = "no_responde";
            }
            todos &= estado.equals("ok");
            l.add(P.mapa("servicio", m.nombre(), "listo", estado.equals("ok"), "estado", estado, "ms", (System.nanoTime() - t) / 1_000_000));
        }
        return org.springframework.http.ResponseEntity.status(todos ? 200 : 503).body(P.mapa("listo", todos, "servicios", l));
    }

    @Bean
    public WebMvcConfigurer cors(@Value("${sigro.cors}") String origenes) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry r) {
                r.addMapping("/**").allowedOrigins(java.util.Arrays.stream(origenes.split(",")).filter(s -> !s.isBlank()).toArray(String[]::new))
                        .allowedMethods("GET", "POST").allowedHeaders("Authorization", "Content-Type", "X-SIGRO-PIN")
                        .exposedHeaders(Pagina.CABECERA);
            }
        };
    }
}
