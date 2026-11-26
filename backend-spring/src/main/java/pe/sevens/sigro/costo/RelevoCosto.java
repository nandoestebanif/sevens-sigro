package pe.sevens.sigro.costo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.sevens.sigro.comun.*;

/**
 * Relevo del outbox hacia costo DENTRO del proceso (port de app/relay.py para el destino «integracion»).
 *
 * <p>En el backend Spring todo corre en un solo proceso y no hay un relevo aparte: sin esto los hechos valorizados de
 * almacén se quedaban en la bandeja y «Gasto en recursos» mostraba «faltan eventos por entregar» y el gráfico por
 * familia vacío. Garantías, las mismas que el relevo de Python:
 * <ul>
 *   <li>Cada evento se reclama con FOR UPDATE SKIP LOCKED: dos instancias no se pisan.</li>
 *   <li>Guardar los hechos y marcar el evento como enviado ocurren en la MISMA transacción del módulo costo: o pasan las
 *       dos cosas o ninguna. Y costo deduplica por evento_id, así que un reintento nunca duplica.</li>
 *   <li>Un evento que falla no frena a los demás: se reintenta con espera creciente (5 s, 10 s, 20 s … tope 1 h).</li>
 * </ul>
 * Se apaga con SIGRO_RELEVO=0 (por ejemplo, si vuelve a correr el relevo de Python).
 */
@Component
public class RelevoCosto {
    private static final Logger LOG = LoggerFactory.getLogger(RelevoCosto.class);
    static final int POR_VUELTA = 200;

    private final Modulo db;
    private final EventosCosto eventos;
    private final boolean activo;

    public RelevoCosto(Modulo costo, EventosCosto eventos, @Value("${SIGRO_RELEVO:1}") String activo) {
        this.db = costo;
        this.eventos = eventos;
        this.activo = !"0".equals(activo);
    }

    static long espera(int intentos) { return Math.min(5L << Math.max(intentos - 1, 0), 3600L); }

    @Scheduled(initialDelayString = "${SIGRO_RELEVO_INICIO_MS:20000}", fixedDelayString = "${SIGRO_RELEVO_MS:5000}")
    public void vuelta() {
        if (!activo) return;
        int enviados = 0, fallidos = 0;
        try {
            for (int i = 0; i < POR_VUELTA; i++) {
                Boolean r = uno();
                if (r == null) break;
                if (r) enviados++; else fallidos++;
            }
        } catch (RuntimeException e) {
            LOG.warn("Relevo de costo: la vuelta se cortó ({}); sigue en la próxima.", e.getClass().getSimpleName());
        }
        if (enviados + fallidos > 0) LOG.info("Relevo de costo: {} entregados, {} para reintentar", enviados, fallidos);
    }

    /** true = entregado, false = falló y quedó para reintento, null = no hay nada pendiente. */
    Boolean uno() {
        return db.en(() -> {
            var ev = db.fila("SELECT id, tipo, proyecto_id, payload::text AS payload, creado_en::text AS creado_en, intentos FROM outbox.evento "
                    + "WHERE enviado_en IS NULL AND destino = 'integracion' AND siguiente_intento <= now() ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED");
            if (ev == null) return null;
            long id = ((Number) ev.get("id")).longValue();
            try {
                var cuerpo = Json.M.createObjectNode().put("evento_id", id).put("tipo", (String) ev.get("tipo"))
                        .put("proyecto_id", ((Number) ev.get("proyecto_id")).longValue()).put("creado_en", String.valueOf(ev.get("creado_en")));
                cuerpo.set("datos", Json.leer((String) ev.get("payload")));
                db.anidado(() -> eventos.registrar(cuerpo));
                db.actualizar("UPDATE outbox.evento SET enviado_en = now(), intentos = intentos + 1, ultimo_error = NULL WHERE id = :i", P.de("i", id));
                return true;
            } catch (RuntimeException e) {
                int n = ((Number) ev.get("intentos")).intValue() + 1;
                String motivo = e instanceof ErrorSigro es ? es.codigo() : e.getClass().getSimpleName();
                db.actualizar("UPDATE outbox.evento SET intentos = :n, ultimo_error = :e, siguiente_intento = now() + make_interval(secs => :w) WHERE id = :i",
                        P.de("n", n, "e", motivo, "w", (int) espera(n), "i", id));
                return false;
            }
        });
    }
}
