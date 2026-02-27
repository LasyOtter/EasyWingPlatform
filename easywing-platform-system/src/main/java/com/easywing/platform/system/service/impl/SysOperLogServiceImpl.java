package com.easywing.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.system.domain.entity.SysOperLog;
import com.easywing.platform.system.domain.query.SysOperLogQuery;
import com.easywing.platform.system.domain.vo.SysOperLogVO;
import com.easywing.platform.system.mapper.SysOperLogMapper;
import com.easywing.platform.system.service.SysOperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysOperLogServiceImpl extends ServiceImpl<SysOperLogMapper, SysOperLog> implements SysOperLogService {

    private final SysOperLogMapper operLogMapper;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Page<SysOperLogVO> selectOperLogPage(Page<SysOperLog> page, SysOperLogQuery query) {
        LambdaQueryWrapper<SysOperLog> wrapper = buildQueryWrapper(query);
        Page<SysOperLog> logPage = operLogMapper.selectPage(page, wrapper);
        Page<SysOperLogVO> voPage = new Page<>(logPage.getCurrent(), logPage.getSize(), logPage.getTotal());
        voPage.setRecords(logPage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public SysOperLogVO selectOperLogById(Long operId) {
        SysOperLog log = operLogMapper.selectById(operId);
        return log != null ? convertToVO(log) : null;
    }

    @Override
    @Async
    public int insertOperLog(SysOperLog operLog) { return operLogMapper.insert(operLog); }

    @Override
    public int deleteOperLogByIds(List<Long> operIds) {
        if (CollectionUtils.isEmpty(operIds)) return 0;
        return operLogMapper.deleteBatchIds(operIds);
    }

    @Override
    public void cleanOperLog() { operLogMapper.delete(new LambdaQueryWrapper<>()); }

    private LambdaQueryWrapper<SysOperLog> buildQueryWrapper(SysOperLogQuery query) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        if (query == null) return wrapper.orderByDesc(SysOperLog::getOperTime);
        wrapper.like(StrUtil.isNotBlank(query.getTitle()), SysOperLog::getTitle, query.getTitle())
                .like(StrUtil.isNotBlank(query.getOperName()), SysOperLog::getOperName, query.getOperName())
                .eq(query.getStatus() != null, SysOperLog::getStatus, query.getStatus());
        if (StrUtil.isNotBlank(query.getBeginTime())) {
            wrapper.ge(SysOperLog::getOperTime, LocalDateTime.parse(query.getBeginTime() + " 00:00:00", DATE_FORMATTER));
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            wrapper.le(SysOperLog::getOperTime, LocalDateTime.parse(query.getEndTime() + " 23:59:59", DATE_FORMATTER));
        }
        return wrapper.orderByDesc(SysOperLog::getOperTime);
    }

    private SysOperLogVO convertToVO(SysOperLog log) {
        SysOperLogVO vo = new SysOperLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }
}
