package pe.sevens.sigro.almacen.datos;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.comun.P;

/** {@link ConsultaRepository} con JDBC: vistas almacen.saldo, saldo_obra y kardex, y agregados del libro. */
@Repository
public class JdbcConsultaRepository implements ConsultaRepository {
    static final String SQL_SEMANA = """
            WITH m AS (
                SELECT m.almacen_id, m.insumo_id, m.periodo_id, m.sentido * m.cantidad AS efecto, coalesce(o.sentido, m.sentido) AS lado
                  FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
                 WHERE m.proyecto_id = :p AND (CAST(:a AS bigint) IS NULL OR m.almacen_id = :a)),
            sem AS (SELECT DISTINCT almacen_id, insumo_id FROM m WHERE periodo_id = :per)
            SELECT sem.almacen_id, r.nombre AS almacen, sem.insumo_id, i.codigo AS insumo_codigo, i.descripcion, i.unidad,
                   coalesce(sum(m.efecto) FILTER (WHERE m.periodo_id = ANY(CAST(:antes AS bigint[]))), 0) AS saldo_inicial,
                   coalesce(sum(m.efecto) FILTER (WHERE m.periodo_id = :per AND m.lado > 0), 0) AS entradas,
                   coalesce(-sum(m.efecto) FILTER (WHERE m.periodo_id = :per AND m.lado < 0), 0) AS salidas
              FROM sem JOIN m ON m.almacen_id = sem.almacen_id AND m.insumo_id = sem.insumo_id
              JOIN almacen.insumo_ref i ON i.id = sem.insumo_id
              JOIN almacen.almacen_ref r ON r.id = sem.almacen_id
             GROUP BY sem.almacen_id, r.nombre, sem.insumo_id, i.codigo, i.descripcion, i.unidad
             ORDER BY r.nombre, i.codigo""";

    private final Modulo db;

    public JdbcConsultaRepository(Modulo almacen) { this.db = almacen; }

    @Override
    public List<Map<String, Object>> stockPorAlmacen(long obraId, Long almacenId) {
        return db.filas("SELECT s.almacen_id, r.nombre AS almacen, s.insumo_id, i.codigo AS insumo_codigo, i.descripcion, i.unidad, "
                + "s.cantidad, s.costo_promedio, coalesce(s.valor, 0) AS valor, s.ultimo_movimiento_en FROM almacen.saldo s JOIN almacen.insumo_ref i ON i.id = s.insumo_id "
                + "JOIN almacen.almacen_ref r ON r.id = s.almacen_id WHERE s.proyecto_id = :p AND (CAST(:a AS bigint) IS NULL OR s.almacen_id = :a) ORDER BY r.nombre, i.codigo",
                P.de("p", obraId, "a", almacenId));
    }

    @Override
    public List<Map<String, Object>> stockConsolidado(long obraId) {
        return db.filas("SELECT NULL::bigint AS almacen_id, NULL::text AS almacen, s.insumo_id, i.codigo AS insumo_codigo, i.descripcion, "
                + "i.unidad, s.cantidad, s.costo_promedio, coalesce(s.valor, 0) AS valor, s.ultimo_movimiento_en FROM almacen.saldo_obra s "
                + "JOIN almacen.insumo_ref i ON i.id = s.insumo_id WHERE s.proyecto_id = :p ORDER BY i.codigo", P.de("p", obraId));
    }

    @Override
    public List<Map<String, Object>> semana(long obraId, Long almacenId, Object periodoId, List<Long> anteriores) {
        return db.filas(SQL_SEMANA, P.de("p", obraId, "a", almacenId, "per", periodoId, "antes", P.enteros(anteriores)));
    }

    @Override
    public List<Map<String, Object>> kardex(long obraId, Long insumoId, Long almacenId) {
        return db.filas("SELECT " + JdbcMovimientoRepository.COLS.replace("m.", "k.") + ", r.motivo_exceso AS recepcion_motivo_exceso, k.revertido_por_id, k.saldo_despues, "
                + "k.promedio_despues FROM almacen.kardex k JOIN almacen.insumo_ref i ON i.id = k.insumo_id LEFT JOIN almacen.recepcion r ON r.id = k.recepcion_id "
                + "WHERE k.proyecto_id = :p AND (CAST(:i AS bigint) IS NULL OR k.insumo_id = :i) AND (CAST(:a AS bigint) IS NULL OR k.almacen_id = :a) ORDER BY k.id LIMIT 2000",
                P.de("p", obraId, "i", insumoId, "a", almacenId));
    }

    // ------------------------------------------------------------------ para compras y costo
    @Override
    public List<Map<String, Object>> recibidoConsumido(long obraId) {
        return db.filas("""
                SELECT m.insumo_id,
                       coalesce(sum(m.sentido * m.cantidad) FILTER (WHERE coalesce(o.tipo, m.tipo) = 'ingreso_compra'), 0) AS recibido,
                       coalesce(-sum(m.sentido * m.cantidad) FILTER (WHERE coalesce(o.tipo, m.tipo) = 'salida'), 0)        AS consumido
                  FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
                 WHERE m.proyecto_id = :p GROUP BY m.insumo_id ORDER BY m.insumo_id""", P.de("p", obraId));
    }

    @Override
    public List<Map<String, Object>> costoPagado(long obraId) {
        return db.filas("""
                SELECT m.insumo_id, sum(m.sentido * m.cantidad) AS cantidad,
                       round(sum(m.sentido * m.cantidad * m.costo_unitario) / nullif(sum(m.sentido * m.cantidad), 0), 6) AS promedio_pagado
                  FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
                 WHERE m.proyecto_id = :p AND coalesce(o.tipo, m.tipo) = 'ingreso_compra'
                 GROUP BY m.insumo_id HAVING sum(m.sentido * m.cantidad) > 0 ORDER BY m.insumo_id""", P.de("p", obraId));
    }

    @Override
    public List<Map<String, Object>> almacenes(long obraId) {
        return db.filas("SELECT id, nombre, tipo, estado FROM almacen.almacen_ref WHERE proyecto_id = :p ORDER BY tipo, nombre", P.de("p", obraId));
    }

    @Override
    public List<Map<String, Object>> saldosValorizados(long obraId, Instant corte) {
        return db.filas("""
                SELECT m.almacen_id, a.nombre AS almacen, m.insumo_id, i.codigo AS insumo_codigo, i.descripcion, i.unidad, i.tipo,
                       sum(m.sentido * m.cantidad) AS cantidad,
                       round(sum(m.sentido * m.cantidad * m.costo_unitario), 2) AS valor,
                       round(sum(m.sentido * m.cantidad * m.costo_unitario) / nullif(sum(m.sentido * m.cantidad), 0), 6) AS costo_promedio,
                       max(m.fecha_servidor) AS ultimo_movimiento_en
                  FROM almacen.movimiento m
                  JOIN almacen.insumo_ref i ON i.id = m.insumo_id
                  JOIN almacen.almacen_ref a ON a.id = m.almacen_id
                 WHERE m.proyecto_id = :p AND m.fecha_servidor <= :c
                 GROUP BY m.almacen_id, a.nombre, m.insumo_id, i.codigo, i.descripcion, i.unidad, i.tipo
                HAVING sum(m.sentido * m.cantidad) <> 0
                 ORDER BY a.nombre, i.codigo""", P.de("p", obraId, "c", Timestamp.from(corte)));
    }

    @Override
    public Map<String, Object> resumenValorizado(long obraId, Instant corte) {
        return db.fila("""
                SELECT coalesce(sum(m.sentido * m.cantidad * m.costo_unitario) FILTER (WHERE coalesce(o.tipo, m.tipo) IN ('ingreso_compra', 'ingreso_sin_compra')), 0) AS recibido,
                       coalesce(-sum(m.sentido * m.cantidad * m.costo_unitario) FILTER (WHERE coalesce(o.tipo, m.tipo) = 'salida'), 0) AS consumido,
                       coalesce(sum(m.sentido * m.cantidad * m.costo_unitario), 0) AS en_stock,
                       max(m.fecha_servidor) AS ultimo_movimiento_en
                  FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
                 WHERE m.proyecto_id = :p AND m.fecha_servidor <= :c""", P.de("p", obraId, "c", Timestamp.from(corte)));
    }

    @Override
    public List<Map<String, Object>> consumoPorInsumo(long obraId, Instant corte) {
        return db.filas("""
                SELECT m.insumo_id, i.codigo AS insumo_codigo, i.descripcion, i.unidad, i.tipo,
                       -sum(m.sentido * m.cantidad) AS cantidad,
                       round(-sum(m.sentido * m.cantidad * m.costo_unitario), 2) AS importe
                  FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
                  JOIN almacen.insumo_ref i ON i.id = m.insumo_id
                 WHERE m.proyecto_id = :p AND m.fecha_servidor <= :c AND coalesce(o.tipo, m.tipo) = 'salida'
                 GROUP BY m.insumo_id, i.codigo, i.descripcion, i.unidad, i.tipo
                HAVING sum(m.sentido * m.cantidad) <> 0 ORDER BY i.codigo""", P.de("p", obraId, "c", Timestamp.from(corte)));
    }
}
