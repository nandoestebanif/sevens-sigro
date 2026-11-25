package pe.sevens.sigro.movil.reenvio;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Habla con los servicios de dominio por /api/v1 con la misma sesión del usuario (su Bearer): los permisos por obra
 * y por celular los vuelve a verificar el servicio dueño. Devuelve estado y cuerpo tal cual, también en un 4xx,
 * para que la app reciba el mismo {codigo, mensaje} de siempre.
 */
@Service
public class ServicioDominio {
    private final RestClient cliente;
    private final String base;

    public ServicioDominio(RestClient restClientDominio, pe.sevens.sigro.movil.config.PropiedadesSigro props) {
        this.cliente = restClientDominio;
        this.base = props.apiUrl().replaceAll("/+$", "");
    }

    public ResponseEntity<byte[]> enviar(HttpMethod metodo, String rutaConConsulta, String autorizacion, byte[] cuerpo) {
        return cliente.method(metodo)
                .uri(URI.create(base + rutaConConsulta)) // URI absoluta: la consulta no se codifica dos veces
                .headers(h -> {
                    if (autorizacion != null) h.set(HttpHeaders.AUTHORIZATION, autorizacion);
                    h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                    if (cuerpo != null && cuerpo.length > 0) h.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(cuerpo == null ? new byte[0] : cuerpo)
                .exchange((req, res) -> {
                    byte[] datos = res.getBody().readAllBytes();
                    var cabeceras = new HttpHeaders();
                    var tipo = res.getHeaders().getContentType();
                    cabeceras.setContentType(tipo != null ? tipo : MediaType.APPLICATION_JSON);
                    return ResponseEntity.status(res.getStatusCode()).headers(cabeceras).body(datos);
                });
    }
}
