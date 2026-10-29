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

# Stage 2: Production runtime with JRE 21.
# Use the regular Linux image instead of Alpine/musl because the Firestore
# gRPC/Netty native stack can abort the JVM on Alpine before port 8080 opens.
FROM eclipse-temurin:21-jre

# Set non-root user for security compliance. The regular Temurin image uses
# Debian user-management tools rather than Alpine's addgroup/adduser flags.
RUN groupadd --system bachatgroup && \
    useradd --system --gid bachatgroup --create-home --home-dir /home/bachatuser bachatuser
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
