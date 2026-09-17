# ─────────────────────────────────────────────────────────────
# TaskNova Dockerfile — Multi-stage Maven build
# ─────────────────────────────────────────────────────────────

# ── Stage 1: Build ───────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Cache dependencies first (only re-runs if pom.xml changes)
COPY pom.xml .
RUN apk add --no-cache maven \
    && mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Create data directory for H2 file database
# On Render, this should be a mounted persistent disk at /data
RUN mkdir -p /app/data

# Expose port
EXPOSE 8080

# Health check (Actuator)
HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Run the application
# Override SPRING_DATASOURCE_URL at runtime to point to mounted disk
ENTRYPOINT ["java", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-Xmx512m", \
            "-jar", "app.jar"]
