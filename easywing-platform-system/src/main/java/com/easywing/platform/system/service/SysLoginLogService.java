package com.easywing.platform.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.easywing.platform.system.domain.entity.SysLoginLog;
import com.easywing.platform.system.domain.query.SysLoginLogQuery;
import com.easywing.platform.system.domain.vo.SysLoginLogVO;
import java.util.List;

public interface SysLoginLogService extends IService<SysLoginLog> {
    Page<SysLoginLogVO> selectLoginLogPage(Page<SysLoginLog> page, SysLoginLogQuery query);
    SysLoginLogVO selectLoginLogById(Long infoId);
    int insertLoginLog(SysLoginLog loginLog);
    int deleteLoginLogByIds(List<Long> infoIds);
    void cleanLoginLog();
}
