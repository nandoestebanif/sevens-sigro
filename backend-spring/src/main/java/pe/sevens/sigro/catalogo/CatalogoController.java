package pe.sevens.sigro.catalogo;

import static pe.sevens.sigro.comun.Esquema.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.sevens.sigro.comun.*;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.seguridad.Acceso.Actor;

/**
 * Catálogo de insumos (port de app/modulos/catalogo/rutas.py): HU-08 alta con código único y «por verificar»,
 * HU-17 factor de conversión con histórico, HU-07 importación del S10. Importar nunca toca saldos.
 */
@RestController
@RequestMapping("/api/v1/catalogo")
public class CatalogoController {
    static final String[] TIPOS = {"material", "mano_obra", "equipo", "subcontrato"};
    static final String COLS = "id, codigo, descripcion, unidad_s10, unidad_compra, factor_conversion, tipo, activo, verificado, medida, norma, marca";
    static final String[] OUT = {"id", "codigo", "descripcion", "unidad_s10", "unidad_compra", "factor_conversion", "tipo", "activo", "verificado", "medida", "norma", "marca"};
    static final int MAX_PDF = 15 * 1024 * 1024;

    static final Esquema INSUMO = de(texto("descripcion").min(3).max(200), texto("unidad").min(1).max(20),
            texto("tipo").defecto("material").uno(TIPOS),
            texto("medida").opcional().max(80), texto("norma").opcional().max(80), texto("marca").opcional().max(80))
            .despues(d -> {
                for (String k : List.of("medida", "norma", "marca")) {
                    String v = d.texto(k);
                    if (v != null) d.poner(k, v.strip().isEmpty() ? null : v.strip());
                }
            });
    static final Esquema FACTOR = de(decimal("factor").gt(0).digitos(18, 6), texto("motivo").min(3).max(300));
    static final Esquema FILA_S10 = de(texto("codigo").min(1).max(30), texto("descripcion").min(1).max(300),
            texto("unidad").min(1).max(20), texto("tipo").uno(TIPOS));
    static final Esquema IMPORTAR = de(lista("insumos", FILA_S10).min(1).max(5000));

    private final Modulo db;
    private final Acceso acceso;
    private final Integraciones integraciones;

    public CatalogoController(Modulo catalogo, Acceso acceso, Integraciones integraciones) {
        this.db = catalogo;
        this.acceso = acceso;
        this.integraciones = integraciones;
    }

    /** Sin tildes, minúsculas y espacios simples: «Cemento  Portland» == «cemento portland». */
    public static String normalizar(String t) {
        String sin = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{Mn}", "");
        return sin.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    Map<String, Object> existente(long id) {
        var r = db.fila("SELECT " + COLS + " FROM catalogo.insumo WHERE id = :i", P.de("i", id));
        if (r == null) throw new ErrorSigro(404, "no_existe", "Ese insumo no existe en el catálogo.");
        return r;
    }

    @GetMapping("/insumos")
    public Object listar() {
        acceso.exigirEnAlgunaObra("insumo.consultar");
        String q = Peticion.texto("q", null, 100);
        String tipo = Peticion.uno("tipo", null, TIPOS);
        Boolean verificado = Peticion.bool("verificado");
        Pagina pagina = Pagina.leer(3000);
        var cond = new ArrayList<>(List.of("true"));
        var p = P.de();
        if (q != null && !q.isEmpty()) { cond.add("(codigo ILIKE :q OR descripcion ILIKE :q)"); p.addValue("q", "%" + q.strip() + "%"); }
        if (tipo != null) { cond.add("tipo = :t"); p.addValue("t", tipo); }
        if (verificado != null) { cond.add("verificado = :v"); p.addValue("v", verificado); }
        String corte = pagina.limite() != null ? pagina.sql() : " LIMIT 3000";
        var filas = db.en(() -> db.filas("SELECT " + COLS + " FROM catalogo.insumo WHERE " + String.join(" AND ", cond) + " ORDER BY codigo" + corte, p));
        return R.todos(pagina.recortar(filas), OUT);
    }

    @PostMapping("/insumos")
    public ResponseEntity<Object> registrar(@RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigirEnAlgunaObra("insumo.registrar");
        Datos d = INSUMO.validar(cuerpo);
        return C.creado(db.en(() -> {
            db.fila("SELECT pg_advisory_xact_lock(70801)");
            List<String> clave = List.of(normalizar(d.texto("descripcion")), normalizar(Objects.requireNonNullElse(d.texto("medida"), "")),
                    normalizar(Objects.requireNonNullElse(d.texto("marca"), "")), normalizar(d.texto("unidad")));
            for (var x : db.filas("SELECT codigo, descripcion, unidad_compra, medida, marca FROM catalogo.insumo")) {
                List<String> k = List.of(normalizar((String) x.get("descripcion")), normalizar(Objects.requireNonNullElse((String) x.get("medida"), "")),
                        normalizar(Objects.requireNonNullElse((String) x.get("marca"), "")), normalizar((String) x.get("unidad_compra")));
                if (k.equals(clave)) {
                    var partes = new ArrayList<String>();
                    for (String c : List.of("descripcion", "medida", "marca")) if (x.get(c) != null && !((String) x.get(c)).isEmpty()) partes.add((String) x.get(c));
                    throw new ErrorSigro(409, "insumo_duplicado", "Ya existe ese insumo en el catálogo: " + x.get("codigo") + " · "
                            + String.join(" ", partes) + ". Úsalo en lugar de crear otro.");
                }
            }
            long n = ((Number) db.escalar("SELECT coalesce(max(substring(codigo from 7)::int), 0) + 1 FROM catalogo.insumo WHERE codigo ~ '^SIGRO-[0-9]+$'", P.de())).longValue();
            return R.de(db.fila("INSERT INTO catalogo.insumo (codigo, descripcion, unidad_s10, unidad_compra, tipo, verificado, creado_por, medida, norma, marca) "
                            + "VALUES (:c, :d, :u, :u, :t, false, :por, :me, :no, :ma) RETURNING " + COLS,
                    P.de("c", String.format("SIGRO-%05d", n), "d", d.texto("descripcion"), "u", d.texto("unidad"), "t", d.texto("tipo"),
                            "por", a.usuarioId(), "me", d.texto("medida"), "no", d.texto("norma"), "ma", d.texto("marca"))), OUT);
        }));
    }

    @PostMapping("/insumos/{insumo_id}/verificar")
    public Object verificar(@PathVariable("insumo_id") long id) {
        acceso.exigirEnAlgunaObra("insumo.verificar");
        return db.en(() -> {
            existente(id);
            return R.de(db.fila("UPDATE catalogo.insumo SET verificado = true WHERE id = :i RETURNING " + COLS, P.de("i", id)), OUT);
        });
    }

    @PostMapping("/insumos/{insumo_id}/factor-conversion")
    public Object factor(@PathVariable("insumo_id") long id, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigirEnAlgunaObra("insumo.factor");
        Datos d = FACTOR.validar(cuerpo);
        return db.en(() -> {
            var ins = existente(id);
            BigDecimal actual = (BigDecimal) ins.get("factor_conversion");
            if (actual.compareTo(d.decimal("factor")) == 0) throw new ErrorSigro(422, "sin_cambio", "El factor ya tiene ese valor.");
            db.actualizar("INSERT INTO catalogo.insumo_factor_historial (insumo_id, factor_anterior, factor_nuevo, motivo, cambiado_por) VALUES (:i, :ant, :nue, :m, :u)",
                    P.de("i", id, "ant", actual, "nue", d.decimal("factor"), "m", d.texto("motivo"), "u", a.usuarioId()));
            return R.de(db.fila("UPDATE catalogo.insumo SET factor_conversion = :f WHERE id = :i RETURNING " + COLS, P.de("f", d.decimal("factor"), "i", id)), OUT);
        });
    }

    @GetMapping("/insumos/{insumo_id}/factor-conversion/historial")
    public Object historial(@PathVariable("insumo_id") long id) {
        acceso.exigirEnAlgunaObra("insumo.consultar");
        return db.en(() -> {
            existente(id);
            return db.filas("SELECT id, factor_anterior, factor_nuevo, motivo, cambiado_por, cambiado_en FROM catalogo.insumo_factor_historial WHERE insumo_id = :i ORDER BY id", P.de("i", id));
        });
    }

    @PostMapping("/insumos/importar-s10")
    public Object importar(@RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigirEnAlgunaObra("insumo.importar");
        Datos d = IMPORTAR.validar(cuerpo);
        List<Datos> filas = d.lista("insumos");
        return db.en(() -> {
            db.fila("SELECT pg_advisory_xact_lock(70801)");
            var codigos = new LinkedHashSet<String>();
            for (Datos f : filas) codigos.add(f.texto("codigo"));
            var existentes = new HashMap<String, Map<String, Object>>();
            for (var r : db.filas("SELECT id, codigo, descripcion, tipo FROM catalogo.insumo WHERE codigo = ANY(:cs)", P.de("cs", P.textos(codigos))))
                existentes.put((String) r.get("codigo"), r);
            var vistos = new HashSet<String>();
            int creados = 0, actualizados = 0, sinCambios = 0;
            var avisos = new ArrayList<String>();
            for (Datos f : filas) {
                String c = f.texto("codigo");
                if (!vistos.add(c)) { avisos.add(c + ": aparece repetido en el archivo; se tomó la primera fila."); continue; }
                var ex = existentes.get(c);
                if (ex == null) {
                    db.actualizar("INSERT INTO catalogo.insumo (codigo, descripcion, unidad_s10, unidad_compra, tipo, verificado, creado_por) VALUES (:c, :d, :u, :u, :t, true, :por)",
                            P.de("c", c, "d", f.texto("descripcion"), "u", f.texto("unidad"), "t", f.texto("tipo"), "por", a.usuarioId()));
                    creados++;
                    continue;
                }
                if (!Objects.equals(ex.get("tipo"), f.texto("tipo")))
                    avisos.add(c + ": el S10 lo trae como «" + f.texto("tipo") + "» y el catálogo como «" + ex.get("tipo") + "»; no se cambió.");
                if (!Objects.equals(ex.get("descripcion"), f.texto("descripcion"))) {
                    db.actualizar("UPDATE catalogo.insumo SET descripcion = :d WHERE id = :i", P.de("d", f.texto("descripcion"), "i", ex.get("id")));
                    actualizados++;
                } else sinCambios++;
            }
            return R.mapa("creados", creados, "actualizados", actualizados, "sin_cambios", sinCambios, "advertencias", avisos);
        });
    }

    @PostMapping("/importaciones-s10")
    public Object leerPdf(@RequestParam(value = "archivo", required = false) MultipartFile archivo) throws java.io.IOException {
        acceso.exigirEnAlgunaObra("insumo.importar");
        if (archivo == null) throw new ErrorSigro(422, "datos_invalidos", "archivo: Field required");
        byte[] contenido = archivo.getBytes();
        if (contenido.length > MAX_PDF) throw new ErrorSigro(413, "archivo_muy_grande", "El PDF supera los 15 MB; exporta solo la Relación de insumos del S10.");
        if (contenido.length == 0) throw new ErrorSigro(422, "archivo_vacio", "El archivo llegó vacío.");
        var existentes = new ArrayList<String>();
        db.en(() -> { for (Object c : db.columna("SELECT codigo FROM catalogo.insumo ORDER BY codigo", P.de())) existentes.add((String) c); return null; });
        String nombre = archivo.getOriginalFilename() == null || archivo.getOriginalFilename().isEmpty() ? "relacion-insumos.pdf" : archivo.getOriginalFilename();
        JsonNode r = integraciones.leerRelacionS10(nombre, contenido, existentes);
        return lectura(r);
    }

    /** Da forma a lo que devolvió Integraciones (LecturaS10Out): números como float, solo los campos del contrato. */
    static Map<String, Object> lectura(JsonNode r) {
        JsonNode res = r.path("resumen");
        var porTipo = new LinkedHashMap<String, Object>();
        res.path("por_tipo").fields().forEachRemaining(e -> porTipo.put(e.getKey(), e.getValue().asLong()));
        var insumos = new ArrayList<Map<String, Object>>();
        for (JsonNode i : r.path("insumos"))
            insumos.add(R.mapa("codigo", i.path("codigo").asText(), "descripcion", i.path("descripcion").asText(), "unidad", i.path("unidad").asText(),
                    "tipo", i.path("tipo").asText(), "cantidad", i.path("cantidad").asDouble(), "precio", i.path("precio").asDouble(),
                    "parcial", i.path("parcial").asDouble(), "accion", i.path("accion").asText(),
                    "advertencia", i.hasNonNull("advertencia") ? i.get("advertencia").asText() : null));
        return R.mapa("obra", txt(r, "obra"), "subpresupuesto", txt(r, "subpresupuesto"), "fecha", txt(r, "fecha"),
                "resumen", R.mapa("leidos", res.path("leidos").asLong(), "a_crear", res.path("a_crear").asLong(), "a_actualizar", res.path("a_actualizar").asLong(),
                        "fallidos", res.path("fallidos").asLong(), "por_tipo", porTipo, "parcial_total", res.path("parcial_total").asDouble()),
                "insumos", insumos, "fallidos", Json.aJava(r.path("fallidos")));
    }

    static String txt(JsonNode r, String k) { return r.hasNonNull(k) ? r.get(k).asText() : null; }
}
