package pe.sevens.sigro.seguridad;

import java.util.Map;
import java.util.Set;

/** Matriz de roles y permisos (docs/permisos.md): qué roles pueden cada acción. Port 1:1 de app/permisos.py. */
public final class Permisos {
    private Permisos() {}

    public static final Map<String, Set<String>> ACCIONES = Map.ofEntries(
            Map.entry("proyecto.crear", Set.of("residente", "administrador")),
            Map.entry("proyecto.ampliar_plazo", Set.of("residente", "administrador")),
            Map.entry("proyecto.cerrar", Set.of("administrador")),
            Map.entry("proyecto.consultar", Set.of("almacenero", "ssoma", "residente", "administrador", "gerente")),
            Map.entry("periodo.consultar", Set.of("almacenero", "ssoma", "residente", "administrador", "gerente")),
            Map.entry("periodo.cerrar", Set.of("residente", "administrador")),
            Map.entry("almacen.registrar", Set.of("almacenero", "residente", "administrador")),
            Map.entry("almacen.consultar", Set.of("almacenero", "residente", "administrador", "gerente")),
            Map.entry("usuario.crear", Set.of("administrador")),
            Map.entry("usuario.consultar", Set.of("administrador")),
            Map.entry("asignacion.escribir", Set.of("administrador")),
            Map.entry("asignacion.consultar", Set.of("administrador", "residente")),
            Map.entry("dispositivo.solicitar", Set.of("almacenero", "ssoma")),
            Map.entry("dispositivo.autorizar", Set.of("administrador")),
            Map.entry("dispositivo.consultar", Set.of("administrador", "residente")),
            Map.entry("trabajador.registrar", Set.of("almacenero", "ssoma", "administrador")),
            Map.entry("trabajador.consultar", Set.of("almacenero", "ssoma", "residente", "administrador")),
            Map.entry("jornada.registrar", Set.of("almacenero", "ssoma")),
            Map.entry("horas.consultar", Set.of("almacenero", "ssoma", "residente", "administrador", "gerente")),
            Map.entry("proveedor.registrar", Set.of("almacenero", "residente", "administrador")),
            Map.entry("proveedor.consultar", Set.of("almacenero", "residente", "administrador", "gerente")),
            Map.entry("compra.registrar", Set.of("almacenero", "residente", "administrador")),
            Map.entry("compra.consultar", Set.of("almacenero", "residente", "administrador", "gerente")),
            Map.entry("solicitud.emitir", Set.of("residente")),
            Map.entry("solicitud.consultar", Set.of("residente", "administrador", "gerente")),
            Map.entry("requerimiento.importar", Set.of("residente", "administrador")),
            Map.entry("requerimiento.ajustar", Set.of("residente", "administrador")),
            Map.entry("requerimiento.agregar", Set.of("residente", "administrador")),
            Map.entry("requerimiento.consultar", Set.of("residente", "administrador", "gerente")),
            Map.entry("partida.importar", Set.of("residente", "administrador")),
            Map.entry("partida.consultar", Set.of("residente", "administrador", "gerente")),
            Map.entry("reposicion.registrar", Set.of("residente", "administrador")),
            Map.entry("reposicion.consultar", Set.of("residente", "administrador", "gerente")),
            Map.entry("critico.marcar", Set.of("residente", "administrador")),
            Map.entry("avance.consultar", Set.of("residente", "administrador", "gerente")),
            Map.entry("salida.registrar", Set.of("almacenero")),
            Map.entry("ingreso.registrar", Set.of("almacenero")),
            Map.entry("recepcion.registrar", Set.of("almacenero")),
            Map.entry("traslado.registrar", Set.of("almacenero")),
            Map.entry("conteo.registrar", Set.of("almacenero")),
            Map.entry("movimiento.revertir", Set.of("almacenero", "residente")),
            Map.entry("stock.consultar", Set.of("almacenero", "ssoma", "residente", "administrador", "gerente")),
            Map.entry("herramienta.prestar", Set.of("almacenero")),
            Map.entry("herramienta.consultar", Set.of("almacenero", "ssoma", "residente", "administrador", "gerente")),
            Map.entry("costo.consultar", Set.of("residente", "administrador", "gerente")),
            Map.entry("insumo.consultar", Set.of("almacenero", "ssoma", "residente", "administrador", "gerente")),
            Map.entry("insumo.registrar", Set.of("almacenero", "residente", "administrador")),
            Map.entry("insumo.verificar", Set.of("residente", "administrador")),
            Map.entry("insumo.factor", Set.of("residente", "administrador")),
            Map.entry("insumo.importar", Set.of("residente", "administrador")));

    public static Set<String> roles(String accion) {
        Set<String> r = ACCIONES.get(accion);
        if (r == null) throw new IllegalArgumentException("Acción sin declarar en la matriz: " + accion);
        return r;
    }
}
