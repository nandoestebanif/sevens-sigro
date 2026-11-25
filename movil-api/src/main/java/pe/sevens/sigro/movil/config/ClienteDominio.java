package pe.sevens.sigro.movil.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Cliente HTTP hacia los servicios de dominio (gateway /api/v1). */
@Configuration
public class ClienteDominio {
    @Bean
    RestClient restClientDominio(RestClient.Builder builder, PropiedadesSigro props) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        var fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(props.tiempoEsperaSegundos()));
        return builder.baseUrl(props.apiUrl().replaceAll("/+$", "")).requestFactory(fabrica).build();
    }
}
