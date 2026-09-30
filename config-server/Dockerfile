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
RUN mvn -q -pl config-server -am package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/config-server/target/config-server-*.jar app.jar
COPY config-server/config-repo config-repo
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
