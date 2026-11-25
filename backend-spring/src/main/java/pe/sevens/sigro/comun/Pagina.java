package pe.sevens.sigro.comun;

import java.util.List;

/**
 * Paginación OPCIONAL y compatible hacia atrás: sin `limite`, la lista completa de siempre. Con `limite` (y `desde`)
 * se devuelve esa página y, si quedan más, la cabecera X-Siguiente-Desde.
 */
public record Pagina(Long limite, long desde) {
    public static final String CABECERA = "X-Siguiente-Desde";

    public static Pagina leer(long maximo) {
        Long l = Peticion.entero("limite", 1L, maximo);
        Long d = Peticion.entero("desde", 0L, null);
        return new Pagina(l, d == null ? 0 : d);
    }

    public static Pagina leer() { return leer(500); }

    /** Pide UNA fila de más para saber si hay otra página sin un count(*) aparte. */
    public String sql() { return limite == null ? "" : " LIMIT " + (limite + 1) + " OFFSET " + desde; }

    public <T> List<T> recortar(List<T> filas) {
        if (limite == null) return filas;
        if (filas.size() > limite) {
            Peticion.cabecera(CABECERA, String.valueOf(desde + limite));
            return filas.subList(0, limite.intValue());
        }
        return filas;
    }

    public <T> List<T> aplicar(List<T> filas) {
        if (limite == null) return filas;
        int a = (int) Math.min(desde, filas.size());
        int b = (int) Math.min(desde + limite + 1, filas.size());
        return recortar(filas.subList(a, b));
    }
}
