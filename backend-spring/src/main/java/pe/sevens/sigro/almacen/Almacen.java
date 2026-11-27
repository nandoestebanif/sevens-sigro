package pe.sevens.sigro.almacen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;
import pe.sevens.sigro.almacen.datos.ConsultaRepository;
import pe.sevens.sigro.comun.Interno;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.comun.P;

/**
 * API interna de almacén: lo que compras y costo le preguntan (recibido y consumido, costo pagado, saldos y resumen
 * valorizados, consumo por insumo). Ninguno lee el esquema almacen: pasan por aquí. Port de app/modulos/almacen/interno.py.
 */
@Component("almacenInterno")
public class Almacen {
    private final Modulo db;
    private final ConsultaRepository consultas;
    private final AlmacenService servicio;

    public Almacen(Modulo almacen, ConsultaRepository consultas, AlmacenService servicio) {
        this.db = almacen;
        this.consultas = consultas;
        this.servicio = servicio;
    }

    /** El corte pedido, sin pasar de ahora (un corte futuro no ve nada que aún no existe). */
    static Instant efectivo(Instant corte) {
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return corte != null && corte.isBefore(ahora) ? corte : ahora;
    }

    public List<Map<String, Object>> recibidoConsumido(long obraId) {
        return Interno.json(db.en(() -> consultas.recibidoConsumido(obraId)));
    }

    public List<Map<String, Object>> costoPagado(long obraId) {
        return Interno.json(db.en(() -> consultas.costoPagado(obraId)));
    }

    public Map<String, Object> saldosValorizados(long obraId, Instant corte) {
        Instant ef = efectivo(corte);
        return Interno.json(db.en(() -> P.mapa("corte", ef, "almacenes", consultas.almacenes(obraId), "filas", consultas.saldosValorizados(obraId, ef))));
    }

    public Map<String, Object> resumenValorizado(long obraId, Instant corte) {
        Instant ef = efectivo(corte);
        return Interno.json(db.en(() -> {
            var r = consultas.resumenValorizado(obraId, ef);
            var out = new LinkedHashMap<String, Object>();
            for (var e : r.entrySet())
                out.put(e.getKey(), e.getKey().equals("ultimo_movimiento_en") ? e.getValue() : ((BigDecimal) e.getValue()).setScale(2, RoundingMode.HALF_EVEN));
            var enStock = (BigDecimal) out.get("en_stock");
            out.put("otros", enStock.subtract((BigDecimal) out.get("recibido")).add((BigDecimal) out.get("consumido")).setScale(2, RoundingMode.HALF_EVEN));
            out.put("corte", ef);
            return out;
        }));
    }

    /**
     * HU-73: ¿queda algo por recibir de esa compra? Lo mismo que responde GET /compras/{id}/recepcion: alguna línea con
     * pendiente mayor que cero. Así el cotejo del QR y la pantalla de recepción no pueden decir cosas distintas.
     */
    public boolean quedaPorRecibir(long obraId, long compraId) {
        return servicio.estadoRecepcion(obraId, compraId).stream().anyMatch(x -> ((BigDecimal) x.get("pendiente")).signum() > 0);
    }

    public Map<String, Object> consumoPorInsumo(long obraId, Instant corte) {
        Instant ef = efectivo(corte);
        return Interno.json(db.en(() -> P.mapa("corte", ef, "filas", consultas.consumoPorInsumo(obraId, ef))));
    }
}
