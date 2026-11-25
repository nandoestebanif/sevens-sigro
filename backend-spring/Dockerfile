# SIGRO · backend Spring Boot 3 + Java 17
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/backend-spring-1.0.0.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-Xmx300m", "-jar", "app.jar"]
