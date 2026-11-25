package pe.sevens.sigro.compras;

import static pe.sevens.sigro.comun.Esquema.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.sevens.sigro.almacen.Almacen;
import pe.sevens.sigro.comun.*;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.seguridad.Acceso.Actor;

/**
 * Requerimiento de la obra (HU-19), saldo por comprar (HU-24), avance (HU-23), reposición (HU-25) e insumos críticos
 * (HU-28). Port de app/modulos/compras/requerimiento.py. Cantidades en unidad del S10; reposición en unidad de compra.
 */
@RestController
@RequestMapping("/api/v1/obras/{obra_id}")
public class RequerimientoController {
    static Campo cant(String n) { return decimal(n).gt(0).digitos(18, 4); }
    static Campo precio(String n) { return decimal(n).ge(0).digitos(18, 4); }

    static final Esquema IMPORTAR = de(lista("filas", de(texto("insumo_codigo").min(1).max(30), cant("cantidad"), precio("precio"))).min(1).max(5000));
    static final Esquema AGREGAR = de(texto("insumo_codigo").min(1).max(30), cant("cantidad"), precio("precio"), texto("motivo").min(3).max(300));
    static final Esquema AJUSTE = de(cant("cantidad"), texto("motivo").min(3).max(300));
    static final Esquema PARAMETRO = de(texto("insumo_codigo").min(1).max(30), precio("stock_seguridad"), precio("punto_reposicion"))
            .despues(d -> {
                if (d.decimal("punto_reposicion").compareTo(d.decimal("stock_seguridad")) < 0)
                    throw error("El punto de reposición no puede ser menor que el stock de seguridad.");
            });
    static final Esquema CRITICO = de(texto("insumo_codigo").min(1).max(30), booleano("critico"));

    static final String REQ = "SELECT r.id, r.insumo_id, r.cantidad_planificada, r.origen, r.motivo_alta, r.precio_planificado, "
            + "round(r.cantidad_planificada * r.precio_planificado, 2) AS costo_planificado FROM compras.requerimiento r ";
    static final List<String> CAMPOS_REQ = List.of("codigo", "descripcion", "unidad_s10");
    static final String[] REQ_OUT = {"id", "insumo_id", "insumo_codigo", "descripcion", "unidad_s10", "cantidad_planificada", "precio_planificado",
            "costo_planificado", "origen=s10", "motivo_alta", "critico"};
    static final String PAR = "SELECT v.id, v.insumo_id, v.stock_seguridad, v.punto_reposicion, v.registrado_por, v.vigente_desde::text AS vigente_desde ";
    static final List<String> CAMPOS_PAR = List.of("codigo", "descripcion", "unidad_compra");
    static final String[] PAR_OUT = {"id", "insumo_id", "insumo_codigo", "descripcion", "unidad_compra", "stock_seguridad", "punto_reposicion", "registrado_por", "vigente_desde"};

    private final Modulo db;
    private final Acceso acceso;
    private final CatalogoCliente catalogo;
    private final Almacen almacen;

    public RequerimientoController(Modulo compras, Acceso acceso, CatalogoCliente catalogo, Almacen almacen) {
        this.db = compras;
        this.acceso = acceso;
        this.catalogo = catalogo;
        this.almacen = almacen;
    }

    static BigDecimal q4(BigDecimal x) { return x.setScale(4, RoundingMode.HALF_UP); }

    /** Agrega a cada fila los datos del insumo (del catálogo); filtra por código o descripción. */
    List<Map<String, Object>> conInsumo(List<Map<String, Object>> rows, List<String> campos, String q) {
        var insumos = catalogo.porId(rows.stream().map(r -> r.get("insumo_id")).toList());
        var salida = new ArrayList<Map<String, Object>>();
        for (var r : rows) {
            var ins = insumos.get(((Number) r.get("insumo_id")).longValue());
            if (ins == null) continue;
            var m = new LinkedHashMap<>(r);
            for (String c : campos) m.put(c.equals("codigo") ? "insumo_codigo" : c, ins.get(c));
            if (q != null) {
                String ql = q.toLowerCase(Locale.ROOT);
                if (!((String) ins.get("codigo")).toLowerCase(Locale.ROOT).contains(ql) && !((String) ins.get("descripcion")).toLowerCase(Locale.ROOT).contains(ql)) continue;
            }
            salida.add(m);
        }
        return salida;
    }

    static String limpioQ(String q) { return q == null || q.strip().isEmpty() ? null : q.strip(); }

    Long idPorCodigo(String codigo) {
        var ins = catalogo.porCodigo(List.of(codigo), false).get(codigo.toUpperCase(Locale.ROOT));
        return ins != null && ins.get("codigo").equals(codigo) ? (Long) ins.get("id") : null;
    }

    Set<Long> criticosDe(long obraId) {
        var s = new HashSet<Long>();
        for (Object o : db.columna("SELECT insumo_id FROM compras.insumo_critico_vigente WHERE proyecto_id = :p AND critico", P.de("p", obraId))) s.add((Long) o);
        return s;
    }

    static Comparator<Map<String, Object>> porCodigo() { return Comparator.comparing(r -> (String) r.get("insumo_codigo")); }

    // ------------------------------------------------------------------ requerimiento (HU-19)
    @PostMapping("/requerimiento/importar")
    public Object importar(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("requerimiento.importar", obraId);
        Datos d = IMPORTAR.validar(cuerpo);
        List<Datos> filas = d.lista("filas");
        var delCatalogo = catalogo.porCodigo(filas.stream().map(f -> f.texto("insumo_codigo")).toList(), false);
        var conocidos = new HashMap<String, Long>();
        for (Datos f : filas) {
            String c = f.texto("insumo_codigo");
            var ins = delCatalogo.get(c.toUpperCase(Locale.ROOT));
            if (ins != null && ins.get("codigo").equals(c)) conocidos.put(c, (Long) ins.get("id"));
        }
        return db.en(() -> {
            var cargados = new HashMap<Long, Map<String, Object>>();
            for (var r : db.filas("SELECT insumo_id, cantidad_planificada, precio_planificado FROM compras.requerimiento WHERE proyecto_id = :p AND insumo_id = ANY(:ids)",
                    P.de("p", obraId, "ids", P.enteros(new HashSet<>(conocidos.values())))))
                cargados.put((Long) r.get("insumo_id"), r);
            int creados = 0, sinCambios = 0;
            var fallidos = new ArrayList<Map<String, Object>>();
            var avisos = new ArrayList<String>();
            var vistos = new HashSet<String>();
            for (Datos f : filas) {
                String c = f.texto("insumo_codigo");
                if (!vistos.add(c)) { fallidos.add(R.mapa("insumo_codigo", c, "motivo", "Aparece repetido en el archivo.")); continue; }
                Long insId = conocidos.get(c);
                if (insId == null) { fallidos.add(R.mapa("insumo_codigo", c, "motivo", "El insumo no está en el catálogo.")); continue; }
                var ex = cargados.get(insId);
                if (ex == null) {
                    db.actualizar("INSERT INTO compras.requerimiento (proyecto_id, insumo_id, cantidad_planificada, precio_planificado, creado_por) VALUES (:p, :i, :c, :pr, :u)",
                            P.de("p", obraId, "i", insId, "c", f.decimal("cantidad"), "pr", f.decimal("precio"), "u", a.usuarioId()));
                    creados++;
                } else if (((BigDecimal) ex.get("cantidad_planificada")).compareTo(q4(f.decimal("cantidad"))) == 0
                        && ((BigDecimal) ex.get("precio_planificado")).compareTo(q4(f.decimal("precio"))) == 0) {
                    sinCambios++;
                } else {
                    sinCambios++;
                    avisos.add(c + ": ya está cargado con " + ex.get("cantidad_planificada") + " a " + ex.get("precio_planificado") + "; el archivo trae "
                            + f.decimal("cantidad") + " a " + f.decimal("precio") + ". No se pisó: corrígelo con un ajuste y su motivo.");
                }
            }
            return R.mapa("creados", creados, "sin_cambios", sinCambios, "fallidos", fallidos, "advertencias", avisos);
        });
    }

    @GetMapping("/requerimiento")
    public Object ver(@PathVariable("obra_id") long obraId) {
        acceso.exigir("requerimiento.consultar", obraId);
        String q = Peticion.texto("q", null, 100);
        boolean soloCriticos = Boolean.TRUE.equals(Peticion.bool("solo_criticos"));
        return db.en(() -> {
            var rows = conInsumo(db.filas(REQ + "WHERE r.proyecto_id = :p", P.de("p", obraId)), CAMPOS_REQ, limpioQ(q));
            var criticos = criticosDe(obraId);
            var l = new ArrayList<Map<String, Object>>();
            for (var r : rows) {
                r.put("critico", criticos.contains((Long) r.get("insumo_id")));
                if ((Boolean) r.get("critico") || !soloCriticos) l.add(R.de(r, REQ_OUT));
            }
            l.sort(porCodigo());
            return l;
        });
    }

    Map<String, Object> reqDeLaObra(long obraId, long reqId) {
        var r = db.fila(REQ + "WHERE r.id = :i AND r.proyecto_id = :p", P.de("i", reqId, "p", obraId));
        var c = r == null ? List.<Map<String, Object>>of() : conInsumo(List.of(r), CAMPOS_REQ, null);
        if (c.isEmpty()) throw new ErrorSigro(404, "no_existe", "Ese requerimiento no existe en esta obra.");
        return c.get(0);
    }

    @PostMapping("/requerimiento")
    public ResponseEntity<Object> agregar(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("requerimiento.agregar", obraId);
        Datos d = AGREGAR.validar(cuerpo);
        Long insId = idPorCodigo(d.texto("insumo_codigo"));
        if (insId == null) throw new ErrorSigro(422, "insumo_desconocido", "El insumo " + d.texto("insumo_codigo") + " no está en el catálogo. Regístralo primero en «Catálogo de insumos».");
        return C.creado(db.en(() -> {
            Object id = db.escalar("INSERT INTO compras.requerimiento (proyecto_id, insumo_id, cantidad_planificada, precio_planificado, creado_por, origen, motivo_alta) "
                    + "VALUES (:p, :i, :c, :pr, :u, 'manual', :m) ON CONFLICT (proyecto_id, insumo_id) DO NOTHING RETURNING id",
                    P.de("p", obraId, "i", insId, "c", d.decimal("cantidad"), "pr", d.decimal("precio"), "u", a.usuarioId(), "m", d.texto("motivo")));
            if (id == null) throw new ErrorSigro(409, "ya_planificado", d.texto("insumo_codigo") + " ya está en el requerimiento de la obra: corrige su cantidad con un ajuste y su motivo.");
            return R.de(reqDeLaObra(obraId, (Long) id), REQ_OUT);
        }));
    }

    @PostMapping("/requerimiento/{req_id}/ajustar")
    public Object ajustar(@PathVariable("obra_id") long obraId, @PathVariable("req_id") long reqId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("requerimiento.ajustar", obraId);
        Datos d = AJUSTE.validar(cuerpo);
        return db.en(() -> {
            var r = reqDeLaObra(obraId, reqId);
            if (((BigDecimal) r.get("cantidad_planificada")).compareTo(q4(d.decimal("cantidad"))) == 0) throw new ErrorSigro(422, "sin_cambio", "La cantidad ya tiene ese valor.");
            db.actualizar("INSERT INTO compras.requerimiento_ajuste (requerimiento_id, cantidad_anterior, cantidad_nueva, motivo, ajustado_por) VALUES (:r, :ant, :nue, :m, :u)",
                    P.de("r", reqId, "ant", r.get("cantidad_planificada"), "nue", d.decimal("cantidad"), "m", d.texto("motivo"), "u", a.usuarioId()));
            db.actualizar("UPDATE compras.requerimiento SET cantidad_planificada = :c WHERE id = :i", P.de("c", d.decimal("cantidad"), "i", reqId));
            return R.de(reqDeLaObra(obraId, reqId), REQ_OUT);
        });
    }

    @GetMapping("/requerimiento/{req_id}/ajustes")
    public Object ajustes(@PathVariable("obra_id") long obraId, @PathVariable("req_id") long reqId) {
        acceso.exigir("requerimiento.consultar", obraId);
        return db.en(() -> {
            reqDeLaObra(obraId, reqId);
            return db.filas("SELECT id, cantidad_anterior, cantidad_nueva, motivo, ajustado_por, ajustado_en::text AS ajustado_en "
                    + "FROM compras.requerimiento_ajuste WHERE requerimiento_id = :i ORDER BY id", P.de("i", reqId));
        });
    }

    // ------------------------------------------------------------------ saldo por comprar (HU-24)
    @GetMapping("/saldo-por-comprar")
    public Object saldo(@PathVariable("obra_id") long obraId) {
        acceso.exigir("requerimiento.consultar", obraId);
        boolean solo = Boolean.TRUE.equals(Peticion.bool("solo_pendientes"));
        String q = Peticion.texto("q", null, 100);
        return db.en(() -> {
            var rows = db.filas("SELECT s.insumo_id, round(s.requerido, 4) AS requerido, round(s.comprado, 4) AS comprado, s.saldo AS saldo_exacto, "
                    + "round(s.saldo, 4) AS saldo, round(s.exceso, 4) AS exceso FROM compras.saldo_por_comprar s WHERE s.proyecto_id = :p" + (solo ? " AND s.saldo > 0" : ""), P.de("p", obraId));
            rows = conInsumo(rows, List.of("codigo", "descripcion", "unidad_s10", "unidad_compra", "factor_conversion"), limpioQ(q));
            var l = new ArrayList<Map<String, Object>>();
            for (var r : rows) {
                BigDecimal factor = (BigDecimal) r.get("factor_conversion");
                r.put("saldo_en_unidad_compra", q4(((BigDecimal) r.get("saldo_exacto")).divide(factor, Dominio.PY)));
                r.put("estado", ((BigDecimal) r.get("exceso")).signum() > 0 ? "excedido" : ((BigDecimal) r.get("saldo")).signum() == 0 ? "completo" : "pendiente");
                l.add(R.de(r, "insumo_id", "insumo_codigo", "descripcion", "unidad_s10", "unidad_compra", "requerido", "comprado", "saldo", "saldo_en_unidad_compra", "exceso", "estado"));
            }
            l.sort(Comparator.<Map<String, Object>, BigDecimal>comparing(r -> ((BigDecimal) r.get("saldo")).negate()).thenComparing(r -> (String) r.get("insumo_codigo")));
            return l;
        });
    }

    // ------------------------------------------------------------------ avance (HU-23)
    static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (parte == null) return null;
        if (total.signum() <= 0) return new BigDecimal("0.00");
        return parte.multiply(BigDecimal.valueOf(100), Dominio.PY).divide(total, Dominio.PY).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal aUnidadCompra(BigDecimal cantidadS10, BigDecimal factor) { return q4(cantidadS10.divide(factor, Dominio.PY)); }

    @GetMapping("/avance")
    public Object avance(@PathVariable("obra_id") long obraId) {
        acceso.exigir("avance.consultar", obraId);
        String q = Peticion.texto("q", null, 100);
        boolean soloCriticos = Boolean.TRUE.equals(Peticion.bool("solo_criticos"));
        var rows = db.en(() -> conInsumo(db.filas("SELECT s.insumo_id, s.requerido, s.comprado, s.saldo FROM compras.saldo_por_comprar s WHERE s.proyecto_id = :p", P.de("p", obraId)),
                List.of("codigo", "descripcion", "unidad_compra", "factor_conversion"), limpioQ(q)));
        Map<Long, BigDecimal[]> alm;
        try {
            alm = new HashMap<>();
            for (var x : almacen.recibidoConsumido(obraId))
                alm.put(((Number) x.get("insumo_id")).longValue(), new BigDecimal[]{Interno.dec(x.get("recibido")), Interno.dec(x.get("consumido"))});
        } catch (ErrorSigro e) {
            alm = null;
        }
        var criticos = db.en(() -> criticosDe(obraId));
        var salida = new ArrayList<Map<String, Object>>();
        for (var r : rows) {
            long iid = (Long) r.get("insumo_id");
            if (soloCriticos && !criticos.contains(iid)) continue;
            BigDecimal factor = (BigDecimal) r.get("factor_conversion");
            BigDecimal req = aUnidadCompra((BigDecimal) r.get("requerido"), factor), com = aUnidadCompra((BigDecimal) r.get("comprado"), factor);
            BigDecimal saldo = aUnidadCompra((BigDecimal) r.get("saldo"), factor);
            BigDecimal rec = null, con = null;
            if (alm != null) {
                var rc = alm.getOrDefault(iid, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                rec = aUnidadCompra(rc[0], factor);
                con = aUnidadCompra(rc[1], factor);
            }
            salida.add(R.mapa("insumo_id", iid, "insumo_codigo", r.get("insumo_codigo"), "descripcion", r.get("descripcion"), "unidad_compra", r.get("unidad_compra"),
                    "requerido", req, "comprado", com, "recibido", rec, "consumido", con, "saldo_por_comprar", saldo,
                    "pct_comprado", porcentaje(com, req), "pct_recibido", porcentaje(rec, req), "pct_consumido", porcentaje(con, req), "critico", criticos.contains(iid)));
        }
        salida.sort(porCodigo());
        var pendiente = alm != null ? List.<String>of() : List.of("recibido y consumido: los da el servicio almacén, que no respondió; vuelve a consultar en un momento");
        return R.mapa("unidad", "unidad_compra", "base_porcentaje", "requerimiento_total", "pendiente", pendiente, "filas", salida);
    }

    // ------------------------------------------------------------------ reposición (HU-25)
    @PostMapping("/parametros-reposicion")
    public ResponseEntity<Object> registrarParametro(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("reposicion.registrar", obraId);
        Datos d = PARAMETRO.validar(cuerpo);
        Long insId = idPorCodigo(d.texto("insumo_codigo"));
        if (insId == null) throw new ErrorSigro(404, "insumo_no_existe", "Ese insumo no está en el catálogo.");
        return C.creado(db.en(() -> {
            Object id = db.escalar("INSERT INTO compras.parametro_reposicion (proyecto_id, insumo_id, stock_seguridad, punto_reposicion, registrado_por) VALUES (:p, :i, :s, :pr, :u) RETURNING id",
                    P.de("p", obraId, "i", insId, "s", d.decimal("stock_seguridad"), "pr", d.decimal("punto_reposicion"), "u", a.usuarioId()));
            return R.de(conInsumo(List.of(db.fila(PAR + "FROM compras.parametro_reposicion v WHERE v.id = :i", P.de("i", id))), CAMPOS_PAR, null).get(0), PAR_OUT);
        }));
    }

    @GetMapping("/parametros-reposicion")
    public Object parametros(@PathVariable("obra_id") long obraId) {
        acceso.exigir("reposicion.consultar", obraId);
        return db.en(() -> {
            var l = R.todos(conInsumo(db.filas(PAR + "FROM compras.parametro_reposicion_vigente v WHERE v.proyecto_id = :p", P.de("p", obraId)), CAMPOS_PAR, null), PAR_OUT);
            l.sort(porCodigo());
            return l;
        });
    }

    @GetMapping("/parametros-reposicion/{insumo_id}/historial")
    public Object historialParametros(@PathVariable("obra_id") long obraId, @PathVariable("insumo_id") long insumoId) {
        acceso.exigir("reposicion.consultar", obraId);
        return db.en(() -> R.todos(conInsumo(db.filas(PAR + "FROM compras.parametro_reposicion v WHERE v.proyecto_id = :p AND v.insumo_id = :i ORDER BY v.id DESC",
                P.de("p", obraId, "i", insumoId)), CAMPOS_PAR, null), PAR_OUT));
    }

    // ------------------------------------------------------------------ críticos (HU-28)
    static final String[] CRIT_OUT = {"insumo_id", "insumo_codigo", "descripcion", "critico", "registrado_por", "registrado_en"};

    @PostMapping("/criticos")
    public ResponseEntity<Object> marcarCritico(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("critico.marcar", obraId);
        Datos d = CRITICO.validar(cuerpo);
        Long insId = idPorCodigo(d.texto("insumo_codigo"));
        if (insId == null) throw new ErrorSigro(404, "insumo_no_existe", "Ese insumo no está en el catálogo.");
        return C.creado(db.en(() -> {
            if (db.fila("SELECT id FROM compras.requerimiento WHERE proyecto_id = :p AND insumo_id = :i", P.de("p", obraId, "i", insId)) == null)
                throw new ErrorSigro(422, "insumo_fuera_del_requerimiento", "Ese insumo no está en el requerimiento de la obra: agrégalo primero.");
            var nuevo = db.fila("INSERT INTO compras.insumo_critico (proyecto_id, insumo_id, critico, registrado_por) VALUES (:p, :i, :c, :u) "
                    + "RETURNING insumo_id, critico, registrado_por, registrado_en::text AS registrado_en", P.de("p", obraId, "i", insId, "c", d.bool("critico"), "u", a.usuarioId()));
            return R.de(conInsumo(List.of(nuevo), List.of("codigo", "descripcion"), null).get(0), CRIT_OUT);
        }));
    }

    @GetMapping("/criticos")
    public Object criticos(@PathVariable("obra_id") long obraId) {
        acceso.exigir("requerimiento.consultar", obraId);
        return db.en(() -> {
            var l = R.todos(conInsumo(db.filas("SELECT insumo_id, critico, registrado_por, registrado_en::text AS registrado_en FROM compras.insumo_critico_vigente "
                    + "WHERE proyecto_id = :p AND critico", P.de("p", obraId)), List.of("codigo", "descripcion"), null), CRIT_OUT);
            l.sort(porCodigo());
            return l;
        });
    }

    @GetMapping("/criticos/candidatos")
    public Object candidatos(@PathVariable("obra_id") long obraId) {
        acceso.exigir("requerimiento.consultar", obraId);
        Long lim = Peticion.entero("limite", 1L, 100L);
        int limite = lim == null ? 20 : lim.intValue();
        return db.en(() -> {
            var rows = conInsumo(db.filas(REQ + "WHERE r.proyecto_id = :p", P.de("p", obraId)), List.of("codigo", "descripcion", "unidad_s10"), null);
            BigDecimal total = BigDecimal.ZERO;
            for (var r : rows) total = total.add((BigDecimal) r.get("costo_planificado"), Dominio.PY);
            var criticos = criticosDe(obraId);
            var l = new ArrayList<Map<String, Object>>();
            for (var r : rows) {
                r.put("critico", criticos.contains((Long) r.get("insumo_id")));
                r.put("pct_del_total", total.signum() > 0 ? porcentaje((BigDecimal) r.get("costo_planificado"), total) : new BigDecimal("0.00"));
                l.add(R.de(r, "insumo_id", "insumo_codigo", "descripcion", "unidad_s10", "costo_planificado", "pct_del_total", "critico"));
            }
            l.sort(Comparator.<Map<String, Object>, BigDecimal>comparing(r -> (BigDecimal) r.get("costo_planificado")).reversed());
            return l.subList(0, Math.min(limite, l.size()));
        });
    }
}
