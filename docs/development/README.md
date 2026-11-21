# Development Guide

## 🛠️ Development Setup

This guide covers everything you need to start developing with EasyWing Platform.

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 21+ | Java runtime |
| Maven | 3.9.0+ | Build tool |
| Docker | Latest | Container runtime |
| Docker Compose | Latest | Local infrastructure |
| Git | Latest | Version control |

### IDE Setup

#### IntelliJ IDEA (Recommended)

1. **Install IntelliJ IDEA Ultimate** (Community works but lacks some Spring features)

2. **Install Plugins**
   - Lombok
   - Spring Boot Assistant
   - MapStruct Support
   - Checkstyle-IDEA
   - EditorConfig

3. **Import Project**
   ```bash
   # Clone and open in IntelliJ
   File > Open > Select project directory
   ```

4. **Enable Annotation Processing**
   ```
   Settings > Build, Execution, Deployment > Compiler > Annotation Processors
   > Enable annotation processing: ✓
   ```

5. **Configure Checkstyle**
   ```
   Settings > Tools > Checkstyle
   > Add configuration file: checkstyle.xml
   > Use Google Checks as baseline
   ```

## 🚀 Getting Started

### 1. Clone and Build

```bash
# Clone repository
git clone https://github.com/LasyOtter/EasyWingPlatform.git
cd EasyWingPlatform

# Build project
mvn clean install -DskipTests
```

### 2. Start Infrastructure

```bash
# Start Docker containers
cd easywing-platform-samples
docker-compose up -d

# Verify services
docker-compose ps
```

Services started:
- Nacos (8848): Service discovery and configuration
- MySQL (3306): Database
- Redis (6379): Cache
- Jaeger (16686): Tracing
- Prometheus (9090): Metrics
- Grafana (3000): Visualization

### 3. Run Sample Application

```bash
# Run user service
cd easywing-platform-samples/sample-user-service
mvn spring-boot:run

# Run order service
cd easywing-platform-samples/sample-order-service
mvn spring-boot:run

# Run API gateway
cd easywing-platform-gateway
mvn spring-boot:run
```

### 4. Verify

- API Gateway: http://localhost:8080
- System Service: http://localhost:8083
- Auth Service: http://localhost:8085
- AI Service: http://localhost:8082
- User Service: http://localhost:8081/swagger-ui.html
- Nacos Console: http://localhost:8081
- Jaeger UI: http://localhost:16686
- Grafana: http://localhost:3000

## 📁 Project Structure

```
EasyWingPlatform/
├── easywing-platform-bom/           # Dependency management
├── easywing-platform-parent/        # Parent POM with plugins
├── easywing-platform-framework/     # Core framework modules
│   ├── easywing-core/              # Core utilities
│   ├── easywing-web/               # Web enhancements
│   ├── easywing-observability/     # Observability
│   └── easywing-security/          # Security
├── easywing-platform-starters/      # Auto-configuration starters
├── easywing-platform-gateway/       # API Gateway
├── easywing-platform-samples/       # Sample applications
└── docs/                            # Documentation
```

## 🔨 Development Workflow

### Creating a New Module

1. **Add to Parent POM**
   ```xml
   <modules>
       <module>your-new-module</module>
   </modules>
   ```

2. **Create Module POM**
   ```xml
   <project>
       <parent>
           <groupId>com.easywing.platform</groupId>
           <artifactId>easywing-platform-parent</artifactId>
           <version>${revision}</version>
       </parent>
       
       <artifactId>your-new-module</artifactId>
       <name>Your New Module</name>
   </project>
   ```

3. **Add to BOM**
   ```xml
   <dependency>
       <groupId>com.easywing.platform</groupId>
       <artifactId>your-new-module</artifactId>
       <version>${project.version}</version>
   </dependency>
   ```

### Creating a Starter

1. **Create Module Structure**
   ```
   easywing-boot-starter-xxx/
   ├── pom.xml
   └── src/
       └── main/
           ├── java/
           │   └── com/easywing/platform/starter/xxx/
           │       ├── XxxAutoConfiguration.java
           │       ├── XxxProperties.java
           │       └── condition/
           └── resources/
               └── META-INF/
                   └── spring/
                       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
   ```

2. **Auto-Configuration Class**
   ```java
   @AutoConfiguration
   @EnableConfigurationProperties(XxxProperties.class)
   @ConditionalOnClass(XxxService.class)
   public class XxxAutoConfiguration {
       
       @Bean
       @ConditionalOnMissingBean
       public XxxService xxxService(XxxProperties properties) {
           return new XxxService(properties);
       }
   }
   ```

3. **Properties Class**
   ```java
   @ConfigurationProperties(prefix = "easywing.xxx")
   public class XxxProperties {
       private boolean enabled = true;
       private String property;
       // getters, setters
   }
   ```

## 🧪 Testing

### Unit Tests

```bash
# Run all unit tests
mvn test

# Run specific test class
mvn test -Dtest=UserServiceTest

# Run with coverage
mvn test jacoco:report
```

### Integration Tests

```bash
# Run integration tests
mvn verify -Pintegration-test

# Run with the integration profile
mvn test -Dspring.profiles.active=integration
```

### Test Naming Convention

```java
class UserServiceTest {
    
    @Test
    void methodName_stateUnderTest_expectedBehavior() {
        // Given
        
        // When
        
        // Then
    }
}
```

## 📝 Code Style

### Run Formatter

```bash
# Check code style
mvn spotless:check

# Auto-format code
mvn spotless:apply
```

### Run Checkstyle

```bash
# Check code style
mvn checkstyle:check

# Generate report
mvn checkstyle:checkstyle
```

## 🔧 Common Tasks

### Update Dependencies

```bash
# Check for updates
mvn versions:display-dependency-updates

# Update a dependency
mvn versions:use-latest-releases -Dincludes=group:artifact
```

### Generate Native Image

```bash
# Build native image
mvn clean package -Pnative
```

### Debug Application

```bash
# Run with debug port
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
```

## 📚 Additional Resources

- [代码风格规范](../code-style-guide.md)
- [API 版本管理](../api-versioning-guide.md)
- [待改进项清单](../improvement-tasks.md)

---

*EasyWing Platform Development Guide*
