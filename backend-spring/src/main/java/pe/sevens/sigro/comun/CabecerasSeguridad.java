package pe.sevens.sigro.comun;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cabeceras de seguridad en TODAS las respuestas de la API (OWASP Secure Headers). La API solo devuelve JSON:
 * <ul>
 *   <li>nada se puede incrustar en otra página (CSP frame-ancestors 'none' + X-Frame-Options);</li>
 *   <li>el navegador no adivina el tipo de contenido (nosniff) ni ejecuta nada de la respuesta (default-src 'none');</li>
 *   <li>las respuestas con datos de la obra no quedan en cachés intermedias ni del navegador (no-store);</li>
 *   <li>HTTPS obligatorio (HSTS) y sin enviar la URL completa a otros sitios (Referrer-Policy).</li>
 * </ul>
 * Se pone antes que todo, así también salen en los 401/403 y en los errores.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CabecerasSeguridad extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadena) throws ServletException, IOException {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        if (!req.getRequestURI().startsWith("/api/")) {
            cadena.doFilter(req, res);
            return;
        }
        res.setHeader("Cache-Control", "no-store");
        // Si un controlador pone su propio Cache-Control (p. ej. «private, no-store» de los comprobantes), reemplaza al de aquí.
        cadena.doFilter(req, new HttpServletResponseWrapper(res) {
            @Override
            public void addHeader(String nombre, String valor) {
                if ("Cache-Control".equalsIgnoreCase(nombre)) super.setHeader(nombre, valor);
                else super.addHeader(nombre, valor);
            }
        });
    }
}
