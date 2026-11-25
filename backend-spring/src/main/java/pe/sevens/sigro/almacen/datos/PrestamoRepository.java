package pe.sevens.sigro.almacen.datos;

import java.math.BigDecimal;
import java.util.*;

/** Préstamos de herramientas y equipos (almacen.prestamo, migración 0023). No mueven el saldo. */
public interface PrestamoRepository {
    /** id y proyecto_id del préstamo con ese uuid_cliente; null si no existe. */
    Map<String, Object> identidadPorUuid(UUID uuidCliente);

    /** El préstamo como sale en la API: con almacén, herramienta y si está atrasado. */
    Map<String, Object> porId(long id);

    /** El préstamo de esa obra, bloqueado hasta el fin de la transacción (para devolverlo una sola vez). */
    Map<String, Object> deLaObraParaDevolver(long obraId, long id);

    /** Cuánto de esa herramienta está prestado y sin devolver en ese almacén. */
    BigDecimal prestadoSinDevolver(long almacenId, long insumoId);

    /** @throws MovimientoDuplicado si el uuid_cliente ya existe (reenvío) */
    long insertar(Map<String, Object> prestamo);

    void devolver(long id, long usuarioId, String estado, String nota);

    List<Map<String, Object>> deLaObra(long obraId, boolean soloSinDevolver);
}
