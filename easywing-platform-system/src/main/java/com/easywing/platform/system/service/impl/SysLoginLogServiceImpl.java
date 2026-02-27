package com.easywing.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.system.domain.entity.SysLoginLog;
import com.easywing.platform.system.domain.query.SysLoginLogQuery;
import com.easywing.platform.system.domain.vo.SysLoginLogVO;
import com.easywing.platform.system.mapper.SysLoginLogMapper;
import com.easywing.platform.system.service.SysLoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysLoginLogServiceImpl extends ServiceImpl<SysLoginLogMapper, SysLoginLog> implements SysLoginLogService {

    private final SysLoginLogMapper loginLogMapper;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Page<SysLoginLogVO> selectLoginLogPage(Page<SysLoginLog> page, SysLoginLogQuery query) {
        LambdaQueryWrapper<SysLoginLog> wrapper = buildQueryWrapper(query);
        Page<SysLoginLog> logPage = loginLogMapper.selectPage(page, wrapper);
        Page<SysLoginLogVO> voPage = new Page<>(logPage.getCurrent(), logPage.getSize(), logPage.getTotal());
        voPage.setRecords(logPage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public SysLoginLogVO selectLoginLogById(Long infoId) {
        SysLoginLog log = loginLogMapper.selectById(infoId);
        return log != null ? convertToVO(log) : null;
    }

    @Override
    public int insertLoginLog(SysLoginLog loginLog) { return loginLogMapper.insert(loginLog); }

    @Override
    public int deleteLoginLogByIds(List<Long> infoIds) {
        if (CollectionUtils.isEmpty(infoIds)) return 0;
        return loginLogMapper.deleteBatchIds(infoIds);
    }

    @Override
    public void cleanLoginLog() { loginLogMapper.delete(new LambdaQueryWrapper<>()); }

    private LambdaQueryWrapper<SysLoginLog> buildQueryWrapper(SysLoginLogQuery query) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        if (query == null) return wrapper.orderByDesc(SysLoginLog::getLoginTime);
        wrapper.like(StrUtil.isNotBlank(query.getUsername()), SysLoginLog::getUsername, query.getUsername())
                .eq(query.getStatus() != null, SysLoginLog::getStatus, query.getStatus());
        if (StrUtil.isNotBlank(query.getBeginTime())) {
            wrapper.ge(SysLoginLog::getLoginTime, LocalDateTime.parse(query.getBeginTime() + " 00:00:00", DATE_FORMATTER));
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            wrapper.le(SysLoginLog::getLoginTime, LocalDateTime.parse(query.getEndTime() + " 23:59:59", DATE_FORMATTER));
        }
        return wrapper.orderByDesc(SysLoginLog::getLoginTime);
    }

    private SysLoginLogVO convertToVO(SysLoginLog log) {
        SysLoginLogVO vo = new SysLoginLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }
}
