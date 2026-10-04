# syntax=docker/dockerfile:1
# Multi-stage: Maven build, then a minimal JRE runtime as a non-root user.
# No credentials are baked in: all configuration arrives via environment
# variables (DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, ...).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN chown app:app app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=90s \
  CMD wget -q -O /dev/null http://localhost:8080/api/v1/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
