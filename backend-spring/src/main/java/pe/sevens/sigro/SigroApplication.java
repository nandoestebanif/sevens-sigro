package pe.sevens.sigro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * SIGRO · backend en Spring Boot 3 + Java 17.
 *
 * <p>Un solo desplegable con un módulo por servicio de dominio (obra, catálogo, compras, tareo, almacén y costo).
 * Cada módulo se conecta a PostgreSQL con SU usuario y solo ve su esquema, igual que los microservicios que
 * reemplaza: lo que necesita de otro módulo se lo pide por su servicio interno, nunca lee sus tablas.</p>
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class SigroApplication {
    public static void main(String[] args) {
        SpringApplication.run(SigroApplication.class, args);
    }
}
