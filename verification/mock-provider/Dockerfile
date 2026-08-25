FROM python:3.12-alpine
RUN adduser -D -u 10001 mock
WORKDIR /app
COPY server.py .
USER mock
EXPOSE 9000
CMD ["python", "server.py"]
