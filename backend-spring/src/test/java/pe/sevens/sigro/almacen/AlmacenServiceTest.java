package pe.sevens.sigro.almacen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.sevens.sigro.almacen.datos.ConsultaRepository;
import pe.sevens.sigro.almacen.datos.DocumentoRepository;
import pe.sevens.sigro.almacen.datos.MovimientoRepository;
import pe.sevens.sigro.catalogo.Catalogo;
import pe.sevens.sigro.compras.Compras;
import pe.sevens.sigro.comun.Datos;
import pe.sevens.sigro.comun.ErrorSigro;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.obra.Obras;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.seguridad.Acceso.Actor;

/**
 * Casos de uso de almacén con los repositorios simulados (Mockito): como el servicio depende de interfaces, se prueba
 * sin base de datos. Las 372 pruebas de contrato por HTTP cubren lo mismo contra PostgreSQL real.
 */
class AlmacenServiceTest {
    static final long OBRA = 7;
    static final UUID UUID_SALIDA = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

    Modulo db;
    MovimientoRepository movimientos;
    DocumentoRepository documentos;
    ConsultaRepository consultas;
    Acceso acceso;
    Obras obras;
    Catalogo catalogo;
    Compras compras;
    AlmacenService servicio;
    final Actor almacenero = new Actor(3, "Almacenero de prueba", OBRA, "almacenero");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        db = mock(Modulo.class);
        when(db.en(any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());   // sin transacción real
        movimientos = mock(MovimientoRepository.class);
        documentos = mock(DocumentoRepository.class);
        consultas = mock(ConsultaRepository.class);
        acceso = mock(Acceso.class);
        obras = mock(Obras.class);
        catalogo = mock(Catalogo.class);
        compras = mock(Compras.class);
        servicio = new AlmacenService(db, movimientos, documentos, consultas, acceso, obras, catalogo, compras);
    }

    static Datos salida(String dispositivo) throws Exception {
        var cuerpo = new LinkedHashMap<String, Object>(Map.of("uuid_cliente", UUID_SALIDA.toString(), "insumo_id", 5, "cantidad", "2",
                "motivo", "Vaciado de columnas", "quien_retira", "Capataz"));
        if (dispositivo != null) cuerpo.put("dispositivo", dispositivo);
        return Contratos.SALIDA.validar(new ObjectMapper().valueToTree(cuerpo));
    }

    @Test
    void reenvioDeLaMismaSalidaDevuelveElOriginalSinInsertar() throws Exception {
        when(movimientos.identidadPorUuid(UUID_SALIDA)).thenReturn(Map.of("id", 40L, "proyecto_id", OBRA, "tipo", "salida"));
        when(movimientos.porId(40L)).thenReturn(Map.of("id", 40L, "tipo", "salida"));

        var r = servicio.salida(almacenero, OBRA, salida("celular-0001"));

        assertFalse(r.creado(), "un reenvío responde 200, no 201");
        assertEquals(40L, ((Map<?, ?>) r.cuerpo()).get("id"));
        verify(movimientos, never()).insertar(any());
    }

    @Test
    void uuidUsadoEnOtraObraEsConflicto() throws Exception {
        when(movimientos.identidadPorUuid(UUID_SALIDA)).thenReturn(Map.of("id", 40L, "proyecto_id", 99L, "tipo", "salida"));

        var e = assertThrows(ErrorSigro.class, () -> servicio.salida(almacenero, OBRA, salida("celular-0001")));

        assertEquals(409, e.estado());
        assertEquals("uuid_reutilizado", e.codigo());
    }

    @Test
    void salidaSinCelularAutorizadoSeRechazaAntesDeTocarElLibro() {
        // el contrato ya exige «dispositivo»: sin él, el cuerpo no pasa la validación (422)
        assertEquals(422, assertThrows(ErrorSigro.class, () -> salida(null)).estado());
        verifyNoInteractions(movimientos);
    }

    @Test
    void salidaNuevaSeInsertaYSePublicaParaCosto() throws Exception {
        when(movimientos.identidadPorUuid(UUID_SALIDA)).thenReturn(null);   // es nueva
        when(acceso.rolEnObra(3L, OBRA)).thenReturn("almacenero");
        when(obras.dispositivoActivo(3L, OBRA, "celular-0001")).thenReturn(11L);
        when(obras.almacenes(OBRA)).thenReturn(List.of(Map.of("id", 2L, "proyecto_id", OBRA, "nombre", "Principal", "tipo", "principal", "estado", "activo")));
        when(catalogo.buscar(any(), any(), anyBoolean())).thenReturn(List.of(Map.of("id", 5L, "codigo", "0201", "descripcion", "Cemento", "unidad_s10", "bls",
                "tipo", "material", "activo", true)));
        when(obras.periodos(OBRA)).thenReturn(List.of(Map.of("id", 1L, "numero", 1L, "fecha_inicio", "2020-01-01", "fecha_fin", "2099-12-31", "estado", "abierto")));
        when(movimientos.insertar(any())).thenReturn(41L);
        when(movimientos.porId(41L)).thenReturn(Map.of("id", 41L, "tipo", "salida", "cantidad", new BigDecimal("2")));
        when(movimientos.paraHechos(List.of(41L))).thenReturn(List.of(pe.sevens.sigro.comun.P.mapa("id", 41L, "proyecto_id", OBRA, "tipo_efectivo", "salida", "familia", "material",
                "sentido", -1, "cantidad", new BigDecimal("2"), "costo_unitario", new BigDecimal("30"), "moneda", "PEN", "fecha", "2026-11-25", "codigo", "0201",
                "unidad", "bls")));

        var r = servicio.salida(almacenero, OBRA, salida("celular-0001"));

        assertTrue(r.creado());
        verify(movimientos).insertar(argThat(m -> "salida".equals(m.get("tipo")) && Integer.valueOf(-1).equals(m.get("sentido")) && Long.valueOf(11L).equals(m.get("dispositivo_id"))));
        verify(movimientos).encolarEvento(eq("almacen.salida_registrada"), eq(OBRA), contains("\"clase\":\"consumo\""));
    }

    @Test
    void stockSigueRespondiendoSiComprasNoResponde() {
        when(consultas.stockPorAlmacen(OBRA, null)).thenReturn(List.of());
        when(consultas.stockConsolidado(OBRA)).thenReturn(List.of(Map.of("insumo_id", 5L, "cantidad", BigDecimal.TEN, "ultimo_movimiento_en", Instant.parse("2026-11-25T15:00:00Z"))));
        when(compras.parametrosReposicion(OBRA)).thenThrow(new ErrorSigro(503, "servicio_no_disponible", "compras caído"));

        var r = servicio.stock(OBRA, null);

        assertEquals(List.of("No se pudo consultar el punto de reposición (compras o catálogo no respondió)."), r.get("advertencias"));
        assertEquals(1, ((List<?>) r.get("consolidado")).size());
    }
}
