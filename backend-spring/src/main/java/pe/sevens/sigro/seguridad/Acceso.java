package pe.sevens.sigro.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.sevens.sigro.comun.ErrorSigro;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.comun.P;

/**
 * Control de acceso en el servidor (regla 9): rol y alcance se verifican en cada petición, contra la asignación
 * vigente en la base del módulo obras (dueño de quién puede qué). Una baja o un retiro corta el acceso al instante.
 */
@Component
public class Acceso {
    public record Actor(long usuarioId, String nombre, Long obraId, String rol) {}

    private final Modulo obras;
    private final Tokens tokens;

    public Acceso(Modulo obras, Tokens tokens) {
        this.obras = obras;
        this.tokens = tokens;
    }

    static ErrorSigro noAutenticado() { return new ErrorSigro(401, "no_autenticado", "Inicia sesión para continuar."); }
    static ErrorSigro sinPermiso() { return new ErrorSigro(403, "sin_permiso", "No tienes permiso para esta operación en esta obra."); }

    private static String bearer() {
        var a = RequestContextHolder.getRequestAttributes();
        if (!(a instanceof ServletRequestAttributes sa)) return null;
        HttpServletRequest r = sa.getRequest();
        String h = r.getHeader("Authorization");
        if (h == null) return null;
        String[] p = h.strip().split("\\s+", 2);
        if (p.length != 2 || !p[0].equalsIgnoreCase("bearer") || p[1].isBlank()) return null;
        return p[1];
    }

    /** El token identifica; obras decide si esa persona sigue activa. */
    public Actor autenticado() {
        String t = bearer();
        if (t == null) throw noAutenticado();
        Long uid = tokens.leer(t);
        if (uid == null) throw noAutenticado();
        var f = obras.en(() -> obras.fila("SELECT id, nombre, estado FROM obra.usuario WHERE id = :i", P.de("i", uid)));
        if (f == null || !"activo".equals(f.get("estado"))) throw noAutenticado();
        return new Actor((Long) f.get("id"), (String) f.get("nombre"), null, null);
    }

    /** Para rutas con {obra_id}: el rol vigente del usuario EN ESA obra debe admitir la acción. */
    public Actor exigir(String accion, long obraId) {
        Set<String> roles = Permisos.roles(accion);
        Actor a = autenticado();
        if (obraId < 1) throw new ErrorSigro(422, "datos_invalidos", "obra_id: Input should be greater than or equal to 1");
        String rol = rolEnObra(a.usuarioId(), obraId);
        if (rol == null || !roles.contains(rol)) throw sinPermiso();
        return new Actor(a.usuarioId(), a.nombre(), obraId, rol);
    }

    /** Para acciones sin obra en la ruta: en alguna obra, un rol que la admita. */
    public Actor exigirEnAlgunaObra(String accion) {
        Set<String> roles = Permisos.roles(accion);
        Actor a = autenticado();
        var f = obras.en(() -> obras.fila(
                "SELECT proyecto_id, rol FROM obra.asignacion WHERE usuario_id = :u AND fecha_retiro IS NULL "
                        + "AND rol = ANY(:roles) ORDER BY (rol = 'administrador') DESC, proyecto_id LIMIT 1",
                P.de("u", a.usuarioId(), "roles", P.textos(new ArrayList<>(roles)))));
        if (f == null) throw sinPermiso();
        return new Actor(a.usuarioId(), a.nombre(), (Long) f.get("proyecto_id"), (String) f.get("rol"));
    }

    public String rolEnObra(long usuarioId, long obraId) {
        return (String) obras.en(() -> obras.escalar(
                "SELECT rol FROM obra.asignacion WHERE usuario_id = :u AND proyecto_id = :p AND fecha_retiro IS NULL",
                P.de("u", usuarioId, "p", obraId)));
    }
}
