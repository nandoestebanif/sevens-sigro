package pe.sevens.sigro.almacen;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.sevens.sigro.comun.C;
import pe.sevens.sigro.comun.ErrorSigro;
import pe.sevens.sigro.comun.Peticion;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.seguridad.Acceso.Actor;

/** Préstamo y devolución de herramientas y equipos: solo HTTP (permiso, contrato y código de respuesta). */
@RestController
@RequestMapping("/api/v1/obras/{obra_id}/almacen/prestamos")
public class PrestamoController {
    private final Acceso acceso;
    private final PrestamoService servicio;

    public PrestamoController(Acceso acceso, PrestamoService servicio) {
        this.acceso = acceso;
        this.servicio = servicio;
    }

    /** ?estado=sin_devolver (por defecto) o todos. */
    @GetMapping
    public Object listar(@PathVariable("obra_id") long obraId) {
        acceso.exigir("herramienta.consultar", obraId);
        String estado = Peticion.texto("estado");
        if (estado == null) estado = "sin_devolver";
        if (!estado.matches("^(sin_devolver|todos)$")) throw new ErrorSigro(422, "datos_invalidos", "estado: debe ser sin_devolver o todos");
        return servicio.listar(obraId, estado.equals("sin_devolver"));
    }

    @PostMapping
    public ResponseEntity<Object> prestar(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("herramienta.prestar", obraId);
        return AlmacenController.responder(servicio.prestar(a, obraId, Contratos.PRESTAMO.validar(cuerpo)));
    }

    @PostMapping("/{prestamo_id}/devolucion")
    public Object devolver(@PathVariable("obra_id") long obraId, @PathVariable("prestamo_id") long id, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("herramienta.prestar", obraId);
        return C.con(200, servicio.devolver(a, obraId, id, Contratos.DEVOLUCION.validar(cuerpo)));
    }
}
