package pe.sevens.sigro.movil.salidas;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class SalidaController {
    private final SalidaService servicio;

    public SalidaController(SalidaService servicio) {
        this.servicio = servicio;
    }

    /** HU-40 · POST /api/v1/obras/{obraId}/almacen/salidas. Reenvío con el mismo uuid ⇒ 200 con el original. */
    @PostMapping("/api/v1/obras/{obraId}/almacen/salidas")
    public ResponseEntity<byte[]> registrar(@PathVariable @Positive int obraId,
                                            @Valid @RequestBody SalidaRequest salida,
                                            @RequestHeader(HttpHeaders.AUTHORIZATION) String autorizacion) throws JsonProcessingException {
        return servicio.registrar(obraId, salida, autorizacion);
    }
}
