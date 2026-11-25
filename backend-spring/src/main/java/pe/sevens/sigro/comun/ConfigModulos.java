package pe.sevens.sigro.comun;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Un pool y un usuario de base por módulo: ninguno comparte permisos con otro. */
@Configuration
public class ConfigModulos {
    private final PropiedadesBase props;

    public ConfigModulos(PropiedadesBase props) {
        this.props = props;
    }

    @Bean public Modulo obras() { return crear("obras"); }
    @Bean public Modulo catalogo() { return crear("catalogo"); }
    @Bean public Modulo compras() { return crear("compras"); }
    @Bean public Modulo tareo() { return crear("tareo"); }
    @Bean public Modulo almacen() { return crear("almacen"); }
    @Bean public Modulo costo() { return crear("costo"); }

    Modulo crear(String nombre) {
        String propia = props.modulos().get(nombre);
        String cadena = propia != null && !propia.isBlank() ? propia : props.url();
        if (cadena == null || cadena.isBlank()) throw new IllegalStateException("Falta la conexión de " + nombre + " (SIGRO_DB_" + nombre.toUpperCase() + " o SIGRO_DB_URL)");
        var c = new HikariConfig();
        Conexion cx = Conexion.leer(cadena);
        c.setJdbcUrl(cx.jdbc());
        if (cx.usuario() != null) c.setUsername(cx.usuario());
        if (cx.clave() != null) c.setPassword(cx.clave());
        c.setPoolName("sigro-" + nombre);
        c.setMaximumPoolSize(props.poolMaximo());
        c.setMinimumIdle(1);
        c.setMaxLifetime(240_000);           // por debajo de los 5 min en que Neon suspende el cómputo
        c.setConnectionTimeout(10_000);
        c.setInitializationFailTimeout(-1);   // arranca aunque la base todavía no responda (Neon dormido)
        String rol = (propia == null || propia.isBlank()) && props.conRol() ? "SET ROLE sigro_svc_" + nombre + "; " : "";
        c.setConnectionInitSql(rol + "SET TIME ZONE 'UTC'");
        return new Modulo(nombre, new HikariDataSource(c));
    }

    /** postgresql://u:c@host:puerto/base?x=y  →  jdbc:postgresql://host:puerto/base?x=y + usuario + clave. */
    record Conexion(String jdbc, String usuario, String clave) {
        static Conexion leer(String cadena) {
            if (cadena.startsWith("jdbc:")) return new Conexion(cadena, null, null);
            String sinEsquema = cadena.replaceFirst("^postgres(ql)?(\\+[a-z0-9]+)?://", "http://");
            URI u = URI.create(sinEsquema);
            String usuario = null, clave = null;
            if (u.getRawUserInfo() != null) {
                String[] uc = u.getRawUserInfo().split(":", 2);
                usuario = URLDecoder.decode(uc[0], StandardCharsets.UTF_8);
                if (uc.length > 1) clave = URLDecoder.decode(uc[1], StandardCharsets.UTF_8);
            }
            String puerto = u.getPort() > 0 ? ":" + u.getPort() : "";
            String consulta = u.getRawQuery() == null ? "" : "?" + u.getRawQuery();
            return new Conexion("jdbc:postgresql://" + u.getHost() + puerto + u.getRawPath() + consulta, usuario, clave);
        }
    }
}
