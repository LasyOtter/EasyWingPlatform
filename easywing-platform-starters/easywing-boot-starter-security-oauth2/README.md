# EasyWing Security OAuth2 Starter

OAuth2 资源服务器安全组件，提供 JWT 认证、授权码模式客户端支持。

## 特性

- OAuth2 资源服务器配置
- JWT 令牌验证
- 统一认证入口
- 权限注解支持

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-security-oauth2</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.easywing.io
          jwk-set-uri: https://auth.easywing.io/.well-known/jwks.json
```

### 3. 安全配置

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
            );
        return http.build();
    }
}
```

## 使用认证

### 获取 Token

```bash
curl -X POST https://auth.easywing.io/oauth/token \
  -d "grant_type=password" \
  -d "username=user" \
  -d "password=pass" \
  -d "client_id=client"
```

### 访问资源

```bash
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/user
```

## JWT 解析

```java
@RestController
public class UserController {

    @GetMapping("/me")
    public UserDetails getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return UserDetails.builder()
            .id(jwt.getSubject())
            .username(jwt.getClaimAsString("username"))
            .roles(jwt.getClaimAsStringList("roles"))
            .build();
    }
}
```

## 权限控制

```java
@RestController
public class AdminController {

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/users")
    public List<User> listUsers() {
        return userService.list();
    }

    @Secured("ROLE_USER")
    @GetMapping("/myorders")
    public List<Order> myOrders() {
        return orderService.myOrders();
    }
}
```
