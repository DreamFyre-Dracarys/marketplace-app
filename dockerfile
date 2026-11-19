# Etapa 1: Compilar el proyecto y generar el .jar
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY pom.xml mvnw mvnw.cmd ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Etapa 2: Ejecutar la aplicación con una imagen liviana de Java
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar
COPY wallet /app/wallet

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
