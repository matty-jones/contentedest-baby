# syntax=docker/dockerfile:1

FROM python:3.12-slim AS base
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# System deps (curl is handy for healthchecks/logs)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates tzdata && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy and install server deps only
COPY server/requirements.txt /app/server/requirements.txt
RUN pip install --upgrade pip && pip install -r /app/server/requirements.txt

# Copy server code
COPY server /app/server

# Create nonroot user first (DB dir must be writable when no volume is mounted)
RUN useradd -m appuser
RUN mkdir -p /app/server/db && chown -R appuser:appuser /app/server/db

# Default env (overridable by compose/.env)
ENV TCB_DB_PATH=/app/server/db/data.db \
    TZ=America/Boise \
    PORT=8080

USER appuser

# Run FastAPI via uvicorn
EXPOSE 8080
CMD ["uvicorn", "server.app.main:app", "--host", "0.0.0.0", "--port", "8080", "--workers", "2"]
