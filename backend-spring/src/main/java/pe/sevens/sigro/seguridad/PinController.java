package pe.sevens.sigro.seguridad;

import static pe.sevens.sigro.comun.Esquema.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import pe.sevens.sigro.comun.*;

/**
 * PIN del panel de administración (migración 0024). El PIN se guarda con Argon2id, como la clave; nunca en claro ni
 * en la bitácora. Verificarlo entrega un token de 15 minutos que el panel manda en la cabecera X-SIGRO-PIN.
 * 5 intentos fallidos seguidos lo bloquean 10 minutos.
 */
@RestController
@RequestMapping("/api/v1/auth/pin")
public class PinController {
    static final int MAX_INTENTOS = 5;
    static final Esquema CREAR = de(texto("clave").min(1).max(200), texto("pin").patron("^\\d{6}$"));
    static final Esquema VERIFICAR = de(texto("pin").patron("^\\d{6}$"));

    private final Modulo db;
    private final Acceso acceso;
    private final Claves claves;
    private final Tokens tokens;

    public PinController(Modulo obras, Acceso acceso, Claves claves, Tokens tokens) {
        this.db = obras;
        this.acceso = acceso;
        this.claves = claves;
        this.tokens = tokens;
    }

    /** Solo un administrador (en alguna obra) tiene panel y PIN. */
    Acceso.Actor admin() {
        var a = acceso.autenticado();
        Object rol = db.en(() -> db.escalar("SELECT 1 FROM obra.asignacion WHERE usuario_id = :u AND rol = 'administrador' AND fecha_retiro IS NULL LIMIT 1",
                P.de("u", a.usuarioId())));
        if (rol == null) throw new ErrorSigro(403, "sin_permiso", "El PIN es del panel de administración.");
        return a;
    }

    static boolean trivial(String pin) {
        return pin.chars().distinct().count() == 1 || "0123456789".contains(pin) || "9876543210".contains(pin);
    }

    @GetMapping
    public Map<String, Object> estado() {
        var a = admin();
        return R.mapa("configurado", acceso.tienePin(a.usuarioId()));
    }

    /** Crear o cambiar el PIN: pide la contraseña (quien tomó el celular o la PC desbloqueada no puede ponerlo). */
    @PostMapping
    public Map<String, Object> crear(@RequestBody(required = false) JsonNode cuerpo) {
        var a = admin();
        Datos d = CREAR.validar(cuerpo);
        if (trivial(d.texto("pin"))) throw new ErrorSigro(422, "pin_debil", "Ese PIN es muy fácil de adivinar (repetido o seguido). Elige otro.");
        return db.en(() -> {
            String hash = (String) db.escalar("SELECT hash_clave FROM obra.usuario WHERE id = :i", P.de("i", a.usuarioId()));
            if (!claves.verificar(d.texto("clave"), hash)) throw new ErrorSigro(403, "clave_actual_incorrecta", "La contraseña no es correcta.");
            db.actualizar("UPDATE obra.usuario SET pin_hash = :h, pin_intentos = 0, pin_bloqueado_hasta = NULL WHERE id = :i",
                    P.de("h", claves.hashear(d.texto("pin")), "i", a.usuarioId()));
            return R.mapa("configurado", true, "pin_token", tokens.crearPin(a.usuarioId()));
        });
    }

    @PostMapping("/verificar")
    public Map<String, Object> verificar(@RequestBody(required = false) JsonNode cuerpo) {
        var a = admin();
        Datos d = VERIFICAR.validar(cuerpo);
        var u = db.en(() -> db.fila("SELECT pin_hash, pin_intentos, pin_bloqueado_hasta FROM obra.usuario WHERE id = :i", P.de("i", a.usuarioId())));
        if (u.get("pin_hash") == null) throw new ErrorSigro(409, "pin_no_configurado", "Primero crea tu PIN de administrador.");
        Instant hasta = (Instant) u.get("pin_bloqueado_hasta");
        if (hasta != null && hasta.isAfter(Instant.now()))
            throw new ErrorSigro(429, "pin_bloqueado", "Demasiados intentos. Vuelve a intentar en unos minutos o entra de nuevo con tu contraseña.");
        if (!claves.verificar(d.texto("pin"), (String) u.get("pin_hash"))) {
            int n = ((Number) u.get("pin_intentos")).intValue() + 1;
            // en su propia transacción: el intento fallido queda aunque la respuesta sea un error
            db.aparte(() -> db.actualizar("UPDATE obra.usuario SET pin_intentos = :n, pin_bloqueado_hasta = :b WHERE id = :i",
                    P.de("n", n >= MAX_INTENTOS ? 0 : n, "b", n >= MAX_INTENTOS ? java.sql.Timestamp.from(Instant.now().plus(10, ChronoUnit.MINUTES)) : null, "i", a.usuarioId())));
            throw new ErrorSigro(403, "pin_incorrecto", n >= MAX_INTENTOS ? "PIN incorrecto. Se bloqueó por 10 minutos."
                    : "PIN incorrecto. Te quedan " + (MAX_INTENTOS - n) + " intentos.");
        }
        db.en(() -> db.actualizar("UPDATE obra.usuario SET pin_intentos = 0, pin_bloqueado_hasta = NULL WHERE id = :i", P.de("i", a.usuarioId())));
        return R.mapa("pin_token", tokens.crearPin(a.usuarioId()), "vence_en_segundos", 900);
    }
}
