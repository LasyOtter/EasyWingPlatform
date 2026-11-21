# EasyWing AI Service Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app
COPY . .

RUN mvn -B -pl easywing-platform-ai -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="EasyWing Team <team@easywing.io>"
LABEL description="EasyWing AI Service - Agent, RAG, MCP and model gateway"
LABEL version="1.0.0"

RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata && \
    addgroup -S easywing && adduser -S easywing -G easywing

WORKDIR /app
COPY --from=builder /app/easywing-platform-ai/target/*.jar app.jar
RUN chown -R easywing:easywing /app

USER easywing

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=100 -Xms256m -Xmx512m -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=prod
ENV NACOS_SERVER_ADDR=nacos:8848
ENV NACOS_DISCOVERY_ENABLED=true
ENV REDIS_HOST=redis
ENV REDIS_PORT=6379

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8082/actuator/health || exit 1

EXPOSE 8082
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
