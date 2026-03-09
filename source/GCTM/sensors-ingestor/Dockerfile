# STAGE 1: Build
FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /app

COPY . .

# Build del modulo specifico e delle sue dipendenze locali (common-dto)
RUN mvn clean package -DskipTests -pl common-dto,sensors-ingestor -am

# STAGE 2: Run
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/sensors-ingestor/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]