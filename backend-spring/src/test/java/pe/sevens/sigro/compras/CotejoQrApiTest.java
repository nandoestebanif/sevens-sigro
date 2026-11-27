package pe.sevens.sigro.compras;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.sevens.sigro.almacen.Almacen;
import pe.sevens.sigro.compras.datos.CompraCotejoRepository;
import pe.sevens.sigro.comun.*;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.seguridad.Acceso.Actor;

/**
 * HU-73 · Pruebas de API de {@code POST /obras/{id}/compras/cotejar-qr} (los 6 resultados, el 422 y el permiso) y del
 * cambio de {@code GET /obras/{id}/compras} (contrato 3.2). Corren por HTTP simulado (MockMvc) con el controlador, el
 * servicio, el parser y el cotejo de verdad; solo la base y almacén son dobles. Contra PostgreSQL real lo cubre
 * {@code api/pruebas/test_api_cotejo_qr.py} en la prueba de paridad.
 */
class CotejoQrApiTest {
    static final long OBRA = 5;
    static final String URL = "/api/v1/obras/" + OBRA + "/compras/cotejar-qr";
    static final String QR = "20999999990|01|F001|00001234|180.00|1180.00|2026-11-20|6|20606456892|";
    static final String QR_OTRO_RUC = "20999999990|01|F001|00000005|18.00|118.00|2026-11-20|6|20888888889|";

    Modulo db;
    Acceso acceso;
    CompraCotejoRepository repositorio;
    Almacen almacen;
    CatalogoCliente catalogo;
    MockMvc mvc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        db = mock(Modulo.class);
        when(db.en(any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());
        acceso = mock(Acceso.class);
        when(acceso.exigir(anyString(), anyLong())).thenAnswer(inv -> new Actor(3, "Almacenero de prueba", inv.getArgument(1), "almacenero"));
        repositorio = mock(CompraCotejoRepository.class);
        almacen = mock(Almacen.class);
        catalogo = mock(CatalogoCliente.class);
        var servicio = new CotejoQrService(db, repositorio, almacen, "20606456892");
        var controlador = new ComprasController(db, acceso, mock(Integraciones.class), catalogo, almacen, mock(Eventos.class), "10", servicio);
        mvc = MockMvcBuilders.standaloneSetup(controlador)
                .setControllerAdvice(new ManejadorErrores())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Json.M))
                .build();
    }

    static CotejoQr.Compra compra(String total, String igv, String estado) {
        return new CotejoQr.Compra(12L, "20999999990", "Aceros del Sur S.A.C.", "factura", "F001-1234", new BigDecimal(total), new BigDecimal(igv),
                LocalDate.of(2026, 11, 20), "PEN", estado, "anulada".equals(estado) ? "Comprobante equivocado" : null, false);
    }

    void hay(CotejoQr.Compra c, boolean quedaPorRecibir) {
        when(repositorio.candidatas(OBRA, "20999999990", "F001", "1234")).thenReturn(List.of(c));
        when(almacen.quedaPorRecibir(OBRA, 12L)).thenReturn(quedaPorRecibir);
    }

    ResultActions cotejar(String qr) throws Exception {
        return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(Json.M.writeValueAsString(Map.of("qr", qr))));
    }

    // ------------------------------------------------------------------ los 6 resultados
    @Test
    void recibir() throws Exception {
        hay(compra("1180.00", "180.00", "registrada"), true);
        cotejar(QR).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("recibir"))
                .andExpect(jsonPath("$.comprobante.ruc_emisor").value("20999999990"))
                .andExpect(jsonPath("$.comprobante.tipo_comprobante").value("factura"))
                .andExpect(jsonPath("$.comprobante.numero_comprobante").value("F001-1234"))
                .andExpect(jsonPath("$.comprobante.igv").value("180.00"))
                .andExpect(jsonPath("$.comprobante.total").value("1180.00"))
                .andExpect(jsonPath("$.comprobante.fecha").value("2026-11-20"))
                .andExpect(jsonPath("$.comprobante.adquirente_tipo").value("6"))
                .andExpect(jsonPath("$.comprobante.adquirente_documento").value("20606456892"))
                .andExpect(jsonPath("$.compra.id").value(12))
                .andExpect(jsonPath("$.compra.estado").value("registrada"))
                .andExpect(jsonPath("$.compra.proveedor_razon_social").value("Aceros del Sur S.A.C."))
                .andExpect(jsonPath("$.compra.total").value("1180.00"))
                .andExpect(jsonPath("$.compra.queda_por_recibir").value(true))
                .andExpect(jsonPath("$.compra", aMapWithSize(5)))
                .andExpect(jsonPath("$.avisos", empty()));
        verify(acceso).exigir("recepcion.registrar", OBRA);
    }

    @Test
    void recibirConAvisos() throws Exception {
        hay(compra("1062.00", "162.00", "registrada"), true);
        cotejar(QR).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("recibir_con_avisos"))
                .andExpect(jsonPath("$.compra.id").value(12))
                .andExpect(jsonPath("$.avisos[0]").value("El total del QR (S/ 1 180.00) difiere en S/ 118.00 del registrado en la compra (S/ 1 062.00)."))
                .andExpect(jsonPath("$.avisos[1]").value(org.hamcrest.Matchers.startsWith("El IGV del QR (S/ 180.00) difiere en S/ 18.00")));
    }

    @Test
    void yaRecibida() throws Exception {
        hay(compra("1180.00", "180.00", "registrada"), false);
        cotejar(QR).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("ya_recibida"))
                .andExpect(jsonPath("$.compra.queda_por_recibir").value(false));
    }

    @Test
    void anulada() throws Exception {
        hay(compra("1180.00", "180.00", "anulada"), true);
        cotejar(QR).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("anulada"))
                .andExpect(jsonPath("$.compra.estado").value("anulada"))
                .andExpect(jsonPath("$.avisos[0]").value("La compra F001-1234 está anulada: Comprobante equivocado."));
        verifyNoInteractions(almacen);    // una anulada no se va a recibir: ni se pregunta
    }

    @Test
    void sinCompra() throws Exception {
        when(repositorio.candidatas(anyLong(), anyString(), anyString(), anyString())).thenReturn(List.of());
        cotejar(QR).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("sin_compra"))
                .andExpect(jsonPath("$.compra").value(nullValue()))
                .andExpect(jsonPath("$.comprobante.numero_comprobante").value("F001-1234"));
        verify(repositorio).candidatas(OBRA, "20999999990", "F001", "1234");
    }

    @Test
    void noEsDeSevens() throws Exception {
        cotejar(QR_OTRO_RUC).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("no_es_de_sevens"))
                .andExpect(jsonPath("$.compra").value(nullValue()))
                .andExpect(jsonPath("$.comprobante.adquirente_documento").value("20888888889"));
        verifyNoInteractions(repositorio, almacen);   // advierte; no busca la compra
    }

    // ------------------------------------------------------------------ errores
    @Test
    void qrNoReconocidoEs422YNoRepiteElTexto() throws Exception {
        String raro = "https://ejemplo.pe/promo?dni=12345678";
        cotejar(raro).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("qr_no_reconocido"))
                .andExpect(jsonPath("$.mensaje").value(not(containsString("ejemplo.pe"))))
                .andExpect(jsonPath("$.mensaje").value(not(containsString("12345678"))));
        verifyNoInteractions(repositorio, almacen);
    }

    @Test
    void cadaNoReconocidoDelContratoEs422() throws Exception {
        for (String qr : List.of("20999999990|01|F001|00001234|180.00", "20999999991|01|F001|00001234|180.00|1180.00|2026-11-20|6|20606456892|",
                "20999999990|07|F001|00000003|18.00|118.00|2026-11-20|6|20606456892|"))
            cotejar(qr).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("qr_no_reconocido"));
    }

    @Test
    void sinElCampoQrEsDatosInvalidos() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("datos_invalidos"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"qr\": \"   \"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("datos_invalidos"));
        cotejar("x".repeat(2001)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("datos_invalidos"));
    }

    @Test
    void sinPermisoEs403YNoSeLeeNada() throws Exception {
        when(acceso.exigir("recepcion.registrar", OBRA)).thenThrow(new ErrorSigro(403, "sin_permiso", "No tienes permiso para esta operación en esta obra."));
        cotejar(QR).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("sin_permiso"));
        verifyNoInteractions(repositorio, almacen);
    }

    @Test
    void sinSesionEs401() throws Exception {
        when(acceso.exigir(anyString(), anyLong())).thenThrow(new ErrorSigro(401, "no_autenticado", "Inicia sesión para continuar."));
        cotejar(QR).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("no_autenticado"));
    }

    // ------------------------------------------------------------------ GET /compras (contrato 3.2)
    @Test
    void elListadoTraeElRucDelProveedorYElTotal() throws Exception {
        var fila = new LinkedHashMap<String, Object>();
        fila.put("id", 12L);
        fila.put("proveedor_id", 4L);
        fila.put("proveedor_ruc", "20999999990");
        fila.put("proveedor_razon_social", "Aceros del Sur S.A.C.");
        fila.put("numero_comprobante", "F001-1234");
        fila.put("base_imponible", new BigDecimal("1000.00"));
        fila.put("igv", new BigDecimal("180.00"));
        fila.put("total", new BigDecimal("1180.00"));
        fila.put("estado", "registrada");
        when(db.filas(startsWith(ComprasController.SQL_COMPRA), any(MapSqlParameterSource.class))).thenReturn(List.of(fila));
        when(db.filas(startsWith("SELECT d.compra_id"), any(MapSqlParameterSource.class))).thenReturn(List.of());
        when(catalogo.porId(any())).thenReturn(Map.of());
        mvc.perform(get("/api/v1/obras/" + OBRA + "/compras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].proveedor_ruc").value("20999999990"))
                .andExpect(jsonPath("$[0].total").value("1180.00"))
                .andExpect(jsonPath("$[0].totales.total").value("1180.00"))   // lo de antes sigue igual
                .andExpect(jsonPath("$[0].proveedor_razon_social").value("Aceros del Sur S.A.C."));
        verify(acceso).exigir("compra.consultar", OBRA);
    }
}
