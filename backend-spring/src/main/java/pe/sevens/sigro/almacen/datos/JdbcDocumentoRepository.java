package pe.sevens.sigro.almacen.datos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.comun.P;

/** {@link DocumentoRepository} con JDBC sobre almacen.recepcion, almacen.traslado y almacen.conteo. */
@Repository
public class JdbcDocumentoRepository implements DocumentoRepository {
    private final Modulo db;

    public JdbcDocumentoRepository(Modulo almacen) { this.db = almacen; }

    // ------------------------------------------------------------------ recepción
    @Override
    public Map<String, Object> recepcionPorUuid(UUID u) {
        return db.fila("SELECT id, uuid_cliente, proyecto_id, compra_id FROM almacen.recepcion WHERE uuid_cliente = :u", P.de("u", u));
    }

    @Override
    public Map<Long, BigDecimal> recibidoDeCompra(long compraId) {
        var recibido = new HashMap<Long, BigDecimal>();
        for (var r : db.filas("SELECT insumo_id, recibido FROM almacen.recibido_por_compra WHERE compra_id = :c", P.de("c", compraId)))
            recibido.put(JdbcMovimientoRepository.l(r.get("insumo_id")), (BigDecimal) r.get("recibido"));
        return recibido;
    }

    @Override
    public void bloquearCompra(long compraId) {
        db.fila("SELECT pg_advisory_xact_lock(hashtext('sigro.recepcion'), CAST(:c AS int))", P.de("c", compraId));
    }

    @Override
    public Map<String, Object> insertarRecepcion(UUID u, long obraId, Object almacenId, long compraId, String motivoExceso, long usuarioId, Long dispositivoId,
                                                 Object fechaDispositivo) {
        return db.fila("INSERT INTO almacen.recepcion (uuid_cliente, proyecto_id, almacen_id, compra_id, motivo_exceso, registrado_por, dispositivo_id, fecha_dispositivo) "
                + "VALUES (:u, :p, :a, :c, :m, :r, :d, :f) RETURNING id, uuid_cliente", P.de("u", u, "p", obraId, "a", almacenId, "c", compraId,
                "m", motivoExceso, "r", usuarioId, "d", dispositivoId, "f", fechaDispositivo));
    }

    // ------------------------------------------------------------------ traslado
    @Override
    public Map<String, Object> trasladoPorUuid(UUID u) {
        return db.fila("SELECT id, uuid_cliente, origen_proyecto_id FROM almacen.traslado WHERE uuid_cliente = :u", P.de("u", u));
    }

    @Override
    public Map<String, Object> trasladoDeLaObra(long obraId, long trasladoId) {
        return db.fila("SELECT id, uuid_cliente, origen_proyecto_id, destino_proyecto_id FROM almacen.traslado WHERE id = :i AND origen_proyecto_id = :p",
                P.de("i", trasladoId, "p", obraId));
    }

    @Override
    public Map<String, Object> insertarTraslado(UUID u, long obraId, Object origenAlmacenId, long destinoObraId, Object destinoAlmacenId, String motivo, long usuarioId) {
        return db.fila("INSERT INTO almacen.traslado (uuid_cliente, origen_proyecto_id, origen_almacen_id, destino_proyecto_id, destino_almacen_id, motivo, registrado_por) "
                + "VALUES (:u, :op, :oa, :dp, :da, :m, :r) RETURNING id, uuid_cliente", P.de("u", u, "op", obraId, "oa", origenAlmacenId, "dp", destinoObraId,
                "da", destinoAlmacenId, "m", motivo, "r", usuarioId));
    }

    // ------------------------------------------------------------------ conteo físico
    @Override
    public Map<String, Object> conteoPorUuid(UUID u) {
        return db.fila("SELECT id, proyecto_id FROM almacen.conteo WHERE uuid_cliente = :u", P.de("u", u));
    }

    @Override
    public Map<String, Object> conteoDeLaObra(long obraId, long conteoId) {
        return db.fila("SELECT id, uuid_cliente, almacen_id, fecha, estado, registrado_por, confirmado_por, confirmado_en FROM almacen.conteo WHERE id = :i AND proyecto_id = :p",
                P.de("i", conteoId, "p", obraId));
    }

    @Override
    public List<Map<String, Object>> lineasDeConteo(long conteoId) {
        return db.filas("SELECT insumo_id, saldo_teorico, cantidad_contada, diferencia, motivo FROM almacen.conteo_linea WHERE conteo_id = :i ORDER BY id", P.de("i", conteoId));
    }

    @Override
    public Object conteoAbierto(Object almacenId) {
        return db.escalar("SELECT id FROM almacen.conteo WHERE almacen_id = :a AND estado = 'borrador'", P.de("a", almacenId));
    }

    @Override
    public Long insertarConteo(UUID u, long obraId, Object almacenId, LocalDate fecha, long usuarioId) {
        return (Long) db.escalar("INSERT INTO almacen.conteo (uuid_cliente, proyecto_id, almacen_id, fecha, registrado_por) VALUES (:u, :p, :a, :f, :r) RETURNING id",
                P.de("u", u, "p", obraId, "a", almacenId, "f", fecha, "r", usuarioId));
    }

    @Override
    public boolean insertarLineaConteo(long conteoId, long insumoId, BigDecimal contada, String motivo) {
        try {
            db.anidado(() -> db.actualizar("INSERT INTO almacen.conteo_linea (conteo_id, insumo_id, saldo_teorico, cantidad_contada, motivo) VALUES (:c, :i, 0, :q, :m)",
                    P.de("c", conteoId, "i", insumoId, "q", contada, "m", motivo)));
            return true;
        } catch (DataAccessException ex) {
            PSQLException pg = JdbcMovimientoRepository.postgres(ex);
            if (pg != null && pg.getServerErrorMessage() != null && "ck_conteo_linea_motivo".equals(pg.getServerErrorMessage().getConstraint())) return false;
            throw ex;
        }
    }

    @Override
    public void confirmarConteo(long conteoId, long usuarioId) {
        db.actualizar("UPDATE almacen.conteo SET estado = 'confirmado', confirmado_por = :u, confirmado_en = now() WHERE id = :i", P.de("u", usuarioId, "i", conteoId));
    }
}
