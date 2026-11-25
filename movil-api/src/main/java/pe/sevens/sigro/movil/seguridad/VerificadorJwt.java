package pe.sevens.sigro.movil.seguridad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import pe.sevens.sigro.movil.config.PropiedadesSigro;

/**
 * Verifica un JWT HS256 (cabecera.cuerpo.firma): la firma con el secreto compartido, el algoritmo y el vencimiento.
 * Comparación de firmas en tiempo constante (MessageDigest.isEqual) para no filtrar información por el tiempo.
 */
@Component
public class VerificadorJwt {
    private final byte[] secreto;
    private final ObjectMapper json;
    private final Clock reloj;

    public VerificadorJwt(PropiedadesSigro props, ObjectMapper json, Optional<Clock> reloj) {
        this.secreto = props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        this.json = json;
        this.reloj = reloj.orElse(Clock.systemUTC());
    }

    /** El id del usuario (claim «sub») si el token es válido; vacío si no lo es. */
    public Optional<String> usuarioDe(String token) {
        try {
            String[] partes = token.split("\\.");
            if (partes.length != 3) return Optional.empty();
            JsonNode cabecera = json.readTree(Base64.getUrlDecoder().decode(partes[0]));
            if (!"HS256".equals(cabecera.path("alg").asText())) return Optional.empty(); // nada de «alg: none»
            byte[] esperada = firmar(partes[0] + "." + partes[1]);
            byte[] recibida = Base64.getUrlDecoder().decode(partes[2]);
            if (!MessageDigest.isEqual(esperada, recibida)) return Optional.empty();
            JsonNode cuerpo = json.readTree(Base64.getUrlDecoder().decode(partes[1]));
            long vence = cuerpo.path("exp").asLong(0);
            if (vence <= reloj.instant().getEpochSecond()) return Optional.empty();
            String sub = cuerpo.path("sub").asText("");
            return sub.isBlank() ? Optional.empty() : Optional.of(sub);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    byte[] firmar(String datos) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
        return mac.doFinal(datos.getBytes(StandardCharsets.US_ASCII));
    }
}
