package pe.sevens.sigro.movil.errores;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/**
 * Todo error sale como {codigo, mensaje}. La app los clasifica (ErrorSigro sellado): 422 = Rechazado con su motivo,
 * 503 = sin servidor, se reintenta.
 */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(ErrorSigro.class)
    ResponseEntity<Map<String, String>> previsto(ErrorSigro e) {
        return ResponseEntity.status(e.estado()).body(Map.of("codigo", e.codigo(), "mensaje", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalido(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return ResponseEntity.unprocessableEntity().body(Map.of("codigo", "validacion", "mensaje", detalle));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> ilegible(HttpMessageNotReadableException e) {
        return ResponseEntity.unprocessableEntity().body(Map.of("codigo", "validacion", "mensaje", "El cuerpo no es un JSON válido para esta acción."));
    }

    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<Map<String, String>> sinServidor(ResourceAccessException e) {
        return ResponseEntity.status(503).body(Map.of("codigo", "servicio_no_disponible",
                "mensaje", "El servidor está despertando o no responde. Lo registrado queda en el celular y se reintenta."));
    }
}
