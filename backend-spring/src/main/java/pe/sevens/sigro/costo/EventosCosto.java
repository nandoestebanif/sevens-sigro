package pe.sevens.sigro.costo;

import static pe.sevens.sigro.comun.Esquema.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import pe.sevens.sigro.comun.*;

/**
 * Entrada de eventos de costo (port de app/modulos/costo/interno.py): el relevo del outbox entrega aquí los hechos ya
 * valorizados de almacén. Idempotente por evento_id. Solo para el relevo: exige X-Servicio-Token fuera de desarrollo.
 */
@RestController
public class EventosCosto {
    static final Esquema HECHO = de(entero("proyecto_id"), texto("familia").uno("material", "mano_obra", "equipo"),
            texto("clase").uno("ingreso", "consumo", "traslado", "ajuste", "compra", "horas"), texto("insumo_codigo"), decimal("cantidad"), texto("unidad"),
            decimal("costo_unitario").ge(0), texto("moneda").defecto("PEN").uno("PEN", "USD"), fecha("fecha"), texto("referencia")).permisivo().sinRecortar();
    static final Esquema EVENTO = de(entero("evento_id"), texto("tipo"), entero("proyecto_id"), texto("creado_en").opcional(),
            objeto("datos", de(lista("hechos", HECHO).defecto(List.of()).max(2000)).permisivo())).permisivo().sinRecortar();

    private final Modulo db;
    private final String token;
    private final boolean dev;

    public EventosCosto(Modulo costo, @Value("${SIGRO_SERVICIO_TOKEN:}") String token, @Value("${SIGRO_ENTORNO:dev}") String entorno) {
        this.db = costo;
        this.token = token == null || token.isBlank() ? null : token;
        this.dev = "dev".equals(entorno);
    }

    void exigirServicio(String recibido) {
        if (token == null) {
            if (dev) return;
            throw new ErrorSigro(503, "servicio_no_configurado", "SIGRO_SERVICIO_TOKEN no está definido en este servicio.");
        }
        if (recibido == null || !MessageDigest.isEqual(recibido.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw new ErrorSigro(401, "servicio_no_autorizado", "Esta ruta es solo para los servicios de SIGRO.");
    }

    @PostMapping("/interno/eventos")
    public Object recibir(@RequestHeader(value = "X-Servicio-Token", required = false) String t, @RequestBody(required = false) JsonNode cuerpo) {
        exigirServicio(t);
        Datos ev = EVENTO.validar(cuerpo);
        List<Datos> hechos = ev.objeto("datos").lista("hechos");
        return db.en(() -> {
            Object nuevo = db.escalar("INSERT INTO costo.evento_recibido (evento_id, tipo) VALUES (:i, :t) ON CONFLICT (evento_id) DO NOTHING RETURNING evento_id",
                    P.de("i", ev.entero("evento_id"), "t", ev.texto("tipo")));
            if (nuevo == null) return R.mapa("evento_id", ev.entero("evento_id"), "duplicado", true, "hechos", 0);
            for (Datos h : hechos)
                db.actualizar("INSERT INTO costo.hecho (evento_id, proyecto_id, familia, clase, insumo_codigo, cantidad, unidad, costo_unitario, moneda, fecha, referencia) "
                                + "VALUES (:e, :p, :f, :c, :ic, :q, :u, :cu, :m, :fe, :r)",
                        P.de("e", ev.entero("evento_id"), "p", h.entero("proyecto_id"), "f", h.texto("familia"), "c", h.texto("clase"), "ic", h.texto("insumo_codigo"),
                                "q", h.decimal("cantidad"), "u", h.texto("unidad"), "cu", h.decimal("costo_unitario"), "m", h.texto("moneda"), "fe", h.fecha("fecha"), "r", h.texto("referencia")));
            return R.mapa("evento_id", ev.entero("evento_id"), "duplicado", false, "hechos", hechos.size());
        });
    }
}
