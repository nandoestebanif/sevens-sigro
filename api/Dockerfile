# Una imagen por microservicio, desde el mismo código: cada una conserva SOLO lo suyo.
#   docker build --build-arg SERVICIO=compras -t sigro-compras ./api
# SERVICIO: obras | catalogo | compras | tareo | almacen | costo | gateway | relevo | migrador
FROM python:3.13-slim
ARG SERVICIO=obras
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 SIGRO_SERVICIO=${SERVICIO}
WORKDIR /app
# requirements.lock: todas las dependencias (también las indirectas) con versión y hash fijos; pip solo instala
# wheels ya compilados (--only-binary), así ningún paquete ejecuta código de instalación al construir.
# Se regenera con:  pip-compile --generate-hashes --strip-extras --allow-unsafe -o requirements.lock requirements.txt
COPY requirements.lock .
RUN pip install --no-cache-dir --only-binary :all: --require-hashes -r requirements.lock
# Solo lo que el servicio usa en ejecución: nada de pruebas, .env, semillas con datos del cliente ni archivos sueltos.
COPY alembic.ini ./
COPY app ./app
COPY migraciones ./migraciones
COPY docs ./docs
RUN python -m app.servicios.podar "${SERVICIO}"
# Sin root: ningún servicio necesita permisos de sistema.
RUN useradd --system --no-create-home sigro && chown -R sigro /app
USER sigro
EXPOSE 8000
# PORT lo fija la nube (Render, Railway); en docker compose queda 8000.
CMD ["sh", "-c", "exec uvicorn app.servicios.${SIGRO_SERVICIO}:app --host 0.0.0.0 --port ${PORT:-8000}"]
