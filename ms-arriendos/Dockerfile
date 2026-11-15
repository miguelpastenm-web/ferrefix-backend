# El contexto de construcción es la RAÍZ del repo (proyecto Maven multimódulo):
#   docker build -f ms-arriendos/Dockerfile .       (docker compose ya lo hace así)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Maven necesita el pom raíz y el de todos los módulos para leer el proyecto, aunque solo se compile este.
COPY pom.xml .
COPY ferrefix-common/pom.xml ferrefix-common/pom.xml
COPY ms-gateway/pom.xml ms-gateway/pom.xml
COPY ms-auth/pom.xml ms-auth/pom.xml
COPY ms-arriendos/pom.xml ms-arriendos/pom.xml
COPY ms-tienda/pom.xml ms-tienda/pom.xml
# Código compartido (verificación del JWT, formato de error, reloj): se compila junto con el servicio (-am).
COPY ferrefix-common/src ferrefix-common/src
COPY ms-arriendos/src ms-arriendos/src
RUN mvn -B -pl ms-arriendos -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build /app/ms-arriendos/target/*.jar app.jar
USER spring:spring
# Puerto interno: no se publica en el host; solo el gateway llega aquí por la red de Docker.
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
