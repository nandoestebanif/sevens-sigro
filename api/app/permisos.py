"""Matriz de roles y permisos (docs/permisos.md) para los módulos obra, compras y tareo.

Cada acción lista los roles que pueden EJECUTARLA (R) o CONSULTARLA (C). Los endpoints declaran
la acción que exigen y la dependencia verifica en el servidor, contra la asignación vigente del
usuario en esa obra. Una prueba recorre todas las rutas y falla si alguna no declara su acción.
"""
from __future__ import annotations

ALMACENERO, SSOMA, RESIDENTE, ADMIN, GERENTE = "almacenero", "ssoma", "residente", "administrador", "gerente"
TODOS = frozenset({ALMACENERO, SSOMA, RESIDENTE, ADMIN, GERENTE})

ACCIONES: dict[str, frozenset[str]] = {
    # --- preparar la obra
    "proyecto.crear": frozenset({RESIDENTE, ADMIN}),
    "proyecto.ampliar_plazo": frozenset({RESIDENTE, ADMIN}),          # gerente "A": pendiente D-A2
    "proyecto.consultar": TODOS,
    "periodo.consultar": TODOS,
    "almacen.registrar": frozenset({ALMACENERO, RESIDENTE, ADMIN}),
    "almacen.consultar": frozenset({ALMACENERO, RESIDENTE, ADMIN, GERENTE}),
    # --- usuarios y accesos
    "usuario.crear": frozenset({ADMIN}),
    "usuario.consultar": frozenset({ADMIN}),
    "asignacion.escribir": frozenset({ADMIN}),
    "asignacion.consultar": frozenset({ADMIN, RESIDENTE}),
    "dispositivo.solicitar": frozenset({ALMACENERO, SSOMA}),
    "dispositivo.autorizar": frozenset({ADMIN}),
    "dispositivo.consultar": frozenset({ADMIN, RESIDENTE}),
    # --- mano de obra
    "trabajador.registrar": frozenset({ALMACENERO, SSOMA}),
    "trabajador.consultar": frozenset({ALMACENERO, SSOMA, RESIDENTE, ADMIN}),
    "jornada.registrar": frozenset({ALMACENERO, SSOMA}),
    "horas.consultar": TODOS,
    # --- compras
    "proveedor.registrar": frozenset({RESIDENTE, ADMIN}),
    "proveedor.consultar": frozenset({RESIDENTE, ADMIN, GERENTE}),
    "compra.registrar": frozenset({RESIDENTE, ADMIN}),
    "compra.consultar": frozenset({RESIDENTE, ADMIN, GERENTE}),
    "solicitud.emitir": frozenset({RESIDENTE}),
    "solicitud.consultar": frozenset({RESIDENTE, ADMIN, GERENTE}),
    # --- requerimiento, saldo por comprar y reposición (HU-19, HU-24, HU-25)
    "requerimiento.importar": frozenset({RESIDENTE, ADMIN}),
    "requerimiento.ajustar": frozenset({RESIDENTE, ADMIN}),
    "requerimiento.consultar": frozenset({RESIDENTE, ADMIN, GERENTE}),
    "reposicion.registrar": frozenset({RESIDENTE, ADMIN}),
    "reposicion.consultar": frozenset({RESIDENTE, ADMIN, GERENTE}),
    # --- catálogo de insumos (HU-07, HU-08, HU-17)
    "insumo.consultar": TODOS,
    "insumo.registrar": frozenset({ALMACENERO, RESIDENTE, ADMIN}),      # el almacenero no espera: lo nombra y el residente lo verifica
    "insumo.verificar": frozenset({RESIDENTE, ADMIN}),
    "insumo.factor": frozenset({RESIDENTE, ADMIN}),
    "insumo.importar": frozenset({RESIDENTE, ADMIN}),
}
