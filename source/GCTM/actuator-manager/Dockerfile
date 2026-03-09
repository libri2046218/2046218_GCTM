FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests -pl common-dto,actuator-manager -am

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/actuator-manager/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]