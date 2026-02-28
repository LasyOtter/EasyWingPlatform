package com.easywing.platform.system.mapper.struct;

import com.easywing.platform.system.domain.entity.SysPost;
import com.easywing.platform.system.domain.vo.SysPostVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostMapper {

    PostMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(PostMapper.class);

    SysPostVO toVO(SysPost post);

    List<SysPostVO> toVOList(List<SysPost> posts);
}
