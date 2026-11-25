package pe.sevens.sigro.movil.salidas;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * HU-40 · Registrar salida de material: el mismo JSON que arma la app (SalidaIn en ApiSigro.kt).
 * Las validaciones de forma se resuelven aquí, antes de tocar el libro; las de negocio (stock, celular autorizado)
 * las decide el servicio dueño.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SalidaRequest(
        @NotNull(message = "falta el identificador del registro")
        @JsonProperty("uuid_cliente") UUID uuidCliente,
        @Positive @JsonProperty("almacen_id") Integer almacenId,
        @NotNull(message = "elige el insumo") @Positive @JsonProperty("insumo_id") Integer insumoId,
        @NotNull(message = "escribe la cantidad")
        @DecimalMin(value = "0", inclusive = false, message = "la cantidad debe ser mayor que cero")
        @Digits(integer = 10, fraction = 4, message = "hasta 4 decimales")
        BigDecimal cantidad,
        @Size(max = 20) String unidad,
        @NotBlank(message = "elige el motivo") @Size(max = 300) String motivo,
        @NotBlank(message = "escribe quién retira") @Size(max = 120) @JsonProperty("quien_retira") String quienRetira,
        @Size(max = 120) String frente,
        @PositiveOrZero @JsonProperty("hora_dispositivo_ms") Long horaDispositivoMs,
        @NotBlank(message = "falta el identificador del celular") @Size(min = 8, max = 128) String dispositivo) {
}
