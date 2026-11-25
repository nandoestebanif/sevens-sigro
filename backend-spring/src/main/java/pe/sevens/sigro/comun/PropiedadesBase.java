package pe.sevens.sigro.comun;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexiones por módulo. Cada una acepta la cadena tal como la usa el servicio original
 * (postgresql://usuario:clave@host/base?sslmode=require) o una JDBC. Si un módulo no trae la suya, usa la común
 * y actúa con su rol (SET ROLE sigro_svc_<modulo>), así nunca tiene más permisos que su esquema.
 */
@ConfigurationProperties(prefix = "sigro.db")
public record PropiedadesBase(String url, Map<String, String> modulos, Integer poolMaximo, Boolean conRol) {
    public PropiedadesBase {
        if (modulos == null) modulos = Map.of();
        if (poolMaximo == null || poolMaximo < 1) poolMaximo = 3;
        if (conRol == null) conRol = Boolean.TRUE;
    }
}
