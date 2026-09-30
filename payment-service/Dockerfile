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
RUN mvn -q -pl payment-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/payment-service/target/payment-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "app.jar"]
