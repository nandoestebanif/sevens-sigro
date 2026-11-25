package pe.sevens.sigro.almacen.datos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Cabeceras de recepciones, traslados y conteos físicos (los movimientos van en {@link MovimientoRepository}). */
public interface DocumentoRepository {
    // recepción
    Map<String, Object> recepcionPorUuid(UUID uuidCliente);
    Map<Long, BigDecimal> recibidoDeCompra(long compraId);
    /** Serializa las recepciones de una misma compra hasta el fin de la transacción. */
    void bloquearCompra(long compraId);
    Map<String, Object> insertarRecepcion(UUID uuid, long obraId, Object almacenId, long compraId, String motivoExceso, long usuarioId, Long dispositivoId, Object fechaDispositivo);

    // traslado
    Map<String, Object> trasladoPorUuid(UUID uuidCliente);
    Map<String, Object> trasladoDeLaObra(long obraId, long trasladoId);
    Map<String, Object> insertarTraslado(UUID uuid, long obraId, Object origenAlmacenId, long destinoObraId, Object destinoAlmacenId, String motivo, long usuarioId);

    // conteo físico
    Map<String, Object> conteoPorUuid(UUID uuidCliente);
    Map<String, Object> conteoDeLaObra(long obraId, long conteoId);
    List<Map<String, Object>> lineasDeConteo(long conteoId);
    Object conteoAbierto(Object almacenId);
    Long insertarConteo(UUID uuid, long obraId, Object almacenId, LocalDate fecha, long usuarioId);
    /** @return false si la base la rechaza porque la diferencia no trae motivo */
    boolean insertarLineaConteo(long conteoId, long insumoId, BigDecimal contada, String motivo);
    void confirmarConteo(long conteoId, long usuarioId);
}
