FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests -pl common-dto,web-engine -am

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/web-engine/target/*.jar app.jar

# Espone la porta del web server (es. 8080 o 9000 a seconda della tua config)
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]