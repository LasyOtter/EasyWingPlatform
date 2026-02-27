package com.easywing.platform.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.easywing.platform.system.domain.entity.SysOperLog;
import com.easywing.platform.system.domain.query.SysOperLogQuery;
import com.easywing.platform.system.domain.vo.SysOperLogVO;
import java.util.List;

public interface SysOperLogService extends IService<SysOperLog> {
    Page<SysOperLogVO> selectOperLogPage(Page<SysOperLog> page, SysOperLogQuery query);
    SysOperLogVO selectOperLogById(Long operId);
    int insertOperLog(SysOperLog operLog);
    int deleteOperLogByIds(List<Long> operIds);
    void cleanOperLog();
}
