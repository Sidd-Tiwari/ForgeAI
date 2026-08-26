FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package
FROM eclipse-temurin:21-jre-noble
RUN groupadd --system --gid 10001 forgeai && useradd --system --uid 10001 --gid forgeai --no-create-home forgeai
WORKDIR /app
COPY --from=build /workspace/target/gateway-*.jar app.jar
USER forgeai
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
