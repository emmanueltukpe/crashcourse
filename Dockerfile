# Multi-stage Dockerfile for Spring Boot Services
#
# EDUCATIONAL NOTE - Multi-stage Docker Builds
#
# Multi-stage builds create smaller, more secure Docker images.
# We use two stages:
# 1. Build stage - Compiles the Java code with Maven
# 2. Runtime stage - Only includes the compiled JAR, not build tools
#
# Benefits:
# - Smaller image size (no Maven, no source code in final image)
# - More secure (fewer tools = smaller attack surface)
# - Faster deployment (smaller images transfer faster)

# ═══════════════════════════════════════════════════════════
# STAGE 1: Build the application
# ═══════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jdk-jammy AS build

# ARG must be declared after FROM to be used in this stage
ARG SERVICE_NAME

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom files
# We copy these first because they change less frequently than source code
# Docker caches layers, so if POMs don't change, this layer is reused
COPY mvnw ./
COPY .mvn .mvn/
COPY pom.xml ./

# Copy module POM files
COPY common/pom.xml common/
COPY auth-service/pom.xml auth-service/
COPY account-service/pom.xml account-service/
COPY mock-exchange/pom.xml mock-exchange/ 
COPY gateway/pom.xml gateway/

# Download dependencies (cached layer if POMs haven't changed)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY common/src common/src
COPY auth-service/src auth-service/src
COPY account-service/src account-service/src
COPY mock-exchange/src mock-exchange/src
COPY gateway/src gateway/src

# Build the application (skip tests for faster builds)
# In production CI/CD, you'd run tests before building the image
RUN ./mvnw clean package -DskipTests -pl ${SERVICE_NAME} -am

# ═══════════════════════════════════════════════════════════
# STAGE 2: Create runtime image
# ═══════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jre-jammy

# Create a non-root user for security
# Running as root inside containers is a security risk
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

# Copy only the compiled JAR from build stage
ARG SERVICE_NAME
COPY --from=build /app/${SERVICE_NAME}/target/*.jar app.jar

# Change ownership to non-root user
RUN chown appuser:appuser app.jar

# Switch to non-root user
USER appuser

# Expose port (documentation only, doesn't actually publish the port)
EXPOSE 8080

# Run the application
# EDUCATIONAL NOTE - Java options
# -XX:+UseContainerSupport: Java respects container memory limits
# -XX:MaxRAMPercentage=75: Use maximum 75% of container memory for heap
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
