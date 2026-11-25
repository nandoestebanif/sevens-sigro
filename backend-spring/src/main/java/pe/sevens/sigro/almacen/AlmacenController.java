package pe.sevens.sigro.almacen;

import static pe.sevens.sigro.comun.Esquema.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.sevens.sigro.catalogo.Catalogo;
import pe.sevens.sigro.compras.Compras;
import pe.sevens.sigro.compras.Dominio;
import pe.sevens.sigro.comun.*;
import pe.sevens.sigro.obra.Obras;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.seguridad.Acceso.Actor;
import pe.sevens.sigro.seguridad.Permisos;

/**
 * Almacén (port de app/modulos/almacen/rutas.py): salidas, ingresos, recepciones, traslados, reversos, conteos, stock,
 * semana y kárdex. R1 el movimiento es un libro · R2 el saldo lo calcula el servidor · R3 reenvío = 200 con el original ·
 * R4 la salida congela el promedio vigente · R5 la salida exige el celular autorizado · R9 rol y obra en cada petición.
 */
@RestController
@RequestMapping("/api/v1/obras/{obra_id}/almacen")
public class AlmacenController {
    static final String CRITERIO = "Costo promedio ponderado por almacén (NIC 2): cada ingreso recalcula el promedio del insumo en ese almacén; "
            + "cada salida congela el promedio vigente en el instante en que se registró. Importes sin IGV.";
    static final String CRITERIO_SEMANA = "Semana = periodo de la obra (HU-03). Saldo inicial: todo lo registrado en periodos anteriores. Entradas y "
            + "salidas: lo registrado en la semana; un reverso resta en la columna del movimiento que anula. Unidad del S10. "
            + "Solo insumos con movimiento en la semana.";
    static final String COLS = "m.id, m.uuid_cliente, m.tipo, m.sentido, m.proyecto_id, m.almacen_id, m.insumo_id, i.codigo AS insumo_codigo, "
            + "i.unidad, m.cantidad, m.costo_unitario, round(m.cantidad * m.costo_unitario, 2) AS importe, m.moneda, m.motivo, "
            + "m.quien_retira, m.frente, m.origen, m.origen_detalle, m.recepcion_id, m.traslado_id, m.conteo_id, m.revierte_a_id, "
            + "m.periodo_id, m.dispositivo_id, m.registrado_por, m.fecha_dispositivo, m.fecha_servidor";
    static final String[] MOV_OUT = {"id", "uuid_cliente", "tipo", "sentido", "proyecto_id", "almacen_id", "insumo_id", "insumo_codigo", "unidad", "cantidad",
            "costo_unitario", "importe", "moneda", "motivo", "quien_retira", "frente", "origen", "origen_detalle", "recepcion_id", "traslado_id", "conteo_id",
            "revierte_a_id", "periodo_id", "dispositivo_id", "registrado_por", "fecha_dispositivo", "fecha_servidor", "saldo"};
    static final String[] KARDEX_OUT;
    static {
        var l = new ArrayList<>(List.of(MOV_OUT));
        l.addAll(List.of("recepcion_motivo_exceso", "revertido_por_id", "saldo_despues", "promedio_despues", "semana_numero"));
        KARDEX_OUT = l.toArray(String[]::new);
    }
    static final String[] STOCK_OUT = {"almacen_id", "almacen", "insumo_id", "insumo_codigo", "descripcion", "unidad", "cantidad", "costo_promedio", "valor",
            "ultimo_movimiento_en", "punto_reposicion", "bajo_punto_reposicion", "stock_seguridad", "bajo_stock_seguridad", "unidad_compra", "cantidad_en_unidad_compra"};

    // ------------------------------------------------------------------ contratos
    static Campo cantidad(String n) { return decimal(n).gt(0).digitos(18, 4); }
    static Campo[] desde(boolean dispositivoObligatorio, boolean fechaObligatoria) {
        return new Campo[]{uuid("uuid_cliente"), entero("hora_dispositivo_ms").opcional().ge(0),
                fechaObligatoria ? fecha("fecha") : fecha("fecha").opcional(),
                dispositivoObligatorio ? texto("dispositivo").min(8).max(128) : texto("dispositivo").opcional().min(8).max(128)};
    }
    static Esquema con(Campo[] base, Campo... mas) {
        var l = new ArrayList<>(List.of(base));
        l.addAll(List.of(mas));
        return Esquema.de(l.toArray(Campo[]::new));
    }
    static final Esquema LINEA = de(entero("insumo_id").ge(1), cantidad("cantidad"));
    static final Esquema SALIDA = con(desde(true, false), entero("almacen_id").opcional().ge(1), entero("insumo_id").ge(1), cantidad("cantidad"),
            texto("unidad").opcional().max(20), texto("motivo").min(1).max(300), texto("quien_retira").min(1).max(120), texto("frente").opcional().max(120));
    static final Esquema INGRESO = con(desde(false, false), entero("almacen_id").opcional().ge(1), entero("insumo_id").ge(1), cantidad("cantidad"),
            texto("unidad").opcional().max(20), decimal("costo_unitario").gt(0).digitos(18, 6), texto("origen").uno(Reglas.ORIGENES),
            texto("origen_detalle").opcional().max(300))
            .despues(d -> {
                String o = d.texto("origen"), det = d.texto("origen_detalle");
                if ((o.equals("otro") || o.equals("saldo_otra_obra")) && (det == null || det.strip().isEmpty()))
                    throw error("Ese origen exige su detalle (para «saldo de otra obra», el nombre de la obra).");
            });
    static final Esquema RECEPCION = con(desde(false, false), entero("compra_id").ge(1), entero("almacen_id").opcional().ge(1),
            lista("lineas", LINEA).opcional().min(1).max(500), texto("motivo_exceso").opcional().max(300));
    static final Esquema VERIFICACION = de(entero("compra_id").ge(1), lista("lineas", LINEA).opcional().min(1).max(500));
    static final Esquema TRASLADO = con(desde(false, false), entero("origen_almacen_id").opcional().ge(1), entero("destino_obra_id").opcional().ge(1),
            entero("destino_almacen_id").ge(1), texto("motivo").min(1).max(300), lista("lineas", LINEA).min(1).max(500))
            .despues(d -> {
                var s = new HashSet<Object>();
                for (Datos l : d.<Datos>lista("lineas")) s.add(l.entero("insumo_id"));
                if (s.size() != d.lista("lineas").size()) throw error("Un insumo aparece dos veces en el traslado.");
            });
    static final Esquema ANULAR = con(desde(false, false), texto("motivo").min(3).max(300));
    static final Esquema CONTEO = con(desde(false, true), entero("almacen_id").opcional().ge(1));
    static final Esquema CONTEO_LINEA = de(entero("insumo_id").ge(1), decimal("cantidad_contada").ge(0).digitos(18, 4), texto("motivo").opcional().max(300));

    private final Modulo db;
    private final Acceso acceso;
    private final Obras obras;
    private final Catalogo catalogo;
    private final Compras compras;

    public AlmacenController(Modulo almacen, Acceso acceso, Obras obras, Catalogo catalogo, Compras compras) {
        this.db = almacen;
        this.acceso = acceso;
        this.obras = obras;
        this.catalogo = catalogo;
        this.compras = compras;
    }

    static long l(Object o) { return ((Number) o).longValue(); }

    // ------------------------------------------------------------------ lo que se pregunta a otros módulos
    Long dispositivo(Actor a, String identificador, String accion, boolean obligatorio) {
        if (identificador == null) {
            if (obligatorio) throw new ErrorSigro(403, "dispositivo_no_autorizado", "Este registro solo se acepta desde un celular autorizado.");
            return null;
        }
        String rol = acceso.rolEnObra(a.usuarioId(), a.obraId());
        if (rol == null || !Permisos.roles(accion).contains(rol)) throw new ErrorSigro(403, "sin_permiso", "No tienes permiso para esta operación en esta obra.");
        return obras.dispositivoActivo(a.usuarioId(), a.obraId(), identificador);
    }

    void asignadoEn(Actor a, long obraId) {
        if (acceso.rolEnObra(a.usuarioId(), obraId) == null) throw new ErrorSigro(403, "sin_permiso", "No tienes permiso para esta operación en esta obra.");
    }

    Map<String, Object> almacen(long obraId, Long almacenId) {
        var lista = obras.almacenes(obraId);
        for (var x : lista)
            db.actualizar("INSERT INTO almacen.almacen_ref (id, proyecto_id, nombre, tipo, estado) VALUES (:i, :p, :n, :t, :e) "
                    + "ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, tipo = EXCLUDED.tipo, estado = EXCLUDED.estado, sincronizado_en = now()",
                    P.de("i", x.get("id"), "p", x.get("proyecto_id"), "n", x.get("nombre"), "t", x.get("tipo"), "e", x.get("estado")));
        var activos = lista.stream().filter(x -> "activo".equals(x.get("estado"))).toList();
        if (almacenId == null) {
            if (activos.size() == 1) return activos.get(0);
            throw new ErrorSigro(422, "almacen_requerido", !activos.isEmpty() ? "Indica el almacén (almacen_id): la obra no tiene uno solo activo."
                    : "La obra no tiene almacenes activos; regístralo primero.");
        }
        return activos.stream().filter(x -> l(x.get("id")) == almacenId).findFirst()
                .orElseThrow(() -> new ErrorSigro(404, "almacen_no_existe", "Ese almacén no existe o no está activo en esa obra."));
    }

    Map<Long, Map<String, Object>> insumos(Collection<Long> idsIn, boolean paraRegistrar) {
        var ids = new TreeSet<>(idsIn);
        if (ids.isEmpty()) return Map.of();
        var salida = new LinkedHashMap<Long, Map<String, Object>>();
        for (var x : catalogo.buscar(ids, List.of(), false)) salida.put(l(x.get("id")), x);
        if (paraRegistrar) {
            var faltan = ids.stream().filter(i -> !salida.containsKey(i) || !Boolean.TRUE.equals(salida.get(i).get("activo"))).map(String::valueOf).toList();
            if (!faltan.isEmpty()) throw new ErrorSigro(422, "insumo_no_existe", "No existen en el catálogo (o están inactivos): " + String.join(", ", faltan) + ".");
            var otros = ids.stream().filter(i -> !Reglas.FAMILIAS.containsKey(salida.get(i).get("tipo"))).map(i -> (String) salida.get(i).get("codigo")).toList();
            if (!otros.isEmpty()) throw new ErrorSigro(422, "insumo_no_almacenable", "Mano de obra y subcontratos no pasan por almacén: " + String.join(", ", otros) + ".");
            for (Long i : ids) {
                var x = salida.get(i);
                db.actualizar("INSERT INTO almacen.insumo_ref (id, codigo, descripcion, unidad, tipo, activo) VALUES (:i, :c, :d, :u, :t, :a) "
                        + "ON CONFLICT (id) DO UPDATE SET codigo = EXCLUDED.codigo, descripcion = EXCLUDED.descripcion, unidad = EXCLUDED.unidad, "
                        + "tipo = EXCLUDED.tipo, activo = EXCLUDED.activo, sincronizado_en = now()",
                        P.de("i", x.get("id"), "c", x.get("codigo"), "d", x.get("descripcion"), "u", x.get("unidad_s10"), "t", x.get("tipo"), "a", x.get("activo")));
            }
        }
        return salida;
    }

    long periodo(long obraId, Long horaMs, boolean esReverso, LocalDate dia) {
        var periodos = obras.periodos(obraId);
        LocalDate f = dia != null ? dia : Reglas.fechaDelHecho(horaMs, Instant.now());
        return l(Reglas.resolverPeriodo(periodos, f, esReverso).get("id"));
    }

    static Timestamp fechaDispositivo(Long ms) { return ms == null ? null : Timestamp.from(Instant.ofEpochMilli(ms)); }

    // ------------------------------------------------------------------ el libro
    Map<String, Object> movimiento(long id) {
        return db.fila("SELECT " + COLS + ", (SELECT sum(x.sentido * x.cantidad) FROM almacen.movimiento x WHERE x.almacen_id = m.almacen_id "
                + "AND x.insumo_id = m.insumo_id AND x.id <= m.id) AS saldo FROM almacen.movimiento m JOIN almacen.insumo_ref i ON i.id = m.insumo_id WHERE m.id = :i", P.de("i", id));
    }

    Map<String, Object> porUuid(long obraId, UUID uuid, Set<String> tipos) {
        var m = db.fila("SELECT id, proyecto_id, tipo FROM almacen.movimiento WHERE uuid_cliente = :u", P.de("u", uuid));
        if (m == null) return null;
        if (l(m.get("proyecto_id")) != obraId || !tipos.contains(m.get("tipo"))) throw new ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otro movimiento.");
        return movimiento((Long) m.get("id"));
    }

    static final class Duplicado extends RuntimeException {
        Duplicado() { super(null, null, false, false); }
    }

    /** Inserta UN movimiento en su propio punto de guardado; el disparador valida el stock y congela el costo. */
    Map<String, Object> insertar(Map<String, Object> m) {
        m.putIfAbsent("costo_unitario", BigDecimal.ZERO);
        var cols = String.join(", ", m.keySet());
        var vals = new StringJoiner(", ");
        for (String k : m.keySet()) vals.add(":" + k);
        var p = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(m);
        long id;
        try {
            id = db.anidado(() -> (Long) db.escalar("INSERT INTO almacen.movimiento (" + cols + ") VALUES (" + vals + ") RETURNING id", p));
        } catch (DataAccessException ex) {
            PSQLException pg = null;
            for (Throwable t = ex; t != null; t = t.getCause()) if (t instanceof PSQLException q) { pg = q; break; }
            ServerErrorMessage sm = pg != null ? pg.getServerErrorMessage() : null;
            String restriccion = sm != null ? sm.getConstraint() : null;
            String mensaje = sm != null && sm.getMessage() != null ? sm.getMessage() : "";
            if ("uq_movimiento_uuid_cliente".equals(restriccion)) throw new Duplicado();
            if (mensaje.startsWith("SIGRO-A1")) throw new ErrorSigro(422, "stock_insuficiente", Reglas.capitalizar(mensaje.split(": ", 2)[1]) + ".");
            if (mensaje.startsWith("SIGRO-A4")) throw new ErrorSigro(409, "reverso_de_reverso", "Un reverso no se revierte: registra el movimiento correcto.");
            if ("uq_movimiento_revierte_a".equals(restriccion)) throw new ErrorSigro(409, "ya_revertido", "Ese movimiento ya fue revertido.");
            throw ex;
        }
        return movimiento(id);
    }

    /** Hechos ya valorizados hacia costo, en la MISMA transacción (outbox, destino 'integracion'). */
    void publicar(String tipo, long obraId, List<Map<String, Object>> movimientos) {
        if (movimientos.isEmpty()) return;
        var ids = movimientos.stream().map(m -> l(m.get("id"))).toList();
        var rows = db.filas("SELECT m.id, m.proyecto_id, m.tipo, coalesce(o.tipo, m.tipo) AS tipo_efectivo, m.sentido, m.cantidad, m.costo_unitario, m.moneda, "
                + "(m.fecha_servidor AT TIME ZONE 'America/Lima')::date AS fecha, i.codigo, i.unidad, i.tipo AS familia FROM almacen.movimiento m "
                + "JOIN almacen.insumo_ref i ON i.id = m.insumo_id LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id WHERE m.id = ANY(:ids) ORDER BY m.id",
                P.de("ids", P.enteros(ids)));
        var hechos = new ArrayList<Map<String, Object>>();
        for (var r : rows)
            hechos.add(R.mapa("proyecto_id", r.get("proyecto_id"), "familia", Reglas.FAMILIAS.get(r.get("familia")), "clase", Reglas.CLASE.get(r.get("tipo_efectivo")),
                    "insumo_codigo", r.get("codigo"), "cantidad", BigDecimal.valueOf(l(r.get("sentido"))).multiply((BigDecimal) r.get("cantidad")).toString(),
                    "unidad", r.get("unidad"), "costo_unitario", r.get("costo_unitario").toString(), "moneda", r.get("moneda"), "fecha", r.get("fecha").toString(),
                    "referencia", "almacen.movimiento:" + r.get("id")));
        db.escalar("INSERT INTO outbox.evento (tipo, proyecto_id, payload, destino) VALUES (:t, :p, CAST(:j AS jsonb), 'integracion') RETURNING id",
                P.de("t", tipo, "p", obraId, "j", Json.texto(R.mapa("hechos", hechos))));
    }

    ResponseEntity<Object> registrarUno(long obraId, String tipoEvento, Map<String, Object> m) {
        try {
            var nuevo = insertar(m);
            publicar(tipoEvento, obraId, List.of(nuevo));
            return C.creado(R.de(nuevo, MOV_OUT));
        } catch (Duplicado d) {
            return C.con(200, R.de(porUuid(obraId, (UUID) m.get("uuid_cliente"), Set.of((String) m.get("tipo"))), MOV_OUT));
        }
    }

    static Map<String, Object> mov(Object... pares) { return new LinkedHashMap<>(P.mapa(pares)); }

    // ------------------------------------------------------------------ HU-40 salida
    @PostMapping("/salidas")
    public ResponseEntity<Object> salida(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("salida.registrar", obraId);
        Datos d = SALIDA.validar(cuerpo);
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var previo = porUuid(obraId, u, Set.of("salida"));
            if (previo != null) return C.con(200, (Object) R.de(previo, MOV_OUT));
            Long disp = dispositivo(a, d.texto("dispositivo"), "salida.registrar", true);
            var alm = almacen(obraId, d.entero("almacen_id"));
            var ins = insumos(List.of(d.entero("insumo_id")), true).get(d.entero("insumo_id"));
            BigDecimal cant = Reglas.aUnidadS10(d.decimal("cantidad"), d.texto("unidad"), ins);
            String frente = d.texto("frente") == null || d.texto("frente").isEmpty() ? null : d.texto("frente");
            return registrarUno(obraId, "almacen.salida_registrada", mov("uuid_cliente", u, "proyecto_id", obraId, "almacen_id", alm.get("id"),
                    "insumo_id", d.entero("insumo_id"), "tipo", "salida", "sentido", -1, "cantidad", cant, "motivo", d.texto("motivo"),
                    "quien_retira", d.texto("quien_retira"), "frente", frente, "periodo_id", periodo(obraId, d.entero("hora_dispositivo_ms"), false, d.fecha("fecha")),
                    "dispositivo_id", disp, "registrado_por", a.usuarioId(), "fecha_dispositivo", fechaDispositivo(d.entero("hora_dispositivo_ms"))));
        });
    }

    // ------------------------------------------------------------------ HU-41 ingreso sin compra
    @PostMapping("/ingresos")
    public ResponseEntity<Object> ingreso(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("ingreso.registrar", obraId);
        Datos d = INGRESO.validar(cuerpo);
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var previo = porUuid(obraId, u, Set.of("ingreso_sin_compra"));
            if (previo != null) return C.con(200, (Object) R.de(previo, MOV_OUT));
            if ("saldo_otra_obra".equals(d.texto("origen"))) {
                var ex = obras.buscar(d.texto("origen_detalle"));
                if (!ex.isEmpty()) throw new ErrorSigro(422, "usar_traslado", "La obra «" + ex.get(0).get("nombre") + "» está registrada en SIGRO: "
                        + "registra un traslado desde esa obra en vez de un ingreso sin compra.");
            }
            Long disp = dispositivo(a, d.texto("dispositivo"), "ingreso.registrar", false);
            var alm = almacen(obraId, d.entero("almacen_id"));
            var ins = insumos(List.of(d.entero("insumo_id")), true).get(d.entero("insumo_id"));
            BigDecimal cant = Reglas.aUnidadS10(d.decimal("cantidad"), d.texto("unidad"), ins);
            BigDecimal costo = Reglas.aCostoS10(d.decimal("costo_unitario"), d.texto("unidad"), ins);
            return registrarUno(obraId, "almacen.ingreso_registrado", mov("uuid_cliente", u, "proyecto_id", obraId, "almacen_id", alm.get("id"),
                    "insumo_id", d.entero("insumo_id"), "tipo", "ingreso_sin_compra", "sentido", 1, "cantidad", cant, "costo_unitario", costo,
                    "origen", d.texto("origen"), "origen_detalle", d.texto("origen_detalle"),
                    "periodo_id", periodo(obraId, d.entero("hora_dispositivo_ms"), false, d.fecha("fecha")), "dispositivo_id", disp,
                    "registrado_por", a.usuarioId(), "fecha_dispositivo", fechaDispositivo(d.entero("hora_dispositivo_ms"))));
        });
    }

    // ------------------------------------------------------------------ HU-45 reverso
    @PostMapping("/movimientos/{movimiento_id}/reverso")
    public ResponseEntity<Object> reverso(@PathVariable("obra_id") long obraId, @PathVariable("movimiento_id") long movId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("movimiento.revertir", obraId);
        Datos d = ANULAR.validar(cuerpo);
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var previo = porUuid(obraId, u, Set.of("reverso"));
            if (previo != null) return C.con(200, (Object) R.de(previo, MOV_OUT));
            var o = db.fila("SELECT * FROM almacen.movimiento WHERE id = :i AND proyecto_id = :p", P.de("i", movId, "p", obraId));
            if (o == null) throw new ErrorSigro(404, "no_existe", "Ese movimiento no existe en esta obra.");
            if (List.of("traslado_salida", "traslado_ingreso").contains(o.get("tipo")))
                throw new ErrorSigro(409, "usar_anular_traslado", "Un traslado se anula entero (sus dos movimientos), no por una mitad.");
            Long disp = dispositivo(a, d.texto("dispositivo"), "movimiento.revertir", false);
            return registrarUno(obraId, "almacen.movimiento_revertido", mov("uuid_cliente", u, "proyecto_id", obraId, "almacen_id", o.get("almacen_id"),
                    "insumo_id", o.get("insumo_id"), "tipo", "reverso", "sentido", -l(o.get("sentido")), "cantidad", o.get("cantidad"), "costo_unitario", o.get("costo_unitario"),
                    "motivo", d.texto("motivo"), "revierte_a_id", o.get("id"), "periodo_id", periodo(obraId, d.entero("hora_dispositivo_ms"), true, d.fecha("fecha")),
                    "dispositivo_id", disp, "registrado_por", a.usuarioId(), "fecha_dispositivo", fechaDispositivo(d.entero("hora_dispositivo_ms"))));
        });
    }

    // ------------------------------------------------------------------ HU-35 recepción
    List<Map<String, Object>> estadoRecepcion(Map<String, Object> compra, Map<Long, Map<String, Object>> insumos) {
        var recibido = new HashMap<Long, BigDecimal>();
        for (var r : db.filas("SELECT insumo_id, recibido FROM almacen.recibido_por_compra WHERE compra_id = :c", P.de("c", compra.get("id"))))
            recibido.put(l(r.get("insumo_id")), (BigDecimal) r.get("recibido"));
        var salida = new ArrayList<Map<String, Object>>();
        for (var lin : this.<Map<String, Object>>filasDe(compra.get("lineas"))) {
            long iid = l(lin.get("insumo_id"));
            BigDecimal factor = Interno.dec(insumos.get(iid).get("factor_conversion"));
            BigDecimal pedido = Reglas.q4par(Interno.dec(lin.get("cantidad")));
            BigDecimal rec = Reglas.q4par(recibido.getOrDefault(iid, BigDecimal.ZERO).divide(factor, Dominio.PY));
            BigDecimal pend = pedido.subtract(rec);
            salida.add(R.mapa("insumo_id", iid, "unidad_compra", lin.get("unidad"), "pedido", pedido, "recibido", rec, "pendiente", pend.signum() >= 0 ? pend : BigDecimal.ZERO));
        }
        return salida;
    }

    @SuppressWarnings("unchecked")
    <T> List<T> filasDe(Object o) { return (List<T>) o; }

    Map<String, Object> compraDeLaObra(long obraId, long compraId) {
        var c = compras.compra(compraId);
        if (l(c.get("proyecto_id")) != obraId) throw new ErrorSigro(404, "compra_no_existe", "Esa compra no existe en esta obra.");
        return c;
    }

    Set<Long> insumosDe(Map<String, Object> compra) {
        var s = new LinkedHashSet<Long>();
        for (var lin : this.<Map<String, Object>>filasDe(compra.get("lineas"))) s.add(l(lin.get("insumo_id")));
        return s;
    }

    Map<String, Object> recepcionOut(Map<String, Object> rec, Map<String, Object> compra, Map<Long, Map<String, Object>> insumos, List<String> advertencias) {
        var movs = new ArrayList<Map<String, Object>>();
        for (Object id : db.columna("SELECT id FROM almacen.movimiento WHERE recepcion_id = :r AND tipo = 'ingreso_compra' ORDER BY id", P.de("r", rec.get("id"))))
            movs.add(R.de(movimiento((Long) id), MOV_OUT));
        return R.mapa("id", rec.get("id"), "uuid_cliente", rec.get("uuid_cliente"), "movimientos", movs, "compra_id", l(compra.get("id")),
                "lineas", estadoRecepcion(compra, insumos), "advertencias", advertencias);
    }

    LinkedHashMap<Long, BigDecimal> pedido(Map<Long, Map<String, Object>> estado, Map<Long, Map<String, Object>> insumos, List<Datos> lineas) {
        var pedir = new LinkedHashMap<Long, BigDecimal>();
        if (lineas == null) {
            estado.forEach((i, x) -> { if (((BigDecimal) x.get("pendiente")).signum() > 0) pedir.put(i, (BigDecimal) x.get("pendiente")); });
            if (pedir.isEmpty()) throw new ErrorSigro(409, "compra_ya_recibida", "Esa compra ya se recibió completa.");
            return pedir;
        }
        for (Datos ln : lineas) {
            long iid = ln.entero("insumo_id");
            if (!estado.containsKey(iid)) throw new ErrorSigro(422, "insumo_no_comprado", "El insumo " + iid + " no está en esa compra.");
            if (pedir.containsKey(iid)) throw new ErrorSigro(422, "datos_invalidos", "Un insumo aparece dos veces en la recepción.");
            var x = estado.get(iid);
            if (ln.decimal("cantidad").compareTo((BigDecimal) x.get("pendiente")) > 0)
                throw new ErrorSigro(422, "recepcion_excede_compra", insumos.get(iid).get("codigo") + ": se compró " + x.get("pedido") + " " + x.get("unidad_compra")
                        + ", ya se recibió " + x.get("recibido") + " y quedan " + x.get("pendiente") + "; no se puede recibir " + ln.decimal("cantidad") + ".");
            pedir.put(iid, ln.decimal("cantidad"));
        }
        return pedir;
    }

    List<Map<String, Object>> verificar(long obraId, Map<Long, Map<String, Object>> insumos, Map<Long, BigDecimal> pedir) {
        var requerido = new HashMap<Long, BigDecimal>();
        for (var r : compras.requerido(obraId)) requerido.put(l(r.get("insumo_id")), Interno.dec(r.get("requerido")));
        var ya = new HashMap<Long, BigDecimal>();
        for (var r : db.filas("SELECT m.insumo_id, coalesce(sum(m.sentido * m.cantidad), 0) AS recibido FROM almacen.movimiento m "
                + "LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id WHERE m.proyecto_id = :p AND m.insumo_id = ANY(:ids) "
                + "AND coalesce(o.tipo, m.tipo) = 'ingreso_compra' GROUP BY m.insumo_id", P.de("p", obraId, "ids", P.enteros(pedir.keySet()))))
            ya.put(l(r.get("insumo_id")), (BigDecimal) r.get("recibido"));
        var salida = new ArrayList<Map<String, Object>>();
        pedir.forEach((iid, cant) -> {
            var ins = insumos.get(iid);
            BigDecimal ahora = Reglas.q4par(cant.multiply(Interno.dec(ins.get("factor_conversion")), Dominio.PY));
            BigDecimal antes = Reglas.q4par(ya.getOrDefault(iid, BigDecimal.ZERO));
            BigDecimal req = requerido.get(iid);
            BigDecimal exceso = Reglas.excesoSobreRequerido(req, antes, ahora);
            salida.add(R.mapa("insumo_id", iid, "insumo_codigo", ins.get("codigo"), "unidad", ins.get("unidad_s10"), "requerido", req != null ? Reglas.q4par(req) : null,
                    "ya_recibido", antes, "con_esta", antes.add(ahora), "exceso", exceso, "excede", exceso != null && exceso.signum() > 0));
        });
        return salida;
    }

    @PostMapping("/recepciones/verificacion")
    public Object verificacion(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        acceso.exigir("recepcion.registrar", obraId);
        Datos d = VERIFICACION.validar(cuerpo);
        return db.en(() -> {
            var compra = compraDeLaObra(obraId, d.entero("compra_id"));
            var insumos = insumos(insumosDe(compra), false);
            var estado = new LinkedHashMap<Long, Map<String, Object>>();
            for (var x : estadoRecepcion(compra, insumos)) estado.put((Long) x.get("insumo_id"), x);
            var lineas = verificar(obraId, insumos, pedido(estado, insumos, d.lista("lineas")));
            return R.mapa("compra_id", l(compra.get("id")), "hay_exceso", lineas.stream().anyMatch(x -> (Boolean) x.get("excede")), "lineas", lineas);
        });
    }

    @PostMapping("/recepciones")
    public ResponseEntity<Object> recepcion(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("recepcion.registrar", obraId);
        Datos d = RECEPCION.validar(cuerpo);
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var previa = db.fila("SELECT id, uuid_cliente, proyecto_id, compra_id FROM almacen.recepcion WHERE uuid_cliente = :u", P.de("u", u));
            var compra = compraDeLaObra(obraId, d.entero("compra_id"));
            var insumos = insumos(insumosDe(compra), previa == null);
            if (previa != null) {
                if (l(previa.get("proyecto_id")) != obraId || l(previa.get("compra_id")) != d.entero("compra_id"))
                    throw new ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otra recepción.");
                return C.con(200, (Object) recepcionOut(previa, compra, insumos, List.of()));
            }
            if (!"registrada".equals(compra.get("estado"))) throw new ErrorSigro(409, "compra_anulada", "Esa compra está anulada: no se puede recibir.");
            Long disp = dispositivo(a, d.texto("dispositivo"), "recepcion.registrar", false);
            var alm = almacen(obraId, d.entero("almacen_id"));
            db.fila("SELECT pg_advisory_xact_lock(hashtext('sigro.recepcion'), CAST(:c AS int))", P.de("c", d.entero("compra_id")));
            var estado = new LinkedHashMap<Long, Map<String, Object>>();
            for (var x : estadoRecepcion(compra, insumos)) estado.put((Long) x.get("insumo_id"), x);
            var pedir = pedido(estado, insumos, d.lista("lineas"));
            var verif = verificar(obraId, insumos, pedir);
            var advertencias = new ArrayList<String>();
            if (verif.stream().anyMatch(x -> (Boolean) x.get("excede"))) {
                String codigos = String.join(", ", verif.stream().filter(x -> (Boolean) x.get("excede")).map(x -> (String) x.get("insumo_codigo")).toList());
                String mot = d.texto("motivo_exceso");
                if (mot == null || mot.strip().isEmpty())
                    throw new ErrorSigro(422, "exceso_sin_motivo", "Con esta recepción se pasa lo requerido (" + codigos + "): indica el motivo del exceso "
                            + "o recibe solo hasta lo requerido.");
                advertencias.add("Se recibió más de lo requerido (" + codigos + "); queda el motivo en el kárdex.");
            }
            long per = periodo(obraId, d.entero("hora_dispositivo_ms"), false, d.fecha("fecha"));
            var rec = db.fila("INSERT INTO almacen.recepcion (uuid_cliente, proyecto_id, almacen_id, compra_id, motivo_exceso, registrado_por, dispositivo_id, fecha_dispositivo) "
                    + "VALUES (:u, :p, :a, :c, :m, :r, :d, :f) RETURNING id, uuid_cliente", P.de("u", u, "p", obraId, "a", alm.get("id"), "c", d.entero("compra_id"),
                    "m", d.texto("motivo_exceso"), "r", a.usuarioId(), "d", disp, "f", fechaDispositivo(d.entero("hora_dispositivo_ms"))));
            var lineas = new HashMap<Long, Map<String, Object>>();
            for (var lin : this.<Map<String, Object>>filasDe(compra.get("lineas"))) lineas.put(l(lin.get("insumo_id")), lin);
            var nuevos = new ArrayList<Map<String, Object>>();
            pedir.forEach((iid, cant) -> {
                BigDecimal factor = Interno.dec(insumos.get(iid).get("factor_conversion"));
                nuevos.add(insertar(mov("uuid_cliente", Reglas.uuidDerivado(u, "recepcion", iid), "proyecto_id", obraId, "almacen_id", alm.get("id"), "insumo_id", iid,
                        "tipo", "ingreso_compra", "sentido", 1, "cantidad", Reglas.q4par(cant.multiply(factor, Dominio.PY)),
                        "costo_unitario", Reglas.costoRecepcion(Interno.dec(lineas.get(iid).get("precio_unitario")), Interno.dec(compra.get("factor_sin_igv")), factor),
                        "recepcion_id", rec.get("id"), "periodo_id", per, "dispositivo_id", disp, "registrado_por", a.usuarioId(),
                        "fecha_dispositivo", fechaDispositivo(d.entero("hora_dispositivo_ms")))));
            });
            publicar("almacen.recepcion_registrada", obraId, nuevos);
            return C.creado((Object) recepcionOut(rec, compra, insumos, advertencias));
        });
    }

    @GetMapping("/compras/{compra_id}/recepcion")
    public Object estadoRecepcion(@PathVariable("obra_id") long obraId, @PathVariable("compra_id") long compraId) {
        acceso.exigir("stock.consultar", obraId);
        return db.en(() -> {
            var compra = compraDeLaObra(obraId, compraId);
            return estadoRecepcion(compra, insumos(insumosDe(compra), false));
        });
    }

    // ------------------------------------------------------------------ HU-42 traslado
    Map<String, Object> trasladoOut(Map<String, Object> t) {
        var movs = new ArrayList<Map<String, Object>>();
        for (Object id : db.columna("SELECT id FROM almacen.movimiento WHERE traslado_id = :t ORDER BY id", P.de("t", t.get("id")))) movs.add(R.de(movimiento((Long) id), MOV_OUT));
        return R.mapa("id", t.get("id"), "uuid_cliente", t.get("uuid_cliente"), "movimientos", movs);
    }

    @PostMapping("/traslados")
    public ResponseEntity<Object> traslado(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("traslado.registrar", obraId);
        Datos d = TRASLADO.validar(cuerpo);
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var previo = db.fila("SELECT id, uuid_cliente, origen_proyecto_id FROM almacen.traslado WHERE uuid_cliente = :u", P.de("u", u));
            if (previo != null) {
                if (l(previo.get("origen_proyecto_id")) != obraId) throw new ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otro traslado.");
                return C.con(200, (Object) trasladoOut(previo));
            }
            long destinoObra = d.entero("destino_obra_id") != null ? d.entero("destino_obra_id") : obraId;
            if (destinoObra != obraId) asignadoEn(a, destinoObra);
            Long disp = dispositivo(a, d.texto("dispositivo"), "traslado.registrar", false);
            var origen = almacen(obraId, d.entero("origen_almacen_id"));
            var destino = almacen(destinoObra, d.entero("destino_almacen_id"));
            if (l(origen.get("id")) == l(destino.get("id"))) throw new ErrorSigro(422, "traslado_al_mismo_almacen", "El almacén de destino debe ser distinto del de origen.");
            List<Datos> lineas = d.lista("lineas");
            insumos(lineas.stream().map(x -> x.entero("insumo_id")).toList(), true);
            long perO = periodo(obraId, d.entero("hora_dispositivo_ms"), false, d.fecha("fecha"));
            long perD = destinoObra == obraId ? perO : periodo(destinoObra, d.entero("hora_dispositivo_ms"), false, d.fecha("fecha"));
            var t = db.fila("INSERT INTO almacen.traslado (uuid_cliente, origen_proyecto_id, origen_almacen_id, destino_proyecto_id, destino_almacen_id, motivo, registrado_por) "
                    + "VALUES (:u, :op, :oa, :dp, :da, :m, :r) RETURNING id, uuid_cliente", P.de("u", u, "op", obraId, "oa", origen.get("id"), "dp", destinoObra,
                    "da", destino.get("id"), "m", d.texto("motivo"), "r", a.usuarioId()));
            var nuevos = new ArrayList<Map<String, Object>>();
            for (Datos ln : lineas) {
                long iid = ln.entero("insumo_id");
                nuevos.add(insertar(mov("uuid_cliente", Reglas.uuidDerivado(u, "salida", iid), "proyecto_id", obraId, "almacen_id", origen.get("id"), "insumo_id", iid,
                        "tipo", "traslado_salida", "sentido", -1, "cantidad", ln.decimal("cantidad"), "periodo_id", perO, "traslado_id", t.get("id"), "motivo", d.texto("motivo"),
                        "dispositivo_id", disp, "registrado_por", a.usuarioId(), "fecha_dispositivo", fechaDispositivo(d.entero("hora_dispositivo_ms")))));
                nuevos.add(insertar(mov("uuid_cliente", Reglas.uuidDerivado(u, "ingreso", iid), "proyecto_id", destinoObra, "almacen_id", destino.get("id"), "insumo_id", iid,
                        "tipo", "traslado_ingreso", "sentido", 1, "cantidad", ln.decimal("cantidad"), "periodo_id", perD, "traslado_id", t.get("id"), "motivo", d.texto("motivo"),
                        "dispositivo_id", disp, "registrado_por", a.usuarioId(), "fecha_dispositivo", fechaDispositivo(d.entero("hora_dispositivo_ms")))));
            }
            publicar("almacen.traslado_registrado", obraId, nuevos);
            return C.creado((Object) trasladoOut(t));
        });
    }

    @PostMapping("/traslados/{traslado_id}/anular")
    public ResponseEntity<Object> anularTraslado(@PathVariable("obra_id") long obraId, @PathVariable("traslado_id") long trasladoId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("traslado.registrar", obraId);
        Datos d = ANULAR.validar(cuerpo);
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var t = db.fila("SELECT id, uuid_cliente, origen_proyecto_id, destino_proyecto_id FROM almacen.traslado WHERE id = :i AND origen_proyecto_id = :p", P.de("i", trasladoId, "p", obraId));
            if (t == null) throw new ErrorSigro(404, "no_existe", "Ese traslado no existe en esta obra.");
            var movs = db.filas("SELECT m.*, r.id AS revertido_por FROM almacen.movimiento m LEFT JOIN almacen.movimiento r ON r.revierte_a_id = m.id "
                    + "WHERE m.traslado_id = :t AND m.tipo IN ('traslado_salida', 'traslado_ingreso') ORDER BY (m.tipo = 'traslado_salida'), m.id", P.de("t", trasladoId));
            if (db.fila("SELECT id FROM almacen.movimiento WHERE uuid_cliente = :u", P.de("u", Reglas.uuidDerivado(u, "reverso", movs.get(0).get("id")))) != null) {
                var l2 = new ArrayList<Map<String, Object>>();
                for (Object id : db.columna("SELECT id FROM almacen.movimiento WHERE traslado_id = :t AND tipo = 'reverso' ORDER BY id", P.de("t", trasladoId)))
                    l2.add(R.de(movimiento((Long) id), MOV_OUT));
                return C.con(200, (Object) R.mapa("id", t.get("id"), "uuid_cliente", t.get("uuid_cliente"), "movimientos", l2));
            }
            if (movs.stream().anyMatch(m -> m.get("revertido_por") != null)) throw new ErrorSigro(409, "ya_revertido", "Ese traslado ya fue anulado.");
            if (l(t.get("destino_proyecto_id")) != obraId) asignadoEn(a, l(t.get("destino_proyecto_id")));
            Long disp = dispositivo(a, d.texto("dispositivo"), "traslado.registrar", false);
            var nuevos = new ArrayList<Map<String, Object>>();
            for (var m : movs)
                nuevos.add(insertar(mov("uuid_cliente", Reglas.uuidDerivado(u, "reverso", m.get("id")), "proyecto_id", m.get("proyecto_id"), "almacen_id", m.get("almacen_id"),
                        "insumo_id", m.get("insumo_id"), "tipo", "reverso", "sentido", -l(m.get("sentido")), "cantidad", m.get("cantidad"), "costo_unitario", m.get("costo_unitario"),
                        "motivo", d.texto("motivo"), "revierte_a_id", m.get("id"), "periodo_id", periodo(l(m.get("proyecto_id")), d.entero("hora_dispositivo_ms"), true, d.fecha("fecha")),
                        "dispositivo_id", disp, "registrado_por", a.usuarioId(), "fecha_dispositivo", fechaDispositivo(d.entero("hora_dispositivo_ms")))));
            publicar("almacen.traslado_anulado", obraId, nuevos);
            return C.creado((Object) R.mapa("id", t.get("id"), "uuid_cliente", t.get("uuid_cliente"), "movimientos", R.todos(nuevos, MOV_OUT)));
        });
    }

    // ------------------------------------------------------------------ HU-46 conteo físico
    Map<String, Object> conteo(long obraId, long conteoId) {
        var c = db.fila("SELECT id, uuid_cliente, almacen_id, fecha, estado, registrado_por, confirmado_por, confirmado_en FROM almacen.conteo WHERE id = :i AND proyecto_id = :p",
                P.de("i", conteoId, "p", obraId));
        if (c == null) throw new ErrorSigro(404, "no_existe", "Ese conteo no existe en esta obra.");
        var m = new LinkedHashMap<>(c);
        m.put("lineas", db.filas("SELECT insumo_id, saldo_teorico, cantidad_contada, diferencia, motivo FROM almacen.conteo_linea WHERE conteo_id = :i ORDER BY id", P.de("i", conteoId)));
        var aj = new ArrayList<Map<String, Object>>();
        for (Object id : db.columna("SELECT id FROM almacen.movimiento WHERE conteo_id = :i ORDER BY id", P.de("i", conteoId))) aj.add(R.de(movimiento((Long) id), MOV_OUT));
        m.put("ajustes", aj);
        return m;
    }

    @PostMapping("/conteos")
    public ResponseEntity<Object> abrirConteo(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("conteo.registrar", obraId);
        Datos d = CONTEO.validar(cuerpo);
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var previo = db.fila("SELECT id, proyecto_id FROM almacen.conteo WHERE uuid_cliente = :u", P.de("u", u));
            if (previo != null) {
                if (l(previo.get("proyecto_id")) != obraId) throw new ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otro conteo.");
                return C.con(200, (Object) conteo(obraId, (Long) previo.get("id")));
            }
            dispositivo(a, d.texto("dispositivo"), "conteo.registrar", false);
            var alm = almacen(obraId, d.entero("almacen_id"));
            Object abierto = db.escalar("SELECT id FROM almacen.conteo WHERE almacen_id = :a AND estado = 'borrador'", P.de("a", alm.get("id")));
            if (abierto != null) throw new ErrorSigro(409, "conteo_abierto", "Ese almacén ya tiene un conteo abierto (#" + abierto + "): confírmalo primero.");
            Object id = db.escalar("INSERT INTO almacen.conteo (uuid_cliente, proyecto_id, almacen_id, fecha, registrado_por) VALUES (:u, :p, :a, :f, :r) RETURNING id",
                    P.de("u", u, "p", obraId, "a", alm.get("id"), "f", d.fecha("fecha"), "r", a.usuarioId()));
            return C.creado((Object) conteo(obraId, (Long) id));
        });
    }

    @PostMapping("/conteos/{conteo_id}/lineas")
    public ResponseEntity<Object> contar(@PathVariable("obra_id") long obraId, @PathVariable("conteo_id") long conteoId, @RequestBody(required = false) JsonNode cuerpo) {
        acceso.exigir("conteo.registrar", obraId);
        Datos d = CONTEO_LINEA.validar(cuerpo);
        return C.creado(db.en(() -> {
            var c = conteo(obraId, conteoId);
            if (!"borrador".equals(c.get("estado"))) throw new ErrorSigro(409, "conteo_confirmado", "Ese conteo ya está confirmado; registra un conteo nuevo.");
            if (this.<Map<String, Object>>filasDe(c.get("lineas")).stream().anyMatch(x -> l(x.get("insumo_id")) == d.entero("insumo_id")))
                throw new ErrorSigro(409, "insumo_ya_contado", "Ese insumo ya se contó en este conteo.");
            insumos(List.of(d.entero("insumo_id")), true);
            String motivo = d.texto("motivo") == null || d.texto("motivo").isEmpty() ? null : d.texto("motivo");
            try {
                db.anidado(() -> db.actualizar("INSERT INTO almacen.conteo_linea (conteo_id, insumo_id, saldo_teorico, cantidad_contada, motivo) VALUES (:c, :i, 0, :q, :m)",
                        P.de("c", conteoId, "i", d.entero("insumo_id"), "q", d.decimal("cantidad_contada"), "m", motivo)));
            } catch (DataAccessException ex) {
                PSQLException pg = null;
                for (Throwable t = ex; t != null; t = t.getCause()) if (t instanceof PSQLException q) { pg = q; break; }
                if (pg != null && pg.getServerErrorMessage() != null && "ck_conteo_linea_motivo".equals(pg.getServerErrorMessage().getConstraint())) {
                    Object teorico = db.escalar("SELECT coalesce(sum(sentido * cantidad), 0) AS q FROM almacen.movimiento WHERE almacen_id = :a AND insumo_id = :i",
                            P.de("a", c.get("almacen_id"), "i", d.entero("insumo_id")));
                    throw new ErrorSigro(422, "diferencia_sin_motivo", "El sistema tiene " + teorico + " y se contó " + d.decimal("cantidad_contada") + ": toda diferencia exige un motivo.");
                }
                throw ex;
            }
            return (Object) conteo(obraId, conteoId);
        }));
    }

    @GetMapping("/conteos/{conteo_id}")
    public Object verConteo(@PathVariable("obra_id") long obraId, @PathVariable("conteo_id") long conteoId) {
        acceso.exigir("stock.consultar", obraId);
        return db.en(() -> conteo(obraId, conteoId));
    }

    @PostMapping("/conteos/{conteo_id}/confirmar")
    public Object confirmar(@PathVariable("obra_id") long obraId, @PathVariable("conteo_id") long conteoId) {
        Actor a = acceso.exigir("conteo.registrar", obraId);
        return db.en(() -> {
            var c = conteo(obraId, conteoId);
            if ("confirmado".equals(c.get("estado"))) return c;
            long per = periodo(obraId, null, false, (LocalDate) c.get("fecha"));
            var nuevos = new ArrayList<Map<String, Object>>();
            for (var ln : this.<Map<String, Object>>filasDe(c.get("lineas"))) {
                BigDecimal dif = (BigDecimal) ln.get("diferencia");
                if (dif.signum() == 0) continue;
                db.fila("SELECT pg_advisory_xact_lock(CAST(:a AS int), CAST(:i AS int))", P.de("a", c.get("almacen_id"), "i", ln.get("insumo_id")));
                BigDecimal actual = (BigDecimal) db.escalar("SELECT coalesce(sum(sentido * cantidad), 0) AS q FROM almacen.movimiento WHERE almacen_id = :a AND insumo_id = :i",
                        P.de("a", c.get("almacen_id"), "i", ln.get("insumo_id")));
                if (actual.compareTo((BigDecimal) ln.get("saldo_teorico")) != 0)
                    throw new ErrorSigro(409, "saldo_cambio", "El saldo del insumo " + ln.get("insumo_id") + " cambió desde el conteo (" + ln.get("saldo_teorico") + " → " + actual
                            + "): registra un conteo nuevo.");
                nuevos.add(insertar(mov("uuid_cliente", Reglas.uuidDerivado((UUID) c.get("uuid_cliente"), "ajuste", ln.get("insumo_id")), "proyecto_id", obraId,
                        "almacen_id", c.get("almacen_id"), "insumo_id", ln.get("insumo_id"), "tipo", "ajuste_conteo", "sentido", dif.signum() > 0 ? 1 : -1,
                        "cantidad", dif.abs(), "motivo", ln.get("motivo"), "conteo_id", conteoId, "periodo_id", per, "registrado_por", a.usuarioId())));
            }
            db.actualizar("UPDATE almacen.conteo SET estado = 'confirmado', confirmado_por = :u, confirmado_en = now() WHERE id = :i", P.de("u", a.usuarioId(), "i", conteoId));
            publicar("almacen.conteo_confirmado", obraId, nuevos);
            return conteo(obraId, conteoId);
        });
    }

    // ------------------------------------------------------------------ HU-43 stock
    @GetMapping("/stock")
    public Object stock(@PathVariable("obra_id") long obraId) {
        acceso.exigir("stock.consultar", obraId);
        Long almacenId = Peticion.entero("almacen_id", 1L, null);
        return db.en(() -> {
            var porAlmacen = db.filas("SELECT s.almacen_id, r.nombre AS almacen, s.insumo_id, i.codigo AS insumo_codigo, i.descripcion, i.unidad, "
                    + "s.cantidad, s.costo_promedio, coalesce(s.valor, 0) AS valor, s.ultimo_movimiento_en FROM almacen.saldo s JOIN almacen.insumo_ref i ON i.id = s.insumo_id "
                    + "JOIN almacen.almacen_ref r ON r.id = s.almacen_id WHERE s.proyecto_id = :p AND (CAST(:a AS bigint) IS NULL OR s.almacen_id = :a) ORDER BY r.nombre, i.codigo",
                    P.de("p", obraId, "a", almacenId));
            var consolidado = new ArrayList<Map<String, Object>>();
            for (var r : db.filas("SELECT NULL::bigint AS almacen_id, NULL::text AS almacen, s.insumo_id, i.codigo AS insumo_codigo, i.descripcion, "
                    + "i.unidad, s.cantidad, s.costo_promedio, coalesce(s.valor, 0) AS valor, s.ultimo_movimiento_en FROM almacen.saldo_obra s "
                    + "JOIN almacen.insumo_ref i ON i.id = s.insumo_id WHERE s.proyecto_id = :p ORDER BY i.codigo", P.de("p", obraId)))
                consolidado.add(new LinkedHashMap<>(r));
            var advertencias = new ArrayList<String>();
            if (!consolidado.isEmpty()) {
                try {
                    var parametros = new HashMap<Long, Map<String, Object>>();
                    for (var p : compras.parametrosReposicion(obraId)) parametros.put(l(p.get("insumo_id")), p);
                    var ins = insumos(consolidado.stream().map(r -> l(r.get("insumo_id"))).toList(), false);
                    for (var r : consolidado) {
                        var par = parametros.get(l(r.get("insumo_id")));
                        var in = ins.get(l(r.get("insumo_id")));
                        if (par == null || in == null) continue;
                        BigDecimal enCompra = ((BigDecimal) r.get("cantidad")).divide(Interno.dec(in.get("factor_conversion")), Dominio.PY);
                        BigDecimal punto = Interno.dec(par.get("punto_reposicion")), seg = Interno.dec(par.get("stock_seguridad"));
                        r.put("punto_reposicion", punto);
                        r.put("bajo_punto_reposicion", enCompra.compareTo(punto) < 0);
                        r.put("stock_seguridad", seg);
                        r.put("bajo_stock_seguridad", enCompra.compareTo(seg) < 0);
                        r.put("unidad_compra", in.get("unidad_compra"));
                        r.put("cantidad_en_unidad_compra", Reglas.q4par(enCompra));
                    }
                } catch (ErrorSigro e) {
                    advertencias.add("No se pudo consultar el punto de reposición (compras o catálogo no respondió).");
                }
            }
            Instant corte = consolidado.stream().map(r -> (Instant) r.get("ultimo_movimiento_en")).max(Comparator.naturalOrder()).orElse(null);
            return R.mapa("criterio", CRITERIO, "corte", corte, "por_almacen", R.todos(porAlmacen, STOCK_OUT), "consolidado", R.todos(consolidado, STOCK_OUT),
                    "advertencias", advertencias);
        });
    }

    // ------------------------------------------------------------------ HU-44 semana
    static final String SQL_SEMANA = """
            WITH m AS (
                SELECT m.almacen_id, m.insumo_id, m.periodo_id, m.sentido * m.cantidad AS efecto, coalesce(o.sentido, m.sentido) AS lado
                  FROM almacen.movimiento m LEFT JOIN almacen.movimiento o ON o.id = m.revierte_a_id
                 WHERE m.proyecto_id = :p AND (CAST(:a AS bigint) IS NULL OR m.almacen_id = :a)),
            sem AS (SELECT DISTINCT almacen_id, insumo_id FROM m WHERE periodo_id = :per)
            SELECT sem.almacen_id, r.nombre AS almacen, sem.insumo_id, i.codigo AS insumo_codigo, i.descripcion, i.unidad,
                   coalesce(sum(m.efecto) FILTER (WHERE m.periodo_id = ANY(CAST(:antes AS bigint[]))), 0) AS saldo_inicial,
                   coalesce(sum(m.efecto) FILTER (WHERE m.periodo_id = :per AND m.lado > 0), 0) AS entradas,
                   coalesce(-sum(m.efecto) FILTER (WHERE m.periodo_id = :per AND m.lado < 0), 0) AS salidas
              FROM sem JOIN m ON m.almacen_id = sem.almacen_id AND m.insumo_id = sem.insumo_id
              JOIN almacen.insumo_ref i ON i.id = sem.insumo_id
              JOIN almacen.almacen_ref r ON r.id = sem.almacen_id
             GROUP BY sem.almacen_id, r.nombre, sem.insumo_id, i.codigo, i.descripcion, i.unidad
             ORDER BY r.nombre, i.codigo""";

    @GetMapping("/semana")
    public ResponseEntity<Object> semana(@PathVariable("obra_id") long obraId) {
        acceso.exigir("stock.consultar", obraId);
        String num = Peticion.texto("numero");
        if (num == null) throw new ErrorSigro(422, "datos_invalidos", "numero: Field required");
        long numero = Peticion.enteroDe("numero", num, 1L, null);
        Long almacenId = Peticion.entero("almacen_id", 1L, null);
        String formato = Peticion.texto("formato");
        if (formato == null) formato = "json";
        if (!formato.matches("^(json|csv)$")) throw new ErrorSigro(422, "datos_invalidos", "formato: String should match pattern '^(json|csv)$'");
        var periodos = obras.periodos(obraId);
        var p = periodos.stream().filter(x -> l(x.get("numero")) == numero).findFirst()
                .orElseThrow(() -> new ErrorSigro(404, "semana_no_existe", "La obra no tiene la semana " + numero + "."));
        var antes = periodos.stream().filter(x -> String.valueOf(x.get("fecha_inicio")).compareTo(String.valueOf(p.get("fecha_inicio"))) < 0).map(x -> l(x.get("id"))).toList();
        var rows = db.en(() -> db.filas(SQL_SEMANA, P.de("p", obraId, "a", almacenId, "per", p.get("id"), "antes", P.enteros(antes))));
        var filas = new ArrayList<Map<String, Object>>();
        for (var r : rows) {
            var m = new LinkedHashMap<>(r);
            m.put("saldo_final", ((BigDecimal) r.get("saldo_inicial")).add((BigDecimal) r.get("entradas")).subtract((BigDecimal) r.get("salidas")));
            filas.add(R.de(m, "almacen_id", "almacen", "insumo_id", "insumo_codigo", "descripcion", "unidad", "saldo_inicial", "entradas", "salidas", "saldo_final"));
        }
        if (formato.equals("csv")) {
            var sb = new StringBuilder("﻿almacen;codigo;insumo;unidad;saldo_inicial;entradas;salidas;saldo_final\n");
            var lineas = new ArrayList<String>();
            for (var r : filas) {
                var cel = new ArrayList<String>();
                for (String k : List.of("almacen", "insumo_codigo", "descripcion", "unidad", "saldo_inicial", "entradas", "salidas", "saldo_final"))
                    cel.add(String.valueOf(r.get(k) == null ? "None" : r.get(k)).replace(";", ","));
                lineas.add(String.join(";", cel));
            }
            sb.append(String.join("\n", lineas)).append("\n");
            return ResponseEntity.ok().header("Content-Type", "text/csv; charset=utf-8")
                    .header("Content-Disposition", "attachment; filename=\"semana-" + numero + ".csv\"").body((Object) sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return ResponseEntity.ok(R.mapa("periodo_id", p.get("id"), "numero", p.get("numero"), "fecha_inicio", p.get("fecha_inicio"), "fecha_fin", p.get("fecha_fin"),
                "estado", p.get("estado"), "criterio", CRITERIO_SEMANA, "filas", filas));
    }

    // ------------------------------------------------------------------ kárdex
    @GetMapping("/kardex")
    public Object kardex(@PathVariable("obra_id") long obraId) {
        acceso.exigir("stock.consultar", obraId);
        Long insumoId = Peticion.entero("insumo_id", 1L, null);
        Long almacenId = Peticion.entero("almacen_id", 1L, null);
        var movs = db.en(() -> db.filas("SELECT " + COLS.replace("m.", "k.") + ", r.motivo_exceso AS recepcion_motivo_exceso, k.revertido_por_id, k.saldo_despues, k.promedio_despues "
                + "FROM almacen.kardex k JOIN almacen.insumo_ref i ON i.id = k.insumo_id LEFT JOIN almacen.recepcion r ON r.id = k.recepcion_id "
                + "WHERE k.proyecto_id = :p AND (CAST(:i AS bigint) IS NULL OR k.insumo_id = :i) AND (CAST(:a AS bigint) IS NULL OR k.almacen_id = :a) ORDER BY k.id LIMIT 2000",
                P.de("p", obraId, "i", insumoId, "a", almacenId)));
        var out = new ArrayList<Map<String, Object>>();
        if (!movs.isEmpty()) {
            var periodos = obras.periodos(obraId);
            var numPorId = new HashMap<Long, Object>();
            for (var p : periodos) numPorId.put(l(p.get("id")), p.get("numero"));
            for (var r : movs) {
                var m = new LinkedHashMap<>(r);
                Object numero = r.get("periodo_id") != null ? numPorId.get(l(r.get("periodo_id"))) : null;
                if (numero == null && r.get("fecha_servidor") != null) {
                    var per = Reglas.periodoDe(periodos, ((Instant) r.get("fecha_servidor")).atOffset(Reglas.LIMA).toLocalDate());
                    numero = per != null ? per.get("numero") : null;
                }
                m.put("semana_numero", numero);
                out.add(R.de(m, KARDEX_OUT));
            }
        }
        return out;
    }
}
