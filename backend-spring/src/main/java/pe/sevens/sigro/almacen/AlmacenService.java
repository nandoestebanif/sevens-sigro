package pe.sevens.sigro.almacen;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import pe.sevens.sigro.almacen.datos.ConsultaRepository;
import pe.sevens.sigro.almacen.datos.DocumentoRepository;
import pe.sevens.sigro.almacen.datos.MovimientoDuplicado;
import pe.sevens.sigro.almacen.datos.MovimientoRepository;
import pe.sevens.sigro.catalogo.Catalogo;
import pe.sevens.sigro.compras.Compras;
import pe.sevens.sigro.compras.Dominio;
import pe.sevens.sigro.comun.*;
import pe.sevens.sigro.obra.Obras;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.seguridad.Acceso.Actor;
import pe.sevens.sigro.seguridad.Permisos;

/**
 * Casos de uso de almacén: salidas, ingresos, recepciones, traslados, reversos, conteos, stock, semana y kárdex. Cada caso
 * corre en UNA transacción del módulo (db.en) y deja el SQL a los repositorios.
 * R1 el movimiento es un libro · R2 el saldo lo calcula el servidor · R3 reenvío = 200 con el original ·
 * R4 la salida congela el promedio vigente · R5 la salida exige el celular autorizado · R9 rol y obra en cada petición.
 */
@Service
public class AlmacenService {
    static final String CRITERIO = "Costo promedio ponderado por almacén (NIC 2): cada ingreso recalcula el promedio del insumo en ese almacén; "
            + "cada salida congela el promedio vigente en el instante en que se registró. Importes sin IGV.";
    static final String CRITERIO_SEMANA = "Semana = periodo de la obra (HU-03). Saldo inicial: todo lo registrado en periodos anteriores. Entradas y "
            + "salidas: lo registrado en la semana; un reverso resta en la columna del movimiento que anula. Unidad del S10. "
            + "Solo insumos con movimiento en la semana.";
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
    static final String[] SEMANA_OUT = {"almacen_id", "almacen", "insumo_id", "insumo_codigo", "descripcion", "unidad", "saldo_inicial", "entradas", "salidas", "saldo_final"};

    /** Resultado de un registro: nuevo (201) o el original de un reenvío (200). */
    public record Resultado(boolean creado, Object cuerpo) {
        static Resultado nuevo(Object cuerpo) { return new Resultado(true, cuerpo); }
        static Resultado existente(Object cuerpo) { return new Resultado(false, cuerpo); }
    }

    /** Semana lista para mostrar o exportar. */
    public record Semana(long numero, Map<String, Object> periodo, List<Map<String, Object>> filas) {}

    private final Modulo db;
    private final MovimientoRepository movimientos;
    private final DocumentoRepository documentos;
    private final ConsultaRepository consultas;
    private final Acceso acceso;
    private final Obras obras;
    private final Catalogo catalogo;
    private final Compras compras;

    public AlmacenService(Modulo almacen, MovimientoRepository movimientos, DocumentoRepository documentos, ConsultaRepository consultas,
                          Acceso acceso, Obras obras, Catalogo catalogo, Compras compras) {
        this.db = almacen;
        this.movimientos = movimientos;
        this.documentos = documentos;
        this.consultas = consultas;
        this.acceso = acceso;
        this.obras = obras;
        this.catalogo = catalogo;
        this.compras = compras;
    }

    static long l(Object o) { return ((Number) o).longValue(); }

    static Timestamp fechaDispositivo(Long ms) { return ms == null ? null : Timestamp.from(Instant.ofEpochMilli(ms)); }

    static String vacioANulo(String s) { return s == null || s.isEmpty() ? null : s; }

    static Map<String, Object> mov(Object... pares) { return new LinkedHashMap<>(P.mapa(pares)); }

    @SuppressWarnings("unchecked")
    static <T> List<T> filasDe(Object o) { return (List<T>) o; }

    static ErrorSigro sinPermiso() { return new ErrorSigro(403, "sin_permiso", "No tienes permiso para esta operación en esta obra."); }

    // ------------------------------------------------------------------ lo que se pregunta a otros módulos
    /** R5: id del celular autorizado (o null si no vino y no es obligatorio). */
    Long dispositivo(Actor a, String identificador, String accion, boolean obligatorio) {
        if (identificador == null) {
            if (obligatorio) throw new ErrorSigro(403, "dispositivo_no_autorizado", "Este registro solo se acepta desde un celular autorizado.");
            return null;
        }
        String rol = acceso.rolEnObra(a.usuarioId(), a.obraId());
        if (rol == null || !Permisos.roles(accion).contains(rol)) throw sinPermiso();
        return obras.dispositivoActivo(a.usuarioId(), a.obraId(), identificador);
    }

    void asignadoEn(Actor a, long obraId) {
        if (acceso.rolEnObra(a.usuarioId(), obraId) == null) throw sinPermiso();
    }

    /** El almacén activo indicado, o el único activo de la obra. Refresca la copia local de almacenes. */
    Map<String, Object> almacen(long obraId, Long almacenId) {
        var lista = obras.almacenes(obraId);
        lista.forEach(movimientos::sincronizarAlmacen);
        var activos = lista.stream().filter(x -> "activo".equals(x.get("estado"))).toList();
        if (almacenId == null) {
            if (activos.size() == 1) return activos.get(0);
            throw new ErrorSigro(422, "almacen_requerido", !activos.isEmpty() ? "Indica el almacén (almacen_id): la obra no tiene uno solo activo."
                    : "La obra no tiene almacenes activos; regístralo primero.");
        }
        return activos.stream().filter(x -> l(x.get("id")) == almacenId).findFirst()
                .orElseThrow(() -> new ErrorSigro(404, "almacen_no_existe", "Ese almacén no existe o no está activo en esa obra."));
    }

    /** Insumos del catálogo por id. Para registrar: deben existir, estar activos y ser almacenables (y se copian localmente). */
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
            for (Long i : ids) movimientos.sincronizarInsumo(salida.get(i));
        }
        return salida;
    }

    long periodo(long obraId, Long horaMs, boolean esReverso, LocalDate dia) {
        var periodos = obras.periodos(obraId);
        LocalDate f = dia != null ? dia : Reglas.fechaDelHecho(horaMs, Instant.now());
        return l(Reglas.resolverPeriodo(periodos, f, esReverso).get("id"));
    }

    // ------------------------------------------------------------------ el libro
    Map<String, Object> vista(long movimientoId) { return R.de(movimientos.porId(movimientoId), MOV_OUT); }

    List<Map<String, Object>> vistas(List<Long> ids) { return ids.stream().map(this::vista).toList(); }

    /** R3: el movimiento ya registrado con ese uuid (null si es nuevo); 409 si el uuid se usó en otra obra u otro tipo. */
    Map<String, Object> porUuid(long obraId, UUID uuid, Set<String> tipos) {
        var m = movimientos.identidadPorUuid(uuid);
        if (m == null) return null;
        if (l(m.get("proyecto_id")) != obraId || !tipos.contains(m.get("tipo"))) throw new ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otro movimiento.");
        return movimientos.porId(l(m.get("id")));
    }

    Map<String, Object> insertar(Map<String, Object> m) {
        m.putIfAbsent("costo_unitario", BigDecimal.ZERO);
        return movimientos.porId(movimientos.insertar(m));
    }

    /** Hechos ya valorizados hacia costo, en la MISMA transacción (outbox, destino 'integracion'). */
    void publicar(String tipo, long obraId, List<Map<String, Object>> nuevos) {
        if (nuevos.isEmpty()) return;
        var hechos = new ArrayList<Map<String, Object>>();
        for (var r : movimientos.paraHechos(nuevos.stream().map(m -> l(m.get("id"))).toList()))
            hechos.add(R.mapa("proyecto_id", r.get("proyecto_id"), "familia", Reglas.FAMILIAS.get(r.get("familia")), "clase", Reglas.CLASE.get(r.get("tipo_efectivo")),
                    "insumo_codigo", r.get("codigo"), "cantidad", BigDecimal.valueOf(l(r.get("sentido"))).multiply((BigDecimal) r.get("cantidad")).toString(),
                    "unidad", r.get("unidad"), "costo_unitario", r.get("costo_unitario").toString(), "moneda", r.get("moneda"), "fecha", r.get("fecha").toString(),
                    "referencia", "almacen.movimiento:" + r.get("id")));
        movimientos.encolarEvento(tipo, obraId, Json.texto(R.mapa("hechos", hechos)));
    }

    Resultado registrarUno(long obraId, String tipoEvento, Map<String, Object> m) {
        try {
            var nuevo = insertar(m);
            publicar(tipoEvento, obraId, List.of(nuevo));
            return Resultado.nuevo(R.de(nuevo, MOV_OUT));
        } catch (MovimientoDuplicado d) {
            return Resultado.existente(R.de(porUuid(obraId, (UUID) m.get("uuid_cliente"), Set.of((String) m.get("tipo"))), MOV_OUT));
        }
    }

    // ------------------------------------------------------------------ HU-40 salida
    public Resultado salida(Actor a, long obraId, Datos d) {
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var previo = porUuid(obraId, u, Set.of("salida"));
            if (previo != null) return Resultado.existente(R.de(previo, MOV_OUT));
            Long disp = dispositivo(a, d.texto("dispositivo"), "salida.registrar", true);
            var alm = almacen(obraId, d.entero("almacen_id"));
            var ins = insumos(List.of(d.entero("insumo_id")), true).get(d.entero("insumo_id"));
            BigDecimal cant = Reglas.aUnidadS10(d.decimal("cantidad"), d.texto("unidad"), ins);
            return registrarUno(obraId, "almacen.salida_registrada", mov("uuid_cliente", u, "proyecto_id", obraId, "almacen_id", alm.get("id"),
                    "insumo_id", d.entero("insumo_id"), "tipo", "salida", "sentido", -1, "cantidad", cant, "motivo", d.texto("motivo"),
                    "quien_retira", d.texto("quien_retira"), "frente", vacioANulo(d.texto("frente")),
                    "periodo_id", periodo(obraId, d.entero("hora_dispositivo_ms"), false, d.fecha("fecha")),
                    "dispositivo_id", disp, "registrado_por", a.usuarioId(), "fecha_dispositivo", fechaDispositivo(d.entero("hora_dispositivo_ms"))));
        });
    }

    // ------------------------------------------------------------------ HU-41 ingreso sin compra
    public Resultado ingreso(Actor a, long obraId, Datos d) {
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var previo = porUuid(obraId, u, Set.of("ingreso_sin_compra"));
            if (previo != null) return Resultado.existente(R.de(previo, MOV_OUT));
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
    public Resultado reverso(Actor a, long obraId, long movId, Datos d) {
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var previo = porUuid(obraId, u, Set.of("reverso"));
            if (previo != null) return Resultado.existente(R.de(previo, MOV_OUT));
            var o = movimientos.deLaObra(obraId, movId);
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
    /** Por cada línea de la compra: pedido, recibido y pendiente, en la unidad de compra. */
    List<Map<String, Object>> estadoRecepcion(Map<String, Object> compra, Map<Long, Map<String, Object>> insumos) {
        var recibido = documentos.recibidoDeCompra(l(compra.get("id")));
        var salida = new ArrayList<Map<String, Object>>();
        for (var lin : AlmacenService.<Map<String, Object>>filasDe(compra.get("lineas"))) {
            long iid = l(lin.get("insumo_id"));
            BigDecimal factor = Interno.dec(insumos.get(iid).get("factor_conversion"));
            BigDecimal pedido = Reglas.q4par(Interno.dec(lin.get("cantidad")));
            BigDecimal rec = Reglas.q4par(recibido.getOrDefault(iid, BigDecimal.ZERO).divide(factor, Dominio.PY));
            BigDecimal pend = pedido.subtract(rec);
            salida.add(R.mapa("insumo_id", iid, "unidad_compra", lin.get("unidad"), "pedido", pedido, "recibido", rec, "pendiente", pend.signum() >= 0 ? pend : BigDecimal.ZERO));
        }
        return salida;
    }

    Map<String, Object> compraDeLaObra(long obraId, long compraId) {
        var c = compras.compra(compraId);
        if (l(c.get("proyecto_id")) != obraId) throw new ErrorSigro(404, "compra_no_existe", "Esa compra no existe en esta obra.");
        return c;
    }

    static Set<Long> insumosDe(Map<String, Object> compra) {
        var s = new LinkedHashSet<Long>();
        for (var lin : AlmacenService.<Map<String, Object>>filasDe(compra.get("lineas"))) s.add(l(lin.get("insumo_id")));
        return s;
    }

    Map<Long, Map<String, Object>> estadoPorInsumo(Map<String, Object> compra, Map<Long, Map<String, Object>> insumos) {
        var estado = new LinkedHashMap<Long, Map<String, Object>>();
        for (var x : estadoRecepcion(compra, insumos)) estado.put((Long) x.get("insumo_id"), x);
        return estado;
    }

    Map<String, Object> recepcionOut(Map<String, Object> rec, Map<String, Object> compra, Map<Long, Map<String, Object>> insumos, List<String> advertencias) {
        return R.mapa("id", rec.get("id"), "uuid_cliente", rec.get("uuid_cliente"), "movimientos", vistas(movimientos.idsDeRecepcion(l(rec.get("id")))),
                "compra_id", l(compra.get("id")), "lineas", estadoRecepcion(compra, insumos), "advertencias", advertencias);
    }

    /** Lo que se va a recibir: todo lo pendiente o las líneas indicadas, sin pasar de lo pendiente. */
    static LinkedHashMap<Long, BigDecimal> pedido(Map<Long, Map<String, Object>> estado, Map<Long, Map<String, Object>> insumos, List<Datos> lineas) {
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

    /** Compara lo que se recibiría con lo requerido por el S10 (exceso por insumo). */
    List<Map<String, Object>> verificar(long obraId, Map<Long, Map<String, Object>> insumos, Map<Long, BigDecimal> pedir) {
        var requerido = new HashMap<Long, BigDecimal>();
        for (var r : compras.requerido(obraId)) requerido.put(l(r.get("insumo_id")), Interno.dec(r.get("requerido")));
        var ya = movimientos.recibidoPorCompra(obraId, pedir.keySet());
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

    public Map<String, Object> verificacion(long obraId, Datos d) {
        return db.en(() -> {
            var compra = compraDeLaObra(obraId, d.entero("compra_id"));
            var insumos = insumos(insumosDe(compra), false);
            var lineas = verificar(obraId, insumos, pedido(estadoPorInsumo(compra, insumos), insumos, d.lista("lineas")));
            return R.mapa("compra_id", l(compra.get("id")), "hay_exceso", lineas.stream().anyMatch(x -> (Boolean) x.get("excede")), "lineas", lineas);
        });
    }

    public Resultado recepcion(Actor a, long obraId, Datos d) {
        UUID u = (UUID) d.get("uuid_cliente");
        long compraId = d.entero("compra_id");
        return db.en(() -> {
            var previa = documentos.recepcionPorUuid(u);
            var compra = compraDeLaObra(obraId, compraId);
            var insumos = insumos(insumosDe(compra), previa == null);
            if (previa != null) {
                if (l(previa.get("proyecto_id")) != obraId || l(previa.get("compra_id")) != compraId)
                    throw new ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otra recepción.");
                return Resultado.existente(recepcionOut(previa, compra, insumos, List.of()));
            }
            if (!"registrada".equals(compra.get("estado"))) throw new ErrorSigro(409, "compra_anulada", "Esa compra está anulada: no se puede recibir.");
            Long disp = dispositivo(a, d.texto("dispositivo"), "recepcion.registrar", false);
            var alm = almacen(obraId, d.entero("almacen_id"));
            documentos.bloquearCompra(compraId);
            var pedir = pedido(estadoPorInsumo(compra, insumos), insumos, d.lista("lineas"));
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
            var rec = documentos.insertarRecepcion(u, obraId, alm.get("id"), compraId, d.texto("motivo_exceso"), a.usuarioId(), disp,
                    fechaDispositivo(d.entero("hora_dispositivo_ms")));
            var lineas = new HashMap<Long, Map<String, Object>>();
            for (var lin : AlmacenService.<Map<String, Object>>filasDe(compra.get("lineas"))) lineas.put(l(lin.get("insumo_id")), lin);
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
            return Resultado.nuevo(recepcionOut(rec, compra, insumos, advertencias));
        });
    }

    public List<Map<String, Object>> estadoRecepcion(long obraId, long compraId) {
        return db.en(() -> {
            var compra = compraDeLaObra(obraId, compraId);
            return estadoRecepcion(compra, insumos(insumosDe(compra), false));
        });
    }

    // ------------------------------------------------------------------ HU-42 traslado
    Map<String, Object> trasladoOut(Map<String, Object> t) {
        return R.mapa("id", t.get("id"), "uuid_cliente", t.get("uuid_cliente"), "movimientos", vistas(movimientos.idsDeTraslado(l(t.get("id")))));
    }

    public Resultado traslado(Actor a, long obraId, Datos d) {
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var previo = documentos.trasladoPorUuid(u);
            if (previo != null) {
                if (l(previo.get("origen_proyecto_id")) != obraId) throw new ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otro traslado.");
                return Resultado.existente(trasladoOut(previo));
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
            var t = documentos.insertarTraslado(u, obraId, origen.get("id"), destinoObra, destino.get("id"), d.texto("motivo"), a.usuarioId());
            var hora = fechaDispositivo(d.entero("hora_dispositivo_ms"));
            var nuevos = new ArrayList<Map<String, Object>>();
            for (Datos ln : lineas) {
                long iid = ln.entero("insumo_id");
                nuevos.add(insertar(mov("uuid_cliente", Reglas.uuidDerivado(u, "salida", iid), "proyecto_id", obraId, "almacen_id", origen.get("id"), "insumo_id", iid,
                        "tipo", "traslado_salida", "sentido", -1, "cantidad", ln.decimal("cantidad"), "periodo_id", perO, "traslado_id", t.get("id"), "motivo", d.texto("motivo"),
                        "dispositivo_id", disp, "registrado_por", a.usuarioId(), "fecha_dispositivo", hora)));
                nuevos.add(insertar(mov("uuid_cliente", Reglas.uuidDerivado(u, "ingreso", iid), "proyecto_id", destinoObra, "almacen_id", destino.get("id"), "insumo_id", iid,
                        "tipo", "traslado_ingreso", "sentido", 1, "cantidad", ln.decimal("cantidad"), "periodo_id", perD, "traslado_id", t.get("id"), "motivo", d.texto("motivo"),
                        "dispositivo_id", disp, "registrado_por", a.usuarioId(), "fecha_dispositivo", hora)));
            }
            publicar("almacen.traslado_registrado", obraId, nuevos);
            return Resultado.nuevo(trasladoOut(t));
        });
    }

    public Resultado anularTraslado(Actor a, long obraId, long trasladoId, Datos d) {
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var t = documentos.trasladoDeLaObra(obraId, trasladoId);
            if (t == null) throw new ErrorSigro(404, "no_existe", "Ese traslado no existe en esta obra.");
            var mitades = movimientos.mitadesDeTraslado(trasladoId);
            if (movimientos.identidadPorUuid(Reglas.uuidDerivado(u, "reverso", mitades.get(0).get("id"))) != null)
                return Resultado.existente(R.mapa("id", t.get("id"), "uuid_cliente", t.get("uuid_cliente"),
                        "movimientos", vistas(movimientos.idsDeReversosDeTraslado(trasladoId))));
            if (mitades.stream().anyMatch(m -> m.get("revertido_por") != null)) throw new ErrorSigro(409, "ya_revertido", "Ese traslado ya fue anulado.");
            if (l(t.get("destino_proyecto_id")) != obraId) asignadoEn(a, l(t.get("destino_proyecto_id")));
            Long disp = dispositivo(a, d.texto("dispositivo"), "traslado.registrar", false);
            var nuevos = new ArrayList<Map<String, Object>>();
            for (var m : mitades)
                nuevos.add(insertar(mov("uuid_cliente", Reglas.uuidDerivado(u, "reverso", m.get("id")), "proyecto_id", m.get("proyecto_id"), "almacen_id", m.get("almacen_id"),
                        "insumo_id", m.get("insumo_id"), "tipo", "reverso", "sentido", -l(m.get("sentido")), "cantidad", m.get("cantidad"), "costo_unitario", m.get("costo_unitario"),
                        "motivo", d.texto("motivo"), "revierte_a_id", m.get("id"), "periodo_id", periodo(l(m.get("proyecto_id")), d.entero("hora_dispositivo_ms"), true, d.fecha("fecha")),
                        "dispositivo_id", disp, "registrado_por", a.usuarioId(), "fecha_dispositivo", fechaDispositivo(d.entero("hora_dispositivo_ms")))));
            publicar("almacen.traslado_anulado", obraId, nuevos);
            return Resultado.nuevo(R.mapa("id", t.get("id"), "uuid_cliente", t.get("uuid_cliente"), "movimientos", R.todos(nuevos, MOV_OUT)));
        });
    }

    // ------------------------------------------------------------------ HU-46 conteo físico
    Map<String, Object> conteo(long obraId, long conteoId) {
        var c = documentos.conteoDeLaObra(obraId, conteoId);
        if (c == null) throw new ErrorSigro(404, "no_existe", "Ese conteo no existe en esta obra.");
        var m = new LinkedHashMap<>(c);
        m.put("lineas", documentos.lineasDeConteo(conteoId));
        m.put("ajustes", vistas(movimientos.idsDeConteo(conteoId)));
        return m;
    }

    public Resultado abrirConteo(Actor a, long obraId, Datos d) {
        UUID u = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            var previo = documentos.conteoPorUuid(u);
            if (previo != null) {
                if (l(previo.get("proyecto_id")) != obraId) throw new ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otro conteo.");
                return Resultado.existente(conteo(obraId, l(previo.get("id"))));
            }
            dispositivo(a, d.texto("dispositivo"), "conteo.registrar", false);
            var alm = almacen(obraId, d.entero("almacen_id"));
            Object abierto = documentos.conteoAbierto(alm.get("id"));
            if (abierto != null) throw new ErrorSigro(409, "conteo_abierto", "Ese almacén ya tiene un conteo abierto (#" + abierto + "): confírmalo primero.");
            long id = documentos.insertarConteo(u, obraId, alm.get("id"), d.fecha("fecha"), a.usuarioId());
            return Resultado.nuevo(conteo(obraId, id));
        });
    }

    public Map<String, Object> contar(long obraId, long conteoId, Datos d) {
        return db.en(() -> {
            var c = conteo(obraId, conteoId);
            if (!"borrador".equals(c.get("estado"))) throw new ErrorSigro(409, "conteo_confirmado", "Ese conteo ya está confirmado; registra un conteo nuevo.");
            long insumoId = d.entero("insumo_id");
            if (AlmacenService.<Map<String, Object>>filasDe(c.get("lineas")).stream().anyMatch(x -> l(x.get("insumo_id")) == insumoId))
                throw new ErrorSigro(409, "insumo_ya_contado", "Ese insumo ya se contó en este conteo.");
            var ins = insumos(List.of(insumoId), true).get(insumoId);
            BigDecimal contada = Reglas.aUnidadS10(d.decimal("cantidad_contada"), d.texto("unidad"), ins);
            if (!documentos.insertarLineaConteo(conteoId, insumoId, contada, vacioANulo(d.texto("motivo")))) {
                BigDecimal teorico = movimientos.saldo(l(c.get("almacen_id")), insumoId);
                throw new ErrorSigro(422, "diferencia_sin_motivo", "El sistema tiene " + teorico + " y se contó " + contada + ": toda diferencia exige un motivo.");
            }
            return conteo(obraId, conteoId);
        });
    }

    public Map<String, Object> verConteo(long obraId, long conteoId) {
        return db.en(() -> conteo(obraId, conteoId));
    }

    /** Confirma el conteo: un ajuste por cada diferencia, si el saldo no cambió desde que se contó. */
    public Map<String, Object> confirmar(Actor a, long obraId, long conteoId) {
        return db.en(() -> {
            var c = conteo(obraId, conteoId);
            if ("confirmado".equals(c.get("estado"))) return c;
            long per = periodo(obraId, null, false, (LocalDate) c.get("fecha"));
            long almacenId = l(c.get("almacen_id"));
            var nuevos = new ArrayList<Map<String, Object>>();
            for (var ln : AlmacenService.<Map<String, Object>>filasDe(c.get("lineas"))) {
                BigDecimal dif = (BigDecimal) ln.get("diferencia");
                if (dif.signum() == 0) continue;
                long insumoId = l(ln.get("insumo_id"));
                movimientos.bloquearSaldo(almacenId, insumoId);
                BigDecimal actual = movimientos.saldo(almacenId, insumoId);
                if (actual.compareTo((BigDecimal) ln.get("saldo_teorico")) != 0)
                    throw new ErrorSigro(409, "saldo_cambio", "El saldo del insumo " + ln.get("insumo_id") + " cambió desde el conteo (" + ln.get("saldo_teorico") + " → " + actual
                            + "): registra un conteo nuevo.");
                nuevos.add(insertar(mov("uuid_cliente", Reglas.uuidDerivado((UUID) c.get("uuid_cliente"), "ajuste", ln.get("insumo_id")), "proyecto_id", obraId,
                        "almacen_id", c.get("almacen_id"), "insumo_id", ln.get("insumo_id"), "tipo", "ajuste_conteo", "sentido", dif.signum() > 0 ? 1 : -1,
                        "cantidad", dif.abs(), "motivo", ln.get("motivo"), "conteo_id", conteoId, "periodo_id", per, "registrado_por", a.usuarioId())));
            }
            documentos.confirmarConteo(conteoId, a.usuarioId());
            publicar("almacen.conteo_confirmado", obraId, nuevos);
            return conteo(obraId, conteoId);
        });
    }

    // ------------------------------------------------------------------ HU-43 stock
    public Map<String, Object> stock(long obraId, Long almacenId) {
        return db.en(() -> {
            var porAlmacen = consultas.stockPorAlmacen(obraId, almacenId);
            var consolidado = new ArrayList<Map<String, Object>>();
            for (var r : consultas.stockConsolidado(obraId)) consolidado.add(new LinkedHashMap<>(r));
            var advertencias = new ArrayList<String>();
            if (!consolidado.isEmpty()) {
                try {
                    marcarReposicion(obraId, consolidado);
                } catch (ErrorSigro e) {
                    advertencias.add("No se pudo consultar el punto de reposición (compras o catálogo no respondió).");
                }
            }
            Instant corte = consolidado.stream().map(r -> (Instant) r.get("ultimo_movimiento_en")).max(Comparator.naturalOrder()).orElse(null);
            return R.mapa("criterio", CRITERIO, "corte", corte, "por_almacen", R.todos(porAlmacen, STOCK_OUT), "consolidado", R.todos(consolidado, STOCK_OUT),
                    "advertencias", advertencias);
        });
    }

    /** Marca cada fila bajo el punto de reposición o el stock de seguridad (ambos en unidad de compra). */
    void marcarReposicion(long obraId, List<Map<String, Object>> consolidado) {
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
    }

    // ------------------------------------------------------------------ HU-44 semana
    public Semana semana(long obraId, long numero, Long almacenId) {
        var periodos = obras.periodos(obraId);
        var p = periodos.stream().filter(x -> l(x.get("numero")) == numero).findFirst()
                .orElseThrow(() -> new ErrorSigro(404, "semana_no_existe", "La obra no tiene la semana " + numero + "."));
        var antes = periodos.stream().filter(x -> String.valueOf(x.get("fecha_inicio")).compareTo(String.valueOf(p.get("fecha_inicio"))) < 0).map(x -> l(x.get("id"))).toList();
        var rows = db.en(() -> consultas.semana(obraId, almacenId, p.get("id"), antes));
        var filas = new ArrayList<Map<String, Object>>();
        for (var r : rows) {
            var m = new LinkedHashMap<>(r);
            m.put("saldo_final", ((BigDecimal) r.get("saldo_inicial")).add((BigDecimal) r.get("entradas")).subtract((BigDecimal) r.get("salidas")));
            filas.add(R.de(m, SEMANA_OUT));
        }
        return new Semana(numero, p, filas);
    }

    public Map<String, Object> semanaJson(Semana s) {
        var p = s.periodo();
        return R.mapa("periodo_id", p.get("id"), "numero", p.get("numero"), "fecha_inicio", p.get("fecha_inicio"), "fecha_fin", p.get("fecha_fin"),
                "estado", p.get("estado"), "criterio", CRITERIO_SEMANA, "filas", s.filas());
    }

    // ------------------------------------------------------------------ kárdex
    public List<Map<String, Object>> kardex(long obraId, Long insumoId, Long almacenId) {
        var movs = db.en(() -> consultas.kardex(obraId, insumoId, almacenId));
        var out = new ArrayList<Map<String, Object>>();
        if (movs.isEmpty()) return out;
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
        return out;
    }
}
