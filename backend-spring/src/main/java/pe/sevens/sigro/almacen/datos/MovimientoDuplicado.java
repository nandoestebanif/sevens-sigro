package pe.sevens.sigro.almacen.datos;

/** El uuid_cliente ya está en el libro: es un reenvío del celular (regla 3), no un error. Sin traza: es control de flujo. */
public final class MovimientoDuplicado extends RuntimeException {
    public MovimientoDuplicado() { super(null, null, false, false); }
}
