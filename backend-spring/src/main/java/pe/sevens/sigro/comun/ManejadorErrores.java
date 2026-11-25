package pe.sevens.sigro.comun;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Errores con la forma que esperan la web y la app: {codigo, mensaje}. El motor de base de datos sostiene las reglas;
 * aquí solo se traduce su rechazo a algo que una persona entiende (port de app/errores.py).
 */
@RestControllerAdvice
public class ManejadorErrores {
    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    record Estado(int http, String codigo) {}

    static final Map<String, Estado> SQLSTATE = Map.ofEntries(
            Map.entry("23505", new Estado(409, "duplicado")),
            Map.entry("23503", new Estado(422, "referencia_invalida")),
            Map.entry("23514", new Estado(422, "regla_incumplida")),
            Map.entry("23001", new Estado(409, "operacion_no_permitida")),
            Map.entry("23P01", new Estado(409, "conflicto")),
            Map.entry("23502", new Estado(422, "dato_obligatorio")),
            Map.entry("22P02", new Estado(422, "formato_invalido")),
            Map.entry("22003", new Estado(422, "valor_fuera_de_rango")),
            Map.entry("22007", new Estado(422, "fecha_invalida")),
            Map.entry("22008", new Estado(422, "fecha_invalida")),
            Map.entry("22021", new Estado(422, "texto_invalido")),
            Map.entry("22P05", new Estado(422, "texto_invalido")));

    static final Map<String, String> AMIGABLES = Map.ofEntries(
            Map.entry("uq_dispositivo_activo_por_usuario", "Esa persona ya tiene un dispositivo autorizado en esta obra; revócalo antes de autorizar otro."),
            Map.entry("uq_dispositivo_activo_identificador", "Ese dispositivo ya está autorizado en esta obra."),
            Map.entry("uq_usuario_usuario", "Ese nombre de usuario ya existe."),
            Map.entry("uq_usuario_dni", "Ya hay un usuario con ese DNI."),
            Map.entry("uq_usuario_correo", "Ya hay un usuario con ese correo."),
            Map.entry("uq_proyecto_nombre", "Ya existe una obra con ese nombre."),
            Map.entry("uq_almacen_proyecto_nombre", "Ya existe un almacén con ese nombre en esta obra."),
            Map.entry("uq_asignacion_vigente", "Esa persona ya tiene una asignación vigente en esta obra; retírala antes de cambiar su rol."),
            Map.entry("uq_trabajador_proyecto_id_documento", "Ya hay un trabajador con ese documento en esta obra."),
            Map.entry("uq_jornada_original", "Ya existe una jornada de ese trabajador en esa fecha; corrígela en vez de registrar otra."),
            Map.entry("uq_jornada_correccion_unica", "Esa jornada ya fue corregida; corrige la versión vigente."),
            Map.entry("uq_proveedor_ruc", "Ya existe un proveedor con ese RUC."),
            Map.entry("uq_compra_proveedor_id_tipo_comprobante_numero_comprobante", "Ese comprobante ya está registrado para este proveedor."),
            Map.entry("uq_periodo_proyecto_id_numero", "Ese periodo ya existe."),
            Map.entry("ex_periodo_sin_solape", "Los periodos de una obra no pueden solaparse."),
            Map.entry("ck_usuario_usuario", "El usuario solo admite letras, números, punto, guion y guion bajo (3 a 40 caracteres)."),
            Map.entry("ck_proyecto_plazo", "La fecha de fin no puede ser anterior a la de inicio."),
            Map.entry("ck_jornada_horas_segun_estado", "Un día presente necesita horas; un día ausente o con permiso no lleva horas."),
            Map.entry("ck_jornada_horas_rango", "Las horas del día no pueden pasar de 24."),
            Map.entry("ck_trabajador_documento", "El documento no tiene un formato válido (el DNI son 8 dígitos)."),
            Map.entry("ck_trabajador_salida", "La fecha de salida no puede ser anterior a la de ingreso."),
            Map.entry("ck_compra_numero_comprobante", "El comprobante debe ser serie-número, por ejemplo F001-4512."),
            Map.entry("ck_compra_detalle_cantidad", "La cantidad de cada línea debe ser mayor que cero."),
            Map.entry("ck_proveedor_validado_ruc_correcto", "Solo se valida un RUC con dígito verificador correcto."),
            Map.entry("fk_compra_solicitud_id_solicitud", "Esa solicitud no pertenece a esta obra."),
            Map.entry("fk_compra_proveedor_id_proveedor", "Ese proveedor no existe."));

    static final Map<String, String> GENERICO = Map.of(
            "23505", "Ya existe un registro con esos datos.",
            "23503", "Un dato hace referencia a algo que no existe en esta obra.",
            "23514", "Un dato no cumple una regla del sistema.",
            "23001", "Esa operación no está permitida: nada se borra ni se edita.",
            "23P01", "Ese registro choca con otro existente.",
            "23502", "Falta un dato obligatorio.");

    private static final Pattern SIGRO = Pattern.compile("^SIGRO[-\\w]*:\\s*(.*)");

    public static ResponseEntity<Map<String, Object>> cuerpo(int estado, String codigo, String mensaje) {
        return ResponseEntity.status(estado).body(P.mapa("codigo", codigo, "mensaje", mensaje));
    }

    /** Traduce un rechazo del motor (port de errores.traducir). */
    public static ErrorSigro traducir(Throwable ex) {
        PSQLException pg = null;
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof PSQLException p) { pg = p; break; }
            if (t instanceof SQLException s && s.getNextException() instanceof PSQLException p) { pg = p; break; }
        }
        String texto = pg != null ? String.valueOf(pg.getMessage()) : String.valueOf(ex.getMessage());
        if (texto.contains("0x00") || texto.contains("\\u0000"))
            return new ErrorSigro(422, "texto_invalido", "Un texto trae un carácter no permitido (NUL). Corrígelo y vuelve a enviar.");
        if (pg == null) return new ErrorSigro(500, "error_interno", "No se pudo completar la operación.");
        String sqlstate = pg.getSQLState();
        Estado e = SQLSTATE.get(sqlstate);
        if (e == null) return new ErrorSigro(500, "error_interno", "No se pudo completar la operación.");
        ServerErrorMessage sm = pg.getServerErrorMessage();
        String restriccion = sm != null ? sm.getConstraint() : null;
        String primario = sm != null && sm.getMessage() != null ? sm.getMessage() : texto;
        String mensaje;
        Matcher m = SIGRO.matcher(primario);
        if (m.find()) mensaje = m.group(1);
        else if (restriccion != null && AMIGABLES.containsKey(restriccion)) mensaje = AMIGABLES.get(restriccion);
        else mensaje = GENERICO.getOrDefault(sqlstate, "El dato recibido no es válido.");
        return new ErrorSigro(e.http(), restriccion != null ? restriccion : e.codigo(), mensaje);
    }

    @ExceptionHandler(ErrorSigro.class)
    public ResponseEntity<Map<String, Object>> sigro(ErrorSigro e) {
        var r = ResponseEntity.status(e.estado());
        e.cabeceras().forEach(r::header);
        return r.body(P.mapa("codigo", e.codigo(), "mensaje", e.getMessage()));
    }

    @ExceptionHandler(Esquema.Invalido.class)
    public ResponseEntity<Map<String, Object>> invalido(Esquema.Invalido e) {
        return sigro(e.comoError());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> base(DataAccessException e) {
        ErrorSigro t = traducir(e);
        if (t.estado() == 500) log.error("Error de base no previsto", e);
        return sigro(t);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> ilegible(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof MismatchedInputException || e.getMessage() != null && e.getMessage().startsWith("Required request body"))
            return cuerpo(422, "datos_invalidos", "Field required");
        return cuerpo(422, "datos_invalidos", "JSON decode error");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> tipo(MethodArgumentTypeMismatchException e) {
        return cuerpo(422, "datos_invalidos", e.getName() + ": Input should be a valid integer, unable to parse string as an integer");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> falta(MissingServletRequestParameterException e) {
        return cuerpo(422, "datos_invalidos", e.getParameterName() + ": Field required");
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> noExiste(Exception e) {
        return cuerpo(404, "no_existe", "Esa ruta no existe.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> metodo(HttpRequestMethodNotSupportedException e) {
        return cuerpo(405, "http_405", "Method Not Allowed");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> medio(HttpMediaTypeNotSupportedException e) {
        return cuerpo(422, "datos_invalidos", "Input should be a valid dictionary or object to extract fields from");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> otro(Exception e) {
        log.error("Error no previsto", e);
        return cuerpo(HttpStatus.INTERNAL_SERVER_ERROR.value(), "error_interno", "No se pudo completar la operación.");
    }
}
