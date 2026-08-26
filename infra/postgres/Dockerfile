FROM golang:1.26.7-alpine3.24 AS gosu-build
ARG GOSU_VERSION=v0.0.0-20260606051551-40506998e34a
RUN GOBIN=/out go install github.com/tianon/gosu@${GOSU_VERSION}

FROM postgres:17-alpine3.24
RUN rm /usr/local/bin/gosu && apk upgrade --no-cache
COPY --from=gosu-build /out/gosu /usr/local/bin/gosu
