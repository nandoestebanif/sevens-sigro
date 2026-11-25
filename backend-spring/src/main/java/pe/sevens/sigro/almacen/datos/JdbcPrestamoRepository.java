package pe.sevens.sigro.almacen.datos;

import java.math.BigDecimal;
import java.util.*;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;
import pe.sevens.sigro.comun.ErrorSigro;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.comun.P;

/** {@link PrestamoRepository} con JDBC sobre almacen.prestamo. */
@Repository
public class JdbcPrestamoRepository implements PrestamoRepository {
    static final String VISTA = "SELECT p.id, p.uuid_cliente, p.proyecto_id, p.almacen_id, a.nombre AS almacen, p.insumo_id, i.codigo AS insumo_codigo, "
            + "i.descripcion, i.unidad, p.cantidad, p.trabajador_id, p.responsable, p.devolver_el, p.nota, p.registrado_por, p.prestado_en, "
            + "p.devuelto_en, p.devuelto_por, p.estado_devolucion, p.nota_devolucion, "
            + "(p.devuelto_en IS NULL AND p.devolver_el < (now() AT TIME ZONE 'America/Lima')::date) AS atrasado "
            + "FROM almacen.prestamo p JOIN almacen.almacen_ref a ON a.id = p.almacen_id JOIN almacen.insumo_ref i ON i.id = p.insumo_id ";

    private final Modulo db;

    public JdbcPrestamoRepository(Modulo almacen) { this.db = almacen; }

    @Override
    public Map<String, Object> identidadPorUuid(UUID u) {
        return db.fila("SELECT id, proyecto_id FROM almacen.prestamo WHERE uuid_cliente = :u", P.de("u", u));
    }

    @Override
    public Map<String, Object> porId(long id) {
        return db.fila(VISTA + "WHERE p.id = :i", P.de("i", id));
    }

    @Override
    public Map<String, Object> deLaObraParaDevolver(long obraId, long id) {
        return db.fila("SELECT id, devuelto_en FROM almacen.prestamo WHERE id = :i AND proyecto_id = :p FOR UPDATE", P.de("i", id, "p", obraId));
    }

    @Override
    public BigDecimal prestadoSinDevolver(long almacenId, long insumoId) {
        return (BigDecimal) db.escalar("SELECT coalesce(sum(cantidad), 0) FROM almacen.prestamo WHERE almacen_id = :a AND insumo_id = :i AND devuelto_en IS NULL",
                P.de("a", almacenId, "i", insumoId));
    }

    @Override
    public long insertar(Map<String, Object> m) {
        var cols = String.join(", ", m.keySet());
        var vals = new StringJoiner(", ");
        for (String k : m.keySet()) vals.add(":" + k);
        try {
            return db.anidado(() -> (Long) db.escalar("INSERT INTO almacen.prestamo (" + cols + ") VALUES (" + vals + ") RETURNING id", new MapSqlParameterSource(m)));
        } catch (DataAccessException ex) {
            PSQLException pg = JdbcMovimientoRepository.postgres(ex);
            if (pg != null && pg.getServerErrorMessage() != null && "uq_prestamo_uuid_cliente".equals(pg.getServerErrorMessage().getConstraint()))
                throw new MovimientoDuplicado();
            throw ex;
        }
    }

    @Override
    public void devolver(long id, long usuarioId, String estado, String nota) {
        try {
            db.actualizar("UPDATE almacen.prestamo SET devuelto_en = now(), devuelto_por = :u, estado_devolucion = :e, nota_devolucion = :n WHERE id = :i",
                    P.de("u", usuarioId, "e", estado, "n", nota, "i", id));
        } catch (DataAccessException ex) {
            PSQLException pg = JdbcMovimientoRepository.postgres(ex);
            if (pg != null && pg.getServerErrorMessage() != null && String.valueOf(pg.getServerErrorMessage().getMessage()).startsWith("SIGRO-H1"))
                throw new ErrorSigro(409, "ya_devuelto", "Ese préstamo ya se devolvió.");
            throw ex;
        }
    }

    @Override
    public List<Map<String, Object>> deLaObra(long obraId, boolean soloSinDevolver) {
        return db.filas(VISTA + "WHERE p.proyecto_id = :p" + (soloSinDevolver ? " AND p.devuelto_en IS NULL" : "")
                + " ORDER BY (p.devuelto_en IS NULL) DESC, p.devolver_el NULLS LAST, p.id DESC LIMIT 1000", P.de("p", obraId));
    }
}
