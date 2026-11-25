package pe.sevens.sigro.almacen;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import pe.sevens.sigro.almacen.AlmacenService.Resultado;
import pe.sevens.sigro.almacen.datos.MovimientoDuplicado;
import pe.sevens.sigro.almacen.datos.MovimientoRepository;
import pe.sevens.sigro.almacen.datos.PrestamoRepository;
import pe.sevens.sigro.comun.*;
import pe.sevens.sigro.seguridad.Acceso.Actor;

/**
 * Préstamo y devolución de herramientas y equipos. Un préstamo no mueve el saldo (la herramienta sigue en la obra):
 * disponible en el almacén = saldo − prestado sin devolver. Se reutilizan las reglas de almacén (almacén activo,
 * insumo del catálogo, celular autorizado) desde {@link AlmacenService}.
 */
@Service
public class PrestamoService {
    static final String[] PRESTAMO_OUT = {"id", "uuid_cliente", "proyecto_id", "almacen_id", "almacen", "insumo_id", "insumo_codigo", "descripcion", "unidad",
            "cantidad", "trabajador_id", "responsable", "devolver_el", "nota", "registrado_por", "prestado_en", "devuelto_en", "devuelto_por",
            "estado_devolucion", "nota_devolucion", "atrasado"};

    private final Modulo db;
    private final PrestamoRepository prestamos;
    private final MovimientoRepository movimientos;
    private final AlmacenService almacen;

    public PrestamoService(Modulo almacen, PrestamoRepository prestamos, MovimientoRepository movimientos, AlmacenService almacenService) {
        this.db = almacen;
        this.prestamos = prestamos;
        this.movimientos = movimientos;
        this.almacen = almacenService;
    }

    static long l(Object o) { return ((Number) o).longValue(); }

    Map<String, Object> vista(long id) { return R.de(prestamos.porId(id), PRESTAMO_OUT); }

    /** R3: el préstamo ya registrado con ese uuid (o null si es nuevo); 409 si el uuid es de otra obra. */
    Map<String, Object> previo(long obraId, UUID u) {
        var p = prestamos.identidadPorUuid(u);
        if (p == null) return null;
        if (l(p.get("proyecto_id")) != obraId) throw new ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otro préstamo.");
        return vista(l(p.get("id")));
    }

    public Resultado prestar(Actor a, long obraId, Datos d) {
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var ya = previo(obraId, u);
            if (ya != null) return Resultado.existente(ya);
            Long disp = almacen.dispositivo(a, d.texto("dispositivo"), "herramienta.prestar", false);
            var alm = almacen.almacen(obraId, d.entero("almacen_id"));
            long insumoId = d.entero("insumo_id");
            var ins = almacen.insumos(List.of(insumoId), true).get(insumoId);
            if (!"equipo".equals(ins.get("tipo")))
                throw new ErrorSigro(422, "no_es_herramienta", ins.get("codigo") + " es un material: solo se prestan herramientas y equipos (tipo «equipo» en el catálogo).");
            long almacenId = l(alm.get("id"));
            movimientos.bloquearSaldo(almacenId, insumoId); // dos préstamos a la vez no prestan lo mismo dos veces
            BigDecimal saldo = movimientos.saldo(almacenId, insumoId);
            BigDecimal prestado = prestamos.prestadoSinDevolver(almacenId, insumoId);
            BigDecimal disponible = Reglas.disponibleParaPrestar(saldo, prestado);
            BigDecimal cantidad = d.decimal("cantidad");
            if (cantidad.compareTo(disponible) > 0)
                throw new ErrorSigro(422, "herramienta_no_disponible", "Hay " + disponible.stripTrailingZeros().toPlainString() + " disponibles de " + ins.get("codigo")
                        + " en " + alm.get("nombre") + " (" + saldo.stripTrailingZeros().toPlainString() + " en el almacén, "
                        + prestado.stripTrailingZeros().toPlainString() + " prestadas): no se pueden prestar " + cantidad.stripTrailingZeros().toPlainString() + ".");
            var fila = new LinkedHashMap<String, Object>(P.mapa("uuid_cliente", u, "proyecto_id", obraId, "almacen_id", almacenId, "insumo_id", insumoId,
                    "cantidad", cantidad, "trabajador_id", d.entero("trabajador_id"), "responsable", d.texto("responsable").strip(), "devolver_el", d.fecha("devolver_el"),
                    "nota", AlmacenService.vacioANulo(d.texto("nota")), "registrado_por", a.usuarioId(), "dispositivo_id", disp,
                    "fecha_dispositivo", AlmacenService.fechaDispositivo(d.entero("hora_dispositivo_ms"))));
            try {
                return Resultado.nuevo(vista(prestamos.insertar(fila)));
            } catch (MovimientoDuplicado dup) {
                return Resultado.existente(previo(obraId, u));
            }
        });
    }

    public Map<String, Object> devolver(Actor a, long obraId, long id, Datos d) {
        return db.en(() -> {
            var p = prestamos.deLaObraParaDevolver(obraId, id);
            if (p == null) throw new ErrorSigro(404, "no_existe", "Ese préstamo no existe en esta obra.");
            if (p.get("devuelto_en") != null) throw new ErrorSigro(409, "ya_devuelto", "Ese préstamo ya se devolvió.");
            prestamos.devolver(id, a.usuarioId(), d.texto("estado"), AlmacenService.vacioANulo(d.texto("nota")));
            var out = new LinkedHashMap<>(vista(id));
            out.put("advertencias", "perdido".equals(d.texto("estado"))
                    ? List.of("Quedó registrada como perdida. Para que el saldo la descuente, regístrala en el conteo físico del almacén.")
                    : List.of());
            return out;
        });
    }

    public Map<String, Object> listar(long obraId, boolean soloSinDevolver) {
        return db.en(() -> {
            var filas = R.todos(prestamos.deLaObra(obraId, soloSinDevolver), PRESTAMO_OUT);
            long atrasados = filas.stream().filter(f -> Boolean.TRUE.equals(f.get("atrasado"))).count();
            long sinDevolver = filas.stream().filter(f -> f.get("devuelto_en") == null).count();
            return R.mapa("sin_devolver", sinDevolver, "atrasados", atrasados, "prestamos", filas, "herramientas", prestamos.disponibles(obraId));
        });
    }
}
