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
package com.easywing.platform.aid.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.aid.domain.dto.AidVisualSettingDTO;
import com.easywing.platform.aid.domain.entity.AidVisualSetting;
import com.easywing.platform.aid.domain.vo.AidVisualSettingVO;
import com.easywing.platform.aid.enums.SettingScope;
import com.easywing.platform.aid.mapper.AidVisualSettingMapper;
import com.easywing.platform.aid.mapper.struct.AidVisualSettingStructMapper;
import com.easywing.platform.aid.prompt.VisualSnapshot;
import com.easywing.platform.aid.service.AidVisualSettingService;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 视觉设定服务实现。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AidVisualSettingServiceImpl extends ServiceImpl<AidVisualSettingMapper, AidVisualSetting>
        implements AidVisualSettingService {

    /** 模板格式版本，导入时按此判断兼容性。 */
    private static final int TEMPLATE_SCHEMA_VERSION = 1;

    private static final String SCHEMA_VERSION_KEY = "schemaVersion";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final AidVisualSettingMapper visualSettingMapper;
    private final AidVisualSettingStructMapper structMapper;

    @Override
    public Page<AidVisualSettingVO> selectPage(long current, long size, String scope, String name) {
        LambdaQueryWrapper<AidVisualSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(scope), AidVisualSetting::getScope, scope)
                .like(StringUtils.hasText(name), AidVisualSetting::getName, name)
                .orderByDesc(AidVisualSetting::getUpdateTime);

        Page<AidVisualSetting> page = visualSettingMapper.selectPage(new Page<>(current, size), wrapper);
        Page<AidVisualSettingVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(structMapper::toVO).toList());
        return result;
    }

    @Override
    public AidVisualSettingVO selectById(Long id) {
        AidVisualSetting setting = visualSettingMapper.selectById(id);
        if (setting == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "视觉设定不存在: " + id);
        }
        return structMapper.toVO(setting);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertSetting(AidVisualSettingDTO dto) {
        AidVisualSetting entity = structMapper.toEntity(dto);
        normalizeOnSave(entity);
        visualSettingMapper.insert(entity);
        log.info("创建视觉设定成功: id={}, name={}", entity.getId(), entity.getName());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSetting(AidVisualSettingDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "设定ID不能为空");
        }
        AidVisualSetting existing = visualSettingMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "视觉设定不存在: " + dto.getId());
        }
        structMapper.updateEntity(existing, dto);
        normalizeOnSave(existing);
        // 版本自增：下游产物（分镜图/视频）记录该版本，版本变化即需重新生成
        existing.setVersion(existing.getVersion() + 1);
        existing.setSnapshotTime(Instant.now().toEpochMilli());
        visualSettingMapper.updateById(existing);
        log.info("更新视觉设定: id={}, version={}", existing.getId(), existing.getVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSettings(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        visualSettingMapper.deleteBatchIds(ids);
        log.info("删除视觉设定 {} 个", ids.size());
    }

    @Override
    public Map<String, Object> exportTemplate(Long id) {
        AidVisualSetting setting = visualSettingMapper.selectById(id);
        if (setting == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "视觉设定不存在: " + id);
        }

        // 用 LinkedHashMap 固定字段顺序，让导出 JSON 可读、可 diff
        Map<String, Object> template = new LinkedHashMap<>();
        template.put(SCHEMA_VERSION_KEY, TEMPLATE_SCHEMA_VERSION);
        template.put("name", setting.getName());
        template.put("styleDescription", setting.getStyleDescription());
        template.put("colorPalette", parseList(setting.getColorPalette()));
        template.put("subStyles", parseList(setting.getSubStyles()));
        template.put("constraintsList", parseList(setting.getConstraintsList()));
        template.put("negativeWords", parseList(setting.getNegativeWords()));
        template.put("aspectRatio", setting.getAspectRatio());
        template.put("tailFrameSubject", setting.getTailFrameSubject());
        template.put("tailFrameBackground", setting.getTailFrameBackground());
        template.put("tailFrameLight", setting.getTailFrameLight());
        template.put("tailFrameComposition", setting.getTailFrameComposition());
        template.put("tailFrameMood", setting.getTailFrameMood());
        template.put("tailFrameColors", parseList(setting.getTailFrameColors()));
        template.put("exportedAt", new Date());
        return template;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importTemplate(Map<String, Object> templateJson, String name, boolean overwrite) {
        if (templateJson == null || templateJson.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "模板内容不能为空");
        }
        String templateName = StringUtils.hasText(name) ? name : asString(templateJson.get("name"));
        if (!StringUtils.hasText(templateName)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "模板缺少名称且未指定导入名称");
        }

        Long duplicatedId = findDuplicatedTemplateId(templateName);
        if (duplicatedId != null && !overwrite) {
            throw new BizException(ErrorCode.AID_ASSET_CODE_EXISTS, "已存在同名模板「" + templateName + "」");
        }

        AidVisualSetting entity =
                duplicatedId != null ? visualSettingMapper.selectById(duplicatedId) : new AidVisualSetting();
        if (entity == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "待覆盖的模板不存在: " + duplicatedId);
        }
        applyTemplate(entity, templateJson);
        entity.setName(templateName);
        entity.setScope(SettingScope.TEMPLATE.getCode());
        normalizeOnSave(entity);

        if (duplicatedId != null) {
            entity.setVersion(entity.getVersion() + 1);
            entity.setSnapshotTime(Instant.now().toEpochMilli());
            visualSettingMapper.updateById(entity);
            log.info("导入覆盖模板: id={}, name={}", entity.getId(), templateName);
            return entity.getId();
        }
        visualSettingMapper.insert(entity);
        log.info("导入新建模板: id={}, name={}", entity.getId(), templateName);
        return entity.getId();
    }

    @Override
    public VisualSnapshot toSnapshot(Long id) {
        AidVisualSetting setting = visualSettingMapper.selectById(id);
        if (setting == null) {
            return null;
        }
        VisualSnapshot.TailFrameSpec tailFrame = VisualSnapshot.TailFrameSpec.builder()
                .subject(setting.getTailFrameSubject())
                .background(setting.getTailFrameBackground())
                .lightDirection(setting.getTailFrameLight())
                .composition(setting.getTailFrameComposition())
                .mood(setting.getTailFrameMood())
                .requiredColorNames(parseList(setting.getTailFrameColors()))
                .build();

        return VisualSnapshot.builder()
                .styleDescription(setting.getStyleDescription())
                .colorPalette(parseList(setting.getColorPalette()))
                .subStyles(parseList(setting.getSubStyles()))
                .constraints(parseList(setting.getConstraintsList()))
                .negativeWords(parseList(setting.getNegativeWords()))
                .aspectRatio(setting.getAspectRatio())
                .snapshotTime(setting.getSnapshotTime())
                .version(setting.getVersion() == null ? 1 : setting.getVersion())
                .tailFrameSpec(tailFrame)
                .build();
    }

    /**
     * 保存前的规范化：补默认作用域与版本，刷新快照时间。
     *
     * @param entity 实体（新增或更新后的引用）
     */
    private void normalizeOnSave(AidVisualSetting entity) {
        if (!StringUtils.hasText(entity.getScope())) {
            entity.setScope(SettingScope.PROJECT.getCode());
        } else {
            SettingScope.of(entity.getScope());
        }
        if (entity.getVersion() == null) {
            entity.setVersion(1);
        }
        entity.setSnapshotTime(Instant.now().toEpochMilli());
    }

    /** 查同名 TEMPLATE，用于决定导入是新建还是覆盖。 */
    private Long findDuplicatedTemplateId(String name) {
        List<AidVisualSetting> matches = visualSettingMapper.selectList(new LambdaQueryWrapper<AidVisualSetting>()
                .eq(AidVisualSetting::getName, name)
                .eq(AidVisualSetting::getScope, SettingScope.TEMPLATE.getCode())
                .last("LIMIT 1"));
        return matches.isEmpty() ? null : matches.get(0).getId();
    }

    /** 把模板 Map 的字段回填到实体；未知字段忽略，缺失字段保持原值。 */
    private void applyTemplate(AidVisualSetting entity, Map<String, Object> template) {
        putIfPresent(template, "styleDescription", value -> entity.setStyleDescription(asString(value)));
        putIfPresent(template, "colorPalette", value -> entity.setColorPalette(asJson(value)));
        putIfPresent(template, "subStyles", value -> entity.setSubStyles(asJson(value)));
        putIfPresent(template, "constraintsList", value -> entity.setConstraintsList(asJson(value)));
        putIfPresent(template, "negativeWords", value -> entity.setNegativeWords(asJson(value)));
        putIfPresent(template, "aspectRatio", value -> entity.setAspectRatio(asString(value)));
        putIfPresent(template, "tailFrameSubject", value -> entity.setTailFrameSubject(asString(value)));
        putIfPresent(template, "tailFrameBackground", value -> entity.setTailFrameBackground(asString(value)));
        putIfPresent(template, "tailFrameLight", value -> entity.setTailFrameLight(asString(value)));
        putIfPresent(template, "tailFrameComposition", value -> entity.setTailFrameComposition(asString(value)));
        putIfPresent(template, "tailFrameMood", value -> entity.setTailFrameMood(asString(value)));
        putIfPresent(template, "tailFrameColors", value -> entity.setTailFrameColors(asJson(value)));
    }

    private void putIfPresent(Map<String, Object> source, String key, java.util.function.Consumer<Object> setter) {
        if (source.containsKey(key) && source.get(key) != null) {
            setter.accept(source.get(key));
        }
    }

    private static String asString(Object value) {
        String text = String.valueOf(value).trim();
        return "null".equals(text) ? null : text;
    }

    /** 模板里的列表在库内以 JSON 字符串存储。 */
    private static String asJson(Object value) {
        try {
            if (value instanceof List<?> list) {
                List<String> items = list.stream().map(String::valueOf).toList();
                return items.isEmpty() ? null : JSON_MAPPER.writeValueAsString(items);
            }
            return value == null ? null : String.valueOf(value);
        } catch (IOException e) {
            throw new IllegalStateException("视觉设定列表序列化失败", e);
        }
    }

    private List<String> parseList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return JSON_MAPPER.readValue(json, STRING_LIST_TYPE);
        } catch (Exception e) {
            // 历史脏数据不应阻断读取
            log.warn("视觉设定 JSON 字段解析失败，按空列表处理: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
