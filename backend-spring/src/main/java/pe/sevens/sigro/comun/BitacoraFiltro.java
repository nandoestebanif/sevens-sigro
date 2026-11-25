package pe.sevens.sigro.comun;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.sevens.sigro.seguridad.Tokens;

/**
 * Bitácora (migración 0024): cada cambio (POST) y cada acceso rechazado (401/403) queda con quién, cuándo, obra, ruta
 * y resultado. A propósito NO guarda cuerpos, parámetros de consulta, cabeceras, tokens, claves, PIN, IP ni datos
 * personales. Si escribir la bitácora falla, la operación del usuario NO se ve afectada.
 */
@Component
public class BitacoraFiltro extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(BitacoraFiltro.class);
    private static final Pattern OBRA = Pattern.compile("^/api/v1/obras/(\\d+)(/.*)?$");
    private final Modulo db;
    private final Tokens tokens;

    public BitacoraFiltro(@Qualifier("obras") Modulo obras, Tokens tokens) {
        this.db = obras;
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest r) {
        String ruta = r.getRequestURI();
        return !ruta.startsWith("/api/v1/") || ruta.startsWith("/api/v1/interno");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadena) throws ServletException, IOException {
        long t0 = System.nanoTime();
        try {
            cadena.doFilter(req, res);
        } finally {
            int estado = res.getStatus();
            String metodo = req.getMethod();
            if ("POST".equals(metodo) || estado == 401 || estado == 403) anotar(req, metodo, estado, (System.nanoTime() - t0) / 1_000_000);
        }
    }

    void anotar(HttpServletRequest req, String metodo, int estado, long ms) {
        try {
            String ruta = req.getRequestURI();
            if (ruta.length() > 300) ruta = ruta.substring(0, 300);
            var m = OBRA.matcher(ruta);
            Long obra = m.matches() ? Long.parseLong(m.group(1)) : null;
            String h = req.getHeader("Authorization");
            Long uid = h != null && h.regionMatches(true, 0, "Bearer ", 0, 7) ? tokens.leer(h.substring(7).strip()) : null;
            String r = ruta;
            db.aparte(() -> db.actualizar("INSERT INTO obra.bitacora (usuario_id, proyecto_id, metodo, ruta, estado, duracion_ms) VALUES (:u, :p, :m, :r, :e, :d)",
                    P.de("u", uid, "p", obra, "m", metodo, "r", r, "e", estado, "d", (int) Math.min(ms, Integer.MAX_VALUE))));
        } catch (RuntimeException e) {
            LOG.warn("No se pudo anotar en la bitácora: {}", e.getClass().getSimpleName()); // sin datos de la petición
        }
    }
}
