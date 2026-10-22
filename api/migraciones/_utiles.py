"""Ayudas para escribir migraciones sin repetir el mismo SQL de protección."""
from alembic import op


def sin_borrado(tabla: str) -> None:
    """Regla 10: nada se borra. Ni DELETE ni TRUNCATE."""
    nombre = tabla.replace(".", "_")
    op.execute(
        f"CREATE TRIGGER tg_{nombre}_no_borrar BEFORE DELETE ON {tabla} "
        f"FOR EACH ROW EXECUTE FUNCTION util.rechazar_borrado()"
    )
    op.execute(
        f"CREATE TRIGGER tg_{nombre}_no_truncar BEFORE TRUNCATE ON {tabla} "
        f"FOR EACH STATEMENT EXECUTE FUNCTION util.rechazar_borrado()"
    )


def solo_insertar(tabla: str) -> None:
    """Regla 1: libro que no se modifica. Ni UPDATE, ni DELETE, ni TRUNCATE."""
    nombre = tabla.replace(".", "_")
    op.execute(
        f"CREATE TRIGGER tg_{nombre}_append_only BEFORE UPDATE OR DELETE ON {tabla} "
        f"FOR EACH ROW EXECUTE FUNCTION util.rechazar_modificacion()"
    )
    op.execute(
        f"CREATE TRIGGER tg_{nombre}_no_truncar BEFORE TRUNCATE ON {tabla} "
        f"FOR EACH STATEMENT EXECUTE FUNCTION util.rechazar_modificacion()"
    )


def conceder(tabla: str, privilegios: str = "SELECT, INSERT", columnas_update: str | None = None) -> None:
    """Privilegios mínimos para el usuario de la aplicación (sigro_app).

    Defensa en profundidad: los disparadores protegen incluso al dueño; los permisos
    impiden que el usuario de la aplicación siquiera lo intente.
    """
    op.execute(f"GRANT {privilegios} ON {tabla} TO sigro_app")
    if columnas_update:
        op.execute(f"GRANT UPDATE ({columnas_update}) ON {tabla} TO sigro_app")
