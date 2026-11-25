/*
 * Copyright 2024-2026 EasyWing Platform Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.easywing.platform.aid.mapper.struct;

import com.easywing.platform.aid.domain.dto.AidShotDTO;
import com.easywing.platform.aid.domain.entity.AidShot;
import com.easywing.platform.aid.domain.vo.AidShotVO;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * 分镜 DTO/VO 转换。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AidShotStructMapper {

    AidShotStructMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(AidShotStructMapper.class);

    AidShotVO toVO(AidShot entity);

    AidShot toEntity(AidShotDTO dto);

    /** 部分更新：DTO 中为 null 的字段不覆盖库内值，产物字段（imageUrl/videoUrl/promptFinal）不接受前端写入。 */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget AidShot entity, AidShotDTO dto);

    List<AidShotVO> toVOList(List<AidShot> entities);
}
