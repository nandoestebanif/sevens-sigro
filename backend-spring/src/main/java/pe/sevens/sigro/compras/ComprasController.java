package pe.sevens.sigro.compras;

import static pe.sevens.sigro.comun.Esquema.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.sevens.sigro.almacen.Almacen;
import pe.sevens.sigro.comun.*;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.seguridad.Acceso.Actor;

/**
 * Compras (port de app/modulos/compras/rutas.py): proveedores (HU-29/30/31), compras con su comprobante (HU-32, HU-33)
 * y solicitudes de materiales (CR-04). Registrar una compra NO mueve stock: la recepción es de almacén.
 */
@RestController
@RequestMapping("/api/v1")
public class ComprasController {
    static final BigDecimal IGV = new BigDecimal("0.18");
    static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static final Esquema PROVEEDOR = de(texto("ruc").patron("^(\\d{11}|\\d{8})$"), texto("razon_social").min(3).max(200));
    static final Esquema LINEA = de(texto("insumo_codigo").min(1).max(30), decimal("cantidad").gt(0).digitos(18, 4),
            texto("unidad_compra").min(1).max(30), decimal("precio_unitario").gt(0).digitos(18, 4), texto("nota_precio").opcional().min(1).max(300));
    static final Esquema COMPRA = de(entero("proveedor_id").ge(1), texto("tipo_comprobante").uno("factura", "boleta", "nota_venta", "otro"),
            texto("numero_comprobante").patron("^[A-Za-z0-9]{1,4}-[0-9]{1,10}$"), fecha("fecha"), texto("moneda").defecto("PEN").uno("PEN", "USD"),
            booleano("precios_incluyen_igv").defecto(true), entero("solicitud_id").opcional().ge(1), lista("lineas", LINEA).min(1).max(200))
            .despues(d -> {
                LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
                if (d.fecha("fecha").isAfter(hoy))
                    throw errorEn("fecha", "la fecha del comprobante (" + d.fecha("fecha").format(DMY) + ") no puede ser posterior a hoy (" + hoy.format(DMY) + ")");
                var codigos = new ArrayList<String>();
                for (Datos l : d.<Datos>lista("lineas")) codigos.add(l.texto("insumo_codigo").toUpperCase(Locale.ROOT));
                var rep = new TreeSet<String>();
                for (String c : codigos) if (Collections.frequency(codigos, c) > 1) rep.add(c);
                if (!rep.isEmpty()) throw error("insumo repetido en la compra: " + String.join(", ", rep) + "; suma las cantidades en una sola línea");
            });
    static final Esquema ANULAR = de(texto("motivo").min(5).max(300));
    static final Esquema LINEA_SOL = de(texto("insumo_codigo").opcional().max(30), texto("descripcion_libre").opcional().max(200),
            decimal("cantidad").gt(0).digitos(18, 4), texto("unidad_compra").min(1).max(30))
            .despues(d -> {
                String c = d.texto("insumo_codigo"), l = d.texto("descripcion_libre");
                if ((c == null || c.isEmpty()) && (l == null || l.strip().isEmpty())) throw error("cada línea necesita un insumo del catálogo o una descripción");
            });
    static final Esquema SOLICITUD = de(lista("lineas", LINEA_SOL).min(1).max(200));

    static final String SQL_PROV = "SELECT p.id, p.ruc, p.razon_social, CASE WHEN p.estado = 'baja' THEN 'de_baja' ELSE p.estado_validacion END AS estado, "
            + "p.alerta_sunat, p.consultado_en, "
            + "(SELECT count(*) FROM compras.compra c WHERE c.proveedor_id = p.id AND c.estado = 'registrada') AS compras_registradas, "
            + "(SELECT max(c.fecha) FROM compras.compra c WHERE c.proveedor_id = p.id AND c.estado = 'registrada') AS ultima_compra FROM compras.proveedor p ";
    static final String[] PROV_OUT = {"id", "ruc", "razon_social", "estado", "alerta_sunat", "consultado_en", "compras_registradas", "ultima_compra", "advertencia"};
    static final String SQL_COMPRA = "SELECT c.id, c.proveedor_id, p.razon_social AS proveedor_razon_social, "
            + "CASE WHEN p.estado = 'baja' THEN 'de_baja' ELSE p.estado_validacion END AS proveedor_estado, p.alerta_sunat AS proveedor_alerta_sunat, "
            + "(SELECT count(*) FROM compras.compra_adjunto j WHERE j.compra_id = c.id AND j.anulado_en IS NULL) AS comprobantes_vigentes, "
            + "c.tipo_comprobante, c.numero_comprobante, c.fecha, c.moneda, c.estado, c.motivo_anulacion, c.solicitud_id, c.base_imponible, c.igv, c.total, "
            + "c.registrado_por, c.creado_en FROM compras.compra c JOIN compras.proveedor p ON p.id = c.proveedor_id ";

    private final Modulo db;
    private final Acceso acceso;
    private final Integraciones integraciones;
    private final CatalogoCliente catalogo;
    private final Almacen almacen;
    private final Eventos eventos;
    private final BigDecimal limitePct;

    public ComprasController(Modulo compras, Acceso acceso, Integraciones integraciones, CatalogoCliente catalogo, Almacen almacen, Eventos eventos,
                             @Value("${SIGRO_LIMITE_PRECIO_PCT:10}") String limitePct) {
        this.db = compras;
        this.acceso = acceso;
        this.integraciones = integraciones;
        this.catalogo = catalogo;
        this.almacen = almacen;
        this.eventos = eventos;
        this.limitePct = new BigDecimal(limitePct);
    }

    static BigDecimal redondear(BigDecimal x) { return x.setScale(2, RoundingMode.HALF_UP); }

    record Consulta(String estado, String alerta, String advertencia, boolean respondio) {}

    Consulta consultar(String ruc) {
        var r = ruc.length() == 11 && Dominio.rucValido(ruc) ? integraciones.consultarRuc(ruc) : null;
        var c = Dominio.clasificar(ruc, r);
        return new Consulta(c.estado(), c.alerta(), c.advertencia(), r != null);
    }

    Map<String, Object> proveedor(long id, String advertencia) {
        var m = new LinkedHashMap<>(db.fila(SQL_PROV + "WHERE p.id = :i", P.de("i", id)));
        m.put("advertencia", advertencia);
        return R.de(m, PROV_OUT);
    }

    // ------------------------------------------------------------------ proveedores
    @GetMapping("/proveedores")
    public Object listarProveedores() {
        acceso.exigirEnAlgunaObra("proveedor.consultar");
        String q = Peticion.texto("q", null, 80);
        Boolean c = Peticion.bool("solo_para_comprar");
        Pagina pagina = Pagina.leer();
        var rows = db.en(() -> db.filas(SQL_PROV + "WHERE (:q = '' OR lower(p.razon_social) LIKE '%' || lower(:q) || '%' OR p.ruc LIKE :q || '%') "
                + "AND (NOT :c OR p.estado = 'activo') ORDER BY p.razon_social, p.id" + pagina.sql(), P.de("q", q == null ? "" : q.strip(), "c", Boolean.TRUE.equals(c))));
        return R.todos(pagina.recortar(rows), PROV_OUT);
    }

    @GetMapping("/proveedores/consulta-ruc/{ruc}")
    public Object consultarRuc(@PathVariable String ruc) {
        acceso.exigirEnAlgunaObra("proveedor.registrar");
        if (!(ruc.length() == 11 && ruc.chars().allMatch(Character::isDigit) && Dominio.rucValido(ruc)))
            throw new ErrorSigro(422, "ruc_invalido", "El RUC tiene 11 dígitos y su dígito verificador debe coincidir con la fórmula SUNAT.");
        var r = integraciones.consultarRuc(ruc);
        if (r == null) throw new ErrorSigro(404, "sunat_sin_respuesta", "SUNAT no respondió: escribe la razón social; el proveedor quedará «por validar».");
        return R.mapa("ruc", r.ruc(), "razon_social", r.razonSocial(), "estado_sunat", r.estadoSunat(), "condicion", r.condicion(),
                "direccion", null, "apto_para_comprar", r.aptoParaComprar());
    }

    @PostMapping("/proveedores")
    public ResponseEntity<Object> crearProveedor(@RequestBody(required = false) JsonNode cuerpo) {
        acceso.exigirEnAlgunaObra("proveedor.registrar");
        Datos d = PROVEEDOR.validar(cuerpo);
        var c = consultar(d.texto("ruc"));
        return C.creado(db.en(() -> {
            long id = (Long) db.escalar("INSERT INTO compras.proveedor (ruc, razon_social, estado_validacion, validado_en, alerta_sunat, consultado_en) "
                    + "VALUES (:r, :n, CAST(:e AS text), CASE WHEN CAST(:e AS text) = 'validado' THEN now() END, CAST(:al AS text), CASE WHEN :ok THEN now() END) RETURNING id",
                    P.de("r", d.texto("ruc"), "n", d.texto("razon_social"), "e", c.estado(), "al", c.alerta(), "ok", c.respondio()));
            return proveedor(id, c.advertencia());
        }));
    }

    @PostMapping("/proveedores/{proveedor_id}/validar")
    public Object validarProveedor(@PathVariable("proveedor_id") long id) {
        acceso.exigirEnAlgunaObra("proveedor.registrar");
        var p = db.en(() -> db.fila("SELECT ruc, estado, estado_validacion FROM compras.proveedor WHERE id = :i", P.de("i", id)));
        if (p == null) throw new ErrorSigro(404, "no_existe", "Ese proveedor no existe.");
        var c = consultar((String) p.get("ruc"));
        return db.en(() -> {
            String adv = c.advertencia();
            if (c.respondio())
                db.actualizar("UPDATE compras.proveedor SET estado_validacion = CAST(:e AS text), alerta_sunat = CAST(:al AS text), "
                        + "consultado_en = now(), validado_en = CASE WHEN CAST(:e AS text) = 'validado' THEN now() END WHERE id = :i",
                        P.de("e", c.estado(), "al", c.alerta(), "i", id));
            else if ("validado".equals(p.get("estado_validacion"))) adv = "No se pudo consultar a SUNAT; el proveedor conserva su última validación.";
            return proveedor(id, adv);
        });
    }

    @PostMapping("/proveedores/{proveedor_id}/baja")
    public Object bajaProveedor(@PathVariable("proveedor_id") long id) {
        acceso.exigirEnAlgunaObra("proveedor.registrar");
        return db.en(() -> {
            if (db.fila("UPDATE compras.proveedor SET estado = 'baja', fecha_baja = now() WHERE id = :i AND estado = 'activo' RETURNING id", P.de("i", id)) == null)
                throw new ErrorSigro(404, "no_existe", "Ese proveedor no existe o ya estaba de baja.");
            return proveedor(id, null);
        });
    }

    // ------------------------------------------------------------------ compras (HU-32)
    List<Map<String, Object>> compraOut(List<Map<String, Object>> cabeceras) {
        if (cabeceras.isEmpty()) return List.of();
        var ids = new ArrayList<Long>();
        for (var c : cabeceras) ids.add((Long) c.get("id"));
        var lineas = db.filas("SELECT d.compra_id, d.insumo_id, d.cantidad, d.unidad AS unidad_compra, d.precio_unitario, d.precio_referencia, "
                + "d.fuera_de_lo_habitual, d.nota_precio FROM compras.compra_detalle d WHERE d.compra_id = ANY(:ids) ORDER BY d.id", P.de("ids", P.enteros(ids)));
        var insumos = catalogo.porId(lineas.stream().map(l -> l.get("insumo_id")).toList());
        var porCompra = new HashMap<Object, List<Map<String, Object>>>();
        for (var l : lineas) {
            var ins = insumos.getOrDefault(((Number) l.get("insumo_id")).longValue(), Map.of());
            var m = new LinkedHashMap<>(l);
            m.put("insumo_codigo", ins.getOrDefault("codigo", "?"));
            m.put("descripcion", ins.getOrDefault("descripcion", "(insumo no disponible)"));
            porCompra.computeIfAbsent(l.get("compra_id"), k -> new ArrayList<>()).add(R.de(m, "insumo_codigo", "descripcion", "cantidad", "unidad_compra",
                    "precio_unitario", "precio_referencia", "fuera_de_lo_habitual", "nota_precio"));
        }
        var out = new ArrayList<Map<String, Object>>();
        for (var c : cabeceras) {
            var m = new LinkedHashMap<>(c);
            m.put("totales", R.mapa("base", c.get("base_imponible"), "igv", c.get("igv"), "total", c.get("total")));
            m.put("lineas", porCompra.getOrDefault(c.get("id"), List.of()));
            m.put("advertencias", List.of());
            out.add(R.de(m, "id", "proveedor_id", "proveedor_razon_social", "proveedor_estado", "proveedor_alerta_sunat", "comprobantes_vigentes",
                    "tipo_comprobante", "numero_comprobante", "fecha", "moneda", "estado", "motivo_anulacion", "solicitud_id", "totales", "lineas",
                    "advertencias", "registrado_por", "creado_en"));
        }
        return out;
    }

    Map<Long, Map<String, Object>> promediosPagados(long obraId) {
        var m = new HashMap<Long, Map<String, Object>>();
        for (var r : almacen.costoPagado(obraId)) m.put(((Number) r.get("insumo_id")).longValue(), r);
        return m;
    }

    @PostMapping("/obras/{obra_id}/compras")
    public ResponseEntity<Object> registrarCompra(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("compra.registrar", obraId);
        Datos d = COMPRA.validar(cuerpo);
        List<Datos> lineas = d.lista("lineas");
        var codigos = new ArrayList<String>();
        for (Datos l : lineas) codigos.add(l.texto("insumo_codigo").toUpperCase(Locale.ROOT));
        var cat = catalogo.porCodigo(codigos, true);
        var faltan = codigos.stream().filter(c -> !cat.containsKey(c)).toList();
        if (!faltan.isEmpty()) throw new ErrorSigro(422, "insumo_no_existe", "No existen en el catálogo (o están inactivos): " + String.join(", ", faltan) + ".");
        BigDecimal bruto = BigDecimal.ZERO;
        for (Datos l : lineas) bruto = bruto.add(l.decimal("cantidad").multiply(l.decimal("precio_unitario"), Dominio.PY), Dominio.PY);
        BigDecimal base, igv, total;
        if (d.bool("precios_incluyen_igv")) {
            total = redondear(bruto);
            base = redondear(total.divide(BigDecimal.ONE.add(IGV), Dominio.PY));
            igv = total.subtract(base);
        } else {
            base = redondear(bruto);
            igv = redondear(base.multiply(IGV, Dominio.PY));
            total = base.add(igv);
        }
        final BigDecimal fb = base, fi = igv, ft = total, fbruto = bruto;
        var advertencias = new ArrayList<String>();
        Map<Long, Map<String, Object>> pagado;
        try {
            pagado = promediosPagados(obraId);
        } catch (ErrorSigro e) {
            pagado = Map.of();
            advertencias.add("No se pudo consultar el precio habitual (servicio de almacén); la compra se registró sin compararlo.");
        }
        final var pag = pagado;
        return C.creado(db.en(() -> {
            long id = (Long) db.escalar("INSERT INTO compras.compra (proyecto_id, proveedor_id, tipo_comprobante, numero_comprobante, fecha, moneda, "
                            + "base_imponible, igv, total, registrado_por, solicitud_id) VALUES (:p, :pv, :t, :n, :f, :m, :b, :i, :tt, :u, :sol) RETURNING id",
                    P.de("p", obraId, "pv", d.entero("proveedor_id"), "t", d.texto("tipo_comprobante"), "n", d.texto("numero_comprobante").toUpperCase(Locale.ROOT),
                            "f", d.fecha("fecha"), "m", d.texto("moneda"), "b", fb, "i", fi, "tt", ft, "u", a.usuarioId(), "sol", d.entero("solicitud_id")));
            BigDecimal factorSinIgv = fbruto.signum() != 0 ? fb.divide(fbruto, Dominio.PY) : BigDecimal.ONE;
            var adv = new ArrayList<String>();
            var prov = db.fila("SELECT estado_validacion, alerta_sunat FROM compras.proveedor WHERE id = :i", P.de("i", d.entero("proveedor_id")));
            if (prov != null && "con_alerta_sunat".equals(prov.get("estado_validacion")))
                adv.add("SUNAT informa al proveedor " + prov.get("alerta_sunat") + ": la compra se registró y queda marcada para el residente.");
            else if (prov != null && "sin_validar".equals(prov.get("estado_validacion")))
                adv.add("El proveedor aún está por validar en SUNAT: la compra se registró y se puede reintentar la validación.");
            adv.addAll(advertencias);
            for (int k = 0; k < lineas.size(); k++) {
                Datos l = lineas.get(k);
                String codigo = codigos.get(k);
                var ins = cat.get(codigo);
                long insId = (Long) ins.get("id");
                Object prom = pag.getOrDefault(insId, Map.of()).get("promedio_pagado");
                BigDecimal ref = Dominio.referenciaEnUnidadDeCompra(Interno.dec(prom), (BigDecimal) ins.get("factor_conversion"));
                BigDecimal desv = Dominio.desviacionPct(l.decimal("precio_unitario").multiply(factorSinIgv, Dominio.PY), ref);
                boolean fuera = Dominio.fueraDeLoHabitual(desv, limitePct);
                if (fuera)
                    adv.add(codigo + ": el precio se aparta " + (desv.signum() >= 0 ? "+" : "") + desv + " % del promedio pagado en la obra (S/ "
                            + ref.setScale(2, RoundingMode.HALF_EVEN) + " sin IGV por " + l.texto("unidad_compra") + "); queda marcado para el residente.");
                db.actualizar("INSERT INTO compras.compra_detalle (compra_id, insumo_id, cantidad, unidad, precio_unitario, precio_referencia, fuera_de_lo_habitual, nota_precio) "
                                + "VALUES (:c, :i, :q, :u, :pr, :ref, :f, :n)",
                        P.de("c", id, "i", insId, "q", l.decimal("cantidad"), "u", l.texto("unidad_compra"), "pr", l.decimal("precio_unitario"),
                                "ref", ref, "f", fuera, "n", l.texto("nota_precio")));
            }
            var out = new LinkedHashMap<>(compraOut(db.filas(SQL_COMPRA + "WHERE c.id = :i", P.de("i", id))).get(0));
            out.put("advertencias", adv);
            return (Object) out;
        }));
    }

    @GetMapping("/obras/{obra_id}/compras/precio-referencia")
    public Object precioReferencia(@PathVariable("obra_id") long obraId) {
        acceso.exigir("compra.consultar", obraId);
        String insumo = Peticion.texto("insumo", 1, 30);
        if (insumo == null) throw new ErrorSigro(422, "datos_invalidos", "insumo: Field required");
        var ins = catalogo.porCodigo(List.of(insumo), false).get(insumo.toUpperCase(Locale.ROOT));
        if (ins == null) throw new ErrorSigro(404, "insumo_no_existe", "No existe el insumo " + insumo + " en el catálogo.");
        var p = promediosPagados(obraId).getOrDefault((Long) ins.get("id"), Map.of());
        BigDecimal prom = p.get("promedio_pagado") != null ? Interno.dec(p.get("promedio_pagado")) : null;
        Object cant = p.get("cantidad");
        return R.mapa("insumo_codigo", ins.get("codigo"), "unidad_compra", ins.get("unidad_compra") != null && !((String) ins.get("unidad_compra")).isEmpty() ? ins.get("unidad_compra") : ins.get("unidad_s10"),
                "promedio_ponderado", Dominio.referenciaEnUnidadDeCompra(prom, (BigDecimal) ins.get("factor_conversion")),
                "unidad_s10", ins.get("unidad_s10"), "promedio_s10", prom, "factor_conversion", ins.get("factor_conversion"),
                "cantidad_recibida_s10", cant == null || (cant instanceof Number n && n.doubleValue() == 0) ? BigDecimal.ZERO : Interno.dec(cant),
                "limite_pct", limitePct);
    }

    @GetMapping("/obras/{obra_id}/compras")
    public Object listarCompras(@PathVariable("obra_id") long obraId) {
        acceso.exigir("compra.consultar", obraId);
        String estado = Peticion.texto("estado");
        if (estado != null && !estado.matches("^(registrada|anulada)$")) throw new ErrorSigro(422, "datos_invalidos", "estado: String should match pattern '^(registrada|anulada)$'");
        Long pv = Peticion.entero("proveedor_id", 1L, null);
        Pagina pagina = Pagina.leer();
        return db.en(() -> compraOut(pagina.recortar(db.filas(SQL_COMPRA + "WHERE c.proyecto_id = :p AND (CAST(:e AS text) IS NULL OR c.estado = :e) "
                + "AND (CAST(:pv AS bigint) IS NULL OR c.proveedor_id = :pv) ORDER BY c.fecha DESC, c.id DESC" + pagina.sql(), P.de("p", obraId, "e", estado, "pv", pv)))));
    }

    @GetMapping("/obras/{obra_id}/compras/{compra_id}")
    public Object verCompra(@PathVariable("obra_id") long obraId, @PathVariable("compra_id") long id) {
        acceso.exigir("compra.consultar", obraId);
        return db.en(() -> {
            var c = db.filas(SQL_COMPRA + "WHERE c.id = :i AND c.proyecto_id = :p", P.de("i", id, "p", obraId));
            if (c.isEmpty()) throw new ErrorSigro(404, "no_existe", "Esa compra no existe en esta obra.");
            return compraOut(c).get(0);
        });
    }

    @PostMapping("/obras/{obra_id}/compras/{compra_id}/anular")
    public Object anularCompra(@PathVariable("obra_id") long obraId, @PathVariable("compra_id") long id, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("compra.registrar", obraId);
        acceso.exigirPin(a, "compra.anular");   // anular saca la compra del gasto y no se deshace
        Datos d = ANULAR.validar(cuerpo);
        var dest = eventos.destinatariosConRol(obraId, List.of("administrador", "residente"), a.usuarioId());
        return db.en(() -> {
            var previa = db.fila("SELECT numero_comprobante FROM compras.compra WHERE id = :i AND proyecto_id = :p", P.de("i", id, "p", obraId));
            if (previa == null) throw new ErrorSigro(404, "no_existe", "Esa compra no existe en esta obra.");
            db.actualizar("UPDATE compras.compra SET estado = 'anulada', motivo_anulacion = :m, anulada_por = :u, anulada_en = now() WHERE id = :i",
                    P.de("m", d.texto("motivo"), "u", a.usuarioId(), "i", id));
            eventos.publicar(db, "compra.anulada", obraId, dest, "Compra anulada",
                    a.nombre() + " anuló la compra " + previa.get("numero_comprobante") + ": " + d.texto("motivo"), R.mapa("compra_id", id));
            return compraOut(db.filas(SQL_COMPRA + "WHERE c.id = :i", P.de("i", id))).get(0);
        });
    }

    // ------------------------------------------------------------ solicitud de materiales
    List<Map<String, Object>> solicitudOut(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        var cab = db.filas("SELECT s.id, s.numero, s.fecha, s.motivo_anulacion, s.emitida_por, v.estado_derivado AS estado "
                + "FROM compras.solicitud s JOIN compras.solicitud_estado v ON v.solicitud_id = s.id WHERE s.id = ANY(:ids) ORDER BY s.numero DESC", P.de("ids", P.enteros(ids)));
        var lin = db.filas("SELECT l.solicitud_id, l.insumo_id, l.descripcion_libre, l.unidad_compra, l.cantidad AS cantidad_solicitada, a.cantidad_comprada, a.cantidad_pendiente "
                + "FROM compras.solicitud_linea l JOIN compras.solicitud_linea_avance a ON a.linea_id = l.id WHERE l.solicitud_id = ANY(:ids) ORDER BY l.id", P.de("ids", P.enteros(ids)));
        var insumos = catalogo.porId(lin.stream().map(l -> l.get("insumo_id")).filter(Objects::nonNull).toList());
        var agrupadas = new HashMap<Object, List<Map<String, Object>>>();
        for (var l : lin) {
            var ins = l.get("insumo_id") != null ? insumos.get(((Number) l.get("insumo_id")).longValue()) : null;
            var m = new LinkedHashMap<>(l);
            m.put("insumo_codigo", ins != null ? ins.get("codigo") : null);
            m.put("descripcion", ins != null ? ins.get("descripcion") : l.get("descripcion_libre"));
            agrupadas.computeIfAbsent(l.get("solicitud_id"), k -> new ArrayList<>()).add(R.de(m, "insumo_codigo", "descripcion", "unidad_compra",
                    "cantidad_solicitada", "cantidad_comprada", "cantidad_pendiente"));
        }
        var out = new ArrayList<Map<String, Object>>();
        for (var c : cab) {
            var m = new LinkedHashMap<>(c);
            m.put("lineas", agrupadas.getOrDefault(c.get("id"), List.of()));
            out.add(R.de(m, "id", "numero", "fecha", "estado", "motivo_anulacion", "emitida_por", "lineas"));
        }
        return out;
    }

    @PostMapping("/obras/{obra_id}/solicitudes")
    public ResponseEntity<Object> emitirSolicitud(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("solicitud.emitir", obraId);
        Datos d = SOLICITUD.validar(cuerpo);
        List<Datos> lineas = d.lista("lineas");
        var codigos = new ArrayList<String>();
        for (Datos l : lineas) if (l.texto("insumo_codigo") != null && !l.texto("insumo_codigo").isEmpty()) codigos.add(l.texto("insumo_codigo").toUpperCase(Locale.ROOT));
        var cat = catalogo.porCodigo(codigos, true);
        var faltan = codigos.stream().filter(c -> !cat.containsKey(c)).toList();
        if (!faltan.isEmpty()) throw new ErrorSigro(422, "insumo_no_existe", "No existen en el catálogo (o están inactivos): " + String.join(", ", faltan) + ".");
        if (new HashSet<>(codigos).size() != codigos.size()) throw new ErrorSigro(422, "insumo_repetido", "Un insumo aparece dos veces; suma las cantidades en una sola línea.");
        var dest = eventos.destinatariosConRol(obraId, List.of("administrador"), a.usuarioId());
        return C.creado(db.en(() -> {
            long sol = (Long) db.escalar("INSERT INTO compras.solicitud (proyecto_id, emitida_por) VALUES (:p, :u) RETURNING id", P.de("p", obraId, "u", a.usuarioId()));
            for (Datos l : lineas) {
                String c = l.texto("insumo_codigo");
                Object insId = c != null && !c.isEmpty() ? cat.get(c.toUpperCase(Locale.ROOT)).get("id") : null;
                db.actualizar("INSERT INTO compras.solicitud_linea (solicitud_id, insumo_id, descripcion_libre, cantidad, unidad_compra) VALUES (:s, :i, :d, :q, :u)",
                        P.de("s", sol, "i", insId, "d", l.texto("descripcion_libre"), "q", l.decimal("cantidad"), "u", l.texto("unidad_compra")));
            }
            Object numero = db.escalar("SELECT numero FROM compras.solicitud WHERE id = :i", P.de("i", sol));
            eventos.publicar(db, "solicitud.emitida", obraId, dest, "Nueva solicitud de materiales",
                    a.nombre() + " emitió la solicitud N.º " + numero + " con " + lineas.size() + " línea(s).", R.mapa("solicitud_id", sol, "numero", numero));
            return (Object) solicitudOut(List.of(sol)).get(0);
        }));
    }

    @GetMapping("/obras/{obra_id}/solicitudes")
    public Object listarSolicitudes(@PathVariable("obra_id") long obraId) {
        acceso.exigir("solicitud.consultar", obraId);
        Pagina pagina = Pagina.leer();
        return db.en(() -> {
            var ids = new ArrayList<Long>();
            for (var r : pagina.recortar(db.filas("SELECT id FROM compras.solicitud WHERE proyecto_id = :p ORDER BY numero DESC" + pagina.sql(), P.de("p", obraId))))
                ids.add((Long) r.get("id"));
            return solicitudOut(ids);
        });
    }

    @GetMapping("/obras/{obra_id}/solicitudes/{solicitud_id}")
    public Object verSolicitud(@PathVariable("obra_id") long obraId, @PathVariable("solicitud_id") long id) {
        acceso.exigir("solicitud.consultar", obraId);
        return db.en(() -> {
            if (db.fila("SELECT id FROM compras.solicitud WHERE id = :i AND proyecto_id = :p", P.de("i", id, "p", obraId)) == null)
                throw new ErrorSigro(404, "no_existe", "Esa solicitud no existe en esta obra.");
            return solicitudOut(List.of(id)).get(0);
        });
    }

    @PostMapping("/obras/{obra_id}/solicitudes/{solicitud_id}/anular")
    public Object anularSolicitud(@PathVariable("obra_id") long obraId, @PathVariable("solicitud_id") long id, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("solicitud.emitir", obraId);
        Datos d = ANULAR.validar(cuerpo);
        return db.en(() -> {
            if (db.fila("SELECT 1 AS x FROM compras.solicitud WHERE id = :i AND proyecto_id = :p", P.de("i", id, "p", obraId)) == null)
                throw new ErrorSigro(404, "no_existe", "Esa solicitud no existe en esta obra.");
            db.actualizar("UPDATE compras.solicitud SET estado = 'anulada', motivo_anulacion = :m, anulada_por = :u, anulada_en = now() WHERE id = :i",
                    P.de("m", d.texto("motivo"), "u", a.usuarioId(), "i", id));
            return solicitudOut(List.of(id)).get(0);
        });
    }
}
