package pe.sevens.sigro.movil.salidas;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Regla 3 · reenviar no duplica. Guarda la respuesta exitosa de cada uuid_cliente (por obra) durante 24 h, con
 * tope de 5 000 entradas (LRU). Si el celular reenvía lo mismo —se cortó la red antes de la respuesta—, se le
 * devuelve la original sin volver a escribir. Si el uuid llega con otro contenido no se usa la copia: decide el
 * servicio dueño (409 uuid_reutilizado). La garantía final sigue siendo UNIQUE(uuid_cliente) en la base.
 */
@Component
public class RegistroIdempotente {
    static final int TOPE = 5_000;
    static final Duration VIGENCIA = Duration.ofHours(24);

    record Respuesta(int estado, byte[] cuerpo, int huella, Instant guardada) {}

    private final Clock reloj;
    private final Map<String, Respuesta> recientes = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Respuesta> mayor) {
            return size() > TOPE;
        }
    };

    public RegistroIdempotente(Optional<Clock> reloj) {
        this.reloj = reloj.orElse(Clock.systemUTC());
    }

    static String clave(int obraId, Object uuid) {
        return obraId + ":" + uuid;
    }

    public synchronized Optional<Respuesta> buscar(String clave, int huellaContenido) {
        Respuesta r = recientes.get(clave);
        if (r == null) return Optional.empty();
        if (r.guardada().plus(VIGENCIA).isBefore(reloj.instant())) {
            recientes.remove(clave);
            return Optional.empty();
        }
        return r.huella() == huellaContenido ? Optional.of(r) : Optional.empty();
    }

    public synchronized void guardar(String clave, int estado, byte[] cuerpo, int huellaContenido) {
        recientes.put(clave, new Respuesta(estado, cuerpo, huellaContenido, reloj.instant()));
    }

    synchronized int tamano() {
        return recientes.size();
    }
}
