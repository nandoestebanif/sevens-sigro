package pe.sevens.sigro.costo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import pe.sevens.sigro.almacen.Almacen;
import pe.sevens.sigro.catalogo.Catalogo;
import pe.sevens.sigro.compras.Compras;
import pe.sevens.sigro.compras.Dominio;
import pe.sevens.sigro.comun.*;
import pe.sevens.sigro.obra.Obras;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.tareo.Tareo;

/**
 * Costo (port de app/modulos/costo/rutas.py): el gasto en RECURSOS de la obra (HU-58), el inventario valorizado (HU-56)
 * y el consumo contra lo planificado (HU-57). Modelo de lectura: cada cifra declara su definición y su fecha de corte.
 */
@RestController
@RequestMapping("/api/v1/obras/{obra_id}/costo")
public class CostoController {
    static final String DECLARACION = "Informa el gasto en recursos de la obra (materiales, herramientas y equipos, horas hombre), no su costo total: "
            + "quedan fuera los gastos generales, los subcontratos, los servicios y la planilla.";
    static final String CRITERIO = "Materiales y equipos al costo promedio ponderado por almacén (NIC 2), congelado en cada salida; importes sin IGV.";
    static final String DEF_COMPRADO = "Valor de las compras registradas hasta la fecha de corte (base imponible, sin IGV, por fecha del comprobante); las anuladas no cuentan.";
    static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    /** Libro y modelo redondean cada importe por su lado: cada hecho puede diferir hasta medio céntimo (no es un evento pendiente). */
    static BigDecimal toleranciaRedondeo(long hechos) { return TOLERANCIA.max(new BigDecimal("0.005").multiply(BigDecimal.valueOf(hechos))); }
    static final ZoneId LIMA = ZoneId.of("America/Lima");
    static final String AVISO = "Lo planificado en el S10 incluye desperdicio y reserva: una desviación moderada es esperable y no es, por sí sola, "
            + "una falla. La comparación se hace por insumo, no por partida.";
    static final String SUPUESTO = "Lo esperado a la fecha de corte es lo planificado repartido de forma lineal en el plazo de la obra; el S10 no trae "
            + "un calendario de consumo por insumo.";
    static final List<String> ORDENES = List.of("importe", "porcentaje", "cantidad", "codigo");

    private final Modulo db;
    private final Acceso acceso;
    private final Almacen almacen;
    private final Compras compras;
    private final Tareo tareo;
    private final Obras obras;
    private final Catalogo catalogo;

    public CostoController(Modulo costo, Acceso acceso, Almacen almacen, Compras compras, Tareo tareo, Obras obras, Catalogo catalogo) {
        this.db = costo;
        this.acceso = acceso;
        this.almacen = almacen;
        this.compras = compras;
        this.tareo = tareo;
        this.obras = obras;
        this.catalogo = catalogo;
    }

    static BigDecimal q2(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
    static long l(Object o) { return ((Number) o).longValue(); }

    /** datetime.isoformat() de Python para un instante en UTC. */
    static String iso(Instant i) { return (String) Interno.json(List.<Object>of(i)).get(0); }
    static Instant deIso(Object s) { return OffsetDateTime.parse((String) s).toInstant(); }

    /** «AAAA-MM-DD» (fin de ese día) o «AAAA-MM-DDTHH:MM[:SS]»; sin zona se entiende hora de Lima. None = ahora. */
    static Instant corteDe(String texto) {
        if (texto == null || texto.strip().isEmpty()) return null;
        String t = texto.strip();
        var invalido = new ErrorSigro(422, "corte_invalido", "La fecha de corte debe ser AAAA-MM-DD o AAAA-MM-DDTHH:MM.");
        try {
            if (t.matches("\\d{4}-\\d{2}-\\d{2}") || t.matches("\\d{8}")) {
                LocalDate d = t.length() == 8 ? LocalDate.parse(t, DateTimeFormatter.BASIC_ISO_DATE) : LocalDate.parse(t);
                return d.plusDays(1).atStartOfDay().minusSeconds(1).atZone(LIMA).toInstant();
            }
            String n = t.replace(' ', 'T').replace("Z", "+00:00");
            if (n.matches(".*[+-]\\d{2}:?\\d{2}$") && n.indexOf('T') > 0 && n.lastIndexOf('+') > n.indexOf('T') || n.matches(".*T.*-\\d{2}:\\d{2}$"))
                return OffsetDateTime.parse(n).toInstant();
            return LocalDateTime.parse(n).atZone(LIMA).toInstant();
        } catch (RuntimeException e) {
            throw invalido;
        }
    }

    // ------------------------------------------------------------------ HU-58 gasto
    @GetMapping("/gasto")
    public Object gasto(@PathVariable("obra_id") long obraId) {
        acceso.exigir("costo.consultar", obraId);
        Instant pedido = corteDe(Peticion.texto("corte"));
        var conciliacion = new ArrayList<String>();
        var lib = almacen.resumenValorizado(obraId, pedido);
        Instant efectivo = deIso(lib.get("corte"));
        String dia = efectivo.atZone(LIMA).toLocalDate().toString();
        String corteTxt = iso(efectivo);
        var c = compras.comprado(obraId, LocalDate.parse(dia));
        var comprado = R.mapa("clave", "comprado", "valor", Interno.dec(c.get("base_imponible")), "definicion", DEF_COMPRADO, "corte", corteTxt, "nota", null);
        var h = tareo.horas(obraId, LocalDate.parse(dia));
        var horas = R.mapa("horas_normales", Interno.dec(h.get("horas_normales")), "horas_extra", Interno.dec(h.get("horas_extra")), "corte", corteTxt);
        BigDecimal otros = Interno.dec(lib.get("otros"));
        BigDecimal rec = Interno.dec(lib.get("recibido")), con = Interno.dec(lib.get("consumido")), stock = Interno.dec(lib.get("en_stock"));
        BigDecimal identidad = rec.subtract(con, Dominio.PY).add(otros, Dominio.PY);
        if (identidad.subtract(stock, Dominio.PY).abs().compareTo(TOLERANCIA) > 0)
            conciliacion.add("Recibido − consumido + otros movimientos (" + identidad + ") no coincide con lo que hay en stock (" + stock + ").");
        var fam = db.en(() -> {
            if (pedido == null) {
                var m = db.fila("SELECT coalesce(sum(importe) FILTER (WHERE clase = 'ingreso'), 0) AS recibido, coalesce(-sum(importe) FILTER (WHERE clase = 'consumo'), 0) AS consumido, "
                        + "coalesce(sum(importe) FILTER (WHERE clase IN ('ingreso', 'consumo', 'traslado', 'ajuste')), 0) AS en_stock, "
                        + "count(*) AS n FROM costo.hecho WHERE proyecto_id = :p AND moneda = 'PEN' AND familia <> 'mano_obra'", P.de("p", obraId));
                BigDecimal tolerancia = toleranciaRedondeo(((Number) m.get("n")).longValue());
                for (String[] k : new String[][]{{"recibido", "Recibido"}, {"consumido", "Consumido"}, {"en_stock", "En stock"}})
                    if (Interno.dec(lib.get(k[0])).subtract((BigDecimal) m.get(k[0])).abs().compareTo(tolerancia) > 0)
                        conciliacion.add(k[1] + ": el libro de Almacén dice " + Interno.dec(lib.get(k[0])) + " y el modelo de costo " + m.get(k[0]) + "; faltan eventos por entregar.");
            }
            return db.filas("SELECT familia, coalesce(sum(importe) FILTER (WHERE clase = 'ingreso'), 0) AS recibido, coalesce(-sum(importe) FILTER (WHERE clase = 'consumo'), 0) AS consumido, "
                    + "coalesce(sum(importe) FILTER (WHERE clase IN ('ingreso', 'consumo', 'traslado', 'ajuste')), 0) AS en_stock, max(fecha)::text AS hasta "
                    + "FROM costo.hecho WHERE proyecto_id = :p AND moneda = 'PEN' AND fecha <= CAST(:d AS date) GROUP BY familia ORDER BY familia", P.de("p", obraId, "d", dia));
        });
        var cifras = List.of(comprado,
                cifra(lib, "recibido", "Valor de lo ingresado al almacén (recepciones e ingresos sin compra), al costo con que entró, neto de reversos.", corteTxt),
                cifra(lib, "consumido", "Valor de las salidas de material, al costo promedio ponderado congelado en cada salida (NIC 2), neto de reversos.", corteTxt),
                cifra(lib, "en_stock", "Valor del inventario que queda en los almacenes de la obra a la fecha de corte; es el total del inventario valorizado (HU-56).", corteTxt),
                R.mapa("clave", "horas_hombre", "valor", null, "definicion", "Valor de las jornadas registradas (horas × tarifa de su categoría), con las horas extra aparte.",
                        "corte", corteTxt, "nota", "Falta la tarifa por categoría (HU-65): se informan las horas en horas_hombre, sin valorizar."));
        return R.mapa("declaracion", DECLARACION, "criterio", CRITERIO, "moneda", "PEN", "corte", efectivo, "cifras", cifras, "otros_movimientos", otros,
                "por_conciliar", !conciliacion.isEmpty(), "conciliacion", conciliacion, "por_familia", fam, "horas_hombre", horas);
    }

    static Map<String, Object> cifra(Map<String, Object> lib, String clave, String definicion, String corte) {
        return R.mapa("clave", clave, "valor", Interno.dec(lib.get(clave)), "definicion", definicion, "corte", corte, "nota", null);
    }

    // ------------------------------------------------------------------ HU-56 inventario
    static BigDecimal pct(BigDecimal parte, BigDecimal total) {
        return total.signum() != 0 ? parte.multiply(BigDecimal.valueOf(100), Dominio.PY).divide(total, Dominio.PY).setScale(2, RoundingMode.HALF_UP) : new BigDecimal("0.00");
    }

    @GetMapping("/inventario")
    public Object inventario(@PathVariable("obra_id") long obraId) {
        acceso.exigir("costo.consultar", obraId);
        String alm = Peticion.texto("almacen");
        if (alm == null) alm = "todos";
        if (!alm.equals("todos") && !alm.matches("\\d+")) throw new ErrorSigro(422, "almacen_invalido", "El almacén es `todos` o el número de un almacén de la obra.");
        Instant pedido = corteDe(Peticion.texto("corte"));
        Instant ahora = Instant.now();
        boolean ajustado = pedido != null && pedido.isAfter(ahora);
        var d = almacen.saldosValorizados(obraId, pedido);
        List<Map<String, Object>> almacenes = lista(d.get("almacenes")), filasD = lista(d.get("filas"));
        var ids = new HashSet<Long>();
        for (var x : almacenes) ids.add(l(x.get("id")));
        final String almacenSel = alm;
        if (!alm.equals("todos") && !ids.contains(Long.parseLong(alm))) throw new ErrorSigro(404, "no_existe", "Ese almacén no existe en esta obra.");
        var rows = new ArrayList<Map<String, Object>>();
        for (var r : filasD) {
            if (!almacenSel.equals("todos") && l(r.get("almacen_id")) != Long.parseLong(almacenSel)) continue;
            var m = new LinkedHashMap<>(r);
            m.put("cantidad", Interno.dec(r.get("cantidad")));
            m.put("valor", Interno.dec(r.get("valor")));
            rows.add(m);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (var r : rows) total = total.add((BigDecimal) r.get("valor"), Dominio.PY);
        var porAlmacen = new ArrayList<Map<String, Object>>();
        for (var x : almacenes) {
            if (!almacenSel.equals("todos") && l(x.get("id")) != Long.parseLong(almacenSel)) continue;
            var mios = rows.stream().filter(r -> l(r.get("almacen_id")) == l(x.get("id"))).toList();
            BigDecimal v = BigDecimal.ZERO;
            for (var r : mios) v = v.add((BigDecimal) r.get("valor"), Dominio.PY);
            porAlmacen.add(R.mapa("almacen_id", l(x.get("id")), "almacen", x.get("nombre"), "insumos", mios.size(), "valor", v, "porcentaje", pct(v, total)));
        }
        var agrupado = new LinkedHashMap<String, Map<String, Object>>();
        for (var r : rows) {
            var g = agrupado.computeIfAbsent((String) r.get("insumo_codigo"), k -> new LinkedHashMap<>(R.mapa("insumo_codigo", r.get("insumo_codigo"),
                    "descripcion", r.get("descripcion"), "unidad", r.get("unidad"), "tipo", r.get("tipo"), "cantidad", BigDecimal.ZERO, "valor", BigDecimal.ZERO,
                    "ultimo", r.get("ultimo_movimiento_en"))));
            g.put("cantidad", ((BigDecimal) g.get("cantidad")).add((BigDecimal) r.get("cantidad"), Dominio.PY));
            g.put("valor", ((BigDecimal) g.get("valor")).add((BigDecimal) r.get("valor"), Dominio.PY));
            String u = (String) r.get("ultimo_movimiento_en");
            if (u.compareTo((String) g.get("ultimo")) > 0) g.put("ultimo", u);
        }
        var grupos = new ArrayList<>(agrupado.values());
        grupos.sort(Comparator.<Map<String, Object>, BigDecimal>comparing(g -> ((BigDecimal) g.get("valor")).negate()).thenComparing(g -> (String) g.get("insumo_codigo")));
        final BigDecimal tot = total;
        var porInsumo = new ArrayList<Map<String, Object>>();
        for (var g : grupos) {
            BigDecimal cant = (BigDecimal) g.get("cantidad"), val = (BigDecimal) g.get("valor");
            porInsumo.add(R.mapa("insumo_codigo", g.get("insumo_codigo"), "descripcion", g.get("descripcion"), "unidad", g.get("unidad"), "tipo", g.get("tipo"),
                    "cantidad", cant, "costo_promedio", cant.signum() != 0 ? val.divide(cant, Dominio.PY).setScale(6, RoundingMode.HALF_EVEN) : null,
                    "valor", val, "porcentaje", pct(val, tot), "ultimo_movimiento_en", deIso(g.get("ultimo"))));
        }
        return R.mapa("declaracion", DECLARACION, "criterio", CRITERIO, "moneda", "PEN", "almacen", almacenSel, "corte", deIso(d.get("corte")), "corte_ajustado", ajustado,
                "valor_total", total, "por_almacen", porAlmacen, "por_insumo", porInsumo);
    }

    @SuppressWarnings("unchecked")
    static <T> List<T> lista(Object o) { return (List<T>) o; }

    // ------------------------------------------------------------------ HU-57 desviación
    static BigDecimal avancePlazo(LocalDate inicio, LocalDate fin, LocalDate hoy) {
        if (!hoy.isAfter(inicio)) return BigDecimal.ZERO;
        if (!hoy.isBefore(fin)) return BigDecimal.ONE;
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(inicio, hoy)).divide(BigDecimal.valueOf(ChronoUnit.DAYS.between(inicio, fin)), Dominio.PY);
    }

    static Map<String, Object> comparar(BigDecimal cantPlan, BigDecimal precio, Map<String, Object> consumo, BigDecimal avance, BigDecimal tolerancia) {
        var M = Dominio.PY;
        BigDecimal consC = consumo != null ? (BigDecimal) consumo.get("cantidad") : BigDecimal.ZERO;
        BigDecimal consI = consumo != null ? (BigDecimal) consumo.get("importe") : BigDecimal.ZERO;
        BigDecimal espC = cantPlan.multiply(avance, M), espI = espC.multiply(precio, M);
        BigDecimal desvC = consC.subtract(espC, M), desvI = consI.subtract(espI, M), efectoC = desvC.multiply(precio, M);
        BigDecimal pctPlan = cantPlan.signum() != 0 ? consC.multiply(BigDecimal.valueOf(100), M).divide(cantPlan, M) : BigDecimal.ZERO;
        BigDecimal desvPct = espI.signum() != 0 ? desvI.multiply(BigDecimal.valueOf(100), M).divide(espI, M) : null;
        String lectura;
        if (desvPct == null) lectura = consI.signum() > 0 ? "sobre_lo_esperado" : "en_rango";
        else lectura = desvPct.compareTo(tolerancia) > 0 ? "sobre_lo_esperado" : desvPct.compareTo(tolerancia.negate()) < 0 ? "bajo_lo_esperado" : "en_rango";
        return R.mapa("planificado_cantidad", cantPlan, "precio_planificado", precio, "planificado_importe", q2(cantPlan.multiply(precio, M)),
                "esperado_cantidad", espC.setScale(4, RoundingMode.HALF_EVEN), "esperado_importe", q2(espI),
                "consumido_cantidad", consC, "consumido_importe", q2(consI),
                "costo_real", consC.signum() != 0 ? consI.divide(consC, M).setScale(6, RoundingMode.HALF_EVEN) : null,
                "consumido_pct_planificado", q2(pctPlan), "ritmo_pt", q2(pctPlan.subtract(avance.multiply(BigDecimal.valueOf(100), M), M)),
                "desviacion_cantidad", desvC.setScale(4, RoundingMode.HALF_EVEN), "desviacion_importe", q2(desvI),
                "desviacion_pct", desvPct == null ? null : q2(desvPct), "efecto_cantidad", q2(efectoC), "efecto_precio", q2(desvI.subtract(efectoC, M)), "lectura", lectura);
    }

    @GetMapping("/desviacion")
    public Object desviacion(@PathVariable("obra_id") long obraId) {
        acceso.exigir("costo.consultar", obraId);
        BigDecimal tol = Peticion.decimal("tolerancia");
        if (tol == null) tol = new BigDecimal("10");
        if (tol.signum() < 0) throw new ErrorSigro(422, "datos_invalidos", "tolerancia: Input should be greater than or equal to 0");
        if (tol.compareTo(BigDecimal.valueOf(100)) > 0) throw new ErrorSigro(422, "datos_invalidos", "tolerancia: Input should be less than or equal to 100");
        String orden = Peticion.texto("orden");
        if (orden == null) orden = "importe";
        if (!ORDENES.contains(orden)) throw new ErrorSigro(422, "orden_invalido", "El orden es uno de: " + String.join(", ", ORDENES) + ".");
        Instant pedido = corteDe(Peticion.texto("corte"));
        var obra = obras.obra(obraId);
        var req = compras.requerido(obraId);
        var cons = almacen.consumoPorInsumo(obraId, pedido);
        Instant efectivo = deIso(cons.get("corte"));
        BigDecimal avance = avancePlazo(LocalDate.parse((String) obra.get("fecha_inicio")), LocalDate.parse((String) obra.get("fecha_fin")), efectivo.atZone(LIMA).toLocalDate());
        var ids = new TreeSet<Long>();
        for (var r : req) ids.add(l(r.get("insumo_id")));
        var cat = new HashMap<Long, Map<String, Object>>();
        if (!ids.isEmpty()) for (var x : catalogo.buscar(ids, List.of(), false)) cat.put(l(x.get("id")), x);
        var consumo = new LinkedHashMap<Long, Map<String, Object>>();
        for (var c : CostoController.<Map<String, Object>>lista(cons.get("filas"))) {
            var m = new LinkedHashMap<>(c);
            m.put("cantidad", Interno.dec(c.get("cantidad")));
            m.put("importe", Interno.dec(c.get("importe")));
            consumo.put(l(c.get("insumo_id")), m);
        }
        var items = new ArrayList<Map<String, Object>>();
        var planificados = new HashSet<Long>();
        for (var r : req) {
            long iid = l(r.get("insumo_id"));
            var x = cat.get(iid);
            if (x == null || !List.of("material", "equipo").contains(x.get("tipo"))) continue;
            planificados.add(iid);
            var dd = comparar(Interno.dec(r.get("requerido")), Interno.dec(r.get("precio_planificado")), consumo.get(iid), avance, tol);
            var it = new LinkedHashMap<String, Object>(R.mapa("insumo_codigo", x.get("codigo"), "descripcion", x.get("descripcion"), "unidad", x.get("unidad_s10"), "tipo", x.get("tipo")));
            it.putAll(dd);
            items.add(it);
        }
        Comparator<Map<String, Object>> porCodigo = Comparator.comparing(x -> (String) x.get("insumo_codigo"));
        switch (orden) {
            case "importe" -> items.sort(Comparator.<Map<String, Object>, BigDecimal>comparing(x -> ((BigDecimal) x.get("desviacion_importe")).abs().negate()).thenComparing(porCodigo));
            case "porcentaje" -> items.sort(Comparator.<Map<String, Object>, Boolean>comparing(x -> x.get("desviacion_pct") == null)
                    .thenComparing(x -> x.get("desviacion_pct") == null ? BigDecimal.ZERO : ((BigDecimal) x.get("desviacion_pct")).abs().negate()).thenComparing(porCodigo));
            case "cantidad" -> items.sort(Comparator.<Map<String, Object>, BigDecimal>comparing(x -> ((BigDecimal) x.get("desviacion_cantidad")).abs().negate()).thenComparing(porCodigo));
            default -> items.sort(porCodigo);
        }
        var sinPlan = new ArrayList<Map<String, Object>>();
        var ordenados = new ArrayList<>(consumo.entrySet());
        ordenados.sort(Comparator.comparing(e -> (String) e.getValue().get("insumo_codigo")));
        BigDecimal sinPlanTotal = BigDecimal.ZERO;
        for (var e : ordenados) {
            if (planificados.contains(e.getKey())) continue;
            var c = e.getValue();
            sinPlan.add(R.mapa("insumo_codigo", c.get("insumo_codigo"), "descripcion", c.get("descripcion"), "unidad", c.get("unidad"), "tipo", c.get("tipo"),
                    "consumido_cantidad", c.get("cantidad"), "consumido_importe", c.get("importe")));
            sinPlanTotal = sinPlanTotal.add((BigDecimal) c.get("importe"), Dominio.PY);
        }
        BigDecimal planT = BigDecimal.ZERO, espT = BigDecimal.ZERO, conT = BigDecimal.ZERO;
        for (var i : items) {
            planT = planT.add((BigDecimal) i.get("planificado_importe"), Dominio.PY);
            espT = espT.add((BigDecimal) i.get("esperado_importe"), Dominio.PY);
            conT = conT.add((BigDecimal) i.get("consumido_importe"), Dominio.PY);
        }
        BigDecimal desvT = conT.subtract(espT, Dominio.PY);
        return R.mapa("declaracion", DECLARACION, "aviso", AVISO, "supuesto", SUPUESTO, "moneda", "PEN", "corte", efectivo,
                "avance_plazo_pct", q2(avance.multiply(BigDecimal.valueOf(100), Dominio.PY)), "tolerancia_pct", tol, "orden", orden,
                "totales", R.mapa("planificado_importe", planT, "esperado_importe", espT, "consumido_importe", conT, "desviacion_importe", desvT,
                        "desviacion_pct", espT.signum() != 0 ? q2(desvT.multiply(BigDecimal.valueOf(100), Dominio.PY).divide(espT, Dominio.PY)) : null),
                "insumos", items, "no_planificados", sinPlan, "no_planificados_importe", sinPlanTotal);
    }
}
