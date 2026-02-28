package com.easywing.platform.system.mapper.struct;

import com.easywing.platform.system.domain.entity.SysOperLog;
import com.easywing.platform.system.domain.vo.SysOperLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OperLogMapper {

    OperLogMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(OperLogMapper.class);

    SysOperLogVO toVO(SysOperLog operLog);

    List<SysOperLogVO> toVOList(List<SysOperLog> operLogs);
}
