package pe.sevens.sigro.comun;

import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Publicación de eventos en la bandeja de salida (outbox), DENTRO de la transacción del hecho que los origina.
 * Quién tiene qué rol en cada obra es dato de obras: los destinatarios se le piden a ese módulo.
 */
@Component
public class Eventos {
    private final Modulo obras;

    public Eventos(Modulo obras) { this.obras = obras; }

    public List<Long> destinatariosConRol(long proyectoId, Collection<String> roles, Long excepto) {
        List<Object> ids = obras.en(() -> obras.columna(
                "SELECT DISTINCT a.usuario_id FROM obra.asignacion a JOIN obra.usuario u ON u.id = a.usuario_id "
                        + "WHERE a.proyecto_id = :p AND a.fecha_retiro IS NULL AND u.estado = 'activo' AND a.rol = ANY(:r) ORDER BY 1",
                P.de("p", proyectoId, "r", P.textos(new TreeSet<>(roles)))));
        var l = new ArrayList<Long>();
        for (Object o : ids) if (!Objects.equals(o, excepto)) l.add((Long) o);
        return l;
    }

    /** Encola el evento con la conexión del módulo que lo origina. Sin destinatarios no se publica nada. */
    public Long publicar(Modulo m, String tipo, long proyectoId, Collection<Long> destinatarios, String titulo, String mensaje, Map<String, Object> datos) {
        var dest = new ArrayList<>(new TreeSet<>(destinatarios));
        if (dest.isEmpty()) return null;
        var payload = P.mapa("destinatarios", dest, "titulo", titulo, "mensaje", mensaje, "datos", datos == null ? Map.of() : datos);
        return (Long) m.escalar("INSERT INTO outbox.evento (tipo, proyecto_id, payload) VALUES (:t, :p, CAST(:j AS jsonb)) RETURNING id",
                P.de("t", tipo, "p", proyectoId, "j", Json.texto(payload)));
    }

    public String nombreObra(long proyectoId) {
        Object n = obras.en(() -> obras.escalar("SELECT nombre FROM obra.proyecto WHERE id = :p", P.de("p", proyectoId)));
        return n != null ? (String) n : "obra " + proyectoId;
    }
}
