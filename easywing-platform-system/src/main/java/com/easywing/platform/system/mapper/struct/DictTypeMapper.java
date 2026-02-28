package com.easywing.platform.system.mapper.struct;

import com.easywing.platform.system.domain.entity.SysDictType;
import com.easywing.platform.system.domain.vo.SysDictTypeVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DictTypeMapper {

    DictTypeMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(DictTypeMapper.class);

    SysDictTypeVO toVO(SysDictType dictType);

    List<SysDictTypeVO> toVOList(List<SysDictType> dictTypes);
}
