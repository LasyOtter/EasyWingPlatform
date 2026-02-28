package com.easywing.platform.system.mapper.struct;

import com.easywing.platform.system.domain.dto.SysUserDTO;
import com.easywing.platform.system.domain.entity.SysUser;
import com.easywing.platform.system.domain.vo.SysUserVO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(UserMapper.class);

    SysUserVO toVO(SysUser user);

    SysUser toEntity(SysUserDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget SysUser user, SysUserDTO dto);

    List<SysUserVO> toVOList(List<SysUser> users);
}
