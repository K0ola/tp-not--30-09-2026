FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY eureka-server eureka-server
COPY config-server config-server
COPY api-gateway api-gateway
COPY class-service class-service
COPY booking-service booking-service
COPY payment-service payment-service
COPY notification-service notification-service
RUN mvn -q -pl api-gateway -am package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/api-gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
