# El contexto de construcción es la RAÍZ del repo (proyecto Maven multimódulo):
#   docker build -f ms-gateway/Dockerfile .       (docker compose ya lo hace así)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Maven necesita el pom raíz y el de todos los módulos para leer el proyecto, aunque solo se compile este.
COPY pom.xml .
COPY ferrefix-common/pom.xml ferrefix-common/pom.xml
COPY ms-gateway/pom.xml ms-gateway/pom.xml
COPY ms-auth/pom.xml ms-auth/pom.xml
COPY ms-tienda/pom.xml ms-tienda/pom.xml
COPY ms-gateway/src ms-gateway/src
RUN mvn -B -pl ms-gateway -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build /app/ms-gateway/target/*.jar app.jar
USER spring:spring
# Único puerto público del backend.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
