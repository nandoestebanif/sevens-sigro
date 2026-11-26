package pe.sevens.sigro.obra;

import java.time.LocalDate;
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

    /**
     * Filtros (todos opcionales y combinables): desde/hasta (fechas de Lima, inclusive), usuario_id, q (texto en la ruta,
     * p. ej. «salidas» o «usuarios»), solo=rechazos (401/403) | errores (4xx/5xx) | cambios (POST 2xx), limite (1–500).
     */
    @GetMapping
    public List<Map<String, Object>> listar(@PathVariable("obra_id") long obraId) {
        acceso.exigir("bitacora.consultar", obraId);
        Long limite = Peticion.entero("limite", 1L, 500L);
        String solo = Peticion.texto("solo");
        LocalDate desde = Peticion.fecha("desde"), hasta = Peticion.fecha("hasta");
        Long usuario = Peticion.entero("usuario_id", 1L, null);
        String q = Peticion.texto("q", null, 60);
        var sql = new StringBuilder("SELECT b.id, b.momento, b.usuario_id, u.nombre AS usuario, b.metodo, b.ruta, b.estado, b.duracion_ms "
                // La obra y lo general (usuarios, proveedores, catálogo, inicios de sesión): no tienen obra en la ruta.
                + "FROM obra.bitacora b LEFT JOIN obra.usuario u ON u.id = b.usuario_id WHERE (b.proyecto_id = :p OR b.proyecto_id IS NULL) ");
        var p = P.de("p", obraId, "l", limite == null ? 200 : limite);
        if ("rechazos".equals(solo)) sql.append("AND b.estado IN (401, 403) ");
        else if ("errores".equals(solo)) sql.append("AND b.estado >= 400 ");
        else if ("cambios".equals(solo)) sql.append("AND b.metodo = 'POST' AND b.estado < 400 ");
        if (desde != null) { sql.append("AND b.momento >= CAST(:desde AS date)::timestamp AT TIME ZONE 'America/Lima' "); p.addValue("desde", desde.toString()); }
        if (hasta != null) { sql.append("AND b.momento < (CAST(:hasta AS date) + 1)::timestamp AT TIME ZONE 'America/Lima' "); p.addValue("hasta", hasta.toString()); }
        if (usuario != null) { sql.append("AND b.usuario_id = :u "); p.addValue("u", usuario); }
        if (q != null && !q.isBlank()) {
            sql.append("AND b.ruta ILIKE :q ");
            p.addValue("q", "%" + q.strip().toLowerCase(java.util.Locale.ROOT).replace("\\", "").replace("%", "").replace("_", "\\_").replace(' ', '-') + "%");
        }
        sql.append("ORDER BY b.id DESC LIMIT :l");
        return db.en(() -> R.todos(db.filas(sql.toString(), p), "id", "momento", "usuario_id", "usuario", "metodo", "ruta", "estado", "duracion_ms"));
    }
}
