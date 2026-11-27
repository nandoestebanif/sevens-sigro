package pe.sevens.sigro.compras.datos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;
import pe.sevens.sigro.compras.CotejoQr;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.comun.P;

/** {@link CompraCotejoRepository} con JDBC sobre compras.compra y compras.proveedor (solo lectura). */
@Repository
public class JdbcCompraCotejoRepository implements CompraCotejoRepository {
    /** El número guardado se normaliza igual que el del QR: «F001-00001234» y «f001-1234» son el mismo comprobante. */
    static final String SQL = "SELECT c.id, p.ruc AS proveedor_ruc, p.razon_social AS proveedor_razon_social, c.tipo_comprobante, c.numero_comprobante, "
            + "c.total, c.igv, c.fecha, c.moneda, c.estado, c.motivo_anulacion FROM compras.compra c JOIN compras.proveedor p ON p.id = c.proveedor_id "
            + "WHERE c.proyecto_id = :p AND p.ruc = :r AND upper(split_part(c.numero_comprobante, '-', 1)) = :s "
            + "AND coalesce(nullif(ltrim(split_part(c.numero_comprobante, '-', 2), '0'), ''), '0') = :n ORDER BY c.id DESC";

    private final Modulo db;

    public JdbcCompraCotejoRepository(Modulo compras) { this.db = compras; }

    @Override
    public List<CotejoQr.Compra> candidatas(long obraId, String rucEmisor, String serie, String numero) {
        return db.filas(SQL, P.de("p", obraId, "r", rucEmisor, "s", serie, "n", numero)).stream()
                .map(f -> new CotejoQr.Compra(((Number) f.get("id")).longValue(), (String) f.get("proveedor_ruc"), (String) f.get("proveedor_razon_social"),
                        (String) f.get("tipo_comprobante"), (String) f.get("numero_comprobante"), (BigDecimal) f.get("total"), (BigDecimal) f.get("igv"),
                        (LocalDate) f.get("fecha"), (String) f.get("moneda"), (String) f.get("estado"), (String) f.get("motivo_anulacion"), false))
                .toList();
    }
}
