package com.easywing.platform.system.mapper.struct;

import com.easywing.platform.system.domain.entity.SysDictData;
import com.easywing.platform.system.domain.vo.SysDictDataVO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DictDataMapper {

    DictDataMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(DictDataMapper.class);

    SysDictDataVO toVO(SysDictData dictData);

    List<SysDictDataVO> toVOList(List<SysDictData> dictDataList);
}
