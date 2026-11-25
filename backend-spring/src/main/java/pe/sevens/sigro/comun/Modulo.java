package pe.sevens.sigro.comun;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lo que cada módulo usa para hablar con SU esquema: SQL con nombre y una transacción por operación
 * (confirma si todo salió bien, revierte si no), como la sesión por petición del servicio original.
 * Las filas salen con tipos de Java simples: Instant, LocalDate, BigDecimal, Long, List y JsonNode (jsonb).
 */
public class Modulo {
    private final String nombre;
    private final DataSource ds;
    private final NamedParameterJdbcTemplate db;
    private final TransactionTemplate tx;
    private final TransactionTemplate txNueva;

    private static final ColumnMapRowMapper FILAS = new ColumnMapRowMapper() {
        @Override
        protected Object getColumnValue(ResultSet rs, int i) throws SQLException {
            return normalizar(rs.getObject(i));
        }
    };

    public Modulo(String nombre, DataSource ds) {
        this.nombre = nombre;
        this.ds = ds;
        this.db = new NamedParameterJdbcTemplate(ds);
        var tm = new DataSourceTransactionManager(ds);
        this.tx = new TransactionTemplate(tm);
        this.txNueva = new TransactionTemplate(tm);
        this.txNueva.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    public static Object normalizar(Object o) throws SQLException {
        if (o == null) return null;
        if (o instanceof java.sql.Timestamp t) return t.toInstant();
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof java.sql.Time t) return t.toLocalTime();
        if (o instanceof Integer i) return i.longValue();
        if (o instanceof Short s) return s.longValue();
        if (o instanceof PGobject p) {
            if (p.getValue() == null) return null;
            if ("json".equals(p.getType()) || "jsonb".equals(p.getType())) return Json.aJava(Json.leer(p.getValue()));
            return p.getValue();
        }
        if (o instanceof java.sql.Array a) {
            Object[] arr = (Object[]) a.getArray();
            var l = new ArrayList<Object>();
            for (Object x : arr) l.add(normalizar(x));
            return l;
        }
        return o;
    }

    public String nombre() { return nombre; }
    public NamedParameterJdbcTemplate db() { return db; }
    public DataSource ds() { return ds; }

    /** Ejecuta en una transacción del módulo (o se une a la que ya está abierta en este módulo). */
    public <T> T en(Supplier<T> trabajo) {
        return tx.execute(st -> trabajo.get());
    }

    public void hacer(Runnable trabajo) {
        tx.executeWithoutResult(st -> trabajo.run());
    }

    /** Transacción propia que se confirma aunque la de afuera se revierta (p. ej. el intento fallido de login). */
    public void aparte(Runnable trabajo) {
        txNueva.executeWithoutResult(st -> trabajo.run());
    }

    /** Punto de guardado dentro de la transacción en curso (begin_nested): si falla, solo se revierte esto. */
    public <T> T anidado(Supplier<T> trabajo) {
        java.sql.Connection con = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(ds);
        java.sql.Savepoint sp;
        try {
            sp = con.setSavepoint();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        try {
            T r = trabajo.get();
            con.releaseSavepoint(sp);
            return r;
        } catch (RuntimeException e) {
            try { con.rollback(sp); } catch (SQLException ignorado) { }
            throw e;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean enTransaccion() {
        return TransactionSynchronizationManager.getResource(ds) != null;
    }

    public List<Map<String, Object>> filas(String sql, MapSqlParameterSource p) {
        return db.query(sql, p, FILAS);
    }

    public List<Map<String, Object>> filas(String sql) {
        return filas(sql, new MapSqlParameterSource());
    }

    /** La primera fila o null. */
    public Map<String, Object> fila(String sql, MapSqlParameterSource p) {
        var l = filas(sql, p);
        return l.isEmpty() ? null : l.get(0);
    }

    public Map<String, Object> fila(String sql) { return fila(sql, new MapSqlParameterSource()); }

    /** Primera columna de la primera fila, o null. */
    public Object escalar(String sql, MapSqlParameterSource p) {
        var f = fila(sql, p);
        return f == null ? null : f.values().iterator().next();
    }

    public List<Object> columna(String sql, MapSqlParameterSource p) {
        var l = new ArrayList<Object>();
        for (var f : filas(sql, p)) l.add(f.values().iterator().next());
        return l;
    }

    public int actualizar(String sql, MapSqlParameterSource p) {
        return db.update(sql, p);
    }

    public void ejecutar(String sql) {
        db.getJdbcTemplate().execute(sql);
    }
}
