package pe.sevens.sigro.seguridad;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** El token del PIN y el de sesión no se confunden: ninguno sirve como el otro. */
class TokensPinTest {
    final Tokens t = new Tokens("una-clave-de-pruebas-larga-de-32-caracteres-o-mas", "pruebas", 8);

    @Test
    void elTokenDePinSoloSirveComoPin() {
        String pin = t.crearPin(7);
        assertEquals(7L, t.leerPin(pin));
        assertNull(t.leer(pin), "un token de PIN no abre sesión");
    }

    @Test
    void elTokenDeSesionNoSirveComoPin() {
        String sesion = t.crear(7);
        assertEquals(7L, t.leer(sesion));
        assertNull(t.leerPin(sesion));
        assertNull(t.leerPin(sesion.substring(0, sesion.length() - 2) + "xx"), "firma alterada");
    }

    @Test
    void pinesTrivialesSeRechazan() {
        assertTrue(PinController.trivial("111111"));
        assertTrue(PinController.trivial("123456"));
        assertTrue(PinController.trivial("654321"));
        assertFalse(PinController.trivial("481937"));
    }
}
