package pe.sevens.sigro.comun;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Entrada ya validada: valores (con sus defectos) y qué campos envió el cliente (model_fields_set). */
public final class Datos {
    private final Map<String, Object> valores;
    private final Set<String> enviados;

    public Datos(Map<String, Object> valores, Set<String> enviados) {
        this.valores = valores;
        this.enviados = enviados;
    }

    public Map<String, Object> valores() { return valores; }
    public Set<String> enviados() { return enviados; }
    public boolean envio(String c) { return enviados.contains(c); }
    public Object get(String c) { return valores.get(c); }
    public void poner(String c, Object v) { valores.put(c, v); }
    public String texto(String c) { return (String) valores.get(c); }
    public Long entero(String c) { Object v = valores.get(c); return v == null ? null : ((Number) v).longValue(); }
    public Integer int32(String c) { Object v = valores.get(c); return v == null ? null : ((Number) v).intValue(); }
    public BigDecimal decimal(String c) { return (BigDecimal) valores.get(c); }
    public LocalDate fecha(String c) { return (LocalDate) valores.get(c); }
    public Boolean bool(String c) { return (Boolean) valores.get(c); }
    public Datos objeto(String c) { return (Datos) valores.get(c); }
    @SuppressWarnings("unchecked")
    public <T> List<T> lista(String c) { return (List<T>) valores.get(c); }
}
