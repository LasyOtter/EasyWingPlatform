# Deployment Guide

## 🚀 Deployment Overview

EasyWing Platform supports multiple deployment strategies from traditional JVM deployments to cloud-native Native Images.

## 📋 Deployment Options

| Option | Startup Time | Memory | Use Case |
|--------|-------------|--------|----------|
| JVM JAR | 2-5s | ~200MB | Traditional deployment |
| JVM Container | 2-5s | ~200MB | Kubernetes, Docker |
| Native Image | < 500ms | < 80MB | Serverless, Fast scaling |
| CRaC | < 100ms | ~200MB | Instant startup |

## 🐳 Docker Deployment

### Building Docker Image

```bash
# Build JVM image
docker build -t easywing/service:latest .

# Build Native image
docker build -t easywing/service:native -f Dockerfile.native .
```

### Dockerfile (JVM)

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/*.jar app.jar

ENV JAVA_OPTS="-Xms128m -Xmx256m"
ENV SPRING_PROFILES_ACTIVE="prod"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### Dockerfile (Native)

```dockerfile
FROM ghcr.io/graalvm/native-image-community:21-muslib AS builder

WORKDIR /app
COPY . .
RUN ./mvnw clean package -Pnative -DskipTests

FROM alpine:latest

WORKDIR /app
COPY --from=builder /app/target/native-image ./app

EXPOSE 8080

ENTRYPOINT ["./app"]
```

## ☸️ Kubernetes Deployment

### Deployment Manifest

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: easywing-service
  labels:
    app: easywing-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: easywing-service
  template:
    metadata:
      labels:
        app: easywing-service
    spec:
      containers:
        - name: service
          image: easywing/service:latest
          ports:
            - containerPort: 8080
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 5
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: JAVA_OPTS
              value: "-Xms128m -Xmx256m"
```

### Service Manifest

```yaml
apiVersion: v1
kind: Service
metadata:
  name: easywing-service
spec:
  selector:
    app: easywing-service
  ports:
    - port: 80
      targetPort: 8080
  type: ClusterIP
```

### ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: easywing-config
data:
  application.yaml: |
    spring:
      datasource:
        url: jdbc:mysql://mysql:3306/easywing
      data:
        redis:
          host: redis
```

## 🔧 Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active profile | `dev` |
| `JAVA_OPTS` | JVM options | `-Xms128m -Xmx256m` |
| `NACOS_SERVER_ADDR` | Nacos address | `localhost:8848` |
| `REDIS_HOST` | Redis host | `localhost` |
| `MYSQL_HOST` | MySQL host | `localhost` |

### Profiles

```bash
# Development
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Production
java -jar app.jar --spring.profiles.active=prod

# Native
./app --spring.profiles.active=native
```

## 🔍 Health Checks

### Liveness Probe

```bash
curl http://localhost:8080/actuator/health/liveness
```

Response:
```json
{
  "status": "UP"
}
```

### Readiness Probe

```bash
curl http://localhost:8080/actuator/health/readiness
```

## 📊 Monitoring

### Prometheus Metrics

```bash
curl http://localhost:8080/actuator/prometheus
```

### OpenTelemetry Tracing

Configure OTLP exporter:
```yaml
otel:
  exporter:
    otlp:
      endpoint: http://jaeger:4317
```

## 🚀 Native Image Deployment

### Build Native Image

```bash
# Install GraalVM
sdk install java 21-graal

# Build native image
mvn clean package -Pnative
```

### Native Image Config

```json
// META-INF/native-image/resource-config.json
{
  "resources": {
    "includes": [
      {"pattern": "application.yml"},
      {"pattern": "application-*.yml"}
    ]
  }
}
```

### Reflection Config

```json
// META-INF/native-image/reflection-config.json
[
  {
    "name": "com.easywing.platform.dto.UserDTO",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  }
]
```

## 🔄 CRaC Deployment

### Create Checkpoint

```bash
# Run with CRaC
java -XX:CRaCCheckpointTo=cr -jar app.jar

# Trigger checkpoint (from another terminal)
jcmd app JDK.checkpoint
```

### Restore from Checkpoint

```bash
# Restore
java -XX:CRaCRestoreFrom=cr
```

## 🛡️ Security Configuration

### TLS/SSL

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

### Secrets Management

Use external secret management:
- Kubernetes Secrets
- HashiCorp Vault
- AWS Secrets Manager
- Azure Key Vault

## 📈 Scaling

### Horizontal Scaling

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: easywing-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: easywing-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

---

*EasyWing Platform Deployment Guide*