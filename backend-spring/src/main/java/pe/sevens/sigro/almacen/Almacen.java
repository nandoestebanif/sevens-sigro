package pe.sevens.sigro.almacen;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import pe.sevens.sigro.comun.Interno;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.comun.P;

/**
 * API interna de almacén: lo que compras y costo le preguntan (recibido y consumido, costo pagado, saldos y resumen
 * valorizados, consumo por insumo). Ninguno lee el esquema almacen. Port de app/modulos/almacen/interno.py.
 */
@Component("almacenInterno")
public class Almacen {
    private final Modulo db;

    public Almacen(Modulo almacen) { this.db = almacen; }

    public List<Map<String, Object>> recibidoConsumido(long obraId) {
        return Interno.json(db.en(() -> db.filas("""
                SELECT m.insumo_id,
                       coalesce(sum(m.sentido * m.cantidad) FILTER (WHERE coalesce(o.tipo, m.tipo) = 'ingreso_compra'), 0) AS recibido,
                       coalesce(-sum(m.sentido * m.cantidad) FILTER (WHERE coalesce(o.tipo, m.tipo) = 'salida'), 0)        AS consumido
                  FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
                 WHERE m.proyecto_id = :p GROUP BY m.insumo_id ORDER BY m.insumo_id""", P.de("p", obraId))));
    }

    public List<Map<String, Object>> costoPagado(long obraId) {
        return Interno.json(db.en(() -> db.filas("""
                SELECT m.insumo_id, sum(m.sentido * m.cantidad) AS cantidad,
                       round(sum(m.sentido * m.cantidad * m.costo_unitario) / nullif(sum(m.sentido * m.cantidad), 0), 6) AS promedio_pagado
                  FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
                 WHERE m.proyecto_id = :p AND coalesce(o.tipo, m.tipo) = 'ingreso_compra'
                 GROUP BY m.insumo_id HAVING sum(m.sentido * m.cantidad) > 0 ORDER BY m.insumo_id""", P.de("p", obraId))));
    }

    static Instant efectivo(Instant corte) {
        Instant ahora = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return corte != null && corte.isBefore(ahora) ? corte : ahora;
    }

    public Map<String, Object> saldosValorizados(long obraId, Instant corte) {
        Instant ef = efectivo(corte);
        return Interno.json(db.en(() -> {
            var almacenes = db.filas("SELECT id, nombre, tipo, estado FROM almacen.almacen_ref WHERE proyecto_id = :p ORDER BY tipo, nombre", P.de("p", obraId));
            var filas = db.filas("""
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
                     ORDER BY a.nombre, i.codigo""", P.de("p", obraId, "c", java.sql.Timestamp.from(ef)));
            return P.mapa("corte", ef, "almacenes", almacenes, "filas", filas);
        }));
    }

    public Map<String, Object> resumenValorizado(long obraId, Instant corte) {
        Instant ef = efectivo(corte);
        return Interno.json(db.en(() -> {
            var r = db.fila("""
                    SELECT coalesce(sum(m.sentido * m.cantidad * m.costo_unitario) FILTER (WHERE coalesce(o.tipo, m.tipo) IN ('ingreso_compra', 'ingreso_sin_compra')), 0) AS recibido,
                           coalesce(-sum(m.sentido * m.cantidad * m.costo_unitario) FILTER (WHERE coalesce(o.tipo, m.tipo) = 'salida'), 0) AS consumido,
                           coalesce(sum(m.sentido * m.cantidad * m.costo_unitario), 0) AS en_stock,
                           max(m.fecha_servidor) AS ultimo_movimiento_en
                      FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
                     WHERE m.proyecto_id = :p AND m.fecha_servidor <= :c""", P.de("p", obraId, "c", java.sql.Timestamp.from(ef)));
            var out = new LinkedHashMap<String, Object>();
            for (var e : r.entrySet())
                out.put(e.getKey(), e.getKey().equals("ultimo_movimiento_en") ? e.getValue()
                        : ((java.math.BigDecimal) e.getValue()).setScale(2, java.math.RoundingMode.HALF_EVEN));
            var b = (java.math.BigDecimal) out.get("en_stock");
            out.put("otros", b.subtract((java.math.BigDecimal) out.get("recibido")).add((java.math.BigDecimal) out.get("consumido")).setScale(2, java.math.RoundingMode.HALF_EVEN));
            out.put("corte", ef);
            return out;
        }));
    }

    public Map<String, Object> consumoPorInsumo(long obraId, Instant corte) {
        Instant ef = efectivo(corte);
        return Interno.json(db.en(() -> P.mapa("corte", ef, "filas", db.filas("""
                SELECT m.insumo_id, i.codigo AS insumo_codigo, i.descripcion, i.unidad, i.tipo,
                       -sum(m.sentido * m.cantidad) AS cantidad,
                       round(-sum(m.sentido * m.cantidad * m.costo_unitario), 2) AS importe
                  FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
                  JOIN almacen.insumo_ref i ON i.id = m.insumo_id
                 WHERE m.proyecto_id = :p AND m.fecha_servidor <= :c AND coalesce(o.tipo, m.tipo) = 'salida'
                 GROUP BY m.insumo_id, i.codigo, i.descripcion, i.unidad, i.tipo
                HAVING sum(m.sentido * m.cantidad) <> 0 ORDER BY i.codigo""", P.de("p", obraId, "c", java.sql.Timestamp.from(ef))))));
    }
}
