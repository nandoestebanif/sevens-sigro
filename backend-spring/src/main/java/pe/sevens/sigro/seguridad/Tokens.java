package pe.sevens.sigro.seguridad;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.sevens.sigro.comun.Json;
import pe.sevens.sigro.comun.P;

/**
 * JWT HS256 compatible con PyJWT: {"sub": "<id>", "exp": <segundos>}. El token dice QUIÉN es; nunca qué puede
 * hacer: el rol y la obra se consultan en la base en cada petición (regla 9).
 */
@Component
public class Tokens {
    static final String DEV = "solo-para-desarrollo-no-usar-en-produccion-0123456789";
    private final byte[] secreto;
    private final int horas;

    public Tokens(@Value("${SIGRO_JWT_SECRET:}") String secreto, @Value("${SIGRO_ENTORNO:dev}") String entorno,
                  @Value("${SIGRO_JWT_HORAS:8}") int horas) {
        if (secreto == null || secreto.isBlank()) {
            if (!"dev".equals(entorno)) throw new IllegalStateException("SIGRO_JWT_SECRET es obligatorio fuera de desarrollo");
            secreto = DEV;
        }
        if (!"dev".equals(entorno) && secreto.length() < 32) throw new IllegalStateException("SIGRO_JWT_SECRET debe tener al menos 32 caracteres");
        this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
        this.horas = horas;
    }

    public int segundos() { return horas * 3600; }

    public String crear(long usuarioId) {
        long exp = Instant.now().getEpochSecond() + segundos();
        String cab = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String cuerpo = b64(Json.texto(P.mapa("sub", Long.toString(usuarioId), "exp", exp)).getBytes(StandardCharsets.UTF_8));
        return cab + "." + cuerpo + "." + b64(firma(cab + "." + cuerpo));
    }

    /** Token del panel de administración tras escribir el PIN: 15 minutos y con la marca "pin" (uno de sesión no sirve). */
    public String crearPin(long usuarioId) {
        long exp = Instant.now().getEpochSecond() + 15 * 60;
        String cab = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String cuerpo = b64(Json.texto(P.mapa("sub", Long.toString(usuarioId), "exp", exp, "pin", true)).getBytes(StandardCharsets.UTF_8));
        return cab + "." + cuerpo + "." + b64(firma(cab + "." + cuerpo));
    }

    /** El usuario del token de PIN, o null si no es de PIN, venció o la firma no cuadra. */
    public Long leerPin(String token) {
        Long uid = leerFirmado(token);
        if (uid == null) return null;
        try {
            JsonNode d = Json.leer(new String(Base64.getUrlDecoder().decode(token.split("\\.", -1)[1]), StandardCharsets.UTF_8));
            return d.path("pin").asBoolean(false) ? uid : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** El id del usuario, o null si el token no es válido, venció o no trae sub/exp. */
    public Long leer(String token) {
        if (token != null && token.split("\\.", -1).length == 3 && esPin(token)) return null; // un token de PIN no abre sesión
        return leerFirmado(token);
    }

    private boolean esPin(String token) {
        try {
            return Json.leer(new String(Base64.getUrlDecoder().decode(token.split("\\.", -1)[1]), StandardCharsets.UTF_8)).path("pin").asBoolean(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Long leerFirmado(String token) {
        try {
            String[] p = token.split("\\.", -1);
            if (p.length != 3) return null;
            JsonNode cab = Json.leer(new String(Base64.getUrlDecoder().decode(p[0]), StandardCharsets.UTF_8));
            if (!"HS256".equals(cab.path("alg").asText())) return null;
            byte[] esperada = firma(p[0] + "." + p[1]);
            if (!MessageDigest.isEqual(esperada, Base64.getUrlDecoder().decode(p[2]))) return null;
            JsonNode d = Json.leer(new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8));
            if (!d.has("exp") || !d.has("sub") || !d.get("exp").isNumber()) return null;
            if (d.get("exp").asLong() <= Instant.now().getEpochSecond()) return null;
            String sub = d.get("sub").isTextual() ? d.get("sub").textValue() : null;
            if (sub == null) return null;
            return Long.parseLong(sub.strip());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private byte[] firma(String datos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            return mac.doFinal(datos.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
}
