# EasyWing Auth Service Dockerfile
# Multi-stage build for minimal image size

# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom files
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY easywing-platform-bom/pom.xml easywing-platform-bom/
COPY easywing-platform-parent/pom.xml easywing-platform-parent/
COPY easywing-platform-framework/pom.xml easywing-platform-framework/
COPY easywing-platform-framework/easywing-core/pom.xml easywing-platform-framework/easywing-core/
COPY easywing-platform-framework/easywing-web/pom.xml easywing-platform-framework/easywing-web/
COPY easywing-platform-framework/easywing-security/pom.xml easywing-platform-framework/easywing-security/
COPY easywing-platform-framework/easywing-observability/pom.xml easywing-platform-framework/easywing-observability/
COPY easywing-platform-framework/easywing-cloud/pom.xml easywing-platform-framework/easywing-cloud/
COPY easywing-platform-auth/pom.xml easywing-platform-auth/
COPY easywing-platform-starters/pom.xml easywing-platform-starters/
COPY easywing-platform-test/pom.xml easywing-platform-test/
COPY easywing-platform-samples/pom.xml easywing-platform-samples/

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B -pl easywing-platform-auth -am

# Copy source code
COPY easywing-platform-framework/ easywing-platform-framework/
COPY easywing-platform-auth/ easywing-platform-auth/
COPY easywing-platform-starters/ easywing-platform-starters/

# Build the application
RUN ./mvnw clean package -DskipTests -B -pl easywing-platform-auth -am

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="EasyWing Team <team@easywing.io>"
LABEL description="EasyWing Auth Service - JWT token issuance and management"
LABEL version="1.0.0"

# Install necessary packages
RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# Create non-root user
RUN addgroup -S easywing && adduser -S easywing -G easywing

WORKDIR /app

# Copy the built JAR
COPY --from=builder /app/easywing-platform-auth/target/*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R easywing:easywing /app

USER easywing

# JVM options
ENV JAVA_OPTS="-XX:+UseG1GC \
-XX:MaxGCPauseMillis=100 \
-XX:+UseStringDeduplication \
-Xms128m \
-Xmx256m \
-Djava.security.egd=file:/dev/./urandom"

# Environment variables
ENV SPRING_PROFILES_ACTIVE=prod
ENV NACOS_SERVER_ADDR=localhost:8848
ENV REDIS_HOST=localhost
ENV REDIS_PORT=6379

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8085/actuator/health || exit 1

# Expose port
EXPOSE 8085

# Entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
