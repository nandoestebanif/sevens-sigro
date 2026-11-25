package pe.sevens.sigro.movil.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración por variables de entorno (nunca escrita en el código):
 * SIGRO_API_URL (servicios de dominio) y SIGRO_JWT_SECRET (el mismo secreto con que se firman las sesiones).
 */
@Validated
@ConfigurationProperties(prefix = "sigro")
public record PropiedadesSigro(@NotBlank String apiUrl, @NotBlank String jwtSecret, int tiempoEsperaSegundos) {
    public PropiedadesSigro {
        if (tiempoEsperaSegundos <= 0) tiempoEsperaSegundos = 70; // el plan gratuito tarda ~1 min en despertar
    }
}
