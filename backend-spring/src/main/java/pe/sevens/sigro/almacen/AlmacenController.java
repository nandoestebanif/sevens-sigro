package pe.sevens.sigro.almacen;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.sevens.sigro.almacen.AlmacenService.Resultado;
import pe.sevens.sigro.comun.C;
import pe.sevens.sigro.comun.ErrorSigro;
import pe.sevens.sigro.comun.Peticion;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.seguridad.Acceso.Actor;

/**
 * Capa web de almacén: cada endpoint exige el permiso en la obra (401/403), valida el cuerpo con su contrato (422),
 * llama al caso de uso y elige el código HTTP (201 nuevo, 200 reenvío). No contiene SQL ni reglas de negocio.
 */
@RestController
@RequestMapping("/api/v1/obras/{obra_id}/almacen")
public class AlmacenController {
    private final Acceso acceso;
    private final AlmacenService servicio;

    public AlmacenController(Acceso acceso, AlmacenService servicio) {
        this.acceso = acceso;
        this.servicio = servicio;
    }

    static ResponseEntity<Object> responder(Resultado r) { return r.creado() ? C.creado(r.cuerpo()) : C.con(200, r.cuerpo()); }

    // ------------------------------------------------------------------ registros que vienen del celular o la web
    @PostMapping("/salidas")
    public ResponseEntity<Object> salida(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("salida.registrar", obraId);
        return responder(servicio.salida(a, obraId, Contratos.SALIDA.validar(cuerpo)));
    }

    @PostMapping("/ingresos")
    public ResponseEntity<Object> ingreso(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("ingreso.registrar", obraId);
        return responder(servicio.ingreso(a, obraId, Contratos.INGRESO.validar(cuerpo)));
    }

    @PostMapping("/movimientos/{movimiento_id}/reverso")
    public ResponseEntity<Object> reverso(@PathVariable("obra_id") long obraId, @PathVariable("movimiento_id") long movId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("movimiento.revertir", obraId);
        return responder(servicio.reverso(a, obraId, movId, Contratos.ANULAR.validar(cuerpo)));
    }

    @PostMapping("/recepciones/verificacion")
    public Object verificacion(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        acceso.exigir("recepcion.registrar", obraId);
        return servicio.verificacion(obraId, Contratos.VERIFICACION.validar(cuerpo));
    }

    @PostMapping("/recepciones")
    public ResponseEntity<Object> recepcion(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("recepcion.registrar", obraId);
        return responder(servicio.recepcion(a, obraId, Contratos.RECEPCION.validar(cuerpo)));
    }

    @GetMapping("/compras/{compra_id}/recepcion")
    public Object estadoRecepcion(@PathVariable("obra_id") long obraId, @PathVariable("compra_id") long compraId) {
        acceso.exigir("stock.consultar", obraId);
        return servicio.estadoRecepcion(obraId, compraId);
    }

    @PostMapping("/traslados")
    public ResponseEntity<Object> traslado(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("traslado.registrar", obraId);
        return responder(servicio.traslado(a, obraId, Contratos.TRASLADO.validar(cuerpo)));
    }

    @PostMapping("/traslados/{traslado_id}/anular")
    public ResponseEntity<Object> anularTraslado(@PathVariable("obra_id") long obraId, @PathVariable("traslado_id") long trasladoId,
                                                 @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("traslado.registrar", obraId);
        return responder(servicio.anularTraslado(a, obraId, trasladoId, Contratos.ANULAR.validar(cuerpo)));
    }

    // ------------------------------------------------------------------ conteo físico
    @PostMapping("/conteos")
    public ResponseEntity<Object> abrirConteo(@PathVariable("obra_id") long obraId, @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("conteo.registrar", obraId);
        return responder(servicio.abrirConteo(a, obraId, Contratos.CONTEO.validar(cuerpo)));
    }

    @PostMapping("/conteos/{conteo_id}/lineas")
    public ResponseEntity<Object> contar(@PathVariable("obra_id") long obraId, @PathVariable("conteo_id") long conteoId, @RequestBody(required = false) JsonNode cuerpo) {
        acceso.exigir("conteo.registrar", obraId);
        return C.creado(servicio.contar(obraId, conteoId, Contratos.CONTEO_LINEA.validar(cuerpo)));
    }

    @GetMapping("/conteos/{conteo_id}")
    public Object verConteo(@PathVariable("obra_id") long obraId, @PathVariable("conteo_id") long conteoId) {
        acceso.exigir("stock.consultar", obraId);
        return servicio.verConteo(obraId, conteoId);
    }

    @PostMapping("/conteos/{conteo_id}/confirmar")
    public Object confirmar(@PathVariable("obra_id") long obraId, @PathVariable("conteo_id") long conteoId) {
        Actor a = acceso.exigir("conteo.registrar", obraId);
        return servicio.confirmar(a, obraId, conteoId);
    }

    // ------------------------------------------------------------------ consultas
    @GetMapping("/stock")
    public Object stock(@PathVariable("obra_id") long obraId) {
        acceso.exigir("stock.consultar", obraId);
        return servicio.stock(obraId, Peticion.entero("almacen_id", 1L, null));
    }

    @GetMapping("/semana")
    public ResponseEntity<Object> semana(@PathVariable("obra_id") long obraId) {
        acceso.exigir("stock.consultar", obraId);
        String num = Peticion.texto("numero");
        if (num == null) throw new ErrorSigro(422, "datos_invalidos", "numero: Field required");
        long numero = Peticion.enteroDe("numero", num, 1L, null);
        Long almacenId = Peticion.entero("almacen_id", 1L, null);
        String formato = Peticion.texto("formato");
        if (formato == null) formato = "json";
        if (!formato.matches("^(json|csv)$")) throw new ErrorSigro(422, "datos_invalidos", "formato: String should match pattern '^(json|csv)$'");
        var s = servicio.semana(obraId, numero, almacenId);
        if (formato.equals("csv"))
            return ResponseEntity.ok().header("Content-Type", "text/csv; charset=utf-8")
                    .header("Content-Disposition", "attachment; filename=\"semana-" + numero + ".csv\"").body((Object) csv(s.filas()).getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok(servicio.semanaJson(s));
    }

    /** CSV para Excel en español: BOM, separador «;» y una fila por insumo. */
    static String csv(List<Map<String, Object>> filas) {
        var sb = new StringBuilder("\uFEFFalmacen;codigo;insumo;unidad;saldo_inicial;entradas;salidas;saldo_final\n");
        var lineas = new ArrayList<String>();
        for (var r : filas) {
            var cel = new ArrayList<String>();
            for (String k : List.of("almacen", "insumo_codigo", "descripcion", "unidad", "saldo_inicial", "entradas", "salidas", "saldo_final"))
                cel.add(String.valueOf(r.get(k) == null ? "None" : r.get(k)).replace(";", ","));
            lineas.add(String.join(";", cel));
        }
        return sb.append(String.join("\n", lineas)).append("\n").toString();
    }

    @GetMapping("/kardex")
    public Object kardex(@PathVariable("obra_id") long obraId) {
        acceso.exigir("stock.consultar", obraId);
        return servicio.kardex(obraId, Peticion.entero("insumo_id", 1L, null), Peticion.entero("almacen_id", 1L, null));
    }
}
