# SIGRO · backend de la app (Spring Boot 3 + Java 17). Render: Root Directory = movil-api, Runtime = Docker.
FROM maven:3.9-eclipse-temurin-17 AS construir
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 sigro
COPY --from=construir /src/target/movil-api-1.0.0.jar app.jar
USER sigro
# 512 MB del plan gratuito: el montón se queda en 300 MB
ENV JAVA_TOOL_OPTIONS="-Xmx300m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
