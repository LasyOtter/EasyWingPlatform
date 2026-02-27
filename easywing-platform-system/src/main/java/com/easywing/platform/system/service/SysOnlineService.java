package com.easywing.platform.system.service;

import com.easywing.platform.system.domain.vo.SysOnlineVO;
import java.util.List;

public interface SysOnlineService {
    List<SysOnlineVO> selectOnlineList(String username);
    void forceLogout(String sessionId);
    long getOnlineCount();
}
