package pe.sevens.sigro.compras;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;
import pe.sevens.sigro.comun.*;

/** API interna de compras: lo que almacén y costo le preguntan (port de app/modulos/compras/interno.py). */
@Component("comprasInterno")
public class Compras {
    private final Modulo db;

    public Compras(Modulo compras) { this.db = compras; }

    /** Cabecera y líneas; factor_sin_igv = base imponible / suma de líneas (NIC 2: el IGV recuperable no es costo). */
    public Map<String, Object> compra(long compraId) {
        return Interno.json(db.en(() -> {
            var c = db.fila("SELECT id, proyecto_id, estado, moneda, fecha, base_imponible, igv, total FROM compras.compra WHERE id = :i", P.de("i", compraId));
            if (c == null) throw new ErrorSigro(404, "compra_no_existe", "Esa compra no existe.");
            var lineas = db.filas("SELECT insumo_id, cantidad, unidad, precio_unitario FROM compras.compra_detalle WHERE compra_id = :i ORDER BY id", P.de("i", compraId));
            BigDecimal bruto = BigDecimal.ZERO;
            for (var l : lineas) bruto = bruto.add(((BigDecimal) l.get("cantidad")).multiply((BigDecimal) l.get("precio_unitario"), Dominio.PY), Dominio.PY);
            var m = new LinkedHashMap<>(c);
            m.put("lineas", lineas);
            m.put("factor_sin_igv", bruto.signum() != 0 ? ((BigDecimal) c.get("base_imponible")).divide(bruto, Dominio.PY) : BigDecimal.ONE);
            return m;
        }));
    }

    public Map<String, Object> comprado(long obraId, LocalDate hasta) {
        return Interno.json(db.en(() -> db.fila("SELECT count(*) AS compras, coalesce(sum(base_imponible), 0) AS base_imponible, coalesce(sum(igv), 0) AS igv, "
                + "coalesce(sum(total), 0) AS total, max(fecha) AS hasta, max(creado_en) AS corte FROM compras.compra WHERE proyecto_id = :p AND estado = 'registrada' "
                + "AND (CAST(:h AS date) IS NULL OR fecha <= CAST(:h AS date))", P.de("p", obraId, "h", hasta))));
    }

    public List<Map<String, Object>> parametrosReposicion(long obraId) {
        return Interno.json(db.en(() -> db.filas("SELECT insumo_id, stock_seguridad, punto_reposicion FROM compras.parametro_reposicion_vigente WHERE proyecto_id = :p", P.de("p", obraId))));
    }

    public List<Map<String, Object>> requerido(long obraId) {
        return Interno.json(db.en(() -> db.filas("SELECT insumo_id, cantidad_planificada AS requerido, precio_planificado FROM compras.requerimiento "
                + "WHERE proyecto_id = :p ORDER BY insumo_id", P.de("p", obraId))));
    }
}
