package pe.sevens.sigro.compras;

import static pe.sevens.sigro.comun.Esquema.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.sevens.sigro.comun.*;
import pe.sevens.sigro.seguridad.Acceso;
import pe.sevens.sigro.seguridad.Acceso.Actor;

/**
 * HU-34 comprobante adjunto de la compra y HU-61 descargarlo. Nunca se borra ni se reemplaza: se anula con su motivo.
 * Cada archivo guarda su huella sha256 y se vuelve a comprobar al descargar. Port de app/modulos/compras/adjuntos.py.
 */
@RestController
@RequestMapping("/api/v1/obras/{obra_id}/compras/{compra_id}/comprobantes")
public class AdjuntosController {
    static final Esquema ANULAR = de(texto("motivo").min(5).max(300));
    static final String SQL = "SELECT j.id, j.compra_id, j.nombre_original AS nombre, j.tipo_mime, j.tamano_bytes, j.sha256, j.subido_por, j.subido_en, "
            + "CASE WHEN j.anulado_en IS NULL THEN 'vigente' ELSE 'anulado' END AS estado, j.anulado_por, j.anulado_en, j.motivo_anulacion FROM compras.compra_adjunto j ";
    static final String[] OUT = {"id", "compra_id", "nombre", "tipo_mime", "tamano_bytes", "sha256", "estado", "subido_por", "subido_en", "anulado_por", "anulado_en", "motivo_anulacion"};

    private final Modulo db;
    private final Acceso acceso;
    private final Eventos eventos;

    public AdjuntosController(Modulo compras, Acceso acceso, Eventos eventos) {
        this.db = compras;
        this.acceso = acceso;
        this.eventos = eventos;
    }

    static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Map<String, Object> compra(long obraId, long compraId) {
        var c = db.fila("SELECT id, estado, numero_comprobante FROM compras.compra WHERE id = :i AND proyecto_id = :p", P.de("i", compraId, "p", obraId));
        if (c == null) throw new ErrorSigro(404, "no_existe", "Esa compra no existe en esta obra.");
        return c;
    }

    Map<String, Object> adjunto(long compraId, long id) {
        var j = db.fila(SQL + "WHERE j.id = :a AND j.compra_id = :c", P.de("a", id, "c", compraId));
        if (j == null) throw new ErrorSigro(404, "no_existe", "Ese comprobante no existe en esta compra.");
        return j;
    }

    @PostMapping
    public ResponseEntity<Object> subir(@PathVariable("obra_id") long obraId, @PathVariable("compra_id") long compraId,
                                        @RequestParam(value = "archivo", required = false) MultipartFile archivo) throws java.io.IOException {
        Actor a = acceso.exigir("compra.registrar", obraId);
        if (archivo == null) throw new ErrorSigro(422, "datos_invalidos", "archivo: Field required");
        byte[] contenido = archivo.getBytes();
        return C.creado(db.en(() -> {
            var c = compra(obraId, compraId);
            if (!"registrada".equals(c.get("estado"))) throw new ErrorSigro(409, "compra_anulada", "La compra está anulada: ya no admite comprobantes nuevos.");
            if (contenido.length > Dominio.MAX_COMPROBANTE_BYTES) throw new ErrorSigro(413, "archivo_muy_grande", "El comprobante supera los 10 MB; reduce la foto o comprime el PDF.");
            if (contenido.length == 0) throw new ErrorSigro(422, "archivo_vacio", "El archivo llegó vacío.");
            String mime = Dominio.tipoReal(contenido);
            if (mime == null) throw new ErrorSigro(415, "tipo_no_admitido", "Solo se admiten PDF, JPG o PNG. Revisa que el archivo no esté dañado.");
            String huella = sha256(contenido);
            var rep = db.fila("SELECT id, anulado_en FROM compras.compra_adjunto WHERE compra_id = :c AND sha256 = :h", P.de("c", compraId, "h", huella));
            if (rep != null) throw new ErrorSigro(409, "comprobante_repetido", "Ese mismo archivo ya se adjuntó a esta compra" + (rep.get("anulado_en") != null ? " y fue anulado." : "."));
            String nombre = Dominio.nombreSeguro(archivo.getOriginalFilename(), mime);
            Object id = db.escalar("INSERT INTO compras.compra_adjunto (compra_id, archivo, nombre_original, tipo_mime, tamano_bytes, sha256, subido_por, almacenamiento) "
                    + "VALUES (:c, :k, :n, :m, :t, :h, :u, 'bd') RETURNING id", P.de("c", compraId, "k", "bd:" + huella, "n", nombre, "m", mime, "t", contenido.length, "h", huella, "u", a.usuarioId()));
            db.actualizar("INSERT INTO compras.compra_adjunto_contenido (adjunto_id, contenido) VALUES (:i, :b)", P.de("i", id, "b", contenido));
            return (Object) R.de(adjunto(compraId, (Long) id), OUT);
        }));
    }

    @GetMapping
    public Object listar(@PathVariable("obra_id") long obraId, @PathVariable("compra_id") long compraId) {
        acceso.exigir("compra.consultar", obraId);
        return db.en(() -> {
            compra(obraId, compraId);
            return R.todos(db.filas(SQL + "WHERE j.compra_id = :c ORDER BY (j.anulado_en IS NOT NULL), j.subido_en, j.id", P.de("c", compraId)), OUT);
        });
    }

    @GetMapping("/{adjunto_id}")
    public ResponseEntity<byte[]> descargar(@PathVariable("obra_id") long obraId, @PathVariable("compra_id") long compraId, @PathVariable("adjunto_id") long id) {
        acceso.exigir("compra.consultar", obraId);
        return db.en(() -> {
            compra(obraId, compraId);
            var j = adjunto(compraId, id);
            var r = db.fila("SELECT contenido FROM compras.compra_adjunto_contenido WHERE adjunto_id = :i", P.de("i", id));
            if (r == null) throw new ErrorSigro(404, "contenido_no_disponible", "El archivo de este comprobante no está disponible.");
            byte[] contenido = (byte[]) r.get("contenido");
            if (!sha256(contenido).equals(j.get("sha256"))) throw new ErrorSigro(409, "comprobante_alterado", "La huella del archivo no coincide con la que se guardó al adjuntarlo.");
            String nombre = URLEncoder.encode((String) j.get("nombre"), StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
            return ResponseEntity.ok().header("Content-Type", (String) j.get("tipo_mime"))
                    .header("Content-Disposition", "attachment; filename*=UTF-8''" + nombre)
                    .header("X-Sigro-Sha256", (String) j.get("sha256")).header("Cache-Control", "private, no-store").body(contenido);
        });
    }

    @PostMapping("/{adjunto_id}/anulacion")
    public Object anular(@PathVariable("obra_id") long obraId, @PathVariable("compra_id") long compraId, @PathVariable("adjunto_id") long id,
                         @RequestBody(required = false) JsonNode cuerpo) {
        Actor a = acceso.exigir("compra.registrar", obraId);
        Datos d = ANULAR.validar(cuerpo);
        var dest = eventos.destinatariosConRol(obraId, List.of("administrador", "residente"), a.usuarioId());
        return db.en(() -> {
            var c = compra(obraId, compraId);
            var j = adjunto(compraId, id);
            if ("anulado".equals(j.get("estado"))) throw new ErrorSigro(409, "ya_anulado", "Ese comprobante ya estaba anulado.");
            db.actualizar("UPDATE compras.compra_adjunto SET anulado_por = :u, anulado_en = now(), motivo_anulacion = :m WHERE id = :i", P.de("u", a.usuarioId(), "m", d.texto("motivo"), "i", id));
            eventos.publicar(db, "comprobante.anulado", obraId, dest, "Comprobante anulado",
                    a.nombre() + " anuló " + j.get("nombre") + " de la compra " + c.get("numero_comprobante") + ": " + d.texto("motivo"),
                    R.mapa("compra_id", compraId, "comprobante_id", id));
            return R.de(adjunto(compraId, id), OUT);
        });
    }
}
