package pe.sevens.sigro.comun;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CabecerasSeguridadTest {
    @Test
    void laApiNoSeIncrustaNiSeGuardaEnCache() throws Exception {
        var res = new MockHttpServletResponse();
        new CabecerasSeguridad().doFilter(new MockHttpServletRequest("GET", "/api/v1/obras"), res, (q, r) -> { });
        assertEquals("nosniff", res.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", res.getHeader("X-Frame-Options"));
        assertEquals("no-store", res.getHeader("Cache-Control"));
    }

    @Test
    void elCacheControlDelControladorReemplazaAlDeFabrica() throws Exception {
        var res = new MockHttpServletResponse();
        new CabecerasSeguridad().doFilter(new MockHttpServletRequest("GET", "/api/v1/adjuntos/1"), res,
                (q, r) -> ((HttpServletResponse) r).addHeader("Cache-Control", "private, no-store"));
        assertEquals(1, res.getHeaders("Cache-Control").size());
        assertEquals("private, no-store", res.getHeader("Cache-Control"));
    }
}
