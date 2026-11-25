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
import com.easywing.platform.aid.domain.dto.AidShotDTO;
import com.easywing.platform.aid.domain.entity.AidEpisode;
import com.easywing.platform.aid.domain.entity.AidShot;
import com.easywing.platform.aid.mapper.AidEpisodeMapper;
import com.easywing.platform.aid.mapper.AidShotMapper;
import com.easywing.platform.aid.mapper.struct.AidShotStructMapperImpl;
import com.easywing.platform.aid.service.impl.AidShotServiceImpl;
import com.easywing.platform.core.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 分镜服务单元测试。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AidShotServiceTest {

    @Mock
    private AidEpisodeMapper episodeMapper;

    @Mock
    private AidShotMapper shotMapper;

    @Spy
    private AidShotStructMapperImpl structMapper = new AidShotStructMapperImpl();

    @InjectMocks
    private AidShotServiceImpl service;

    @Test
    void insertFillsDraftStatusAndPersistsShotNo() {
        when(episodeMapper.selectById(1L)).thenReturn(new AidEpisode());
        when(shotMapper.selectCount(any())).thenReturn(0L);
        when(shotMapper.insert(any(AidShot.class))).thenAnswer(inv -> {
            ((AidShot) inv.getArgument(0)).setId(7L);
            return 1;
        });

        Long id = service.insertShot(dto());

        assertThat(id).isEqualTo(7L);
        ArgumentCaptor<AidShot> captor = ArgumentCaptor.forClass(AidShot.class);
        verify(shotMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(captor.getValue().getShotNo()).isEqualTo(1);
        assertThat(captor.getValue().getEpisodeId()).isEqualTo(1L);
    }

    /** 同集内镜号唯一：重号会让时间轴排序与产物回写都出现歧义。 */
    @Test
    void insertRejectsDuplicateShotNo() {
        when(episodeMapper.selectById(1L)).thenReturn(new AidEpisode());
        when(shotMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.insertShot(dto()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("镜号已存在");
    }

    @Test
    void insertRejectsMissingEpisode() {
        when(episodeMapper.selectById(99L)).thenReturn(null);

        AidShotDTO dto = dto();
        dto.setEpisodeId(99L);

        assertThatThrownBy(() -> service.insertShot(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("分集不存在");
    }

    /** 不允许跨分集移动分镜：会连带影响已生成的产物归属。 */
    @Test
    void updateRejectsCrossEpisodeMove() {
        AidShot existing = new AidShot();
        existing.setId(5L);
        existing.setEpisodeId(1L);
        existing.setShotNo(1);
        when(shotMapper.selectById(5L)).thenReturn(existing);

        AidShotDTO dto = dto();
        dto.setId(5L);
        dto.setEpisodeId(2L);

        assertThatThrownBy(() -> service.updateShot(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("跨分集");
    }

    @Test
    void updateRequiresId() {
        assertThatThrownBy(() -> service.updateShot(dto()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("分镜ID不能为空");
    }

    @Test
    void deleteRemovesShots() {
        service.deleteShots(java.util.List.of(1L, 2L));
        verify(shotMapper).deleteBatchIds(java.util.List.of(1L, 2L));
    }

    @Test
    void emptyPageReturnsEmptyRecords() {
        Page<AidShot> raw = new Page<>(1, 10, 0);
        when(shotMapper.selectPage(any(), any())).thenReturn(raw);

        Page<?> result = service.selectPage(1, 10, 1L);

        assertThat(result.getRecords()).isEmpty();
    }

    private static AidShotDTO dto() {
        AidShotDTO dto = new AidShotDTO();
        dto.setEpisodeId(1L);
        dto.setShotNo(1);
        dto.setDescription("夸父追日，汗流浃背");
        return dto;
    }
}
