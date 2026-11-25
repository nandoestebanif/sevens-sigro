package pe.sevens.sigro.compras;

import static pe.sevens.sigro.comun.Esquema.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import pe.sevens.sigro.comun.*;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.seguridad.Acceso.Actor;

/** Partidas y análisis de precios unitarios (HU-16). Port de app/modulos/compras/partidas.py. */
@RestController
@RequestMapping("/api/v1/obras/{obra_id}")
public class PartidasController {
    static final Esquema IMPORTAR = de(lista("filas", de(texto("partida_codigo").min(1).max(40), texto("partida_descripcion").min(1).max(300),
            texto("partida_unidad").min(1).max(20), texto("insumo_codigo").min(1).max(30), decimal("cantidad_por_unidad").gt(0).digitos(18, 6),
            texto("unidad").min(1).max(20))).min(1).max(20000));

    private final Modulo db;
    private final Acceso acceso;
    private final CatalogoCliente catalogo;

    public PartidasController(Modulo compras, Acceso acceso, CatalogoCliente catalogo) {
        this.db = compras;
        this.acceso = acceso;
        this.catalogo = catalogo;
    }

    @PostMapping("/partidas/importar-apu")
    public Object importar(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("partida.importar", obraId);
        Datos d = IMPORTAR.validar(cuerpo);
        List<Datos> filas = d.lista("filas");
        var delCatalogo = catalogo.porCodigo(filas.stream().map(f -> f.texto("insumo_codigo")).toList(), false);
        return db.en(() -> {
            int pNuevas = 0, rNuevos = 0, sinCambios = 0;
            var fallidos = new ArrayList<Map<String, Object>>();
            var avisos = new ArrayList<String>();
            var cache = new HashMap<List<String>, Long>();
            var vistos = new HashSet<List<Object>>();
            for (Datos f : filas) {
                String pc = f.texto("partida_codigo"), ic = f.texto("insumo_codigo");
                String desc = String.join(" ", f.texto("partida_descripcion").strip().split("\\s+"));
                var clave = List.of(pc, desc.toLowerCase(Locale.ROOT));
                var ins = delCatalogo.get(ic.toUpperCase(Locale.ROOT));
                if (ins == null || !ins.get("codigo").equals(ic)) {
                    fallidos.add(R.mapa("partida_codigo", pc, "insumo_codigo", ic, "motivo", "El recurso no está en el catálogo."));
                    continue;
                }
                if (!cache.containsKey(clave)) {
                    Object ex = db.escalar("SELECT id FROM compras.partida WHERE proyecto_id = :p AND codigo = :c AND lower(btrim(descripcion)) = lower(btrim(:d))",
                            P.de("p", obraId, "c", pc, "d", desc));
                    if (ex == null) {
                        ex = db.escalar("INSERT INTO compras.partida (proyecto_id, codigo, descripcion, unidad, creado_por) VALUES (:p, :c, :d, :u, :us) RETURNING id",
                                P.de("p", obraId, "c", pc, "d", desc, "u", f.texto("partida_unidad"), "us", a.usuarioId()));
                        pNuevas++;
                    }
                    cache.put(clave, (Long) ex);
                }
                long pid = cache.get(clave);
                if (!vistos.add(List.of(pid, ic))) {
                    fallidos.add(R.mapa("partida_codigo", pc, "insumo_codigo", ic, "motivo", "El recurso aparece repetido en la misma partida."));
                    continue;
                }
                var rc = db.fila("SELECT cantidad_por_unidad FROM compras.partida_recurso WHERE partida_id = :p AND insumo_id = :i", P.de("p", pid, "i", ins.get("id")));
                if (rc == null) {
                    db.actualizar("INSERT INTO compras.partida_recurso (partida_id, insumo_id, cantidad_por_unidad, unidad) VALUES (:p, :i, :c, :u)",
                            P.de("p", pid, "i", ins.get("id"), "c", f.decimal("cantidad_por_unidad"), "u", f.texto("unidad")));
                    rNuevos++;
                    if (!f.texto("unidad").strip().toLowerCase(Locale.ROOT).equals(((String) ins.get("unidad_s10")).strip().toLowerCase(Locale.ROOT)))
                        avisos.add(pc + " · " + ic + ": el APU lo da en «" + f.texto("unidad") + "» y el catálogo en «" + ins.get("unidad_s10") + "».");
                } else {
                    sinCambios++;
                    if (((BigDecimal) rc.get("cantidad_por_unidad")).compareTo(f.decimal("cantidad_por_unidad").setScale(6, RoundingMode.HALF_EVEN)) != 0)
                        avisos.add(pc + " · " + ic + ": ya está con " + rc.get("cantidad_por_unidad") + "; el archivo trae " + f.decimal("cantidad_por_unidad") + ". No se pisó.");
                }
            }
            return R.mapa("partidas_creadas", pNuevas, "recursos_creados", rNuevos, "sin_cambios", sinCambios, "fallidos", fallidos, "advertencias", avisos);
        });
    }

    @GetMapping("/partidas")
    public Object partidas(@PathVariable("obra_id") long obraId) {
        acceso.exigir("partida.consultar", obraId);
        String q = Peticion.texto("q", null, 100);
        var p = P.de("p", obraId);
        String cond = "";
        if (q != null && !q.isEmpty()) { cond = " AND (pa.codigo ILIKE :q OR pa.descripcion ILIKE :q)"; p.addValue("q", "%" + q.strip() + "%"); }
        String c = cond;
        return db.en(() -> db.filas("SELECT pa.id, pa.codigo, pa.descripcion, pa.unidad, count(r.id) AS recursos FROM compras.partida pa "
                + "LEFT JOIN compras.partida_recurso r ON r.partida_id = pa.id WHERE pa.proyecto_id = :p" + c + " GROUP BY pa.id ORDER BY pa.codigo, pa.descripcion", p));
    }

    @GetMapping("/partidas/{partida_id}/recursos")
    public Object recursos(@PathVariable("obra_id") long obraId, @PathVariable("partida_id") long partidaId) {
        acceso.exigir("partida.consultar", obraId);
        return db.en(() -> {
            if (db.fila("SELECT 1 AS x FROM compras.partida WHERE id = :i AND proyecto_id = :p", P.de("i", partidaId, "p", obraId)) == null)
                throw new ErrorSigro(404, "no_existe", "Esa partida no existe en esta obra.");
            var rs = db.filas("SELECT insumo_id, cantidad_por_unidad, unidad FROM compras.partida_recurso WHERE partida_id = :i", P.de("i", partidaId));
            var insumos = catalogo.porId(rs.stream().map(r -> r.get("insumo_id")).toList());
            var l = new ArrayList<Map<String, Object>>();
            for (var r : rs) {
                var ins = insumos.get((Long) r.get("insumo_id"));
                if (ins == null) continue;
                l.add(R.mapa("insumo_id", r.get("insumo_id"), "insumo_codigo", ins.get("codigo"), "descripcion", ins.get("descripcion"), "unidad_s10", ins.get("unidad_s10"),
                        "cantidad_por_unidad", r.get("cantidad_por_unidad"), "unidad", r.get("unidad")));
            }
            l.sort(Comparator.comparing(x -> (String) x.get("insumo_codigo")));
            return l;
        });
    }

    @GetMapping("/insumos/{insumo_id}/partidas")
    public Object usanElInsumo(@PathVariable("obra_id") long obraId, @PathVariable("insumo_id") long insumoId) {
        acceso.exigir("partida.consultar", obraId);
        return db.en(() -> db.filas("SELECT pa.id, pa.codigo, pa.descripcion, pa.unidad, 1 AS recursos FROM compras.partida pa "
                + "JOIN compras.partida_recurso r ON r.partida_id = pa.id WHERE pa.proyecto_id = :p AND r.insumo_id = :i ORDER BY pa.codigo", P.de("p", obraId, "i", insumoId)));
    }
}
