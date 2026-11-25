package pe.sevens.sigro.almacen;

import static pe.sevens.sigro.comun.Esquema.*;

import java.util.*;
import pe.sevens.sigro.comun.Datos;
import pe.sevens.sigro.comun.Esquema;

/**
 * Contratos de entrada de almacén (DTO validados): qué campos acepta cada operación, sus límites y formatos. Un cuerpo que
 * no cumple se rechaza con 422 antes de llegar al servicio.
 */
final class Contratos {
    private Contratos() {}

    static Campo cantidad(String n) { return decimal(n).gt(0).digitos(18, 4); }

    /** Campos comunes de lo que llega del celular: uuid_cliente (reenvío idempotente), hora y celular. */
    static Campo[] desde(boolean dispositivoObligatorio, boolean fechaObligatoria) {
        return new Campo[]{uuid("uuid_cliente"), entero("hora_dispositivo_ms").opcional().ge(0),
                fechaObligatoria ? fecha("fecha") : fecha("fecha").opcional(),
                dispositivoObligatorio ? texto("dispositivo").min(8).max(128) : texto("dispositivo").opcional().min(8).max(128)};
    }

    static Esquema con(Campo[] base, Campo... mas) {
        var l = new ArrayList<>(List.of(base));
        l.addAll(List.of(mas));
        return Esquema.de(l.toArray(Campo[]::new));
    }

    static final Esquema LINEA = de(entero("insumo_id").ge(1), cantidad("cantidad"));
    static final Esquema SALIDA = con(desde(true, false), entero("almacen_id").opcional().ge(1), entero("insumo_id").ge(1), cantidad("cantidad"),
            texto("unidad").opcional().max(20), texto("motivo").min(1).max(300), texto("quien_retira").min(1).max(120), texto("frente").opcional().max(120));
    static final Esquema INGRESO = con(desde(false, false), entero("almacen_id").opcional().ge(1), entero("insumo_id").ge(1), cantidad("cantidad"),
            texto("unidad").opcional().max(20), decimal("costo_unitario").gt(0).digitos(18, 6), texto("origen").uno(Reglas.ORIGENES),
            texto("origen_detalle").opcional().max(300))
            .despues(d -> {
                String o = d.texto("origen"), det = d.texto("origen_detalle");
                if ((o.equals("otro") || o.equals("saldo_otra_obra")) && (det == null || det.strip().isEmpty()))
                    throw error("Ese origen exige su detalle (para «saldo de otra obra», el nombre de la obra).");
            });
    static final Esquema RECEPCION = con(desde(false, false), entero("compra_id").ge(1), entero("almacen_id").opcional().ge(1),
            lista("lineas", LINEA).opcional().min(1).max(500), texto("motivo_exceso").opcional().max(300));
    static final Esquema VERIFICACION = de(entero("compra_id").ge(1), lista("lineas", LINEA).opcional().min(1).max(500));
    static final Esquema TRASLADO = con(desde(false, false), entero("origen_almacen_id").opcional().ge(1), entero("destino_obra_id").opcional().ge(1),
            entero("destino_almacen_id").ge(1), texto("motivo").min(1).max(300), lista("lineas", LINEA).min(1).max(500))
            .despues(d -> {
                var s = new HashSet<Object>();
                for (Datos l : d.<Datos>lista("lineas")) s.add(l.entero("insumo_id"));
                if (s.size() != d.lista("lineas").size()) throw error("Un insumo aparece dos veces en el traslado.");
            });
    static final Esquema ANULAR = con(desde(false, false), texto("motivo").min(3).max(300));
    static final Esquema CONTEO = con(desde(false, true), entero("almacen_id").opcional().ge(1));
    static final String[] ESTADOS_DEVOLUCION = {"bueno", "danado", "perdido"};
    static final Esquema PRESTAMO = con(desde(false, false), entero("almacen_id").opcional().ge(1), entero("insumo_id").ge(1), cantidad("cantidad"),
            entero("trabajador_id").opcional().ge(1), texto("responsable").min(3).max(120), fecha("devolver_el").opcional(),
            texto("nota").opcional().max(300));
    static final Esquema DEVOLUCION = de(texto("estado").uno(ESTADOS_DEVOLUCION), texto("nota").opcional().max(300))
            .despues(d -> {
                String n = d.texto("nota");
                if (!"bueno".equals(d.texto("estado")) && (n == null || n.strip().isEmpty()))
                    throw error("Si vuelve dañada o se perdió, escribe qué pasó: queda como constancia.");
            });
    /** «unidad» opcional: la app cuenta en la unidad de compra (bolsas) y el servidor la pasa a la del S10 con el factor. */
    static final Esquema CONTEO_LINEA = de(entero("insumo_id").ge(1), decimal("cantidad_contada").ge(0).digitos(18, 4), texto("motivo").opcional().max(300),
            texto("unidad").opcional().max(20));
}
