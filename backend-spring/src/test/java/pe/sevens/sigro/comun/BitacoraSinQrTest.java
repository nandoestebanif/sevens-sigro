package pe.sevens.sigro.comun;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.sevens.sigro.seguridad.Tokens;

/**
 * HU-73, criterio 8: cotejar el QR queda en la bitácora como cualquier POST (quién, obra, ruta, estado y duración),
 * pero el texto del QR no se guarda: ni entero ni en partes (RUC del adquirente, montos).
 */
class BitacoraSinQrTest {
    static final String QR = "20999999990|01|F001|00001234|180.00|1180.00|2026-11-20|6|20606456892|aGFzaA==";

    @Test
    void elCotejoSeAnotaSinElTextoDelQr() throws Exception {
        Modulo db = mock(Modulo.class);
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; }).when(db).aparte(any());
        Tokens tokens = mock(Tokens.class);
        when(tokens.leer("tok")).thenReturn(3L);
        var filtro = new BitacoraFiltro(db, tokens);

        var req = new MockHttpServletRequest("POST", "/api/v1/obras/5/compras/cotejar-qr");
        req.addHeader("Authorization", "Bearer tok");
        req.setContentType("application/json");
        req.setContent(("{\"qr\":\"" + QR + "\"}").getBytes(StandardCharsets.UTF_8));
        var res = new MockHttpServletResponse();
        filtro.doFilter(req, res, (rq, rs) -> rq.getInputStream().readAllBytes());   // el controlador sí lee el cuerpo

        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(db).actualizar(sql.capture(), params.capture());
        assertTrue(sql.getValue().startsWith("INSERT INTO obra.bitacora"));
        var p = params.getValue();
        assertEquals(3L, p.getValue("u"));
        assertEquals(5L, p.getValue("p"));
        assertEquals("/api/v1/obras/5/compras/cotejar-qr", p.getValue("r"));

        var guardado = new ArrayList<String>();
        for (String n : p.getParameterNames()) guardado.add(String.valueOf(p.getValue(n)));
        for (String pedazo : List.of(QR, "20999999990", "20606456892", "1180.00", "aGFzaA==", "|"))
            assertTrue(guardado.stream().noneMatch(v -> v.contains(pedazo)), "la bitácora no guarda «" + pedazo + "»: " + guardado);
    }
}
