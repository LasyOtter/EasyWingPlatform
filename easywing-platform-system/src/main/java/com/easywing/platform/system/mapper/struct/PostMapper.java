package com.easywing.platform.system.mapper.struct;

import com.easywing.platform.system.domain.entity.SysPost;
import com.easywing.platform.system.domain.vo.SysPostVO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostMapper {

    PostMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(PostMapper.class);

    SysPostVO toVO(SysPost post);

    List<SysPostVO> toVOList(List<SysPost> posts);
}
