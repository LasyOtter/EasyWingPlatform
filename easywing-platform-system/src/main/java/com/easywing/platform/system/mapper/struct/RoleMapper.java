package com.easywing.platform.system.mapper.struct;

import com.easywing.platform.system.domain.dto.SysRoleDTO;
import com.easywing.platform.system.domain.entity.SysRole;
import com.easywing.platform.system.domain.vo.SysRoleVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoleMapper {

    RoleMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(RoleMapper.class);

    SysRoleVO toVO(SysRole role);

    SysRole toEntity(SysRoleDTO dto);

    List<SysRoleVO> toVOList(List<SysRole> roles);
}
