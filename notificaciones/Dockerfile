FROM python:3.13-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 NOTIF_DB=/datos/notificaciones.db
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY main.py almacen.py ./
RUN useradd --system --no-create-home sigro && mkdir /datos && chown sigro /datos
USER sigro
VOLUME /datos
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
