# Una imagen por microservicio, desde el mismo código: cada una conserva SOLO lo suyo.
#   docker build --build-arg SERVICIO=compras -t sigro-compras ./api
# SERVICIO: obras | catalogo | compras | tareo | gateway | relevo | migrador
FROM python:3.13-slim
ARG SERVICIO=obras
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 SIGRO_SERVICIO=${SERVICIO}
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
RUN python -m app.servicios.podar "${SERVICIO}"
# Sin root: ningún servicio necesita permisos de sistema.
RUN useradd --system --no-create-home sigro && chown -R sigro /app
USER sigro
EXPOSE 8000
# PORT lo fija la nube (Render, Railway); en docker compose queda 8000.
CMD ["sh", "-c", "exec uvicorn app.servicios.${SIGRO_SERVICIO}:app --host 0.0.0.0 --port ${PORT:-8000}"]
