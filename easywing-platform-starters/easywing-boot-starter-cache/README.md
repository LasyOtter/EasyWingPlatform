# EasyWing Cache Starter

多级缓存组件，支持内存缓存、Caffeine、Redis，支持缓存自动刷新、缓存保护。

## 特性

- 多级缓存（本地 + 分布式）
- 注解驱动缓存操作
- 缓存预热
- 缓存保护机制
- 缓存统计

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置

```yaml
easywing:
  cache:
    enabled: true
    levels:
      - type: caffeine
        spec: maximumSize=1000,expireAfterWrite=10m
      - type: redis
        ttl: 30m
    warm-up:
      enabled: true
      initial-delay: 10s
```

### 3. 使用缓存

```java
@Service
public class ProductService {

    @MultiLevelCache(cacheName = "product", key = "#id")
    public Product getProduct(Long id) {
        return productMapper.selectById(id);
    }
}
```

## 注解说明

### @MultiLevelCache

多级缓存注解：

```java
@MultiLevelCache(
    cacheName = "product",
    key = "#id",
    ttl = "10m",
    localFirst = true
)
public Product getProduct(Long id) {
    return productMapper.selectById(id);
}
```

### @CachePut

更新缓存：

```java
@CachePut(cacheName = "product", key = "#product.id")
public Product updateProduct(Product product) {
    return productMapper.update(product);
}
```

### @CacheEvict

删除缓存：

```java
@CacheEvict(cacheName = "product", key = "#id")
public void deleteProduct(Long id) {
    productMapper.deleteById(id);
}

@CacheEvict(cacheName = "product", allEntries = true)
public void clearProductCache() {
}
```

## 多级缓存

```
请求 → Caffeine → Redis → DB
         ↓
      命中返回
```

### 配置示例

```yaml
easywing:
  cache:
    levels:
      # 第一级：本地缓存（Caffeine）
      - type: caffeine
        spec: maximumSize=10000,expireAfterWrite=5m
      # 第二级：分布式缓存（Redis）
      - type: redis
        ttl: 30m
```

## 缓存预热

```java
@Component
public class ProductCacheWarmUp implements CacheWarmUp {

    @Override
    public void warmUp() {
        List<Product> products = productMapper.selectAll();
        products.forEach(p -> {
            MultiLevelCacheService.put("product", p.getId(), p);
        });
    }
}
```

## 缓存统计

```java
@Autowired
private CacheStatsService statsService;

public void printStats() {
    CacheStats stats = statsService.getStats("product");
    System.out.println("Hits: " + stats.getHits());
    System.out.println("Misses: " + stats.getMisses());
    System.out.println("HitRate: " + stats.getHitRate());
}
```
