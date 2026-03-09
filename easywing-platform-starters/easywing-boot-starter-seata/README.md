# EasyWing Seata Starter

分布式事务解决方案，支持 AT、TCC、Saga、XA 模式。

## 特性

- Seata 分布式事务自动配置
- 支持 AT、TCC、Saga、XA 模式
- 与 Spring Cloud 深度集成

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.easywing.platform</groupId>
    <artifactId>easywing-boot-starter-seata</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置

```yaml
seata:
  enabled: true
  application-id: order-service
  tx-service-group: my_tx_group
  registry:
    type: nacos
    nacos:
      server-addr: localhost:8848
      namespace: seata
  config:
    type: nacos
    nacos:
      server-addr: localhost:8848
```

### 3. 使用分布式事务

```java
@GlobalTransactional
public void createOrder(Order order) {
    // 扣库存
    inventoryService.deduct(order.getProductId(), order.getQuantity());
    
    // 扣余额
    accountService.deduct(order.getUserId(), order.getAmount());
    
    // 创建订单
    orderRepository.save(order);
}
```

## 事务模式

### AT 模式（推荐）

自动补偿事务，最简单易用：

```java
@GlobalTransactional
public void transfer(TransferRequest request) {
    accountService.decrease(request.getFromAccount(), request.getAmount());
    accountService.increase(request.getToAccount(), request.getAmount());
}
```

### TCC 模式

try-confirm-cancel，适用于非数据库操作：

```java
@LocalTCC
public interface StorageService {
    
    @TwoPhaseBusinessAction(
        name = "deduct",
        commitMethod = "confirm",
        rollbackMethod = "cancel"
    )
    boolean tryDeduct(@BusinessActionContextParameter(paramName = "productId") String productId,
                      @BusinessActionContextParameter(paramName = "quantity") int quantity);
    
    boolean confirm(BusinessActionContext context);
    
    boolean cancel(BusinessActionContext context);
}
```

### Saga 模式

长业务流程编排：

```java
@SagaStateMachine(stateMachineJson = "...")
public interface OrderService {
    // ...
}
```

## 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `seata.enabled` | 启用 Seata | false |
| `seata.application-id` | 应用ID | - |
| `seata.tx-service-group` | 事务组 | my_tx_group |
| `seata.registry.type` | 注册中心类型 | file |
| `seata.config.type` | 配置中心类型 | file |
