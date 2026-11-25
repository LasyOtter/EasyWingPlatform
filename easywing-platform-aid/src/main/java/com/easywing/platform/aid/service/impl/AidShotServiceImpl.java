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
import com.easywing.platform.aid.domain.dto.AidShotDTO;
import com.easywing.platform.aid.domain.entity.AidShot;
import com.easywing.platform.aid.domain.vo.AidShotVO;
import com.easywing.platform.aid.enums.ShotStatus;
import com.easywing.platform.aid.mapper.AidEpisodeMapper;
import com.easywing.platform.aid.mapper.AidShotMapper;
import com.easywing.platform.aid.mapper.struct.AidShotStructMapper;
import com.easywing.platform.aid.service.AidShotService;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 分镜服务实现。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AidShotServiceImpl extends ServiceImpl<AidShotMapper, AidShot> implements AidShotService {

    private final AidEpisodeMapper episodeMapper;
    private final AidShotMapper shotMapper;
    private final AidShotStructMapper structMapper;

    @Override
    public Page<AidShotVO> selectPage(long current, long size, Long episodeId) {
        LambdaQueryWrapper<AidShot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(episodeId != null, AidShot::getEpisodeId, episodeId).orderByAsc(AidShot::getShotNo);

        Page<AidShot> page = shotMapper.selectPage(new Page<>(current, size), wrapper);
        Page<AidShotVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(structMapper::toVO).toList());
        return result;
    }

    @Override
    public AidShotVO selectById(Long id) {
        AidShot shot = shotMapper.selectById(id);
        if (shot == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "分镜不存在: " + id);
        }
        return structMapper.toVO(shot);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertShot(AidShotDTO dto) {
        requireEpisodeExists(dto.getEpisodeId());
        requireShotNoUnique(dto.getEpisodeId(), dto.getShotNo(), null);

        AidShot entity = structMapper.toEntity(dto);
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(ShotStatus.DRAFT.getCode());
        } else {
            ShotStatus.of(entity.getStatus());
        }
        shotMapper.insert(entity);
        log.info("创建分镜成功: id={}, episodeId={}, no={}", entity.getId(), entity.getEpisodeId(), entity.getShotNo());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShot(AidShotDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "分镜ID不能为空");
        }
        AidShot existing = shotMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "分镜不存在: " + dto.getId());
        }
        if (dto.getEpisodeId() != null && !dto.getEpisodeId().equals(existing.getEpisodeId())) {
            throw new BizException(ErrorCode.AID_TASK_STATE_INVALID, "不允许跨分集移动分镜");
        }
        requireShotNoUnique(existing.getEpisodeId(), dto.getShotNo(), dto.getId());

        structMapper.updateEntity(existing, dto);
        if (StringUtils.hasText(existing.getStatus())) {
            ShotStatus.of(existing.getStatus());
        }
        shotMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteShots(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        shotMapper.deleteBatchIds(ids);
        log.info("删除分镜 {} 个", ids.size());
    }

    private void requireShotNoUnique(Long episodeId, Integer shotNo, Long excludeId) {
        if (episodeId == null || shotNo == null) {
            return;
        }
        LambdaQueryWrapper<AidShot> wrapper = new LambdaQueryWrapper<AidShot>()
                .eq(AidShot::getEpisodeId, episodeId)
                .eq(AidShot::getShotNo, shotNo);
        if (excludeId != null) {
            wrapper.ne(AidShot::getId, excludeId);
        }
        if (shotMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.AID_ASSET_CODE_EXISTS, "镜号已存在: " + shotNo);
        }
    }

    private void requireEpisodeExists(Long episodeId) {
        if (episodeId == null || episodeMapper.selectById(episodeId) == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "所属分集不存在: " + episodeId);
        }
    }
}
