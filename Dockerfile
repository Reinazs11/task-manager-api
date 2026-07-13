# Dockerfile — Multi-stage build para a Task Manager API.
#
# Estágio 1 (builder): compila e empacota o JAR com Maven.
# Estágio 2 (runtime): imagem enxuta só com o JAR e JRE.

# ----- Estágio 1: Build -----
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

# Copia apenas o pom.xml para cachear dependências (camada separada).
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copia o código-fonte e empacota (sem rodar testes neste estágio).
COPY src ./src
RUN mvn -B clean package -DskipTests

# ----- Estágio 2: Runtime -----
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Cria usuário não-root para rodar a aplicação (boa prática de segurança).
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /workspace/target/*.jar /app/app.jar

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
