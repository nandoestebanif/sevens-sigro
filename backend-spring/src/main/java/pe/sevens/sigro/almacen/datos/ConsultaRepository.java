package pe.sevens.sigro.almacen.datos;

import java.time.Instant;
import java.util.*;

/** Consultas de lectura: stock, semana, kárdex y los resúmenes que piden compras y costo. */
public interface ConsultaRepository {
    List<Map<String, Object>> stockPorAlmacen(long obraId, Long almacenId);
    List<Map<String, Object>> stockConsolidado(long obraId);
    List<Map<String, Object>> semana(long obraId, Long almacenId, Object periodoId, List<Long> periodosAnteriores);
    List<Map<String, Object>> kardex(long obraId, Long insumoId, Long almacenId);

    List<Map<String, Object>> recibidoConsumido(long obraId);
    List<Map<String, Object>> costoPagado(long obraId);
    List<Map<String, Object>> almacenes(long obraId);
    List<Map<String, Object>> saldosValorizados(long obraId, Instant corte);
    Map<String, Object> resumenValorizado(long obraId, Instant corte);
    List<Map<String, Object>> consumoPorInsumo(long obraId, Instant corte);
}
