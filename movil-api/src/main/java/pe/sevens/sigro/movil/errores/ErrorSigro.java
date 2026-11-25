package pe.sevens.sigro.movil.errores;

/** Error previsto, con el contrato que la app ya entiende: estado HTTP + {codigo, mensaje}. */
public class ErrorSigro extends RuntimeException {
    private final int estado;
    private final String codigo;

    public ErrorSigro(int estado, String codigo, String mensaje) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
    }

    public int estado() { return estado; }
    public String codigo() { return codigo; }
}
