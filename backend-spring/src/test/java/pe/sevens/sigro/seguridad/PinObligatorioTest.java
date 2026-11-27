package pe.sevens.sigro.seguridad;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** 27/11: el PIN es obligatorio en las acciones delicadas; solo las pruebas de paridad lo apagan, nunca en producción. */
class PinObligatorioTest {
    @Test
    void porDefectoEsObligatorio() {
        assertTrue(Acceso.pinObligatorio(null, null));
        assertTrue(Acceso.pinObligatorio("1", ""));
        assertTrue(Acceso.pinObligatorio("", "pruebas"));
    }

    @Test
    void soloSeApagaFueraDeProduccion() {
        assertFalse(Acceso.pinObligatorio("0", "pruebas"));
        assertFalse(Acceso.pinObligatorio("false", null));
        assertTrue(Acceso.pinObligatorio("0", "produccion"), "en producción no se puede apagar");
        assertTrue(Acceso.pinObligatorio("0", " PRODUCCION "));
    }

    @Test
    void lasAccionesDelicadasEstanEnLaLista() {
        for (String a : new String[] {"periodo.cerrar", "proyecto.cerrar", "proyecto.ampliar_plazo", "proyecto.recortar_plazo",
                "compra.anular", "requerimiento.ajustar", "insumo.factor", "usuario.crear", "dispositivo.autorizar"})
            assertTrue(Acceso.CON_PIN.contains(a), a);
        assertFalse(Acceso.CON_PIN.contains("salida.registrar"), "el almacenero no necesita PIN para registrar");
    }
}
