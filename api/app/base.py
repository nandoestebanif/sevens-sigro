"""Base declarativa compartida y convención de nombres.

Las restricciones llevan nombre explícito y estable: así un error de la base se puede
traducir a un mensaje de dominio ("ese RUC ya existe") y las migraciones se pueden
revertir por nombre.
"""
from sqlalchemy import MetaData
from sqlalchemy.orm import DeclarativeBase

CONVENCION = {
    "ix": "ix_%(table_name)s_%(column_0_N_name)s",
    "uq": "uq_%(table_name)s_%(column_0_N_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_N_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}


class Base(DeclarativeBase):
    metadata = MetaData(naming_convention=CONVENCION)
