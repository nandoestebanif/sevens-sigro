package pe.sevens.sigro.almacen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.sevens.sigro.almacen.datos.MovimientoRepository;
import pe.sevens.sigro.almacen.datos.PrestamoRepository;
import pe.sevens.sigro.comun.Datos;
import pe.sevens.sigro.comun.ErrorSigro;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.seguridad.Acceso.Actor;

/** Préstamo de herramientas con repositorios simulados: reglas de disponibilidad, tipo y devolución única. */
class PrestamoServiceTest {
    static final long OBRA = 7;
    Modulo db;
    PrestamoRepository prestamos;
    MovimientoRepository movimientos;
    AlmacenService almacen;
    PrestamoService servicio;
    final Actor almacenero = new Actor(3, "Almacenero", OBRA, "almacenero");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        db = mock(Modulo.class);
        when(db.en(any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());
        prestamos = mock(PrestamoRepository.class);
        movimientos = mock(MovimientoRepository.class);
        almacen = mock(AlmacenService.class);
        servicio = new PrestamoService(db, prestamos, movimientos, almacen);
        when(prestamos.identidadPorUuid(any())).thenReturn(null);
        when(almacen.almacen(eq(OBRA), any())).thenReturn(Map.of("id", 2L, "nombre", "Almacén central", "estado", "activo"));
    }

    void herramienta(String tipo) {
        when(almacen.insumos(any(), eq(true))).thenReturn(Map.of(9L, Map.of("id", 9L, "codigo", "0301", "tipo", tipo)));
    }

    static Datos prestamo(String cantidad) {
        return Contratos.PRESTAMO.validar(new ObjectMapper().valueToTree(Map.of("uuid_cliente", UUID.randomUUID().toString(), "insumo_id", 9,
                "cantidad", cantidad, "responsable", "Luis Quispe")));
    }

    @Test
    void noSePrestaMasDeLoDisponible() {
        herramienta("equipo");
        when(movimientos.saldo(2L, 9L)).thenReturn(new BigDecimal("3"));
        when(prestamos.prestadoSinDevolver(2L, 9L)).thenReturn(new BigDecimal("2"));
        var e = assertThrows(ErrorSigro.class, () -> servicio.prestar(almacenero, OBRA, prestamo("2")));
        assertEquals("herramienta_no_disponible", e.codigo());
        assertTrue(e.getMessage().startsWith("Hay 1 disponibles"), e.getMessage());
        verify(prestamos, never()).insertar(any());
        verify(movimientos).bloquearSaldo(2L, 9L);
    }

    @Test
    void unMaterialNoSePresta() {
        herramienta("material");
        assertEquals("no_es_herramienta", assertThrows(ErrorSigro.class, () -> servicio.prestar(almacenero, OBRA, prestamo("1"))).codigo());
    }

    @Test
    void prestamoValidoSeRegistra() {
        herramienta("equipo");
        when(movimientos.saldo(2L, 9L)).thenReturn(new BigDecimal("3"));
        when(prestamos.prestadoSinDevolver(2L, 9L)).thenReturn(BigDecimal.ONE);
        when(prestamos.insertar(any())).thenReturn(5L);
        when(prestamos.porId(5L)).thenReturn(Map.of("id", 5L, "responsable", "Luis Quispe"));
        var r = servicio.prestar(almacenero, OBRA, prestamo("2"));
        assertTrue(r.creado());
        verify(prestamos).insertar(argThat(m -> "Luis Quispe".equals(m.get("responsable")) && new BigDecimal("2").compareTo((BigDecimal) m.get("cantidad")) == 0));
    }

    @Test
    void unaDevolucionSoloUnaVez() {
        when(prestamos.deLaObraParaDevolver(OBRA, 5L)).thenReturn(Map.of("id", 5L, "devuelto_en", "2026-11-25T10:00:00Z"));
        var d = Contratos.DEVOLUCION.validar(new ObjectMapper().valueToTree(Map.of("estado", "bueno")));
        assertEquals("ya_devuelto", assertThrows(ErrorSigro.class, () -> servicio.devolver(almacenero, OBRA, 5L, d)).codigo());
    }

    @Test
    void danadaOPerdidaExigeDecirQuePaso() {
        assertEquals(422, assertThrows(ErrorSigro.class,
                () -> Contratos.DEVOLUCION.validar(new ObjectMapper().valueToTree(Map.of("estado", "perdido"))))
                .estado());
    }
}
