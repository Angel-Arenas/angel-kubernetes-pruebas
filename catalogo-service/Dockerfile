# ==========================================
# Etapa 1: Compilación (Build Stage)
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copiar configuración Maven y dependencias para cachear capas
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copiar código fuente y compilar artefacto JAR
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Etapa 2: Imagen final liviana de ejecución (Runtime Stage)
# ==========================================
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="Bodega Peirano DevOps" \
      service="catalogo-service" \
      domain="tienda-de-vinos"

WORKDIR /app

# Crear usuario y grupo no privilegiado por seguridad
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copiar únicamente el archivo JAR generado desde la etapa de compilación
COPY --from=builder /build/target/catalogo-service-*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8081

# Parámetros JVM optimizados para contenedores
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]
