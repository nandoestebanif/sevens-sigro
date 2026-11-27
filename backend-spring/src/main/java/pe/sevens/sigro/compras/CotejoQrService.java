package pe.sevens.sigro.compras;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.sevens.sigro.almacen.Almacen;
import pe.sevens.sigro.compras.datos.CompraCotejoRepository;
import pe.sevens.sigro.comun.ErrorSigro;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.comun.R;

/**
 * HU-73 · Caso de uso «cotejar el QR de la factura» (POST /obras/{id}/compras/cotejar-qr): lee el QR, busca la compra en
 * el esquema compras, pregunta a almacén si queda algo por recibir y aplica {@link CotejoQr}. Solo consulta: no
 * registra nada ni guarda el texto del QR en ningún lado (criterio 8). El permiso lo exige el controlador.
 */
@Service
public class CotejoQrService {
    static final String NO_RECONOCIDO = "Ese código no es el QR de una factura o boleta electrónica de SUNAT. Busca la compra a mano.";

    private final Modulo db;
    private final CompraCotejoRepository compras;
    private final Almacen almacen;
    private final String rucEmpresa;

    public CotejoQrService(Modulo compras, CompraCotejoRepository repositorio, Almacen almacen,
                           @Value("${SIGRO_RUC_EMPRESA:20606456892}") String rucEmpresa) {
        this.db = compras;
        this.compras = repositorio;
        this.almacen = almacen;
        this.rucEmpresa = rucEmpresa;
    }

    public Map<String, Object> cotejar(long obraId, String texto) {
        QrFacturaSunat qr = QrFacturaSunat.leer(texto);
        if (qr == null) throw new ErrorSigro(422, "qr_no_reconocido", NO_RECONOCIDO);   // sin repetir el texto recibido
        List<CotejoQr.Compra> candidatas = List.of();
        if (qr.emitidoA(rucEmpresa)) {          // no_es_de_sevens: ni se busca la compra
            String[] sn = qr.numeroComprobante().split("-", 2);
            var halladas = db.en(() -> compras.candidatas(obraId, qr.rucEmisor(), sn[0], sn[1]));
            candidatas = CotejoQr.buscar(qr, halladas)
                    .map(c -> c.anulada() ? c : c.conQuedaPorRecibir(almacen.quedaPorRecibir(obraId, c.id())))
                    .map(List::of).orElse(List.of());
        }
        return respuesta(qr, CotejoQr.cotejar(qr, candidatas, rucEmpresa));
    }

    static Map<String, Object> respuesta(QrFacturaSunat qr, CotejoQr.Cotejo cotejo) {
        var c = cotejo.compra();
        Object compra = c == null ? null : R.mapa("id", c.id(), "estado", c.estado(), "proveedor_razon_social", c.proveedorRazonSocial(),
                "total", c.total(), "queda_por_recibir", c.quedaPorRecibir());
        return R.mapa("comprobante", qr.aMapa(), "resultado", cotejo.resultado().valor(), "compra", compra, "avisos", cotejo.avisos());
    }
}
