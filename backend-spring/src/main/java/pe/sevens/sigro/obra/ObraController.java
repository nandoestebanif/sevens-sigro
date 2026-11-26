package pe.sevens.sigro.obra;

import static pe.sevens.sigro.comun.Esquema.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.sevens.sigro.comun.*;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.seguridad.Acceso.Actor;
import pe.sevens.sigro.seguridad.Claves;
import pe.sevens.sigro.seguridad.Tokens;

/**
 * Módulo obra (port de app/modulos/obra/rutas.py): sesión, usuarios, asignación, proyecto, periodos, almacenes y
 * dispositivos. HU-01, HU-02, HU-03, HU-12, HU-13, HU-14 y la autorización de dispositivos (R5).
 */
@RestController
@RequestMapping("/api/v1")
public class ObraController {
    static final String[] ROLES = {"almacenero", "ssoma", "residente", "administrador", "gerente"};
    static final LocalDate FECHA_MINIMA = LocalDate.of(2000, 1, 1), FECHA_MAXIMA = LocalDate.of(2100, 12, 31);
    static final long PLAZO_MAXIMO_DIAS = 3653;

    static final Esquema LOGIN = de(texto("usuario").min(1).max(60), texto("clave").min(1).max(200));
    static final Esquema CAMBIAR_CLAVE = de(texto("clave_actual").max(200), texto("clave_nueva").max(200));
    static final Esquema CONTACTO = de(
            texto("dni").opcional().patron("^\\d{8}$"),
            texto("correo").opcional().max(120).patron("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"),
            texto("celular").opcional().patron("^9\\d{8}$"))
            .antes(ObraController::vaciosYFormato);
    static final Esquema USUARIO = CONTACTO.mas(
            texto("nombre").min(1).max(120), texto("usuario").patron("^[A-Za-z0-9._-]{3,40}$"), texto("clave").max(200));
    static final Esquema CLAVE_TEMPORAL = de(texto("clave").max(200));
    static final Esquema OBRA = de(texto("nombre").min(1).max(120),
            fecha("fecha_inicio").ge(FECHA_MINIMA).le(FECHA_MAXIMA), fecha("fecha_fin").ge(FECHA_MINIMA).le(FECHA_MAXIMA),
            entero("dia_corte").defecto(7).ge(1).le(7))
            .despues(d -> {
                if (ChronoUnit.DAYS.between(d.fecha("fecha_inicio"), d.fecha("fecha_fin")) > PLAZO_MAXIMO_DIAS)
                    throw error("el plazo de una obra no puede pasar de 10 años");
            });
    static final Esquema AMPLIAR = de(fecha("nueva_fecha_fin").ge(FECHA_MINIMA).le(FECHA_MAXIMA), texto("motivo").min(3).max(500));
    static final Esquema ALMACEN = de(texto("nombre").min(1).max(80), texto("tipo").defecto("frente").uno("central", "frente"));
    static final Esquema ASIGNACION = de(entero("usuario_id").ge(1), texto("rol").uno(ROLES));
    static final Esquema DISPOSITIVO = de(texto("identificador").patron("^[A-Za-z0-9._:-]{8,128}$"));
    static final Esquema REVOCAR = de(texto("motivo").min(3).max(300));

    static final String COLS_USUARIO = "id, nombre, usuario, estado, creado_en, fecha_baja, dni, correo, celular";
    static final String[] USUARIO_OUT = {"id", "nombre", "usuario", "estado", "creado_en", "fecha_baja", "dni", "correo", "celular"};
    static final String[] OBRA_OUT = {"id", "nombre", "fecha_inicio", "fecha_fin", "dia_corte", "estado", "semana", "semanas_totales", "rol"};
    static final String SQL_ASIG = "SELECT a.id, a.usuario_id, u.nombre AS usuario_nombre, u.usuario, a.rol, a.fecha_asignacion, a.fecha_retiro "
            + "FROM obra.asignacion a JOIN obra.usuario u ON u.id = a.usuario_id ";
    static final String[] ASIG_OUT = {"id", "usuario_id", "usuario_nombre", "usuario", "rol", "fecha_asignacion", "fecha_retiro"};
    static final String SQL_DISP = "SELECT d.id, d.usuario_id, u.nombre AS usuario_nombre, d.identificador, d.estado, d.solicitado_en, "
            + "d.fecha_autorizacion, d.fecha_revocacion, d.motivo_revocacion FROM obra.dispositivo d JOIN obra.usuario u ON u.id = d.usuario_id ";
    static final String[] DISP_OUT = {"id", "usuario_id", "usuario_nombre", "identificador", "estado", "solicitado_en", "fecha_autorizacion", "fecha_revocacion", "motivo_revocacion"};

    private final Modulo db;
    private final Acceso acceso;
    private final Claves claves;
    private final Tokens tokens;
    private final Eventos eventos;
    private final Integraciones integraciones;
    private final int intentosMax, bloqueoMinutos;

    public ObraController(Modulo obras, Acceso acceso, Claves claves, Tokens tokens, Eventos eventos, Integraciones integraciones,
                          @org.springframework.beans.factory.annotation.Value("${SIGRO_INTENTOS_MAX:5}") int intentosMax,
                          @org.springframework.beans.factory.annotation.Value("${SIGRO_BLOQUEO_MINUTOS:15}") int bloqueoMinutos) {
        this.db = obras;
        this.acceso = acceso;
        this.claves = claves;
        this.tokens = tokens;
        this.eventos = eventos;
        this.integraciones = integraciones;
        this.intentosMax = intentosMax;
        this.bloqueoMinutos = bloqueoMinutos;
    }

    static ObjectNode vaciosYFormato(ObjectNode d) {
        for (String k : List.of("dni", "correo", "celular")) {
            JsonNode v = d.get(k);
            if (v != null && v.isTextual()) {
                String s = k.equals("correo") ? v.textValue().strip().toLowerCase(Locale.ROOT) : v.textValue().strip().replace(" ", "");
                if (s.isEmpty()) d.putNull(k); else d.put(k, s);
            }
        }
        return d;
    }

    List<Map<String, Object>> obrasDe(long usuarioId) {
        return R.todos(db.filas("SELECT p.id, p.nombre, a.rol, p.estado FROM obra.asignacion a JOIN obra.proyecto p ON p.id = a.proyecto_id "
                + "WHERE a.usuario_id = :u AND a.fecha_retiro IS NULL ORDER BY (p.estado <> 'en_ejecucion'), p.nombre", P.de("u", usuarioId)),
                "id", "nombre", "rol", "estado=en_ejecucion");
    }

    // ------------------------------------------------------------------- sesión (HU-12)
    @PostMapping("/auth/login")
    public Object login(@RequestBody(required = false) JsonNode cuerpo) {
        Datos d = LOGIN.validar(cuerpo);
        Object r = db.en(() -> {
            var u = db.fila("SELECT * FROM obra.usuario WHERE lower(usuario) = lower(:n)", P.de("n", d.texto("usuario").strip()));
            Instant ahora = Instant.now();
            var generico = new ErrorSigro(401, "credenciales_invalidas", "Usuario o contraseña incorrectos.");
            if (u != null && u.get("bloqueado_hasta") != null && ((Instant) u.get("bloqueado_hasta")).isAfter(ahora)) {
                claves.verificar(d.texto("clave"), (String) u.get("hash_clave"));
                return new ErrorSigro(429, "cuenta_bloqueada", "Demasiados intentos. Espera unos minutos e inténtalo de nuevo.");
            }
            boolean ok = claves.verificar(d.texto("clave"), u != null ? (String) u.get("hash_clave") : null);
            if (u == null || !ok || !"activo".equals(u.get("estado"))) {
                if (u != null && "activo".equals(u.get("estado"))) {
                    long intentos = ((Number) u.get("intentos_fallidos")).longValue() + 1;
                    Instant bloqueo = intentos >= intentosMax ? ahora.plus(bloqueoMinutos, ChronoUnit.MINUTES) : null;
                    db.actualizar("UPDATE obra.usuario SET intentos_fallidos = :i, bloqueado_hasta = :b WHERE id = :id",
                            P.de("i", bloqueo != null ? 0 : intentos, "b", bloqueo == null ? null : java.sql.Timestamp.from(bloqueo), "id", u.get("id")));
                }
                return generico;     // el intento fallido queda registrado: la transacción se confirma
            }
            long id = (Long) u.get("id");
            if (((Number) u.get("intentos_fallidos")).longValue() != 0 || u.get("bloqueado_hasta") != null)
                db.actualizar("UPDATE obra.usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id = :id", P.de("id", id));
            return R.mapa("token", tokens.crear(id), "tipo", "bearer", "expira_en", tokens.segundos(),
                    "usuario", R.de(u, "id", "nombre", "usuario"), "obras", obrasDe(id));
        });
        if (r instanceof ErrorSigro e) throw e;
        return r;
    }

    @GetMapping("/auth/yo")
    public Object yo() {
        Actor a = acceso.autenticado();
        return db.en(() -> R.mapa("usuario", db.fila("SELECT id, nombre, usuario FROM obra.usuario WHERE id = :i", P.de("i", a.usuarioId())),
                "obras", obrasDe(a.usuarioId())));
    }

    @PostMapping("/auth/cambiar-clave")
    public ResponseEntity<Void> cambiarClave(@RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.autenticado();
        Datos d = CAMBIAR_CLAVE.validar(cuerpo);
        db.hacer(() -> {
            String hash = (String) db.escalar("SELECT hash_clave FROM obra.usuario WHERE id = :i", P.de("i", a.usuarioId()));
            if (!claves.verificar(d.texto("clave_actual"), hash)) throw new ErrorSigro(403, "clave_actual_incorrecta", "La contraseña actual no es correcta.");
            String motivo = Claves.aceptable(d.texto("clave_nueva"));
            if (motivo != null) throw new ErrorSigro(422, "clave_debil", motivo);
            db.actualizar("UPDATE obra.usuario SET hash_clave = :h WHERE id = :i", P.de("h", claves.hashear(d.texto("clave_nueva")), "i", a.usuarioId()));
        });
        return C.vacio();
    }

    // ---------------------------------------------------------------------- usuarios
    @PostMapping("/usuarios")
    public ResponseEntity<Object> crearUsuario(@RequestBody(required = false) JsonNode cuerpo) {
        acceso.exigirEnAlgunaObra("usuario.crear");
        Datos d = USUARIO.validar(cuerpo);
        String motivo = Claves.aceptable(d.texto("clave"));
        if (motivo != null) throw new ErrorSigro(422, "clave_debil", motivo);
        String hash = claves.hashear(d.texto("clave"));
        return C.creado(db.en(() -> R.de(db.fila("INSERT INTO obra.usuario (nombre, usuario, hash_clave, dni, correo, celular) "
                        + "VALUES (:n, :u, :h, :d, :c, :t) RETURNING " + COLS_USUARIO,
                P.de("n", d.texto("nombre").strip(), "u", d.texto("usuario").strip(), "h", hash, "d", d.texto("dni"), "c", d.texto("correo"), "t", d.texto("celular"))),
                USUARIO_OUT)));
    }

    @GetMapping("/usuarios")
    public Object listarUsuarios() {
        acceso.exigirEnAlgunaObra("usuario.consultar");
        return db.en(() -> R.todos(db.filas("SELECT " + COLS_USUARIO + " FROM obra.usuario ORDER BY nombre"), USUARIO_OUT));
    }

    @GetMapping("/usuarios/consulta-dni/{dni}")
    public Object consultarDni(@PathVariable String dni) {
        acceso.exigirEnAlgunaObra("usuario.crear");
        if (!dni.matches("\\d{8}")) throw new ErrorSigro(422, "dni_invalido", "El DNI tiene 8 dígitos.");
        var r = integraciones.consultarDni(dni);
        if (r == null) throw new ErrorSigro(404, "reniec_sin_respuesta", "RENIEC no respondió: escribe el nombre a mano.");
        String apellidos = String.join(" ", java.util.stream.Stream.of(r.apellidoPaterno(), r.apellidoMaterno()).filter(x -> x != null && !x.isEmpty()).toList());
        return R.mapa("dni", r.dni(), "nombres", r.nombres(), "apellido_paterno", r.apellidoPaterno(),
                "apellido_materno", r.apellidoMaterno(), "nombre_completo", r.nombres() + " " + apellidos);
    }

    @PostMapping("/usuarios/{usuario_id}/contacto")
    public Object contacto(@PathVariable("usuario_id") long usuarioId, @RequestBody(required = false) JsonNode cuerpo) {
        acceso.exigirEnAlgunaObra("usuario.crear");
        Datos d = CONTACTO.validar(cuerpo);
        if (d.enviados().isEmpty()) throw new ErrorSigro(422, "sin_cambios", "Envía al menos el DNI, el correo o el celular.");
        var r = db.en(() -> db.fila("UPDATE obra.usuario SET dni = CASE WHEN :cd THEN CAST(:d AS text) ELSE dni END, "
                        + "correo = CASE WHEN :cc THEN CAST(:c AS text) ELSE correo END, "
                        + "celular = CASE WHEN :ct THEN CAST(:t AS text) ELSE celular END WHERE id = :i RETURNING " + COLS_USUARIO,
                P.de("i", usuarioId, "d", d.texto("dni"), "c", d.texto("correo"), "t", d.texto("celular"),
                        "cd", d.envio("dni"), "cc", d.envio("correo"), "ct", d.envio("celular"))));
        if (r == null) throw new ErrorSigro(404, "no_existe", "Ese usuario no existe.");
        return R.de(r, USUARIO_OUT);
    }

    @PostMapping("/usuarios/{usuario_id}/clave-temporal")
    public Object claveTemporal(@PathVariable("usuario_id") long usuarioId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigirEnAlgunaObra("usuario.crear");
        Datos d = CLAVE_TEMPORAL.validar(cuerpo);
        if (usuarioId == a.usuarioId()) throw new ErrorSigro(409, "clave_propia", "Tu propia clave cámbiala con «Cambiar mi contraseña».");
        String motivo = Claves.aceptable(d.texto("clave"));
        if (motivo != null) throw new ErrorSigro(422, "clave_debil", motivo);
        String hash = claves.hashear(d.texto("clave"));
        var r = db.en(() -> db.fila("UPDATE obra.usuario SET hash_clave = :h, intentos_fallidos = 0, bloqueado_hasta = NULL "
                + "WHERE id = :i AND estado = 'activo' RETURNING " + COLS_USUARIO, P.de("h", hash, "i", usuarioId)));
        if (r == null) throw new ErrorSigro(404, "no_existe", "Ese usuario no existe o está de baja.");
        return R.de(r, USUARIO_OUT);
    }

    @PostMapping("/usuarios/{usuario_id}/baja")
    public Object baja(@PathVariable("usuario_id") long usuarioId) {
        Actor a = acceso.exigirEnAlgunaObra("usuario.crear");
        if (usuarioId == a.usuarioId()) throw new ErrorSigro(409, "baja_propia", "No puedes darte de baja a ti mismo.");
        return db.en(() -> {
            var r = db.fila("UPDATE obra.usuario SET estado = 'baja', fecha_baja = now() WHERE id = :i AND estado = 'activo' RETURNING " + COLS_USUARIO,
                    P.de("i", usuarioId));
            if (r == null) throw new ErrorSigro(404, "no_existe", "Ese usuario no existe o ya estaba de baja.");
            db.actualizar("UPDATE obra.asignacion SET fecha_retiro = now(), retirado_por = :a WHERE usuario_id = :u AND fecha_retiro IS NULL",
                    P.de("a", a.usuarioId(), "u", usuarioId));
            return R.de(r, USUARIO_OUT);
        });
    }

    // ------------------------------------------------------------------ proyecto (HU-01)
    Map<String, Object> obraOut(long obraId, String rol) {
        var p = new LinkedHashMap<>(db.fila("SELECT id, nombre, fecha_inicio, fecha_fin, dia_corte, estado FROM obra.proyecto WHERE id = :i", P.de("i", obraId)));
        var per = db.fila("SELECT count(*) AS total, coalesce(max(numero) FILTER (WHERE current_date BETWEEN fecha_inicio AND fecha_fin), "
                + "CASE WHEN current_date < min(fecha_inicio) THEN 0 ELSE count(*) END) AS actual FROM obra.periodo WHERE proyecto_id = :i AND estado <> 'fuera_de_plazo'", P.de("i", obraId));
        p.put("semanas_totales", per.get("total"));
        p.put("semana", per.get("actual"));
        p.put("rol", rol);
        return R.de(p, OBRA_OUT);
    }

    @GetMapping("/obras")
    public Object listarObras() {
        Actor a = acceso.autenticado();
        return db.en(() -> R.todos(db.filas("SELECT p.id, p.nombre, p.fecha_inicio, p.fecha_fin, p.dia_corte, p.estado, a.rol, "
                + "coalesce(per.total, 0) AS semanas_totales, per.actual AS semana "
                + "FROM obra.asignacion a JOIN obra.proyecto p ON p.id = a.proyecto_id "
                + "LEFT JOIN LATERAL (SELECT count(*) AS total, coalesce(max(numero) FILTER (WHERE current_date BETWEEN fecha_inicio AND fecha_fin), "
                + "CASE WHEN current_date < min(fecha_inicio) THEN 0 ELSE count(*) END) AS actual FROM obra.periodo WHERE proyecto_id = p.id AND estado <> 'fuera_de_plazo') per ON true "
                + "WHERE a.usuario_id = :u AND a.fecha_retiro IS NULL ORDER BY (p.estado <> 'en_ejecucion'), p.nombre", P.de("u", a.usuarioId())), OBRA_OUT));
    }

    @PostMapping("/obras")
    public ResponseEntity<Object> crearObra(@RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigirEnAlgunaObra("proyecto.crear");
        Datos d = OBRA.validar(cuerpo);
        return C.creado(db.en(() -> {
            long id = (Long) db.escalar("INSERT INTO obra.proyecto (nombre, fecha_inicio, fecha_fin, dia_corte, creado_por) VALUES (:n, :i, :f, :c, :u) RETURNING id",
                    P.de("n", d.texto("nombre").strip(), "i", d.fecha("fecha_inicio"), "f", d.fecha("fecha_fin"), "c", d.int32("dia_corte"), "u", a.usuarioId()));
            db.actualizar("INSERT INTO obra.asignacion (usuario_id, proyecto_id, rol, asignado_por) VALUES (:u, :p, :r, :u)",
                    P.de("u", a.usuarioId(), "p", id, "r", a.rol()));
            return obraOut(id, a.rol());
        }));
    }

    @GetMapping("/obras/{obra_id}")
    public Object verObra(@PathVariable("obra_id") long obraId) {
        Actor a = acceso.exigir("proyecto.consultar", obraId);
        return db.en(() -> obraOut(obraId, a.rol()));
    }

    @PostMapping("/obras/{obra_id}/cerrar")
    public Object cerrarObra(@PathVariable("obra_id") long obraId) {
        Actor a = acceso.exigir("proyecto.cerrar", obraId);
        return db.en(() -> {
            if (db.fila("UPDATE obra.proyecto SET estado = 'cerrado' WHERE id = :p AND estado = 'en_ejecucion' RETURNING id", P.de("p", obraId)) == null)
                throw new ErrorSigro(409, "obra_ya_cerrada", "Esta obra ya estaba cerrada.");
            return obraOut(obraId, a.rol());
        });
    }

    @PostMapping("/obras/{obra_id}/ampliar-plazo")
    public Object ampliarPlazo(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("proyecto.ampliar_plazo", obraId);
        Datos d = AMPLIAR.validar(cuerpo);
        return db.en(() -> {
            LocalDate inicio = (LocalDate) db.escalar("SELECT fecha_inicio FROM obra.proyecto WHERE id = :p", P.de("p", obraId));
            if (inicio != null && ChronoUnit.DAYS.between(inicio, d.fecha("nueva_fecha_fin")) > PLAZO_MAXIMO_DIAS)
                throw new ErrorSigro(422, "plazo_excesivo", "El plazo de una obra no puede pasar de 10 años.");
            db.fila("SELECT obra.ampliar_plazo(:p, :f, :m, :a)", P.de("p", obraId, "f", d.fecha("nueva_fecha_fin"), "m", d.texto("motivo").strip(), "a", a.usuarioId()));
            return obraOut(obraId, a.rol());
        });
    }

    /** Recortar el plazo (26/11): solo el administrador y con PIN; hasta el fin de una semana, nunca a una fecha pasada (migración 0026). */
    @PostMapping("/obras/{obra_id}/recortar-plazo")
    public Object recortarPlazo(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("proyecto.recortar_plazo", obraId);
        Datos d = AMPLIAR.validar(cuerpo);
        return db.en(() -> {
            db.fila("SELECT obra.recortar_plazo(:p, :f, :m, :a)", P.de("p", obraId, "f", d.fecha("nueva_fecha_fin"), "m", d.texto("motivo").strip(), "a", a.usuarioId()));
            return obraOut(obraId, a.rol());
        });
    }

    @GetMapping("/obras/{obra_id}/plazo-historial")
    public Object historialPlazo(@PathVariable("obra_id") long obraId) {
        acceso.exigir("proyecto.consultar", obraId);
        return db.en(() -> R.todos(db.filas("SELECT h.id, h.fecha_fin_anterior, h.fecha_fin_nueva, h.motivo, u.nombre AS autor, h.fecha "
                + "FROM obra.proyecto_plazo_historial h JOIN obra.usuario u ON u.id = h.autor WHERE h.proyecto_id = :p ORDER BY h.fecha DESC, h.id DESC",
                P.de("p", obraId)), "id", "fecha_fin_anterior", "fecha_fin_nueva", "motivo", "autor", "fecha"));
    }

    @GetMapping("/obras/{obra_id}/periodos")
    public Object periodos(@PathVariable("obra_id") long obraId) {
        acceso.exigir("periodo.consultar", obraId);
        return db.en(() -> db.filas("SELECT id, numero, fecha_inicio, fecha_fin, estado FROM obra.periodo WHERE proyecto_id = :p ORDER BY numero", P.de("p", obraId)));
    }

    @PostMapping("/obras/{obra_id}/periodos/{numero}/cerrar")
    public Object cerrarPeriodo(@PathVariable("obra_id") long obraId, @PathVariable long numero) {
        Actor a = acceso.exigir("periodo.cerrar", obraId);
        var p = db.en(() -> db.fila("UPDATE obra.periodo SET estado = 'cerrado', cerrado_en = coalesce(cerrado_en, now()), "
                + "cerrado_por = coalesce(cerrado_por, :u) WHERE proyecto_id = :p AND numero = :n AND estado <> 'fuera_de_plazo' RETURNING id, numero, fecha_inicio, fecha_fin, estado",
                P.de("u", a.usuarioId(), "p", obraId, "n", numero)));
        if (p == null) throw new ErrorSigro(404, "no_existe", "La obra no tiene la semana " + numero + ".");
        return p;
    }

    // ---------------------------------------------------------------------- almacenes
    @GetMapping("/obras/{obra_id}/almacenes")
    public Object almacenes(@PathVariable("obra_id") long obraId) {
        acceso.exigir("almacen.consultar", obraId);
        return db.en(() -> db.filas("SELECT id, nombre, tipo, estado FROM obra.almacen WHERE proyecto_id = :p ORDER BY nombre", P.de("p", obraId)));
    }

    @PostMapping("/obras/{obra_id}/almacenes")
    public ResponseEntity<Object> crearAlmacen(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        acceso.exigir("almacen.registrar", obraId);
        Datos d = ALMACEN.validar(cuerpo);
        return C.creado(db.en(() -> db.fila("INSERT INTO obra.almacen (proyecto_id, nombre, tipo) VALUES (:p, :n, :t) RETURNING id, nombre, tipo, estado",
                P.de("p", obraId, "n", d.texto("nombre").strip(), "t", d.texto("tipo")))));
    }

    // ---------------------------------------------------------- asignación (HU-13 · HU-14)
    @GetMapping("/obras/{obra_id}/asignaciones")
    public Object asignaciones(@PathVariable("obra_id") long obraId) {
        acceso.exigir("asignacion.consultar", obraId);
        Boolean v = Peticion.bool("vigentes");
        boolean vigentes = v == null || v;
        return db.en(() -> R.todos(db.filas(SQL_ASIG + "WHERE a.proyecto_id = :p AND (NOT :v OR a.fecha_retiro IS NULL) ORDER BY u.nombre, a.id",
                P.de("p", obraId, "v", vigentes)), ASIG_OUT));
    }

    @PostMapping("/obras/{obra_id}/asignaciones")
    public ResponseEntity<Object> asignar(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("asignacion.escribir", obraId);
        Datos d = ASIGNACION.validar(cuerpo);
        return C.creado(db.en(() -> {
            var u = db.fila("SELECT id, estado FROM obra.usuario WHERE id = :i", P.de("i", d.entero("usuario_id")));
            if (u == null) throw new ErrorSigro(404, "usuario_no_existe", "Ese usuario no existe.");
            if (!"activo".equals(u.get("estado"))) throw new ErrorSigro(409, "usuario_de_baja", "Ese usuario está de baja.");
            Object id = db.escalar("INSERT INTO obra.asignacion (usuario_id, proyecto_id, rol, asignado_por) VALUES (:u, :p, :r, :a) RETURNING id",
                    P.de("u", d.entero("usuario_id"), "p", obraId, "r", d.texto("rol"), "a", a.usuarioId()));
            return R.de(db.fila(SQL_ASIG + "WHERE a.id = :i", P.de("i", id)), ASIG_OUT);
        }));
    }

    @PostMapping("/obras/{obra_id}/asignaciones/{asignacion_id}/retiro")
    public Object retirar(@PathVariable("obra_id") long obraId, @PathVariable("asignacion_id") long asignacionId) {
        Actor a = acceso.exigir("asignacion.escribir", obraId);
        return db.en(() -> {
            var x = db.fila("SELECT * FROM obra.asignacion WHERE id = :i AND proyecto_id = :p", P.de("i", asignacionId, "p", obraId));
            if (x == null) throw new ErrorSigro(404, "no_existe", "Esa asignación no existe en esta obra.");
            if (x.get("fecha_retiro") != null) throw new ErrorSigro(409, "ya_retirada", "Esa asignación ya fue retirada.");
            if ("administrador".equals(x.get("rol"))) {
                long otros = (Long) db.escalar("SELECT count(*) FROM obra.asignacion WHERE proyecto_id = :p AND rol = 'administrador' AND fecha_retiro IS NULL AND id <> :i",
                        P.de("p", obraId, "i", asignacionId));
                if (otros == 0) throw new ErrorSigro(409, "ultimo_administrador", "La obra no puede quedarse sin administrador.");
            }
            db.actualizar("UPDATE obra.asignacion SET fecha_retiro = now(), retirado_por = :a WHERE id = :i", P.de("a", a.usuarioId(), "i", asignacionId));
            return R.de(db.fila(SQL_ASIG + "WHERE a.id = :i", P.de("i", asignacionId)), ASIG_OUT);
        });
    }

    // ------------------------------------------------------------------- dispositivos (R5)
    @GetMapping("/obras/{obra_id}/dispositivos")
    public Object dispositivos(@PathVariable("obra_id") long obraId) {
        acceso.exigir("dispositivo.consultar", obraId);
        return db.en(() -> R.todos(db.filas(SQL_DISP + "WHERE d.proyecto_id = :p ORDER BY d.id", P.de("p", obraId)), DISP_OUT));
    }

    @GetMapping("/obras/{obra_id}/dispositivos/mios")
    public Object misDispositivos(@PathVariable("obra_id") long obraId) {
        Actor a = acceso.exigir("dispositivo.solicitar", obraId);
        return db.en(() -> R.todos(db.filas(SQL_DISP + "WHERE d.proyecto_id = :p AND d.usuario_id = :u ORDER BY d.id", P.de("p", obraId, "u", a.usuarioId())), DISP_OUT));
    }

    @PostMapping("/obras/{obra_id}/dispositivos")
    public ResponseEntity<Object> solicitarDispositivo(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("dispositivo.solicitar", obraId);
        Datos d = DISPOSITIVO.validar(cuerpo);
        var dest = eventos.destinatariosConRol(obraId, List.of("administrador"), a.usuarioId());
        String obra = eventos.nombreObra(obraId);
        return C.creado(db.en(() -> {
            Long id = (Long) db.escalar("INSERT INTO obra.dispositivo (proyecto_id, usuario_id, identificador) VALUES (:p, :u, :i) RETURNING id",
                    P.de("p", obraId, "u", a.usuarioId(), "i", d.texto("identificador").strip()));
            eventos.publicar(db, "dispositivo.solicitado", obraId, dest, "Dispositivo por autorizar",
                    a.nombre() + " pidió autorizar su celular en " + obra + ".", R.mapa("dispositivo_id", id, "usuario_id", a.usuarioId()));
            return R.de(db.fila(SQL_DISP + "WHERE d.id = :i", P.de("i", id)), DISP_OUT);
        }));
    }

    Map<String, Object> dispositivoDeLaObra(long obraId, long id) {
        var d = db.fila("SELECT * FROM obra.dispositivo WHERE id = :i AND proyecto_id = :p", P.de("i", id, "p", obraId));
        if (d == null) throw new ErrorSigro(404, "no_existe", "Ese dispositivo no existe en esta obra.");
        return d;
    }

    @PostMapping("/obras/{obra_id}/dispositivos/{dispositivo_id}/autorizar")
    public Object autorizar(@PathVariable("obra_id") long obraId, @PathVariable("dispositivo_id") long id) {
        Actor a = acceso.exigir("dispositivo.autorizar", obraId);
        String obra = eventos.nombreObra(obraId);
        return db.en(() -> {
            var d = dispositivoDeLaObra(obraId, id);
            if (!"pendiente".equals(d.get("estado")))
                throw new ErrorSigro(409, "estado_invalido", "Solo se autoriza un dispositivo pendiente (este está " + d.get("estado") + ").");
            db.actualizar("UPDATE obra.dispositivo SET estado = 'activo', autorizado_por = :a, fecha_autorizacion = now() WHERE id = :i", P.de("a", a.usuarioId(), "i", id));
            eventos.publicar(db, "dispositivo.autorizado", obraId, List.of((Long) d.get("usuario_id")), "Tu dispositivo fue autorizado",
                    "Ya puedes registrar desde tu celular en " + obra + ".", R.mapa("dispositivo_id", id));
            return R.de(db.fila(SQL_DISP + "WHERE d.id = :i", P.de("i", id)), DISP_OUT);
        });
    }

    @PostMapping("/obras/{obra_id}/dispositivos/{dispositivo_id}/revocar")
    public Object revocar(@PathVariable("obra_id") long obraId, @PathVariable("dispositivo_id") long id, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("dispositivo.autorizar", obraId);
        Datos datos = REVOCAR.validar(cuerpo);
        String motivo = datos.texto("motivo").strip();
        return db.en(() -> {
            var d = dispositivoDeLaObra(obraId, id);
            if ("revocado".equals(d.get("estado"))) throw new ErrorSigro(409, "estado_invalido", "Ese dispositivo ya estaba revocado.");
            db.actualizar("UPDATE obra.dispositivo SET estado = 'revocado', revocado_por = :a, fecha_revocacion = now(), motivo_revocacion = :m WHERE id = :i",
                    P.de("a", a.usuarioId(), "m", motivo, "i", id));
            eventos.publicar(db, "dispositivo.revocado", obraId, List.of((Long) d.get("usuario_id")), "Tu dispositivo fue revocado",
                    "Motivo: " + motivo, R.mapa("dispositivo_id", id));
            return R.de(db.fila(SQL_DISP + "WHERE d.id = :i", P.de("i", id)), DISP_OUT);
        });
    }

    // ------------------------------------------------------------------- notificaciones
    @GetMapping("/notificaciones")
    public Object notificaciones() {
        Actor a = acceso.autenticado();
        Boolean solo = Peticion.bool("solo_no_leidas");
        Long limite = Peticion.entero("limite", 1L, 200L);
        var r = integraciones.notificaciones("GET", "/notificaciones", R.mapa("usuario_id", a.usuarioId(),
                "solo_no_leidas", Boolean.TRUE.equals(solo) ? "true" : "false", "limite", limite == null ? 50 : limite));
        if (r.estado() != 200) throw new ErrorSigro(503, "notificaciones_no_disponible", "El servicio de notificaciones respondió con un error.");
        return Json.leer(r.cuerpo());
    }

    @PostMapping("/notificaciones/{notificacion_id}/leida")
    public ResponseEntity<Void> leida(@PathVariable("notificacion_id") long id) {
        Actor a = acceso.autenticado();
        var r = integraciones.notificaciones("POST", "/notificaciones/" + id + "/leida", R.mapa("usuario_id", a.usuarioId()));
        if (r.estado() == 404) throw new ErrorSigro(404, "no_existe", "Esa notificación no existe.");
        if (r.estado() != 204) throw new ErrorSigro(503, "notificaciones_no_disponible", "El servicio de notificaciones respondió con un error.");
        return C.vacio();
    }
}
