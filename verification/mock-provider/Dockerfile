FROM python:3.12.14-alpine3.24
RUN apk upgrade --no-cache && adduser -D -u 10001 mock
WORKDIR /app
COPY server.py .
USER mock
EXPOSE 9000
CMD ["python", "server.py"]
