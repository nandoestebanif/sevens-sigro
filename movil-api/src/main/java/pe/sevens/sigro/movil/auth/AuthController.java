package pe.sevens.sigro.movil.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pe.sevens.sigro.movil.reenvio.ServicioDominio;

/** HU-12 · Entrar. La clave se valida contra el hash argon2 del servicio de identidad; aquí solo la forma. */
@RestController
public class AuthController {
    public record LoginRequest(@NotBlank(message = "escribe tu usuario") @Size(max = 60) String usuario,
                               @NotBlank(message = "escribe tu clave") @Size(max = 200) String clave) {}

    private final ServicioDominio dominio;
    private final ObjectMapper json;

    public AuthController(ServicioDominio dominio, ObjectMapper json) {
        this.dominio = dominio;
        this.json = json;
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<byte[]> entrar(@Valid @RequestBody LoginRequest login) throws JsonProcessingException {
        var limpio = new LoginRequest(login.usuario().trim(), login.clave());
        return dominio.enviar(HttpMethod.POST, "/api/v1/auth/login", null, json.writeValueAsBytes(limpio));
    }
}
