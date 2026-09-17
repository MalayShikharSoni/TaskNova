# ─────────────────────────────────────────────────────────────
# TaskNova Dockerfile — Multi-stage Maven build
# ─────────────────────────────────────────────────────────────

# ── Stage 1: Build ───────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Cache dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B || mvn dependency:resolve -B

# Copy source and build fat JAR (skip tests during container packaging)
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create unprivileged system user and set ownership
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /app/data \
    && chown -R spring:spring /app

# Copy built JAR from builder stage with correct ownership
COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

# Switch to non-root user
USER spring

# Expose default port
EXPOSE 8080

# Container healthcheck (supports dynamic Render PORT)
HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 \
  CMD wget -qO- "http://localhost:${PORT:-8080}/actuator/health" || exit 1

# Launch application with container-aware memory limits
# -XX:MaxRAMPercentage=75 sizes heap to ~384MB in 512MB RAM free-tier container
ENTRYPOINT ["java", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:InitialRAMPercentage=50.0", \
            "-jar", "app.jar"]
