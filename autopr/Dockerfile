FROM python:3.12.14-alpine3.24 AS build
RUN apk upgrade --no-cache
WORKDIR /build
COPY pyproject.toml .
COPY forge_autopr forge_autopr
RUN pip wheel --no-cache-dir --wheel-dir /wheels .
FROM python:3.12.14-alpine3.24
RUN apk upgrade --no-cache && adduser -D -u 10001 forgeai
COPY --from=build /wheels /wheels
RUN pip install --no-cache-dir /wheels/* && rm -rf /wheels
USER forgeai
EXPOSE 8000
CMD ["uvicorn","forge_autopr.main:app","--host","0.0.0.0","--port","8000","--proxy-headers","--forwarded-allow-ips=*"]
