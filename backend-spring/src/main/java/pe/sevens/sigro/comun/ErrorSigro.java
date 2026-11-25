package pe.sevens.sigro.comun;

import java.util.LinkedHashMap;
import java.util.Map;

/** Error previsto con el contrato que esperan la web y la app: estado HTTP + {codigo, mensaje}. */
public class ErrorSigro extends RuntimeException {
    private final int estado;
    private final String codigo;
    private final Map<String, String> cabeceras = new LinkedHashMap<>();

    public ErrorSigro(int estado, String codigo, String mensaje) {
        super(mensaje, null, false, false);
        this.estado = estado;
        this.codigo = codigo;
    }

    public int estado() { return estado; }
    public String codigo() { return codigo; }
    public Map<String, String> cabeceras() { return cabeceras; }
    public ErrorSigro cabecera(String n, String v) { cabeceras.put(n, v); return this; }

    public static ErrorSigro noEncontrado(String codigo, String mensaje) { return new ErrorSigro(404, codigo, mensaje); }
    public static ErrorSigro conflicto(String codigo, String mensaje) { return new ErrorSigro(409, codigo, mensaje); }
    public static ErrorSigro invalido(String codigo, String mensaje) { return new ErrorSigro(422, codigo, mensaje); }
}
