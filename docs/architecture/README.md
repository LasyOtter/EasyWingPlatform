# Architecture Documentation

## 🏗️ Architecture Overview

EasyWing Platform follows a modular, cloud-native architecture designed for enterprise-grade microservices applications.

## 🎯 Design Principles

### 1. Cloud-Native First
- **Container-Ready**: Docker-first design
- **Kubernetes-Friendly**: Health checks, graceful shutdown
- **Service Mesh Compatible**: Works with Istio, Linkerd

### 2. Resilience by Default
- **Circuit Breakers**: Built-in fault tolerance
- **Retry with Backoff**: Automatic retry mechanisms
- **Rate Limiting**: Protection against traffic spikes

### 3. Observability Built-In
- **OpenTelemetry**: Unified observability
- **Distributed Tracing**: End-to-end request tracing
- **Metrics**: Prometheus-compatible metrics

### 4. Developer Experience
- **Convention over Configuration**: Sensible defaults
- **Auto-Configuration**: Spring Boot style
- **Hot Reload**: Fast development iteration

## 📦 Module Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    EasyWing Platform                        │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                  API Gateway                         │   │
│  │  (Spring Cloud Gateway + Rate Limiting + Auth)      │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                 Core Framework                       │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │   │
│  │  │  Core   │ │  Web    │ │Security │ │Observ.  │   │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘   │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │   │
│  │  │  Cloud  │ │  Data   │ │ Cache   │ │ Feign   │   │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   Starters                          │   │
│  │  (Auto-configuration for each module)               │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## 🔧 Core Modules

### easywing-core
Core utilities and shared components:
- Constants and enums
- Exception hierarchy
- Result wrapper (RFC 9457)
- Context management
- Utility classes

### easywing-web
Web layer enhancements:
- RFC 9457 Problem Details
- Global exception handling
- Request/Response logging
- API versioning
- CORS configuration

### easywing-observability
Observability integration:
- OpenTelemetry tracing
- Micrometer metrics
- Structured logging
- Health indicators

### easywing-security
Security components:
- OAuth2 Resource Server
- JWT validation
- Permission management
- Security context

## 🔗 Integration Points

### External Systems
```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Nacos     │    │   Redis     │    │  MySQL      │
│  (Config &  │    │  (Cache &   │    │ (Storage)   │
│  Discovery) │    │   Session)  │    │             │
└─────────────┘    └─────────────┘    └─────────────┘
       ▲                  ▲                  ▲
       │                  │                  │
       └──────────────────┴──────────────────┘
                          │
              ┌───────────┴───────────┐
              │   EasyWing Platform   │
              └───────────────────────┘
                          │
       ┌──────────────────┼──────────────────┐
       ▼                  ▼                  ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Jaeger    │    │  Prometheus │    │   Kafka     │
│  (Tracing)  │    │  (Metrics)  │    │ (Messaging) │
└─────────────┘    └─────────────┘    └─────────────┘
```

## 📋 Architecture Decisions

See [Architecture Decision Records (ADR)](./decisions/) for major architectural decisions.

## 🔄 Data Flow

### Request Processing
```
Client Request
      │
      ▼
┌─────────────┐
│ API Gateway │ ── Auth Check, Rate Limit, Routing
└─────────────┘
      │
      ▼
┌─────────────┐
│   Service   │ ── Business Logic
└─────────────┘
      │
      ├──▶ Cache (Redis)
      ├──▶ Database (MySQL)
      └──▶ External Services (Feign)
      │
      ▼
Response (RFC 9457 format)
```

## 🚀 Technology Stack

| Layer | Technology |
|-------|------------|
| Gateway | Spring Cloud Gateway |
| Service Framework | Spring Boot 3.3 |
| Security | Spring Security 6.3 + OAuth 2.1 |
| Observability | OpenTelemetry + Micrometer |
| Cache | Redisson + Caffeine |
| Database | MyBatis-Plus + Druid |
| Message Queue | RocketMQ / Kafka |
| Distributed TX | Seata |

## 📊 Performance Targets

| Metric | Target |
|--------|--------|
| Startup Time (JVM) | < 3 seconds |
| Startup Time (Native) | < 500ms |
| Memory Usage (JVM) | < 200MB |
| Memory Usage (Native) | < 80MB |
| Response Latency (P99) | < 50ms |
| Throughput | > 10K RPS |

---

*EasyWing Platform Architecture*
