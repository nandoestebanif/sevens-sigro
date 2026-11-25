package pe.sevens.sigro.tareo;

import static pe.sevens.sigro.comun.Esquema.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.sevens.sigro.comun.*;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.seguridad.Acceso.Actor;

/**
 * Tareo (port de app/modulos/tareo/rutas.py): trabajadores y jornadas (HU-62, HU-64). La jornada es un libro (R1):
 * corregir inserta otra que apunta a la anterior. La categoría y el periodo los decide la base, no el cliente.
 */
@RestController
@RequestMapping("/api/v1/obras/{obra_id}")
public class TareoController {
    static final String[] CATEGORIAS = {"operario", "oficial", "peon"};
    static final String[] ESTADOS = {"presente", "ausente", "permiso"};
    static final String COLS_TRAB = "id, nombre, nombres, apellido_paterno, apellido_materno, tipo_documento, documento, categoria, cuadrilla, fecha_ingreso, fecha_salida";
    static final String[] TRAB_OUT = COLS_TRAB.split(", ");
    static final String COLS_JOR = "id, uuid_cliente, trabajador_id, fecha, estado, horas_normales, horas_extra, categoria, periodo_id, registrado_por, fecha_servidor, corrige_a_id, motivo_correccion";
    static final String[] JOR_OUT = COLS_JOR.split(", ");

    static String limpio(String t) {
        if (t == null || t.isEmpty()) return null;
        String r = String.join(" ", t.strip().split("\\s+"));
        return r.isEmpty() ? null : r;
    }

    static String nombreDe(String nombres, String pat, String mat) {
        var ap = new ArrayList<String>();
        if (pat != null) ap.add(pat);
        if (mat != null) ap.add(mat);
        return String.join(" ", ap) + ", " + nombres;
    }

    static final Esquema TRABAJADOR = de(texto("nombres").opcional().min(2).max(80), texto("apellido_paterno").opcional().min(1).max(60),
            texto("apellido_materno").opcional().max(60), texto("nombre").opcional().min(3).max(120),
            texto("tipo_documento").defecto("DNI").uno("DNI", "CE", "PASAPORTE"), texto("documento").min(6).max(20),
            texto("categoria").uno(CATEGORIAS), texto("cuadrilla").min(1).max(60), fecha("fecha_ingreso"))
            .despues(d -> {
                for (String k : List.of("nombres", "apellido_paterno", "apellido_materno")) d.poner(k, limpio(d.texto(k)));
                if (d.texto("nombres") != null || d.texto("apellido_paterno") != null) {
                    if (d.texto("nombres") == null || d.texto("apellido_paterno") == null) throw error("Escribe los nombres y al menos el apellido paterno.");
                    d.poner("nombre", nombreDe(d.texto("nombres"), d.texto("apellido_paterno"), d.texto("apellido_materno")));
                } else if (d.texto("nombre") == null || d.texto("nombre").isEmpty()) throw error("Escribe los nombres y apellidos del trabajador.");
            });
    static final Esquema EDITAR = de(texto("nombres").opcional().min(2).max(80), texto("apellido_paterno").opcional().min(1).max(60),
            texto("apellido_materno").opcional().max(60), texto("categoria").opcional().uno(CATEGORIAS), texto("cuadrilla").opcional().min(1).max(60))
            .despues(d -> {
                for (String k : List.of("nombres", "apellido_paterno", "apellido_materno")) d.poner(k, limpio(d.texto(k)));
                if ((d.texto("nombres") == null) != (d.texto("apellido_paterno") == null))
                    throw error("Para corregir el nombre envía los nombres y al menos el apellido paterno.");
                if (d.texto("nombres") == null && d.texto("categoria") == null && (d.texto("cuadrilla") == null || d.texto("cuadrilla").isEmpty()))
                    throw error("Envía al menos el nombre, la categoría o la cuadrilla.");
            });
    static final Esquema BAJA = de(fecha("fecha_salida"));
    static Campo horas(String n) { return decimal(n).defecto(java.math.BigDecimal.ZERO).ge(0).le(24).digitos(4, 2); }
    static final Esquema JORNADA = de(entero("trabajador_id").ge(1), fecha("fecha"), texto("estado").uno(ESTADOS),
            horas("horas_normales"), horas("horas_extra"), uuid("uuid_cliente").opcional());
    static final Esquema CORRECCION = de(texto("estado").uno(ESTADOS), horas("horas_normales"), horas("horas_extra"), texto("motivo").min(3).max(300));

    private final Modulo db, obras;
    private final Acceso acceso;
    private final Integraciones integraciones;

    public TareoController(Modulo tareo, Modulo obras, Acceso acceso, Integraciones integraciones) {
        this.db = tareo;
        this.obras = obras;
        this.acceso = acceso;
        this.integraciones = integraciones;
    }

    @GetMapping("/trabajadores/consulta-dni/{dni}")
    public Object consultarDni(@PathVariable("obra_id") long obraId, @PathVariable String dni) {
        acceso.exigir("trabajador.registrar", obraId);
        if (!dni.matches("\\d{8}")) throw new ErrorSigro(422, "dni_invalido", "El DNI tiene 8 dígitos.");
        var r = integraciones.consultarDni(dni);
        if (r == null) throw new ErrorSigro(404, "reniec_sin_respuesta", "RENIEC no respondió: escribe los nombres y apellidos a mano.");
        return R.mapa("dni", r.dni(), "nombres", r.nombres(), "apellido_paterno", r.apellidoPaterno(), "apellido_materno", r.apellidoMaterno());
    }

    @PostMapping("/trabajadores")
    public ResponseEntity<Object> registrar(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("trabajador.registrar", obraId);
        Datos d = TRABAJADOR.validar(cuerpo);
        return C.creado(db.en(() -> R.de(db.fila("INSERT INTO tareo.trabajador (proyecto_id, nombre, nombres, apellido_paterno, apellido_materno, tipo_documento, "
                        + "documento, categoria, cuadrilla, fecha_ingreso, registrado_por) VALUES (:p, :n, :no, :ap, :am, :td, :d, :c, :q, :fi, :u) RETURNING " + COLS_TRAB,
                P.de("p", obraId, "n", d.texto("nombre"), "no", d.texto("nombres"), "ap", d.texto("apellido_paterno"), "am", d.texto("apellido_materno"),
                        "td", d.texto("tipo_documento"), "d", d.texto("documento"), "c", d.texto("categoria"), "q", d.texto("cuadrilla"),
                        "fi", d.fecha("fecha_ingreso"), "u", a.usuarioId())), TRAB_OUT)));
    }

    @GetMapping("/trabajadores")
    public Object listar(@PathVariable("obra_id") long obraId) {
        acceso.exigir("trabajador.consultar", obraId);
        Boolean s = Peticion.bool("solo_activos");
        Pagina pagina = Pagina.leer();
        return R.todos(pagina.recortar(db.en(() -> db.filas("SELECT " + COLS_TRAB + " FROM tareo.trabajador WHERE proyecto_id = :p "
                + "AND (NOT :s OR fecha_salida IS NULL OR fecha_salida >= current_date) ORDER BY cuadrilla, nombre, id" + pagina.sql(),
                P.de("p", obraId, "s", Boolean.TRUE.equals(s))))), TRAB_OUT);
    }

    @PostMapping("/trabajadores/{trabajador_id}/datos")
    public Object corregir(@PathVariable("obra_id") long obraId, @PathVariable("trabajador_id") long id, @RequestBody(required = false) JsonNode cuerpo) {
        acceso.exigir("trabajador.registrar", obraId);
        Datos d = EDITAR.validar(cuerpo);
        String nombre = d.texto("nombres") == null ? null : nombreDe(d.texto("nombres"), d.texto("apellido_paterno"), d.texto("apellido_materno"));
        var r = db.en(() -> db.fila("UPDATE tareo.trabajador SET nombre = coalesce(CAST(:n AS text), nombre), "
                + "nombres = CASE WHEN :cn THEN CAST(:no AS text) ELSE nombres END, "
                + "apellido_paterno = CASE WHEN :cn THEN CAST(:ap AS text) ELSE apellido_paterno END, "
                + "apellido_materno = CASE WHEN :cn THEN CAST(:am AS text) ELSE apellido_materno END, "
                + "categoria = coalesce(CAST(:c AS text), categoria), cuadrilla = coalesce(CAST(:q AS text), cuadrilla) "
                + "WHERE id = :i AND proyecto_id = :p RETURNING " + COLS_TRAB,
                P.de("n", nombre, "cn", d.texto("nombres") != null, "no", d.texto("nombres"), "ap", d.texto("apellido_paterno"), "am", d.texto("apellido_materno"),
                        "c", d.texto("categoria"), "q", d.texto("cuadrilla"), "i", id, "p", obraId)));
        if (r == null) throw new ErrorSigro(404, "no_existe", "Ese trabajador no existe en esta obra.");
        return R.de(r, TRAB_OUT);
    }

    @PostMapping("/trabajadores/{trabajador_id}/baja")
    public Object baja(@PathVariable("obra_id") long obraId, @PathVariable("trabajador_id") long id, @RequestBody(required = false) JsonNode cuerpo) {
        acceso.exigir("trabajador.registrar", obraId);
        Datos d = BAJA.validar(cuerpo);
        return db.en(() -> {
            if (db.fila("SELECT 1 AS x FROM tareo.trabajador WHERE id = :i AND proyecto_id = :p", P.de("i", id, "p", obraId)) == null)
                throw new ErrorSigro(404, "no_existe", "Ese trabajador no existe en esta obra.");
            return R.de(db.fila("UPDATE tareo.trabajador SET fecha_salida = :f WHERE id = :i AND proyecto_id = :p RETURNING " + COLS_TRAB,
                    P.de("f", d.fecha("fecha_salida"), "i", id, "p", obraId)), TRAB_OUT);
        });
    }

    @PostMapping("/jornadas")
    public ResponseEntity<Object> jornada(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("jornada.registrar", obraId);
        Datos d = JORNADA.validar(cuerpo);
        UUID uu = (UUID) d.get("uuid_cliente");
        return db.en(() -> {
            if (uu != null) {
                var previa = db.fila("SELECT " + COLS_JOR + ", proyecto_id FROM tareo.jornada WHERE uuid_cliente = :u", P.de("u", uu));
                if (previa != null) {
                    if (!Objects.equals(previa.get("proyecto_id"), obraId) || !Objects.equals(previa.get("trabajador_id"), d.entero("trabajador_id"))
                            || !Objects.equals(previa.get("fecha"), d.fecha("fecha")))
                        throw new ErrorSigro(409, "uuid_reutilizado", "Ese identificador ya se usó para otra jornada.");
                    return C.con(200, (Object) R.de(previa, JOR_OUT));
                }
            }
            return C.creado((Object) R.de(db.fila("INSERT INTO tareo.jornada (uuid_cliente, proyecto_id, trabajador_id, fecha, estado, horas_normales, horas_extra, "
                            + "registrado_por, categoria, periodo_id) VALUES (:uu, :p, :t, :f, :es, :hn, :he, :u, 'peon', 0) RETURNING " + COLS_JOR,
                    P.de("uu", uu, "p", obraId, "t", d.entero("trabajador_id"), "f", d.fecha("fecha"), "es", d.texto("estado"),
                            "hn", d.decimal("horas_normales"), "he", d.decimal("horas_extra"), "u", a.usuarioId())), JOR_OUT));
        });
    }

    @PostMapping("/jornadas/{jornada_id}/correccion")
    public ResponseEntity<Object> correccion(@PathVariable("obra_id") long obraId, @PathVariable("jornada_id") long id, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("jornada.registrar", obraId);
        Datos d = CORRECCION.validar(cuerpo);
        return C.creado(db.en(() -> {
            var orig = db.fila("SELECT id, trabajador_id, fecha FROM tareo.jornada WHERE id = :i AND proyecto_id = :p", P.de("i", id, "p", obraId));
            if (orig == null) throw new ErrorSigro(404, "no_existe", "Esa jornada no existe en esta obra.");
            if (db.fila("SELECT 1 AS x FROM tareo.jornada WHERE corrige_a_id = :i", P.de("i", id)) != null)
                throw new ErrorSigro(409, "ya_corregida", "Esa jornada ya fue corregida; corrige la versión vigente.");
            return R.de(db.fila("INSERT INTO tareo.jornada (proyecto_id, trabajador_id, fecha, estado, horas_normales, horas_extra, "
                            + "registrado_por, categoria, periodo_id, corrige_a_id, motivo_correccion) VALUES (:p, :t, :f, :es, :hn, :he, :u, 'peon', 0, :c, :m) RETURNING " + COLS_JOR,
                    P.de("p", obraId, "t", orig.get("trabajador_id"), "f", orig.get("fecha"), "es", d.texto("estado"), "hn", d.decimal("horas_normales"),
                            "he", d.decimal("horas_extra"), "u", a.usuarioId(), "c", id, "m", d.texto("motivo"))), JOR_OUT);
        }));
    }

    @GetMapping("/jornadas")
    public Object jornadas(@PathVariable("obra_id") long obraId) {
        acceso.exigir("trabajador.consultar", obraId);
        var fecha = Peticion.fecha("fecha");
        Long t = Peticion.entero("trabajador_id", 1L, null);
        Pagina pagina = Pagina.leer();
        return R.todos(pagina.recortar(db.en(() -> db.filas("SELECT " + COLS_JOR + " FROM tareo.jornada_vigente WHERE proyecto_id = :p "
                + "AND (CAST(:f AS date) IS NULL OR fecha = :f) AND (CAST(:t AS bigint) IS NULL OR trabajador_id = :t) ORDER BY fecha DESC, trabajador_id, id" + pagina.sql(),
                P.de("p", obraId, "f", fecha, "t", t)))), JOR_OUT);
    }

    @GetMapping("/horas-acumuladas")
    public Object horas(@PathVariable("obra_id") long obraId) {
        acceso.exigir("horas.consultar", obraId);
        Long pe = Peticion.entero("periodo_id", 1L, null);
        var rows = db.en(() -> db.filas("SELECT h.periodo_id, h.trabajador_id, t.nombre AS trabajador, h.cuadrilla, h.categoria, "
                + "h.dias_presente, h.dias_ausente, h.dias_permiso, h.horas_normales, h.horas_extra, u.ultima AS ultima_jornada "
                + "FROM tareo.horas_acumuladas h JOIN tareo.trabajador t ON t.id = h.trabajador_id "
                + "LEFT JOIN (SELECT periodo_id, trabajador_id, categoria, max(fecha) AS ultima FROM tareo.jornada_vigente "
                + "WHERE proyecto_id = :p GROUP BY periodo_id, trabajador_id, categoria) u "
                + "ON u.periodo_id = h.periodo_id AND u.trabajador_id = h.trabajador_id AND u.categoria = h.categoria "
                + "WHERE h.proyecto_id = :p AND (CAST(:pe AS bigint) IS NULL OR h.periodo_id = :pe)", P.de("p", obraId, "pe", pe)));
        if (rows.isEmpty()) return List.of();
        var numero = new HashMap<Object, Object>();
        obras.en(() -> { for (var p : obras.filas("SELECT id, numero FROM obra.periodo WHERE proyecto_id = :p ORDER BY numero", P.de("p", obraId))) numero.put(p.get("id"), p.get("numero")); return null; });
        var l = new ArrayList<Map<String, Object>>();
        for (var r : rows) {
            var m = new LinkedHashMap<>(r);
            m.put("periodo_numero", numero.get(r.get("periodo_id")));
            l.add(R.de(m, "periodo_id", "periodo_numero", "trabajador_id", "trabajador", "cuadrilla", "categoria", "dias_presente", "dias_ausente",
                    "dias_permiso", "horas_normales", "horas_extra", "ultima_jornada"));
        }
        l.sort(Comparator.<Map<String, Object>>comparingLong(r -> r.get("periodo_numero") == null ? 0 : ((Number) r.get("periodo_numero")).longValue())
                .thenComparing(r -> (String) r.get("cuadrilla")).thenComparing(r -> (String) r.get("trabajador")));
        return l;
    }
}
