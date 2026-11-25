package pe.sevens.sigro.movil.salidas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import pe.sevens.sigro.movil.reenvio.ServicioDominio;

/** Caso de uso «registrar salida» del lado del servidor de la app. */
@Service
public class SalidaService {
    private final ServicioDominio dominio;
    private final RegistroIdempotente idempotencia;
    private final ObjectMapper json;

    public SalidaService(ServicioDominio dominio, RegistroIdempotente idempotencia, ObjectMapper json) {
        this.dominio = dominio;
        this.idempotencia = idempotencia;
        this.json = json;
    }

    public ResponseEntity<byte[]> registrar(int obraId, SalidaRequest salida, String autorizacion) throws JsonProcessingException {
        String clave = RegistroIdempotente.clave(obraId, salida.uuidCliente());
        int huella = salida.hashCode(); // record: igualdad por contenido
        var previa = idempotencia.buscar(clave, huella);
        if (previa.isPresent()) {
            return ResponseEntity.status(200).header("X-Sigro-Origen", "reenvio-idempotente")
                    .contentType(MediaType.APPLICATION_JSON).body(previa.get().cuerpo());
        }
        byte[] cuerpo = json.writeValueAsBytes(salida);
        var res = dominio.enviar(HttpMethod.POST, "/api/v1/obras/" + obraId + "/almacen/salidas", autorizacion, cuerpo);
        if (res.getStatusCode().is2xxSuccessful()) idempotencia.guardar(clave, res.getStatusCode().value(), res.getBody(), huella);
        return res;
    }
}
