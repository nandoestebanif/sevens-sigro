package pe.sevens.sigro.seguridad;

import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Contraseñas con Argon2id, con los mismos parámetros que argon2-cffi por defecto (t=3, m=64 MiB, p=4, sal 16,
 * hash 32): los hashes que ya están en la base siguen valiendo. Pocos a la vez para no agotar la memoria.
 */
@Component
public class Claves {
    private final Argon2PasswordEncoder argon = new Argon2PasswordEncoder(16, 32, 4, 65536, 3);
    private final Semaphore turnos;
    private final String relleno;

    public Claves(@Value("${SIGRO_ARGON2_SIMULTANEOS:2}") int simultaneos) {
        this.turnos = new Semaphore(Math.max(simultaneos, 1));
        this.relleno = argon.encode("relleno-para-igualar-tiempos");
    }

    public String hashear(String clave) {
        return conTurno(() -> argon.encode(clave));
    }

    /** Verifica contra un hash de relleno si el usuario no existe: no se distinguen los casos por el tiempo. */
    public boolean verificar(String clave, String hash) {
        boolean ok = conTurno(() -> {
            try {
                return argon.matches(clave, hash != null ? hash : relleno);
            } catch (RuntimeException e) {
                return false;
            }
        });
        return ok && hash != null;
    }

    /** El motivo del rechazo, o null si sirve. */
    public static String aceptable(String clave) {
        if (clave.codePointCount(0, clave.length()) < 10) return "La contraseña debe tener al menos 10 caracteres.";
        boolean digito = clave.chars().anyMatch(Character::isDigit);
        if (clave.toLowerCase().equals(clave) || clave.toUpperCase().equals(clave) || !digito)
            return "La contraseña debe mezclar mayúsculas, minúsculas y al menos un número.";
        return null;
    }

    private <T> T conTurno(java.util.function.Supplier<T> f) {
        turnos.acquireUninterruptibly();
        try {
            return f.get();
        } finally {
            turnos.release();
        }
    }
}
