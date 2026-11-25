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

import com.easywing.platform.aid.domain.dto.AidVisualSettingDTO;
import com.easywing.platform.aid.domain.entity.AidVisualSetting;
import com.easywing.platform.aid.domain.vo.AidVisualSettingVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * 视觉设定 DTO/VO 转换。
 * <p>
 * 实体把列表字段存成 JSON 字符串（{@code colorPalette} / {@code subStyles} 等），
 * DTO/VO 则暴露为 {@code List<String>}，因此这里显式声明序列化方向：
 * entity ↔ VO 走 JSON 反序列化，DTO → entity 走 JSON 序列化。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class AidVisualSettingStructMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    /**
     * entity → VO：JSON 列还原为列表。
     * <p>{@code snapshotTime}/{@code version} 由 MapStruct 按同名字段直传。</p>
     */
    @Mapping(target = "colorPalette", source = "colorPalette", qualifiedByName = "jsonToList")
    @Mapping(target = "subStyles", source = "subStyles", qualifiedByName = "jsonToList")
    @Mapping(target = "constraintsList", source = "constraintsList", qualifiedByName = "jsonToList")
    @Mapping(target = "negativeWords", source = "negativeWords", qualifiedByName = "jsonToList")
    @Mapping(target = "tailFrameColors", source = "tailFrameColors", qualifiedByName = "jsonToList")
    public abstract AidVisualSettingVO toVO(AidVisualSetting entity);

    /**
     * DTO → entity：列表序列化为 JSON 列。
     */
    @Mapping(target = "colorPalette", source = "colorPalette", qualifiedByName = "listToJson")
    @Mapping(target = "subStyles", source = "subStyles", qualifiedByName = "listToJson")
    @Mapping(target = "constraintsList", source = "constraintsList", qualifiedByName = "listToJson")
    @Mapping(target = "negativeWords", source = "negativeWords", qualifiedByName = "listToJson")
    @Mapping(target = "tailFrameColors", source = "tailFrameColors", qualifiedByName = "listToJson")
    public abstract AidVisualSetting toEntity(AidVisualSettingDTO dto);

    /** 部分更新：DTO 中为 null 的字段不覆盖库内值。 */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "colorPalette", source = "colorPalette", qualifiedByName = "listToJson")
    @Mapping(target = "subStyles", source = "subStyles", qualifiedByName = "listToJson")
    @Mapping(target = "constraintsList", source = "constraintsList", qualifiedByName = "listToJson")
    @Mapping(target = "negativeWords", source = "negativeWords", qualifiedByName = "listToJson")
    @Mapping(target = "tailFrameColors", source = "tailFrameColors", qualifiedByName = "listToJson")
    public abstract void updateEntity(@MappingTarget AidVisualSetting entity, AidVisualSettingDTO dto);

    /**
     * JSON 字符串转列表；null/空白一律返回空列表，避免前端拿到 null。
     *
     * @param json 库内 JSON 列
     * @return 字符串列表，永不为 null
     */
    @Named("jsonToList")
    protected List<String> jsonToList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST);
        } catch (Exception e) {
            // 历史脏数据不应让整条记录读取失败，降级为空列表并保留原始内容在库里
            return Collections.emptyList();
        }
    }

    /**
     * 列表转 JSON 字符串；空列表返回 null，让库内保持 NULL 语义而非 "[]"。
     *
     * @param list 列表
     * @return JSON 字符串，空时返回 null
     */
    @Named("listToJson")
    protected String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            throw new IllegalStateException("视觉设定的列表字段序列化失败", e);
        }
    }
}
