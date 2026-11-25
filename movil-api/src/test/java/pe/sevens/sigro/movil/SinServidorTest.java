package pe.sevens.sigro.movil;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Si los servicios no responden (plan gratuito dormido o caído): 503, que la app trata como «se reintenta». */
@SpringBootTest(properties = { "sigro.api-url=http://127.0.0.1:9", "sigro.jwt-secret=" + Tokens.SECRETO })
@AutoConfigureMockMvc
class SinServidorTest {
    @Autowired MockMvc mvc;

    @Test
    void servicioCaido() throws Exception {
        mvc.perform(get("/api/v1/obras").header("Authorization", "Bearer " + Tokens.valido()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.codigo").value("servicio_no_disponible"));
    }
}
