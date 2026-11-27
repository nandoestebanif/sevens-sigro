package pe.sevens.sigro.compras.datos;

import java.util.List;
import pe.sevens.sigro.compras.CotejoQr;

/** HU-73: las compras de la obra que podrían ser las del QR (esquema compras; nada de otro esquema). */
public interface CompraCotejoRepository {
    /**
     * Compras de la obra del proveedor con ese RUC y ese comprobante (serie y número sin ceros a la izquierda), vigentes o
     * anuladas. {@code quedaPorRecibir} viene en {@code false}: eso lo dice almacén.
     */
    List<CotejoQr.Compra> candidatas(long obraId, String rucEmisor, String serie, String numero);
}
