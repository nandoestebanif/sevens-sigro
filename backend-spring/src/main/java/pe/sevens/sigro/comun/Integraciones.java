package pe.sevens.sigro.comun;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cliente de los servicios externos: Integraciones (SUNAT/RENIEC vía Factiliza, lectura del S10) y Notificaciones.
 * Frontera ADR-01: devuelven datos y este backend decide. Si no responden, la operación no se bloquea (salvo el S10).
 */
@Component
public class Integraciones {
    public record RucSunat(String ruc, String razonSocial, String estadoSunat, String condicion, boolean aptoParaComprar) {}
    public record DniReniec(String dni, String nombres, String apellidoPaterno, String apellidoMaterno) {}
    public record Respuesta(int estado, String cuerpo) {}

    private final String integraciones, notificaciones, token;
    private final pe.sevens.sigro.integraciones.Padron padron;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public Integraciones(@Value("${SIGRO_INTEGRACIONES_URL:}") String integraciones,
                         @Value("${SIGRO_NOTIFICACIONES_URL:}") String notificaciones,
                         @Value("${SIGRO_SERVICIO_TOKEN:}") String token, pe.sevens.sigro.integraciones.Padron padron) {
        this.padron = padron;
        this.integraciones = vacio(integraciones) ? null : integraciones.replaceAll("/+$", "");
        this.notificaciones = vacio(notificaciones) ? null : notificaciones.replaceAll("/+$", "");
        this.token = vacio(token) ? null : token;
    }

    static boolean vacio(String s) { return s == null || s.isBlank(); }

    private HttpRequest.Builder base(String url, double segundos) {
        var b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis((long) (segundos * 1000)));
        if (token != null) b.header("X-Servicio-Token", token);
        return b;
    }

    public RucSunat consultarRuc(String ruc) {
        if (integraciones == null) {          // sin servicio aparte: el padrón se consulta desde aquí (FACTILIZA_TOKEN / APISPERU_TOKEN)
            var r = padron.ruc(ruc);
            return r == null ? null : new RucSunat(r.ruc(), r.razonSocial(), r.estadoSunat(), r.condicion(), r.aptoParaComprar());
        }
        try {
            var r = http.send(base(integraciones + "/ruc/" + ruc, 4).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return null;
            JsonNode d = Json.leer(r.body());
            return new RucSunat(req(d, "ruc"), req(d, "razon_social"), req(d, "estado_sunat"), req(d, "condicion"), d.get("apto_para_comprar").asBoolean());
        } catch (Exception e) {
            return null;
        }
    }

    public DniReniec consultarDni(String dni) {
        if (integraciones == null) {
            var r = padron.dni(dni);
            if (r == null || r.nombres().isEmpty() || r.apellidoPaterno().isEmpty()) return null;
            return new DniReniec(r.dni(), r.nombres(), r.apellidoPaterno(), r.apellidoMaterno());
        }
        try {
            var r = http.send(base(integraciones + "/dni/" + dni, 4).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return null;
            JsonNode d = Json.leer(r.body());
            String nombres = req(d, "nombres"), pat = req(d, "apellido_paterno");
            if (nombres.isEmpty() || pat.isEmpty()) return null;
            String mat = d.hasNonNull("apellido_materno") ? d.get("apellido_materno").asText() : "";
            return new DniReniec(req(d, "dni"), nombres, pat, mat);
        } catch (Exception e) {
            return null;
        }
    }

    private static String req(JsonNode d, String k) {
        if (!d.has(k)) throw new NoSuchElementException(k);
        return d.get(k).isNull() ? "" : d.get(k).asText();
    }

    /** HU-07: Integraciones lee el PDF del S10. Sin modo degradado: si no responde, se dice por qué. */
    public JsonNode leerRelacionS10(String nombre, byte[] contenido, List<String> codigosExistentes) {
        if (integraciones == null) return leerLocal(nombre, contenido, codigosExistentes);
        String limite = "----sigro" + UUID.randomUUID();
        var partes = new java.io.ByteArrayOutputStream();
        try {
            String cab = "--" + limite + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"" + nombre.replace("\"", "") + "\"\r\nContent-Type: application/pdf\r\n\r\n";
            partes.write(cab.getBytes(StandardCharsets.UTF_8));
            partes.write(contenido);
            String campo = "\r\n--" + limite + "\r\nContent-Disposition: form-data; name=\"codigos_existentes\"\r\n\r\n" + String.join(",", codigosExistentes) + "\r\n--" + limite + "--\r\n";
            partes.write(campo.getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        HttpResponse<String> r;
        try {
            r = http.send(base(integraciones + "/s10/relacion-insumos", 60).header("Content-Type", "multipart/form-data; boundary=" + limite)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(partes.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new ErrorSigro(503, "integraciones_no_disponible", "El servicio que lee el PDF del S10 no responde; inténtalo en un momento. El archivo no se pierde.");
        }
        if (r.statusCode() == 200) return Json.leer(r.body());
        if (r.statusCode() == 401 || r.statusCode() == 403)
            throw new ErrorSigro(503, "integraciones_rechazo_el_servicio", "Integraciones rechazó el token de servicio del catálogo. Avisa al administrador del sistema.");
        if (r.statusCode() == 422) {
            String detalle = null;
            try { JsonNode d = Json.leer(r.body()).get("detail"); if (d != null && d.isTextual()) detalle = d.textValue(); } catch (RuntimeException ignorado) { }
            throw new ErrorSigro(422, "archivo_s10_invalido", detalle != null ? detalle : "No se pudo leer el archivo como una Relación de insumos del S10.");
        }
        throw new ErrorSigro(503, "integraciones_con_error", "Integraciones respondió con un error (" + r.statusCode() + ").");
    }

    /** HU-07 sin servicio aparte: el mismo lector del S10 (PDFBox), con las mismas respuestas que Integraciones. */
    JsonNode leerLocal(String nombre, byte[] contenido, List<String> codigosExistentes) {
        if (!nombre.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) throw new ErrorSigro(422, "archivo_s10_invalido", "Se espera el PDF exportado del S10.");
        pe.sevens.sigro.integraciones.LectorS10.Lectura l;
        try {
            l = pe.sevens.sigro.integraciones.LectorS10.leer(contenido);
        } catch (Exception e) {
            throw new ErrorSigro(422, "archivo_s10_invalido", "El archivo no se pudo leer como un PDF del S10.");
        }
        if (l.insumos.isEmpty())
            throw new ErrorSigro(422, "archivo_s10_invalido", "El PDF no trae filas de insumos reconocibles: ¿es la «Relación de insumos» "
                    + "(precios y cantidades de recursos requeridos por tipo)?");
        var existentes = new HashSet<String>();
        for (String c : codigosExistentes) if (!c.strip().isEmpty()) existentes.add(c.strip());
        var insumos = new ArrayList<Map<String, Object>>();
        var porTipo = new LinkedHashMap<String, Object>();
        int aCrear = 0, aAct = 0;
        double total = 0;
        for (var i : l.insumos) {
            String accion = existentes.contains(i.codigo()) ? "actualizar" : "crear";
            if (accion.equals("crear")) aCrear++; else aAct++;
            porTipo.merge(i.tipo(), 1L, (a, b) -> (Long) a + (Long) b);
            total += i.parcial();
            insumos.add(P.mapa("codigo", i.codigo(), "descripcion", i.descripcion(), "unidad", i.unidad(), "tipo", i.tipo(), "cantidad", i.cantidad(),
                    "precio", i.precio(), "parcial", i.parcial(), "accion", accion, "advertencia", i.advertencia()));
        }
        var r = P.mapa("obra", l.obra, "subpresupuesto", l.subpresupuesto, "fecha", l.fecha,
                "resumen", P.mapa("leidos", insumos.size(), "a_crear", aCrear, "a_actualizar", aAct, "fallidos", l.fallidos.size(), "por_tipo", porTipo,
                        "parcial_total", new java.math.BigDecimal(total).setScale(2, java.math.RoundingMode.HALF_EVEN).doubleValue()),
                "insumos", insumos, "fallidos", l.fallidos);
        return Json.leer(Json.texto(r));
    }

    /** Llamada al servicio de notificaciones con el token de servicio. */
    public Respuesta notificaciones(String metodo, String ruta, Map<String, Object> params) {
        if (notificaciones == null || token == null)
            throw new ErrorSigro(503, "notificaciones_no_disponible", "El servicio de notificaciones no está configurado.");
        var q = new StringJoiner("&", "?", "");
        params.forEach((k, v) -> q.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8)));
        try {
            var b = base(notificaciones + ruta + q, 4);
            b = "POST".equals(metodo) ? b.POST(HttpRequest.BodyPublishers.noBody()) : b.GET();
            var r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Respuesta(r.statusCode(), r.body());
        } catch (Exception e) {
            throw new ErrorSigro(503, "notificaciones_no_disponible", "No se pudo consultar las notificaciones; inténtalo en un momento.");
        }
    }
}
