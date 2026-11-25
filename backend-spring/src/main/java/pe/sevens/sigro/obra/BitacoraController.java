package pe.sevens.sigro.obra;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.comun.P;
import pe.sevens.sigro.comun.Peticion;
import pe.sevens.sigro.comun.R;
import pe.sevens.sigro.seguridad.Acceso;

/** Bitácora de la obra para el administrador (con PIN si lo configuró). Lo más reciente primero, hasta 500. */
@RestController
@RequestMapping("/api/v1/obras/{obra_id}/bitacora")
public class BitacoraController {
    private final Modulo db;
    private final Acceso acceso;

    public BitacoraController(Modulo obras, Acceso acceso) {
        this.db = obras;
        this.acceso = acceso;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@PathVariable("obra_id") long obraId) {
        acceso.exigir("bitacora.consultar", obraId);
        Long limite = Peticion.entero("limite", 1L, 500L);
        String solo = Peticion.texto("solo"); // «rechazos»: solo 401/403
        return db.en(() -> R.todos(db.filas("SELECT b.id, b.momento, b.usuario_id, u.nombre AS usuario, b.metodo, b.ruta, b.estado, b.duracion_ms "
                + "FROM obra.bitacora b LEFT JOIN obra.usuario u ON u.id = b.usuario_id WHERE b.proyecto_id = :p "
                + ("rechazos".equals(solo) ? "AND b.estado IN (401, 403) " : "") + "ORDER BY b.id DESC LIMIT :l",
                P.de("p", obraId, "l", limite == null ? 200 : limite)), "id", "momento", "usuario_id", "usuario", "metodo", "ruta", "estado", "duracion_ms"));
    }
}
