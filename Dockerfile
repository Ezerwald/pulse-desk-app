# PulseDesk Multi-stage Build
# Optimized for IBM Assignment Submission

# ── Stage 1: Build ───────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Leverage Docker layer caching for dependencies
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN ./mvnw dependency:go-offline -q

# Compile and package the application
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: Run as non-root user
RUN addgroup -S pulsedesk && adduser -S pulsedesk -G pulsedesk
USER pulsedesk

WORKDIR /app

# Copy artifact from builder stage
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Production-ready JVM configuration
# - UseContainerSupport: Ensures JVM is aware of cgroup resource limits
# - MaxRAMPercentage: Dynamically sizes heap relative to container memory
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-jar", "app.jar"]