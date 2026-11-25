package pe.sevens.sigro.almacen.datos;

import java.math.BigDecimal;
import java.util.*;

/**
 * Acceso al libro de movimientos (almacen.movimiento), a las copias locales de almacenes e insumos y a la bandeja de
 * salida de eventos. Las operaciones devuelven filas como mapas columna → valor, igual que las respuestas JSON.
 */
public interface MovimientoRepository {
    /** Movimiento con su insumo y el saldo acumulado hasta él. */
    Map<String, Object> porId(long id);

    /** id, proyecto_id y tipo del movimiento con ese uuid_cliente; null si no existe. */
    Map<String, Object> identidadPorUuid(UUID uuidCliente);

    /** Movimiento completo de la obra (para revertirlo); null si no es de esa obra. */
    Map<String, Object> deLaObra(long obraId, long id);

    /**
     * Inserta un movimiento en su propio punto de guardado. El disparador de la base valida el stock y congela el costo.
     * @throws MovimientoDuplicado si el uuid_cliente ya existe
     * @throws pe.sevens.sigro.comun.ErrorSigro stock insuficiente (422), reverso de reverso o ya revertido (409)
     */
    long insertar(Map<String, Object> movimiento);

    List<Long> idsDeRecepcion(long recepcionId);
    List<Long> idsDeTraslado(long trasladoId);
    List<Long> idsDeReversosDeTraslado(long trasladoId);
    List<Long> idsDeConteo(long conteoId);

    /** Las dos mitades de un traslado, con el id de su reverso si lo tienen (ingreso primero). */
    List<Map<String, Object>> mitadesDeTraslado(long trasladoId);

    BigDecimal saldo(long almacenId, long insumoId);

    /** Bloqueo por almacén e insumo hasta el fin de la transacción (conteo físico). */
    void bloquearSaldo(long almacenId, long insumoId);

    /** Cuánto se recibió por compra en la obra, por insumo (reversos incluidos). */
    Map<Long, BigDecimal> recibidoPorCompra(long obraId, Collection<Long> insumoIds);

    void sincronizarAlmacen(Map<String, Object> almacen);
    void sincronizarInsumo(Map<String, Object> insumo);

    /** Datos de los movimientos para valorizarlos como hechos hacia costo. */
    List<Map<String, Object>> paraHechos(List<Long> ids);

    /** Encola un evento en outbox.evento, en la MISMA transacción (patrón outbox). */
    void encolarEvento(String tipo, long obraId, String payloadJson);
}
