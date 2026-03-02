package com.easywing.platform.system.event.listener;

import com.easywing.platform.system.event.UserDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletedEventListener {

    private final StringRedisTemplate redisTemplate;

    @Async
    @EventListener
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Processing user deleted event: userCount={}, operator={}",
                event.getUserIds().size(), event.getOperator());

        try {
            // 1. 清理用户缓存
            for (Long userId : event.getUserIds()) {
                redisTemplate.delete("user:" + userId);
                redisTemplate.delete("user:permissions:" + userId);
                redisTemplate.delete("user:roles:" + userId);
            }
            log.debug("Deleted user caches: {}", event.getUserIds());

            // 2. 强制用户下线（删除会话）
            for (Long userId : event.getUserIds()) {
                redisTemplate.delete("user:session:" + userId);
                redisTemplate.delete("user:token:" + userId);
            }
            log.debug("Deleted user sessions: {}", event.getUserIds());

            log.info("User deleted event processed successfully: userCount={}",
                    event.getUserIds().size());
        } catch (Exception e) {
            log.error("Failed to process user deleted event: userIds={}",
                    event.getUserIds(), e);
        }
    }
}
