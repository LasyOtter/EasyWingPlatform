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

import com.easywing.platform.aid.domain.dto.AidAssetDTO;
import com.easywing.platform.aid.domain.entity.AidAsset;
import com.easywing.platform.aid.domain.vo.AidAssetVO;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * 资产库 DTO/VO 转换。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AidAssetStructMapper {

    AidAssetStructMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(AidAssetStructMapper.class);

    AidAssetVO toVO(AidAsset entity);

    AidAsset toEntity(AidAssetDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget AidAsset entity, AidAssetDTO dto);

    List<AidAssetVO> toVOList(List<AidAsset> entities);
}
