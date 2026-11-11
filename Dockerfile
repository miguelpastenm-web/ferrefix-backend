FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Carpeta de imágenes subidas: debe existir y ser de "spring" ANTES de cambiar de usuario.
# Al montar el volumen nombrado por primera vez, Docker copia estos permisos al volumen.
ENV FERREFIX_UPLOAD_DIR=/app/uploads
RUN mkdir -p /app/uploads && chown -R spring:spring /app/uploads
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
