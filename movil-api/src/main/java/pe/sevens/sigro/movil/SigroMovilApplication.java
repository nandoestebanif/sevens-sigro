package pe.sevens.sigro.movil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * SIGRO · backend de la app móvil del almacenero (Spring Boot 3 + Java 17).
 *
 * <p>Es la puerta de la app: expone el mismo contrato /api/v1 que ya usa el celular, verifica la sesión (JWT),
 * valida cada pedido con Bean Validation, responde un reenvío idempotente sin volver a escribir y traduce los
 * errores al formato {codigo, mensaje}. Las reglas del libro de almacén (stock, costo promedio, reverso) siguen en
 * los servicios de dominio y en la base: este backend no las duplica, se las pide.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SigroMovilApplication {
    public static void main(String[] args) {
        SpringApplication.run(SigroMovilApplication.class, args);
    }
}
