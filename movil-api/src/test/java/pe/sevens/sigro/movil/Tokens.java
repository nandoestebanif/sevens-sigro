package pe.sevens.sigro.movil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Firma tokens HS256 como lo hace el servicio de identidad (PyJWT), para las pruebas. */
final class Tokens {
    static final String SECRETO = "secreto-de-pruebas-con-mas-de-32-caracteres";
    private Tokens() {}

    static String firmar(String sub, long exp, String secreto, String alg) throws Exception {
        var b64 = Base64.getUrlEncoder().withoutPadding();
        String cab = b64.encodeToString(("{\"alg\":\"" + alg + "\",\"typ\":\"JWT\"}").getBytes(StandardCharsets.UTF_8));
        String cuerpo = b64.encodeToString(("{\"sub\":\"" + sub + "\",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String firma = b64.encodeToString(mac.doFinal((cab + "." + cuerpo).getBytes(StandardCharsets.US_ASCII)));
        return cab + "." + cuerpo + "." + firma;
    }

    static String valido() throws Exception {
        return firmar("7", System.currentTimeMillis() / 1000 + 3600, SECRETO, "HS256");
    }
}
