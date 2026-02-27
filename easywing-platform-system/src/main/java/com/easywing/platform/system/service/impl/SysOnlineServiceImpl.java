package com.easywing.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.easywing.platform.system.domain.vo.SysOnlineVO;
import com.easywing.platform.system.service.SysOnlineService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SysOnlineServiceImpl implements SysOnlineService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String ONLINE_KEY_PREFIX = "online:user:";

    @Override
    public List<SysOnlineVO> selectOnlineList(String username) {
        Set<String> keys = redisTemplate.keys(ONLINE_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return new ArrayList<>();
        List<SysOnlineVO> onlineList = new ArrayList<>();
        for (String key : keys) {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof SysOnlineVO online) {
                if (StrUtil.isBlank(username) || online.getUsername().contains(username)) {
                    onlineList.add(online);
                }
            }
        }
        return onlineList;
    }

    @Override
    public void forceLogout(String sessionId) { redisTemplate.delete(ONLINE_KEY_PREFIX + sessionId); }

    @Override
    public long getOnlineCount() {
        Set<String> keys = redisTemplate.keys(ONLINE_KEY_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }

    public void saveOnlineUser(SysOnlineVO onlineVO) {
        String key = ONLINE_KEY_PREFIX + onlineVO.getSessionId();
        redisTemplate.opsForValue().set(key, onlineVO, 30, TimeUnit.MINUTES);
    }
}
