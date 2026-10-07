# ==============================================================================
# Multi-Stage Production Dockerfile for Bachat Gat / SHG Management Web App
# Target Platform: Google Cloud Run
# ==============================================================================

# Stage 1: Build JAR using Maven and Eclipse Temurin JDK 21
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and package application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal production runtime with JRE 21
FROM eclipse-temurin:21-jre-alpine

# Set non-root user for security compliance
RUN addgroup -S bachatgroup && adduser -S bachatuser -G bachatgroup
USER bachatuser:bachatgroup

WORKDIR /app

# Copy executable jar from builder stage
COPY --from=builder /build/target/bachatgat-shg-manager-1.0.0.jar app.jar

# Cloud Run dynamic port specification (default 8080)
ENV PORT=8080
EXPOSE 8080

# Production JVM optimizations for containerized Cloud Run
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
