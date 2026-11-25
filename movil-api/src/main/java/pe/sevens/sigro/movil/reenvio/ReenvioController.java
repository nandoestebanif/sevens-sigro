package pe.sevens.sigro.movil.reenvio;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.sevens.sigro.movil.errores.ErrorSigro;

/**
 * Las demás llamadas de la app (obras, catálogo, stock, compras por recibir, trabajadores, asistencia…) pasan a su
 * servicio dueño. Solo las rutas que la app usa: una lista blanca, no un proxy abierto.
 */
@RestController
public class ReenvioController {
    static final List<Pattern> PERMITIDAS = List.of(
            Pattern.compile("^/api/v1/auth/yo$"),
            Pattern.compile("^/api/v1/obras$"),
            Pattern.compile("^/api/v1/catalogo/insumos$"),
            Pattern.compile("^/api/v1/obras/\\d+/(almacenes|compras|dispositivos|dispositivos/mios|trabajadores|horas-acumuladas|jornadas)$"),
            Pattern.compile("^/api/v1/obras/\\d+/trabajadores/\\d+/baja$"),
            Pattern.compile("^/api/v1/obras/\\d+/jornadas/\\d+/correccion$"),
            Pattern.compile("^/api/v1/obras/\\d+/almacen/(stock|ingresos|entradas|reversos|recepciones/verificacion)$"),
            Pattern.compile("^/api/v1/obras/\\d+/almacen/compras/\\d+/recepcion$"),
            Pattern.compile("^/api/v1/obras/\\d+/almacen/[a-z\\-]+$"));

    private final ServicioDominio dominio;

    public ReenvioController(ServicioDominio dominio) {
        this.dominio = dominio;
    }

    @RequestMapping("/api/v1/**")
    public ResponseEntity<byte[]> reenviar(HttpServletRequest req, @RequestBody(required = false) byte[] cuerpo) {
        String ruta = req.getRequestURI();
        if (PERMITIDAS.stream().noneMatch(p -> p.matcher(ruta).matches())) {
            throw new ErrorSigro(404, "ruta_desconocida", "Esa ruta no existe en el backend de la app.");
        }
        String consulta = req.getQueryString() == null ? "" : "?" + req.getQueryString();
        return dominio.enviar(HttpMethod.valueOf(req.getMethod()), ruta + consulta, req.getHeader(HttpHeaders.AUTHORIZATION), cuerpo);
    }
}
