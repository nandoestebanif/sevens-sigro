package pe.sevens.sigro.catalogo;

import java.util.*;
import org.springframework.stereotype.Component;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.comun.P;

/**
 * API interna del catálogo: los demás módulos le piden los datos de los insumos (código, descripción, unidades,
 * factor). Compras y almacén guardan solo el insumo_id; nunca hacen JOIN a este esquema.
 */
@Component("catalogoInterno")
public class Catalogo {
    public static final String COLUMNAS = "id, codigo, descripcion, unidad_s10, unidad_compra, factor_conversion, tipo, activo, verificado";
    private final Modulo db;

    public Catalogo(Modulo catalogo) { this.db = catalogo; }

    /** Insumos por id o por código (sin distinguir mayúsculas). Lo que no existe simplemente no viene. */
    public List<Map<String, Object>> buscar(Collection<? extends Number> ids, Collection<String> codigos, boolean soloActivos) {
        if ((ids == null || ids.isEmpty()) && (codigos == null || codigos.isEmpty())) return List.of();
        var cods = new ArrayList<String>();
        if (codigos != null) for (String c : codigos) cods.add(c.toUpperCase(Locale.ROOT));
        return pe.sevens.sigro.comun.Interno.json(db.en(() -> db.filas("SELECT " + COLUMNAS + " FROM catalogo.insumo WHERE (id = ANY(:ids) OR upper(codigo) = ANY(:cods)) "
                + "AND (NOT :act OR activo) ORDER BY codigo",
                P.de("ids", P.enteros(ids == null ? List.of() : ids), "cods", P.textos(cods), "act", soloActivos))));
    }

    /** id → fila. */
    public Map<Long, Map<String, Object>> porId(Collection<? extends Number> ids) {
        var m = new LinkedHashMap<Long, Map<String, Object>>();
        for (var f : buscar(ids, List.of(), false)) m.put(((Number) f.get("id")).longValue(), f);
        return m;
    }
}
