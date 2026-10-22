"""Fábrica de datos de prueba. Todo se inserta con SQL real; nada se simula."""
from __future__ import annotations

import itertools
from datetime import date

from app.modulos.compras.dominio import digito_verificador_ruc

HASH = "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"
_contador = itertools.count(1)


def ruc_nuevo(prefijo: str = "20") -> str:
    """Un RUC con dígito verificador correcto y distinto cada vez."""
    k = next(_contador)
    base = f"{prefijo}{k:08d}"
    return base + str(digito_verificador_ruc(base))


class Fabrica:
    def __init__(self, conn):
        self.c = conn

    def q1(self, sql: str, params=()):
        return self.c.execute(sql, params).fetchone()["id"]

    def n(self) -> int:
        return next(_contador)

    # ----------------------------------------------------------------- obra
    def usuario(self, usuario: str | None = None) -> int:
        k = self.n()
        return self.q1(
            "INSERT INTO obra.usuario (nombre, usuario, hash_clave) VALUES (%s, %s, %s) RETURNING id",
            (f"Usuario {k}", usuario or f"usuario{k}", HASH),
        )

    def proyecto(self, por: int | None = None, inicio=date(2026, 10, 19), fin=date(2026, 12, 6),
                 nombre: str | None = None, dia_corte: int = 7) -> int:
        por = por or self.usuario()
        return self.q1(
            "INSERT INTO obra.proyecto (nombre, fecha_inicio, fecha_fin, dia_corte, creado_por) "
            "VALUES (%s, %s, %s, %s, %s) RETURNING id",
            (nombre or f"Obra {self.n()}", inicio, fin, dia_corte, por),
        )

    def asignar(self, usuario: int, proyecto: int, rol: str, por: int | None = None) -> int:
        return self.q1(
            "INSERT INTO obra.asignacion (usuario_id, proyecto_id, rol, asignado_por) VALUES (%s, %s, %s, %s) RETURNING id",
            (usuario, proyecto, rol, por or usuario),
        )

    def dispositivo(self, proyecto: int, usuario: int, activo_por: int | None = None) -> int:
        ident = f"android-{self.n():06d}-xxxxxxxx"
        if activo_por is None:
            return self.q1(
                "INSERT INTO obra.dispositivo (proyecto_id, usuario_id, identificador) VALUES (%s, %s, %s) RETURNING id",
                (proyecto, usuario, ident),
            )
        return self.q1(
            "INSERT INTO obra.dispositivo (proyecto_id, usuario_id, identificador, estado, autorizado_por, fecha_autorizacion) "
            "VALUES (%s, %s, %s, 'activo', %s, now()) RETURNING id",
            (proyecto, usuario, ident, activo_por),
        )

    def activar_dispositivo(self, dispositivo: int, por: int) -> None:
        self.c.execute(
            "UPDATE obra.dispositivo SET estado = 'activo', autorizado_por = %s, fecha_autorizacion = now() WHERE id = %s",
            (por, dispositivo),
        )

    def obra_con_equipo(self, **kw):
        """Obra con administrador, residente, almacenero y SSOMA asignados."""
        admin = self.usuario()
        p = self.proyecto(admin, **kw)
        eq = {"admin": admin, "proyecto": p}
        for rol in ("residente", "almacenero", "ssoma"):
            u = self.usuario()
            self.asignar(u, p, rol, admin)
            eq[rol] = u
        return eq

    def obra_completa(self, **kw):
        """Como obra_con_equipo, pero el administrador también está ASIGNADO (rol vigente) y hay un gerente."""
        eq = self.obra_con_equipo(**kw)
        self.asignar(eq["admin"], eq["proyecto"], "administrador", eq["admin"])
        g = self.usuario()
        self.asignar(g, eq["proyecto"], "gerente", eq["admin"])
        eq["gerente"] = g
        return eq

    # --------------------------------------------------------------- catálogo
    def insumo(self, codigo: str | None = None, tipo: str = "material", unidad: str = "kg") -> int:
        k = self.n()
        return self.q1(
            "INSERT INTO catalogo.insumo (codigo, descripcion, unidad_s10, unidad_compra, tipo) VALUES (%s, %s, %s, %s, %s) RETURNING id",
            (codigo or f"INS{k:06d}", f"Insumo de prueba {k}", unidad, unidad, tipo),
        )

    # ----------------------------------------------------------------- tareo
    def trabajador(self, proyecto: int, por: int, categoria: str = "peon", ingreso=date(2026, 10, 19),
                   cuadrilla: str = "Cuadrilla 1", documento: str | None = None) -> int:
        k = self.n()
        return self.q1(
            "INSERT INTO tareo.trabajador (proyecto_id, nombre, documento, categoria, cuadrilla, fecha_ingreso, registrado_por) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s) RETURNING id",
            (proyecto, f"Trabajador {k}", documento or f"{70000000 + k}", categoria, cuadrilla, ingreso, por),
        )

    def jornada(self, proyecto: int, trabajador: int, por: int, fecha=date(2026, 10, 20), estado: str = "presente",
                normales="8", extra="0", corrige_a: int | None = None, motivo: str | None = None) -> int:
        return self.q1(
            "INSERT INTO tareo.jornada (proyecto_id, trabajador_id, fecha, estado, horas_normales, horas_extra, "
            "registrado_por, corrige_a_id, motivo_correccion) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id",
            (proyecto, trabajador, fecha, estado, normales, extra, por, corrige_a, motivo),
        )

    # ---------------------------------------------------------------- compras
    def proveedor(self, ruc: str | None = None, validado: bool = False, razon: str | None = None) -> int:
        ruc = ruc or ruc_nuevo()
        return self.q1(
            "INSERT INTO compras.proveedor (ruc, razon_social, estado_validacion, validado_en) "
            "VALUES (%s, %s, %s, CASE WHEN %s THEN now() END) RETURNING id",
            (ruc, razon or f"Proveedor {self.n()} SAC", "validado" if validado else "sin_validar", validado),
        )

    def compra(self, proyecto: int, proveedor: int, por: int, numero: str | None = None, base="100.00", igv="18.00",
               total: str | None = None, solicitud: int | None = None, fecha=date(2026, 10, 21), tipo: str = "factura") -> int:
        from decimal import Decimal
        total = total if total is not None else str(Decimal(base) + Decimal(igv))
        return self.q1(
            "INSERT INTO compras.compra (proyecto_id, proveedor_id, tipo_comprobante, numero_comprobante, fecha, "
            "base_imponible, igv, total, registrado_por, solicitud_id) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) RETURNING id",
            (proyecto, proveedor, tipo, numero or f"F001-{self.n():05d}", fecha, base, igv, total, por, solicitud),
        )

    def detalle(self, compra: int, insumo: int, cantidad="10", unidad: str = "kg", precio="5.00") -> int:
        return self.q1(
            "INSERT INTO compras.compra_detalle (compra_id, insumo_id, cantidad, unidad, precio_unitario) "
            "VALUES (%s, %s, %s, %s, %s) RETURNING id",
            (compra, insumo, cantidad, unidad, precio),
        )

    def solicitud(self, proyecto: int, por: int) -> int:
        return self.q1("INSERT INTO compras.solicitud (proyecto_id, emitida_por) VALUES (%s, %s) RETURNING id", (proyecto, por))

    def linea(self, solicitud: int, insumo: int | None, cantidad="100", unidad: str = "kg", libre: str | None = None) -> int:
        return self.q1(
            "INSERT INTO compras.solicitud_linea (solicitud_id, insumo_id, descripcion_libre, cantidad, unidad_compra) "
            "VALUES (%s, %s, %s, %s, %s) RETURNING id",
            (solicitud, insumo, libre, cantidad, unidad),
        )
