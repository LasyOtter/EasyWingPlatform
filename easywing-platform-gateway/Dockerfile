# EasyWing Gateway Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app
COPY . .

RUN mvn -B -pl easywing-platform-gateway -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="EasyWing Team <team@easywing.io>"
LABEL description="EasyWing High-Performance API Gateway"
LABEL version="1.0.0"

RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata && \
    addgroup -S easywing && adduser -S easywing -G easywing

WORKDIR /app
COPY --from=builder /app/easywing-platform-gateway/target/*.jar app.jar
RUN chown -R easywing:easywing /app

USER easywing

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=100 -Xms200m -Xmx200m -XX:MaxDirectMemorySize=100m -Djava.security.egd=file:/dev/./urandom -Dspring.main.lazy-initialization=true -Dspring.main.web-application-type=reactive"
ENV SPRING_PROFILES_ACTIVE=prod
ENV NACOS_SERVER_ADDR=nacos:8848
ENV NACOS_DISCOVERY_ENABLED=true
ENV REDIS_HOST=redis
ENV REDIS_PORT=6379

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
