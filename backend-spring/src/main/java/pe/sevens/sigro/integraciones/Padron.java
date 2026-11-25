package pe.sevens.sigro.integraciones;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.sevens.sigro.comun.Json;

/**
 * Consulta de RUC (SUNAT) y DNI (RENIEC) en el padrón, vía Factiliza o apisperu (port de integraciones/main.py).
 * Los tokens viven solo en el entorno (FACTILIZA_TOKEN, APISPERU_TOKEN; PADRON_PROVEEDOR elige). Lo consultado hoy no
 * se vuelve a consultar hoy (cuida el plan gratuito). Devuelve null si no se pudo: quien llama nunca se bloquea.
 */
@Component
public class Padron {
    public record Ruc(String ruc, String razonSocial, String estadoSunat, String condicion, String direccion, boolean aptoParaComprar) {}
    public record Dni(String dni, String nombres, String apellidoPaterno, String apellidoMaterno, String nombreCompleto) {}

    static final long TTL_MS = 24L * 60 * 60 * 1000;
    private final String proveedor, factiliza, apisperu;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final Map<String, Object[]> cache = new ConcurrentHashMap<>();

    public Padron(@Value("${PADRON_PROVEEDOR:factiliza}") String proveedor, @Value("${FACTILIZA_TOKEN:}") String factiliza,
                  @Value("${APISPERU_TOKEN:}") String apisperu) {
        this.proveedor = proveedor == null || proveedor.isBlank() ? "factiliza" : proveedor.strip().toLowerCase(Locale.ROOT);
        this.factiliza = factiliza == null || factiliza.isBlank() ? null : factiliza;
        this.apisperu = apisperu == null || apisperu.isBlank() ? null : apisperu;
    }

    public boolean configurado() { return "apisperu".equals(proveedor) ? apisperu != null : factiliza != null; }

    JsonNode consultar(String tipo, String numero) {
        boolean api = "apisperu".equals(proveedor);
        String token = api ? apisperu : factiliza;
        if (token == null) return null;
        String url = api ? "https://dniruc.apisperu.com/api/v1/" + tipo + "/" + numero : "https://api.factiliza.com/v1/" + tipo + "/info/" + numero;
        try {
            var r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return null;
            JsonNode c = Json.leer(r.body());
            if (api) {
                JsonNode d = c.path("data").isObject() ? c.get("data") : c;
                if (d.isEmpty() || (d.has("success") && !d.get("success").asBoolean(true))) return null;
                return d;
            }
            JsonNode d = c.path("data");
            if (!c.path("success").asBoolean(false) || !d.isObject() || d.isEmpty()) return null;
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    /** Repara los caracteres de Windows-1252 que el padrón devuelve como controles (SEVEN\u0092S → SEVEN'S). */
    static String texto(String v) {
        var sb = new StringBuilder();
        for (char ch : v.toCharArray()) {
            if (ch >= 0x80 && ch <= 0x9f) {
                String d = new String(new byte[]{(byte) ch}, java.nio.charset.Charset.forName("windows-1252"));
                if (d.equals("�")) continue;
                ch = d.charAt(0);
            }
            switch (ch) {
                case '’', '‘' -> sb.append('\'');
                case '“', '”' -> sb.append('"');
                default -> sb.append(ch);
            }
        }
        return sb.toString().strip();
    }

    static String de(JsonNode d, String... claves) {
        for (String c : claves) {
            JsonNode v = d.get(c);
            if (v != null && !v.isNull() && !v.asText().isEmpty()) return texto(v.asText());
        }
        return "";
    }

    public Ruc ruc(String ruc) {
        if (!ruc.matches("\\d{11}")) return null;
        Object[] c = cache.get("ruc:" + ruc);
        if (c != null && System.currentTimeMillis() - (long) c[0] < TTL_MS) return (Ruc) c[1];
        JsonNode d = consultar("ruc", ruc);
        if (d == null) return null;
        String estado = de(d, "estado").toUpperCase(Locale.ROOT), condicion = de(d, "condicion").toUpperCase(Locale.ROOT);
        String razon = de(d, "nombre_o_razon_social", "razonSocial", "razon_social", "nombre");
        if (razon.isEmpty()) return null;
        String dir = de(d, "direccion_completa", "direccion");
        String num = de(d, "numero", "ruc");
        var r = new Ruc(num.isEmpty() ? ruc : num, razon, estado.isEmpty() ? "DESCONOCIDO" : estado, condicion.isEmpty() ? "DESCONOCIDA" : condicion,
                dir.isEmpty() ? null : dir, estado.equals("ACTIVO") && condicion.equals("HABIDO"));
        cache.put("ruc:" + ruc, new Object[]{System.currentTimeMillis(), r});
        return r;
    }

    public Dni dni(String dni) {
        if (!dni.matches("\\d{8}")) return null;
        Object[] c = cache.get("dni:" + dni);
        if (c != null && System.currentTimeMillis() - (long) c[0] < TTL_MS) return (Dni) c[1];
        JsonNode d = consultar("dni", dni);
        if (d == null) return null;
        String nombres = de(d, "nombres"), pat = de(d, "apellido_paterno", "apellidoPaterno"), mat = de(d, "apellido_materno", "apellidoMaterno");
        String completo = de(d, "nombre_completo", "nombreCompleto");
        if (completo.isEmpty()) completo = (nombres + " " + pat + " " + mat).strip();
        if (completo.isEmpty()) return null;
        String num = d.hasNonNull("numero") && !d.get("numero").asText().isEmpty() ? d.get("numero").asText() : dni;
        var r = new Dni(num, nombres, pat, mat, completo);
        cache.put("dni:" + dni, new Object[]{System.currentTimeMillis(), r});
        return r;
    }
}
