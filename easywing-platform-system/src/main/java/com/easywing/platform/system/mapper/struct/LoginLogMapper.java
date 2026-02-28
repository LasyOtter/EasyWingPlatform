package com.easywing.platform.system.mapper.struct;

import com.easywing.platform.system.domain.entity.SysLoginLog;
import com.easywing.platform.system.domain.vo.SysLoginLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LoginLogMapper {

    LoginLogMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(LoginLogMapper.class);

    SysLoginLogVO toVO(SysLoginLog loginLog);

    List<SysLoginLogVO> toVOList(List<SysLoginLog> loginLogs);
}
