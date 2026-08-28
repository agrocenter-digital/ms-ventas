FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S agrocenter && adduser -S agrocenter -G agrocenter
WORKDIR /app

COPY --from=build --chown=agrocenter:agrocenter /workspace/target/ms-ventas-*.jar app.jar

LABEL org.opencontainers.image.title="AgroCenter ms-ventas" \
      org.opencontainers.image.description="Microservicio de checkout e historial de ventas" \
      org.opencontainers.image.vendor="AgroCenter"

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

USER agrocenter
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
