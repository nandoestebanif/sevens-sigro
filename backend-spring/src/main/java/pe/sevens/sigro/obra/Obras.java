package pe.sevens.sigro.obra;

import java.util.*;
import org.springframework.stereotype.Component;
import pe.sevens.sigro.comun.ErrorSigro;
import pe.sevens.sigro.comun.Interno;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.comun.P;

/**
 * API interna de obras (port de app/modulos/obra/interno.py): dueño de la identidad, los accesos y los dispositivos.
 * Los demás módulos le preguntan aquí; ninguno lee el esquema obra.
 */
@Component("obrasInterno")
public class Obras {
    private final Modulo db;

    public Obras(Modulo obras) { this.db = obras; }

    /** Regla 5: el celular debe estar ACTIVO para esa persona en esa obra; devuelve su id. */
    public Long dispositivoActivo(long usuarioId, long obraId, String identificador) {
        Object id = db.en(() -> db.escalar("SELECT id FROM obra.dispositivo WHERE proyecto_id = :p AND usuario_id = :u AND identificador = :i AND estado = 'activo'",
                P.de("p", obraId, "u", usuarioId, "i", identificador.strip())));
        if (id == null) throw new ErrorSigro(403, "dispositivo_no_autorizado", "Este celular no está autorizado para registrar en esta obra; pide su autorización al administrador.");
        return (Long) id;
    }

    /** Obras con ese nombre (sin distinguir tildes, mayúsculas ni espacios de más). */
    public List<Map<String, Object>> buscar(String nombre) {
        return Interno.json(db.en(() -> db.filas("SELECT id, nombre, estado FROM obra.proyecto "
                + "WHERE lower(translate(regexp_replace(btrim(nombre), '[[:space:]]+', ' ', 'g'), 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun')) "
                + "    = lower(translate(regexp_replace(btrim(:n), '[[:space:]]+', ' ', 'g'), 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun')) ORDER BY id", P.de("n", nombre))));
    }

    public Map<String, Object> obra(long obraId) {
        var o = db.en(() -> db.fila("SELECT id, nombre, fecha_inicio, fecha_fin, estado FROM obra.proyecto WHERE id = :i", P.de("i", obraId)));
        if (o == null) throw new ErrorSigro(404, "no_existe", "Esa obra no existe.");
        return Interno.json(o);
    }

    public List<Map<String, Object>> periodos(long obraId) {
        return Interno.json(db.en(() -> db.filas("SELECT id, numero, fecha_inicio, fecha_fin, estado FROM obra.periodo WHERE proyecto_id = :p ORDER BY numero", P.de("p", obraId))));
    }

    public List<Map<String, Object>> almacenes(long obraId) {
        return Interno.json(db.en(() -> db.filas("SELECT id, proyecto_id, nombre, tipo, estado FROM obra.almacen WHERE proyecto_id = :p ORDER BY id", P.de("p", obraId))));
    }
}
