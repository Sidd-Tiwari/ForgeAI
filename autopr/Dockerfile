FROM python:3.12-slim AS build
WORKDIR /build
COPY pyproject.toml .
COPY forge_autopr forge_autopr
RUN pip wheel --no-cache-dir --wheel-dir /wheels .
FROM python:3.12-slim
RUN useradd --system --uid 10001 --create-home forgeai
COPY --from=build /wheels /wheels
RUN pip install --no-cache-dir /wheels/* && rm -rf /wheels
USER forgeai
EXPOSE 8000
CMD ["uvicorn","forge_autopr.main:app","--host","0.0.0.0","--port","8000","--proxy-headers","--forwarded-allow-ips=*"]
