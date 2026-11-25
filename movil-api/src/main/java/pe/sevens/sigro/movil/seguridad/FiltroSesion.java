package pe.sevens.sigro.movil.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Toda ruta /api/v1 exige un Bearer válido, salvo entrar. Sin token o con token vencido: 401 {codigo, mensaje},
 * igual que el servidor original, así la app cierra la sesión sin borrar su cola por enviar.
 */
@Component
public class FiltroSesion extends OncePerRequestFilter {
    public static final String ATRIBUTO_USUARIO = "sigro.usuario";
    private static final Set<String> PUBLICAS = Set.of("/api/v1/auth/login");

    private final VerificadorJwt verificador;

    public FiltroSesion(VerificadorJwt verificador) {
        this.verificador = verificador;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String ruta = req.getRequestURI();
        return !ruta.startsWith("/api/v1/") || PUBLICAS.contains(ruta) || "OPTIONS".equals(req.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadena)
            throws ServletException, IOException {
        String cabecera = req.getHeader("Authorization");
        var usuario = cabecera != null && cabecera.startsWith("Bearer ")
                ? verificador.usuarioDe(cabecera.substring(7).trim())
                : java.util.Optional.<String>empty();
        if (usuario.isEmpty()) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("{\"codigo\":\"sesion_vencida\",\"mensaje\":\"Tu sesión venció. Vuelve a entrar.\"}");
            return;
        }
        req.setAttribute(ATRIBUTO_USUARIO, usuario.get());
        cadena.doFilter(req, res);
    }
}
