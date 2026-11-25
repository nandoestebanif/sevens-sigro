package pe.sevens.sigro.tareo;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;
import pe.sevens.sigro.comun.Modulo;
import pe.sevens.sigro.comun.P;

/** API interna de tareo: costo le pregunta las horas hombre registradas (HU-58), sin leer el esquema tareo. */
@Component("tareoInterno")
public class Tareo {
    private final Modulo db;

    public Tareo(Modulo tareo) { this.db = tareo; }

    /** Horas de las jornadas vigentes (normales y extra aparte) y la fecha de la última; con `hasta`, solo hasta ese día. */
    public Map<String, Object> horas(long obraId, LocalDate hasta) {
        return pe.sevens.sigro.comun.Interno.json(db.en(() -> db.fila("SELECT coalesce(sum(horas_normales), 0) AS horas_normales, coalesce(sum(horas_extra), 0) AS horas_extra, "
                + "max(fecha) AS hasta FROM tareo.jornada_vigente WHERE proyecto_id = :p AND (CAST(:h AS date) IS NULL OR fecha <= CAST(:h AS date))",
                P.de("p", obraId, "h", hasta))));
    }
}
