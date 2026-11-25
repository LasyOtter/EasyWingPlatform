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
package com.easywing.platform.aid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.aid.domain.dto.AidVisualSettingDTO;
import com.easywing.platform.aid.domain.entity.AidVisualSetting;
import com.easywing.platform.aid.mapper.AidVisualSettingMapper;
import com.easywing.platform.aid.mapper.struct.AidVisualSettingStructMapperImpl;
import com.easywing.platform.aid.prompt.VisualSnapshot;
import com.easywing.platform.aid.service.impl.AidVisualSettingServiceImpl;
import com.easywing.platform.core.exception.BizException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 视觉设定服务单元测试（AC-11：可配置、可导出、可导入，导入后配置生效）。
 * <p>
 * 用 Mockito 打桩 Mapper，聚焦「导出→导入」的语义与字段映射；起真实数据库只会拖慢反馈，
 * 表结构与 SQL 正确性由 Flyway + 集成测试覆盖。
 * </p>
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AidVisualSettingServiceTest {

    @Mock
    private AidVisualSettingMapper visualSettingMapper;

    /** MapStruct 生成实现，直接复用真实转换逻辑，避免手写替身漏掉字段。 */
    @Spy
    private AidVisualSettingStructMapperImpl structMapper = new AidVisualSettingStructMapperImpl();

    @InjectMocks
    private AidVisualSettingServiceImpl service;

    private static AidVisualSettingDTO sampleDto() {
        AidVisualSettingDTO dto = new AidVisualSettingDTO();
        dto.setName("厚涂国风");
        dto.setStyleDescription("厚涂写实国风，电影级光影");
        dto.setColorPalette(List.of("玄青 #1A2E4A", "朱砂 #C0392B"));
        dto.setSubStyles(List.of("国漫", "奇幻"));
        dto.setConstraintsList(List.of("主体居中构图", "保持角色五官一致"));
        dto.setNegativeWords(List.of("模糊", "变形手"));
        dto.setAspectRatio("9:16");
        dto.setTailFrameSubject("夸父背影");
        dto.setTailFrameColors(List.of("玄青", "朱砂"));
        return dto;
    }

    /** AC-11：未指定作用域时回落 PROJECT，且版本/快照时间被填充。 */
    @Test
    void insertFillsDefaultScopeAndVersion() {
        when(visualSettingMapper.insert(any(AidVisualSetting.class))).thenAnswer(inv -> {
            ((AidVisualSetting) inv.getArgument(0)).setId(1L);
            return 1;
        });

        Long id = service.insertSetting(sampleDto());

        assertThat(id).isEqualTo(1L);
        AidVisualSetting saved = captureInserted();
        assertThat(saved.getScope()).isEqualTo("PROJECT");
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getSnapshotTime()).isPositive();
        // DTO 的 List 必须落到库内 JSON 列，否则前端配好的色盘会丢
        assertThat(saved.getColorPalette()).contains("玄青 #1A2E4A", "朱砂 #C0392B");
        assertThat(saved.getTailFrameColors()).contains("玄青");
    }

    /** AC-11：导出的 JSON 必须含色盘等关键字段，结构稳定可直接回灌。 */
    @Test
    void exportProducesImportableTemplate() {
        when(visualSettingMapper.selectById(7L)).thenReturn(entity(7L, "厚涂国风"));

        Map<String, Object> template = service.exportTemplate(7L);

        assertThat(template).containsEntry("schemaVersion", 1);
        assertThat(template).containsEntry("name", "厚涂国风");
        assertThat(template.get("colorPalette")).isEqualTo(List.of("玄青 #1A2E4A", "朱砂 #C0392B"));
        assertThat(template.get("tailFrameColors")).isEqualTo(List.of("玄青", "朱砂"));
        assertThat(template).containsEntry("aspectRatio", "9:16");
    }

    /** AC-11：导入新建模板，作用域固定为 TEMPLATE 以区别于项目级设定。 */
    @Test
    void importCreatesNewTemplateWhenNameFree() {
        when(visualSettingMapper.selectList(any())).thenReturn(List.of());
        when(visualSettingMapper.insert(any(AidVisualSetting.class))).thenAnswer(inv -> {
            ((AidVisualSetting) inv.getArgument(0)).setId(3L);
            return 1;
        });

        Long id = service.importTemplate(
                Map.of(
                        "name", "导入的国风",
                        "styleDescription", "厚涂写实国风",
                        "colorPalette", List.of("玄青 #1A2E4A"),
                        "aspectRatio", "9:16"),
                null,
                false);

        assertThat(id).isEqualTo(3L);
        AidVisualSetting saved = captureInserted();
        assertThat(saved.getScope()).isEqualTo("TEMPLATE");
        assertThat(saved.getName()).isEqualTo("导入的国风");
        assertThat(saved.getColorPalette()).isEqualTo("[\"玄青 #1A2E4A\"]");
    }

    /** AC-11：同名模板默认拒绝覆盖，避免误改历史模板。 */
    @Test
    void importRejectsDuplicateNameUnlessOverwrite() {
        when(visualSettingMapper.selectList(any())).thenReturn(List.of(entity(9L, "厚涂国风")));

        assertThatThrownBy(() -> service.importTemplate(Map.of("name", "厚涂国风"), null, false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("同名模板");
    }

    /** 同名且显式 overwrite=true 时走更新并自增版本。 */
    @Test
    void importOverwritesSameNameWhenRequested() {
        when(visualSettingMapper.selectList(any())).thenReturn(List.of(entity(9L, "厚涂国风")));
        when(visualSettingMapper.selectById(9L)).thenReturn(entity(9L, "厚涂国风"));
        when(visualSettingMapper.updateById(any(AidVisualSetting.class))).thenReturn(1);

        Long id = service.importTemplate(Map.of("name", "厚涂国风", "colorPalette", List.of("黛蓝")), null, true);

        assertThat(id).isEqualTo(9L);
        ArgumentCaptor<AidVisualSetting> captor = ArgumentCaptor.forClass(AidVisualSetting.class);
        verify(visualSettingMapper).updateById(captor.capture());
        // entity() 固定 version=2，覆盖导入后应自增为 3
        assertThat(captor.getValue().getVersion()).isEqualTo(3);
    }

    /** 缺少名称且未显式命名时必须报错，否则会落库一条无名模板。 */
    @Test
    void importRequiresName() {
        assertThatThrownBy(() -> service.importTemplate(Map.of("styleDescription", "无名的"), null, false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("名称");
    }

    /** 快照转换：库内 JSON 列还原为列表，供提示词编译器直接使用。 */
    @Test
    void toSnapshotConvertsJsonColumns() {
        when(visualSettingMapper.selectById(1L)).thenReturn(entity(1L, "厚涂国风"));

        VisualSnapshot snapshot = service.toSnapshot(1L);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getStyleDescription()).isEqualTo(entity(1L, "厚涂国风").getStyleDescription());
        assertThat(snapshot.getColorPalette()).containsExactly("玄青 #1A2E4A", "朱砂 #C0392B");
        assertThat(snapshot.getSubStyles()).containsExactly("国漫", "奇幻");
        assertThat(snapshot.getConstraints()).containsExactly("主体居中构图", "保持角色五官一致");
        assertThat(snapshot.getNegativeWords()).containsExactly("模糊", "变形手");
        assertThat(snapshot.getAspectRatio()).isEqualTo("9:16");
        assertThat(snapshot.getVersion()).isEqualTo(entity(1L, "厚涂国风").getVersion());
        assertThat(snapshot.getTailFrameSpec()).isNotNull();
        assertThat(snapshot.getTailFrameSpec().getSubject()).isEqualTo("夸父背影");
        assertThat(snapshot.getTailFrameSpec().getRequiredColorNames()).containsExactly("玄青", "朱砂");
    }

    /** 脏数据容错：JSON 解析失败降级为空列表而非抛异常，避免一条脏数据让整页打不开。 */
    @Test
    void toSnapshotToleratesMalformedJson() {
        AidVisualSetting broken = entity(2L, "坏数据");
        broken.setColorPalette("not-a-json");
        broken.setVersion(1);
        when(visualSettingMapper.selectById(2L)).thenReturn(broken);

        VisualSnapshot snapshot = service.toSnapshot(2L);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getColorPalette()).isEmpty();
    }

    /** 设定不存在时返回 null，交由调用方决定降级还是报错。 */
    @Test
    void toSnapshotReturnsNullWhenMissing() {
        when(visualSettingMapper.selectById(999L)).thenReturn(null);

        assertThat(service.toSnapshot(999L)).isNull();
    }

    /** 未知作用域必须显式报错，而不是静默落库变成脏状态。 */
    @Test
    void insertRejectsUnknownScope() {
        AidVisualSettingDTO dto = sampleDto();
        dto.setScope("BOGUS");

        assertThatThrownBy(() -> service.insertSetting(dto)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 修改时版本自增：这是下游产物判定失效的依据。 */
    @Test
    void updateIncrementsVersion() {
        int originalVersion = 6;
        AidVisualSetting existing = entity(5L, "厚涂国风");
        existing.setVersion(originalVersion);
        when(visualSettingMapper.selectById(5L)).thenReturn(existing);
        when(visualSettingMapper.updateById(any(AidVisualSetting.class))).thenReturn(1);

        AidVisualSettingDTO dto = sampleDto();
        dto.setId(5L);
        service.updateSetting(dto);

        ArgumentCaptor<AidVisualSetting> captor = ArgumentCaptor.forClass(AidVisualSetting.class);
        verify(visualSettingMapper).updateById(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(originalVersion + 1);
    }

    /** 分页查询空结果不应触发任何统计或转换。 */
    @Test
    void emptyPageReturnsEmptyRecords() {
        Page<AidVisualSetting> raw = new Page<>(1, 10, 0);
        when(visualSettingMapper.selectPage(any(), any())).thenReturn(raw);

        Page<?> result = service.selectPage(1, 10, null, null);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    private AidVisualSetting captureInserted() {
        ArgumentCaptor<AidVisualSetting> captor = ArgumentCaptor.forClass(AidVisualSetting.class);
        verify(visualSettingMapper).insert(captor.capture());
        return captor.getValue();
    }

    private static AidVisualSetting entity(Long id, String name) {
        AidVisualSetting setting = new AidVisualSetting();
        setting.setId(id);
        setting.setName(name);
        setting.setScope("TEMPLATE");
        setting.setStyleDescription("厚涂写实国风，电影级光影");
        setting.setColorPalette("[\"玄青 #1A2E4A\",\"朱砂 #C0392B\"]");
        setting.setSubStyles("[\"国漫\",\"奇幻\"]");
        setting.setConstraintsList("[\"主体居中构图\",\"保持角色五官一致\"]");
        setting.setNegativeWords("[\"模糊\",\"变形手\"]");
        setting.setAspectRatio("9:16");
        setting.setTailFrameSubject("夸父背影");
        setting.setTailFrameColors("[\"玄青\",\"朱砂\"]");
        setting.setSnapshotTime(1_700_000_000_000L);
        setting.setVersion(2);
        return setting;
    }
}
