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
import com.easywing.platform.aid.domain.dto.AidAssetDTO;
import com.easywing.platform.aid.domain.entity.AidAsset;
import com.easywing.platform.aid.domain.entity.AidProject;
import com.easywing.platform.aid.domain.query.AidAssetQuery;
import com.easywing.platform.aid.mapper.AidAssetMapper;
import com.easywing.platform.aid.mapper.AidProjectMapper;
import com.easywing.platform.aid.mapper.struct.AidAssetStructMapperImpl;
import com.easywing.platform.aid.service.impl.AidAssetServiceImpl;
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
 * 资产库服务单元测试（AC-11：资产编号支持自定义）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AidAssetServiceTest {

    @Mock
    private AidAssetMapper assetMapper;

    @Mock
    private AidProjectMapper projectMapper;

    @Spy
    private AidAssetStructMapperImpl structMapper = new AidAssetStructMapperImpl();

    @InjectMocks
    private AidAssetServiceImpl service;

    /** AC-11：用户按习惯写 C01 / S01 / P01 这类编号应被原样保留。 */
    @Test
    void insertKeepsUserDefinedCode() {
        when(projectMapper.selectById(1L)).thenReturn(new AidProject());
        when(assetMapper.selectCount(any())).thenReturn(0L);
        when(assetMapper.insert(any(AidAsset.class))).thenAnswer(inv -> {
            ((AidAsset) inv.getArgument(0)).setId(100L);
            return 1;
        });

        Long id = service.insertAsset(dto("S01"));

        assertThat(id).isEqualTo(100L);
        ArgumentCaptor<AidAsset> captor = ArgumentCaptor.forClass(AidAsset.class);
        verify(assetMapper).insert(captor.capture());
        assertThat(captor.getValue().getAssetCode()).isEqualTo("S01");
        // 类型缺省回落 CHARACTER，避免库内出现 NULL 让前端筛选漏项
        assertThat(captor.getValue().getAssetType()).isEqualTo("CHARACTER");
    }

    /** 同项目编号重复必须拒绝：编号是提示词引用的键，重号会让引用歧义。 */
    @Test
    void insertRejectsDuplicateCodeInSameProject() {
        when(projectMapper.selectById(1L)).thenReturn(new AidProject());
        when(assetMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.insertAsset(dto("C01")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("资产编号已存在");
    }

    /** 项目不存在时应报错而不是落一条孤儿资产。 */
    @Test
    void insertRejectsMissingProject() {
        when(projectMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> {
                    AidAssetDTO dto = dto("C01");
                    dto.setProjectId(9L);
                    service.insertAsset(dto);
                })
                .isInstanceOf(BizException.class)
                .hasMessageContaining("项目不存在");
    }

    /** 分页必须强制带 projectId，否则会退化成跨项目全表扫描。 */
    @Test
    void pageRequiresProjectId() {
        AidAssetQuery query = new AidAssetQuery();
        query.setProjectId(null);

        assertThatThrownBy(() -> service.selectAssetPage(1, 10, query))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("项目ID不能为空");
    }

    /** 编号标注格式供提示词编译器使用：编号:名称·首句描述。 */
    @Test
    @SuppressWarnings("unchecked")
    void annotationUsesCodeNameAndFirstSentence() {
        AidAsset character = new AidAsset();
        character.setAssetCode("C01");
        character.setName("夸父");
        character.setDescription("身形高大，赤裸上身。\n第二句不应出现");
        character.setAssetType("CHARACTER");

        // 不传 wrapper 打桩（方法内部必然传 null 语义的查询条件对象），避开纯 Mockito 下
        // 无法初始化 MyBatis-Plus TableInfo lambda 缓存的限制
        when(assetMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(character));

        Map<String, String> annotations = service.selectAnnotationByCodes(1L);

        assertThat(annotations).containsEntry("C01", "C01:夸父·身形高大，赤裸上身。");
    }

    /** 空结果分页不应触发转换异常。 */
    @Test
    void emptyPageReturnsEmptyRecords() {
        Page<AidAsset> raw = new Page<>(1, 10, 0);
        when(assetMapper.selectPage(any(), any())).thenReturn(raw);

        AidAssetQuery query = new AidAssetQuery();
        query.setProjectId(1L);

        Page<?> result = service.selectAssetPage(1, 10, query);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    /** 未知类型应显式报错而非静默落库。 */
    @Test
    void insertRejectsUnknownAssetType() {
        when(projectMapper.selectById(1L)).thenReturn(new AidProject());
        when(assetMapper.selectCount(any())).thenReturn(0L);

        AidAssetDTO dto = dto("C01");
        dto.setAssetType("BOGUS");

        assertThatThrownBy(() -> service.insertAsset(dto)).isInstanceOf(IllegalArgumentException.class);
    }

    private static AidAssetDTO dto(String code) {
        AidAssetDTO dto = new AidAssetDTO();
        dto.setProjectId(1L);
        dto.setAssetCode(code);
        dto.setName("夸父");
        return dto;
    }
}
