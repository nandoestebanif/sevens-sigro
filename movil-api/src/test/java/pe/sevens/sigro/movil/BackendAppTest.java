package pe.sevens.sigro.movil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** El backend de la app de punta a punta: sesión, validación, idempotencia, reenvío y errores. */
@SpringBootTest
@AutoConfigureMockMvc
class BackendAppTest {
    static final ServidorFalso dominio;
    static {
        try { dominio = new ServidorFalso(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("sigro.api-url", dominio::url);
        r.add("sigro.jwt-secret", () -> Tokens.SECRETO);
    }

    @AfterAll
    static void parar() { dominio.parar(); }

    @Autowired MockMvc mvc;

    @BeforeEach
    void limpiar() { dominio.reiniciar(200, "{}"); }

    static final String SALIDA = """
            {"uuid_cliente":"%s","insumo_id":12,"cantidad":"25","motivo":"Vaciado de columnas","quien_retira":"Maestro Juan",
             "hora_dispositivo_ms":1764000000000,"dispositivo":"celular-almacen-01"}""";

    @Test @DisplayName("sin token: 401 con {codigo, mensaje} y no se llama al servicio")
    void sinToken() throws Exception {
        mvc.perform(get("/api/v1/obras")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("sesion_vencida"));
        assertThat(dominio.pedidos).isEmpty();
    }

    @Test @DisplayName("token vencido, con otra firma o con alg none: 401")
    void tokensMalos() throws Exception {
        long ayer = System.currentTimeMillis() / 1000 - 86400;
        for (String t : new String[] { Tokens.firmar("7", ayer, Tokens.SECRETO, "HS256"),
                Tokens.firmar("7", ayer + 2 * 86400, "otro-secreto-que-no-es-el-del-servidor-xx", "HS256"),
                Tokens.firmar("7", ayer + 2 * 86400, Tokens.SECRETO, "none"), "no.es.jwt" }) {
            mvc.perform(get("/api/v1/obras").header("Authorization", "Bearer " + t)).andExpect(status().isUnauthorized());
        }
        assertThat(dominio.pedidos).isEmpty();
    }

    @Test @DisplayName("con sesión: las obras se piden al servicio con el mismo Bearer")
    void obras() throws Exception {
        dominio.reiniciar(200, "[{\"id\":1,\"nombre\":\"Multifamiliar Flores\",\"rol\":\"almacenero\"}]");
        String t = Tokens.valido();
        mvc.perform(get("/api/v1/obras").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("Multifamiliar Flores"));
        assertThat(dominio.pedidos).singleElement().satisfies(p -> {
            assertThat(p.ruta()).isEqualTo("/api/v1/obras");
            assertThat(p.autorizacion()).isEqualTo("Bearer " + t);
        });
    }

    @Test @DisplayName("la consulta se pasa tal cual (búsqueda en el catálogo)")
    void consulta() throws Exception {
        mvc.perform(get("/api/v1/catalogo/insumos").queryParam("q", "cemento portland").header("Authorization", "Bearer " + Tokens.valido()))
                .andExpect(status().isOk());
        assertThat(dominio.pedidos.get(0).ruta()).isEqualTo("/api/v1/catalogo/insumos?q=cemento%20portland");
    }

    @Test @DisplayName("una ruta que la app no usa: 404, no es un proxy abierto")
    void listaBlanca() throws Exception {
        mvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + Tokens.valido()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("ruta_desconocida"));
        assertThat(dominio.pedidos).isEmpty();
    }

    @Test @DisplayName("salida con cantidad cero: 422 con el campo, sin tocar el libro")
    void salidaInvalida() throws Exception {
        String cuerpo = SALIDA.formatted("3f1c2a7e-1b2c-4d5e-8f90-112233445566").replace("\"25\"", "\"0\"");
        mvc.perform(post("/api/v1/obras/1/almacen/salidas").header("Authorization", "Bearer " + Tokens.valido())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("validacion"))
                .andExpect(jsonPath("$.mensaje").value("cantidad: la cantidad debe ser mayor que cero"));
        assertThat(dominio.pedidos).isEmpty();
    }

    @Test @DisplayName("salida sin uuid ni celular: 422 con los dos motivos")
    void salidaIncompleta() throws Exception {
        String cuerpo = "{\"insumo_id\":12,\"cantidad\":\"2\",\"motivo\":\"x\",\"quien_retira\":\"Juan\"}";
        mvc.perform(post("/api/v1/obras/1/almacen/salidas").header("Authorization", "Bearer " + Tokens.valido())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value("dispositivo: falta el identificador del celular; uuidCliente: falta el identificador del registro"));
    }

    @Test @DisplayName("regla 3: el reenvío con el mismo uuid devuelve la original sin volver a escribir")
    void idempotencia() throws Exception {
        dominio.reiniciar(201, "{\"id\":901,\"uuid_cliente\":\"aa\",\"saldo\":\"151\"}");
        String cuerpo = SALIDA.formatted("9b2f6c1d-0a1b-4c2d-9e3f-aabbccddeeff");
        String t = Tokens.valido();
        mvc.perform(post("/api/v1/obras/1/almacen/salidas").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(901));
        mvc.perform(post("/api/v1/obras/1/almacen/salidas").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(901))
                .andExpect(header().string("X-Sigro-Origen", "reenvio-idempotente"));
        assertThat(dominio.pedidos).hasSize(1);
        assertThat(dominio.pedidos.get(0).cuerpo()).contains("\"uuid_cliente\":\"9b2f6c1d-0a1b-4c2d-9e3f-aabbccddeeff\"").contains("\"cantidad\":25");
    }

    @Test @DisplayName("el mismo uuid con otro contenido no usa la copia: decide el servicio (409)")
    void uuidReutilizado() throws Exception {
        String t = Tokens.valido();
        dominio.reiniciar(201, "{\"id\":902}");
        String uuid = "11111111-2222-4333-8444-555555555555";
        mvc.perform(post("/api/v1/obras/1/almacen/salidas").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(SALIDA.formatted(uuid)));
        dominio.estado = 409; dominio.respuesta = "{\"detail\":{\"codigo\":\"uuid_reutilizado\",\"mensaje\":\"Ese identificador ya se usó para otro movimiento.\"}}";
        mvc.perform(post("/api/v1/obras/1/almacen/salidas").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                        .content(SALIDA.formatted(uuid).replace("\"25\"", "\"30\"")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail.codigo").value("uuid_reutilizado"));
        assertThat(dominio.pedidos).hasSize(2);
    }

    @Test @DisplayName("un rechazo de negocio (stock insuficiente) llega igual a la app")
    void rechazoNegocio() throws Exception {
        dominio.reiniciar(422, "{\"detail\":{\"codigo\":\"stock_insuficiente\",\"mensaje\":\"Stock insuficiente: disponible 10\"}}");
        mvc.perform(post("/api/v1/obras/1/almacen/salidas").header("Authorization", "Bearer " + Tokens.valido())
                        .contentType(MediaType.APPLICATION_JSON).content(SALIDA.formatted("77777777-2222-4333-8444-555555555555")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail.codigo").value("stock_insuficiente"));
    }

    @Test @DisplayName("entrar no pide token y limpia espacios del usuario")
    void entrar() throws Exception {
        dominio.reiniciar(200, "{\"token\":\"x\",\"expira_en\":28800,\"usuario\":{\"id\":7,\"nombre\":\"A\",\"usuario\":\"almacenero\"},\"obras\":[]}");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"  almacenero \",\"clave\":\"x1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expira_en").value(28800));
        assertThat(dominio.pedidos.get(0).cuerpo()).isEqualTo("{\"usuario\":\"almacenero\",\"clave\":\"x1\"}");
        assertThat(dominio.pedidos.get(0).autorizacion()).isNull();
    }

    @Test @DisplayName("entrar sin clave: 422 sin llamar al servicio")
    void entrarSinClave() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"usuario\":\"almacenero\",\"clave\":\"\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.mensaje").value("clave: escribe tu clave"));
        assertThat(dominio.pedidos).isEmpty();
    }

    @Test @DisplayName("salud responde sin sesión")
    void salud() throws Exception {
        mvc.perform(get("/salud")).andExpect(status().isOk()).andExpect(content().json("{\"estado\":\"ok\",\"servicio\":\"movil-api\"}"));
    }
}
