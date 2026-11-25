package pe.sevens.sigro.compras;

import java.util.*;
import org.springframework.stereotype.Component;
import pe.sevens.sigro.catalogo.Catalogo;
import pe.sevens.sigro.comun.Interno;

/** Lo que compras necesita saber de los insumos, pedido al módulo catálogo (port de catalogo_cliente.py). */
@Component
public class CatalogoCliente {
    private final Catalogo catalogo;

    public CatalogoCliente(Catalogo catalogo) { this.catalogo = catalogo; }

    static Map<String, Object> normalizar(Map<String, Object> i) {
        var m = new LinkedHashMap<>(i);
        if (m.get("factor_conversion") != null) m.put("factor_conversion", Interno.dec(m.get("factor_conversion")));
        m.put("id", ((Number) m.get("id")).longValue());
        return m;
    }

    public Map<Long, Map<String, Object>> porId(Collection<?> ids) {
        var s = new TreeSet<Long>();
        for (Object x : ids) if (x != null) s.add(((Number) x).longValue());
        if (s.isEmpty()) return Map.of();
        var m = new LinkedHashMap<Long, Map<String, Object>>();
        for (var i : catalogo.buscar(s, List.of(), false)) { var n = normalizar(i); m.put((Long) n.get("id"), n); }
        return m;
    }

    /** Clave: el código en MAYÚSCULAS. */
    public Map<String, Map<String, Object>> porCodigo(Collection<String> codigos, boolean soloActivos) {
        var s = new TreeSet<String>();
        for (String c : codigos) if (c != null && !c.isEmpty()) s.add(c.toUpperCase(Locale.ROOT));
        if (s.isEmpty()) return Map.of();
        var m = new LinkedHashMap<String, Map<String, Object>>();
        for (var i : catalogo.buscar(List.of(), s, soloActivos)) m.put(((String) i.get("codigo")).toUpperCase(Locale.ROOT), normalizar(i));
        return m;
    }
}
