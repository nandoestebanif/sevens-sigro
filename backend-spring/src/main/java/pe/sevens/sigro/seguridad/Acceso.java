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
    /** 27/11: el PIN es obligatorio en las acciones de CON_PIN (quien no lo tiene, debe crearlo primero). Solo las pruebas
        de paridad lo apagan (SIGRO_PIN_OBLIGATORIO=0) y nunca en producción: ver {@link #pinObligatorio}. */
    private final boolean pinObligatorio;

    public Acceso(Modulo obras, Tokens tokens,
                  @org.springframework.beans.factory.annotation.Value("${SIGRO_PIN_OBLIGATORIO:1}") String pinObligatorio,
                  @org.springframework.beans.factory.annotation.Value("${SIGRO_ENTORNO:}") String entorno) {
        this.obras = obras;
        this.tokens = tokens;
        this.pinObligatorio = pinObligatorio(pinObligatorio, entorno);
    }

    /** Apagar el PIN obligatorio solo se acepta fuera de producción: en producción siempre se exige. */
    static boolean pinObligatorio(String valor, String entorno) {
        if ("produccion".equalsIgnoreCase(entorno == null ? "" : entorno.strip())) return true;
        return !"0".equals(valor == null ? "" : valor.strip()) && !"false".equalsIgnoreCase(valor == null ? "" : valor.strip());
    }

    static ErrorSigro noAutenticado() { return new ErrorSigro(401, "no_autenticado", "Inicia sesión para continuar."); }
    static ErrorSigro sinPermiso() { return new ErrorSigro(403, "sin_permiso", "No tienes permiso para esta operación en esta obra."); }

    /**
     * Acciones que exigen el PIN (cabecera X-SIGRO-PIN; sin PIN configurado: 403 pin_no_configurado):
     * el panel de administración y lo irreversible o que cambia el plan de la obra (26/11, decisión del equipo).
     * «compra.anular» no es una acción de la matriz: el permiso es compra.registrar y aquí solo se marca que pide PIN.
     */
    public static final Set<String> CON_PIN = Set.of("usuario.crear", "usuario.consultar", "asignacion.escribir", "dispositivo.autorizar", "bitacora.consultar",
            "periodo.cerrar", "proyecto.cerrar", "proyecto.ampliar_plazo", "proyecto.recortar_plazo", "compra.anular", "requerimiento.ajustar", "insumo.factor");

    /** Roles que tienen alguna acción con PIN: solo ellos pueden crear o usar un PIN. */
    public static Set<String> rolesConPin() {
        var r = new java.util.TreeSet<String>();
        for (String a : CON_PIN) r.addAll(Permisos.roles("compra.anular".equals(a) ? "compra.registrar" : a));
        return r;
    }

    private static String cabecera(String nombre) {
        var a = RequestContextHolder.getRequestAttributes();
        return a instanceof ServletRequestAttributes sa ? sa.getRequest().getHeader(nombre) : null;
    }

    /** ¿Tiene PIN configurado? (sin PIN, el panel web obliga a crearlo antes de entrar). */
    public boolean tienePin(long usuarioId) {
        return obras.en(() -> obras.escalar("SELECT pin_hash FROM obra.usuario WHERE id = :i", P.de("i", usuarioId))) != null;
    }

    public void exigirPin(Actor a, String accion) {
        if (!CON_PIN.contains(accion)) return;
        if (!tienePin(a.usuarioId())) {
            if (!pinObligatorio) return;
            throw new ErrorSigro(403, "pin_no_configurado", "Esta acción pide PIN y aún no tienes uno: créalo con tu contraseña y vuelve a intentarlo.");
        }
        String t = cabecera("X-SIGRO-PIN");
        Long uid = t == null ? null : tokens.leerPin(t);
        if (uid == null || uid != a.usuarioId()) throw new ErrorSigro(403, "pin_requerido", "Esta acción pide tu PIN: escríbelo para continuar.");
    }

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
        var actor = new Actor(a.usuarioId(), a.nombre(), obraId, rol);
        exigirPin(actor, accion);
        return actor;
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
        var actor = new Actor(a.usuarioId(), a.nombre(), (Long) f.get("proyecto_id"), (String) f.get("rol"));
        exigirPin(actor, accion);
        return actor;
    }

    public String rolEnObra(long usuarioId, long obraId) {
        return (String) obras.en(() -> obras.escalar(
                "SELECT rol FROM obra.asignacion WHERE usuario_id = :u AND proyecto_id = :p AND fecha_retiro IS NULL",
                P.de("u", usuarioId, "p", obraId)));
    }
}
