package pe.sevens.sigro.almacen.datos;

import java.math.BigDecimal;
import java.util.*;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;
import pe.sevens.sigro.almacen.Reglas;
import pe.sevens.sigro.comun.ErrorSigro;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.comun.P;

/** {@link MovimientoRepository} con JDBC sobre el esquema almacen, con el rol de base del módulo (sigro_svc_almacen). */
@Repository
public class JdbcMovimientoRepository implements MovimientoRepository {
    /** Columnas de un movimiento tal como salen en la API (m = movimiento, i = insumo_ref). */
    public static final String COLS = "m.id, m.uuid_cliente, m.tipo, m.sentido, m.proyecto_id, m.almacen_id, m.insumo_id, i.codigo AS insumo_codigo, "
            + "i.unidad, m.cantidad, m.costo_unitario, round(m.cantidad * m.costo_unitario, 2) AS importe, m.moneda, m.motivo, "
            + "m.quien_retira, m.frente, m.origen, m.origen_detalle, m.recepcion_id, m.traslado_id, m.conteo_id, m.revierte_a_id, "
            + "m.periodo_id, m.dispositivo_id, m.registrado_por, m.fecha_dispositivo, m.fecha_servidor";

    private final Modulo db;

    public JdbcMovimientoRepository(Modulo almacen) { this.db = almacen; }

    static long l(Object o) { return ((Number) o).longValue(); }

    static List<Long> ids(List<Object> col) { return col.stream().map(JdbcMovimientoRepository::l).toList(); }

    /** Primer PSQLException en la cadena de causas (null si el error no vino de PostgreSQL). */
    static PSQLException postgres(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) if (t instanceof PSQLException q) return q;
        return null;
    }

    @Override
    public Map<String, Object> porId(long id) {
        return db.fila("SELECT " + COLS + ", (SELECT sum(x.sentido * x.cantidad) FROM almacen.movimiento x WHERE x.almacen_id = m.almacen_id "
                + "AND x.insumo_id = m.insumo_id AND x.id <= m.id) AS saldo FROM almacen.movimiento m JOIN almacen.insumo_ref i ON i.id = m.insumo_id WHERE m.id = :i", P.de("i", id));
    }

    @Override
    public Map<String, Object> identidadPorUuid(UUID uuidCliente) {
        return db.fila("SELECT id, proyecto_id, tipo FROM almacen.movimiento WHERE uuid_cliente = :u", P.de("u", uuidCliente));
    }

    @Override
    public Map<String, Object> deLaObra(long obraId, long id) {
        return db.fila("SELECT * FROM almacen.movimiento WHERE id = :i AND proyecto_id = :p", P.de("i", id, "p", obraId));
    }

    @Override
    public long insertar(Map<String, Object> m) {
        var cols = String.join(", ", m.keySet());
        var vals = new StringJoiner(", ");
        for (String k : m.keySet()) vals.add(":" + k);
        var p = new MapSqlParameterSource(m);
        try {
            return db.anidado(() -> (Long) db.escalar("INSERT INTO almacen.movimiento (" + cols + ") VALUES (" + vals + ") RETURNING id", p));
        } catch (DataAccessException ex) {
            PSQLException pg = postgres(ex);
            ServerErrorMessage sm = pg != null ? pg.getServerErrorMessage() : null;
            String restriccion = sm != null ? sm.getConstraint() : null;
            String mensaje = sm != null && sm.getMessage() != null ? sm.getMessage() : "";
            if ("uq_movimiento_uuid_cliente".equals(restriccion)) throw new MovimientoDuplicado();
            if (mensaje.startsWith("SIGRO-A1")) throw new ErrorSigro(422, "stock_insuficiente", Reglas.capitalizar(mensaje.split(": ", 2)[1]) + ".");
            if (mensaje.startsWith("SIGRO-A4")) throw new ErrorSigro(409, "reverso_de_reverso", "Un reverso no se revierte: registra el movimiento correcto.");
            if ("uq_movimiento_revierte_a".equals(restriccion)) throw new ErrorSigro(409, "ya_revertido", "Ese movimiento ya fue revertido.");
            throw ex;
        }
    }

    @Override
    public List<Long> idsDeRecepcion(long recepcionId) {
        return ids(db.columna("SELECT id FROM almacen.movimiento WHERE recepcion_id = :r AND tipo = 'ingreso_compra' ORDER BY id", P.de("r", recepcionId)));
    }

    @Override
    public List<Long> idsDeTraslado(long trasladoId) {
        return ids(db.columna("SELECT id FROM almacen.movimiento WHERE traslado_id = :t ORDER BY id", P.de("t", trasladoId)));
    }

    @Override
    public List<Long> idsDeReversosDeTraslado(long trasladoId) {
        return ids(db.columna("SELECT id FROM almacen.movimiento WHERE traslado_id = :t AND tipo = 'reverso' ORDER BY id", P.de("t", trasladoId)));
    }

    @Override
    public List<Long> idsDeConteo(long conteoId) {
        return ids(db.columna("SELECT id FROM almacen.movimiento WHERE conteo_id = :i ORDER BY id", P.de("i", conteoId)));
    }

    @Override
    public List<Map<String, Object>> mitadesDeTraslado(long trasladoId) {
        return db.filas("SELECT m.*, r.id AS revertido_por FROM almacen.movimiento m LEFT JOIN almacen.movimiento r ON r.revierte_a_id = m.id "
                + "WHERE m.traslado_id = :t AND m.tipo IN ('traslado_salida', 'traslado_ingreso') ORDER BY (m.tipo = 'traslado_salida'), m.id", P.de("t", trasladoId));
    }

    @Override
    public BigDecimal saldo(long almacenId, long insumoId) {
        return (BigDecimal) db.escalar("SELECT coalesce(sum(sentido * cantidad), 0) AS q FROM almacen.movimiento WHERE almacen_id = :a AND insumo_id = :i",
                P.de("a", almacenId, "i", insumoId));
    }

    @Override
    public void bloquearSaldo(long almacenId, long insumoId) {
        db.fila("SELECT pg_advisory_xact_lock(CAST(:a AS int), CAST(:i AS int))", P.de("a", almacenId, "i", insumoId));
    }

    @Override
    public Map<Long, BigDecimal> recibidoPorCompra(long obraId, Collection<Long> insumoIds) {
        var ya = new HashMap<Long, BigDecimal>();
        for (var r : db.filas("SELECT m.insumo_id, coalesce(sum(m.sentido * m.cantidad), 0) AS recibido FROM almacen.movimiento m "
                + "LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id WHERE m.proyecto_id = :p AND m.insumo_id = ANY(:ids) "
                + "AND coalesce(o.tipo, m.tipo) = 'ingreso_compra' GROUP BY m.insumo_id", P.de("p", obraId, "ids", P.enteros(insumoIds))))
            ya.put(l(r.get("insumo_id")), (BigDecimal) r.get("recibido"));
        return ya;
    }

    @Override
    public void sincronizarAlmacen(Map<String, Object> x) {
        db.actualizar("INSERT INTO almacen.almacen_ref (id, proyecto_id, nombre, tipo, estado) VALUES (:i, :p, :n, :t, :e) "
                + "ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, tipo = EXCLUDED.tipo, estado = EXCLUDED.estado, sincronizado_en = now()",
                P.de("i", x.get("id"), "p", x.get("proyecto_id"), "n", x.get("nombre"), "t", x.get("tipo"), "e", x.get("estado")));
    }

    @Override
    public void sincronizarInsumo(Map<String, Object> x) {
        db.actualizar("INSERT INTO almacen.insumo_ref (id, codigo, descripcion, unidad, tipo, activo) VALUES (:i, :c, :d, :u, :t, :a) "
                + "ON CONFLICT (id) DO UPDATE SET codigo = EXCLUDED.codigo, descripcion = EXCLUDED.descripcion, unidad = EXCLUDED.unidad, "
                + "tipo = EXCLUDED.tipo, activo = EXCLUDED.activo, sincronizado_en = now()",
                P.de("i", x.get("id"), "c", x.get("codigo"), "d", x.get("descripcion"), "u", x.get("unidad_s10"), "t", x.get("tipo"), "a", x.get("activo")));
    }

    @Override
    public List<Map<String, Object>> paraHechos(List<Long> ids) {
        return db.filas("SELECT m.id, m.proyecto_id, m.tipo, coalesce(o.tipo, m.tipo) AS tipo_efectivo, m.sentido, m.cantidad, m.costo_unitario, m.moneda, "
                + "(m.fecha_servidor AT TIME ZONE 'America/Lima')::date AS fecha, i.codigo, i.unidad, i.tipo AS familia FROM almacen.movimiento m "
                + "JOIN almacen.insumo_ref i ON i.id = m.insumo_id LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id WHERE m.id = ANY(:ids) ORDER BY m.id",
                P.de("ids", P.enteros(ids)));
    }

    @Override
    public void encolarEvento(String tipo, long obraId, String payloadJson) {
        db.escalar("INSERT INTO outbox.evento (tipo, proyecto_id, payload, destino) VALUES (:t, :p, CAST(:j AS jsonb), 'integracion') RETURNING id",
                P.de("t", tipo, "p", obraId, "j", payloadJson));
    }
}
