package com.easywing.platform.system.mapper.struct;

import com.easywing.platform.system.domain.entity.SysDept;
import com.easywing.platform.system.domain.vo.SysDeptVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DeptMapper {

    DeptMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(DeptMapper.class);

    SysDeptVO toVO(SysDept dept);

    List<SysDeptVO> toVOList(List<SysDept> depts);
}
