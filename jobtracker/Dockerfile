# ==========================================
# Estágio 1: Build da aplicação Spring Boot
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copia pom.xml e dependências primeiro para cacheamento eficiente
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia código-fonte e compila gerando o .jar (pulando testes)
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# Estágio 2: Imagem final leve para execução
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Criação de usuário não-root por questões de segurança
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copia o artefato do estágio de build
COPY --from=build --chown=spring:spring /app/target/jobtracker-0.0.1-SNAPSHOT.jar app.jar

# Configurações do container
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
