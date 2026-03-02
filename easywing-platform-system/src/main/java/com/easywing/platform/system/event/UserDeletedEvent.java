package com.easywing.platform.system.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class UserDeletedEvent extends ApplicationEvent {

    private final List<Long> userIds;
    private final String operator;
    private final LocalDateTime deleteTime;

    public UserDeletedEvent(List<Long> userIds, String operator) {
        super(userIds);
        this.userIds = userIds;
        this.operator = operator;
        this.deleteTime = LocalDateTime.now();
    }
}
