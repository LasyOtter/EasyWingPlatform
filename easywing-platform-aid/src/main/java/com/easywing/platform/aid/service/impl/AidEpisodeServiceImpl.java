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
import com.easywing.platform.aid.domain.dto.AidEpisodeDTO;
import com.easywing.platform.aid.domain.entity.AidEpisode;
import com.easywing.platform.aid.domain.entity.AidProject;
import com.easywing.platform.aid.domain.entity.AidShot;
import com.easywing.platform.aid.domain.vo.AidEpisodeVO;
import com.easywing.platform.aid.enums.EpisodeStatus;
import com.easywing.platform.aid.mapper.AidEpisodeMapper;
import com.easywing.platform.aid.mapper.AidProjectMapper;
import com.easywing.platform.aid.mapper.AidShotMapper;
import com.easywing.platform.aid.mapper.struct.AidEpisodeStructMapper;
import com.easywing.platform.aid.service.AidEpisodeService;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 分集服务实现。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AidEpisodeServiceImpl extends ServiceImpl<AidEpisodeMapper, AidEpisode> implements AidEpisodeService {

    private final AidEpisodeMapper episodeMapper;
    private final AidProjectMapper projectMapper;
    private final AidShotMapper shotMapper;
    private final AidEpisodeStructMapper structMapper;

    @Override
    public Page<AidEpisodeVO> selectPage(long current, long size, Long projectId) {
        LambdaQueryWrapper<AidEpisode> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(AidEpisode::getProjectId, projectId);
        }
        wrapper.orderByDesc(AidEpisode::getCreateTime);

        Page<AidEpisode> page = episodeMapper.selectPage(new Page<>(current, size), wrapper);
        List<AidEpisode> records = page.getRecords();
        Set<Long> episodeIds = records.stream().map(AidEpisode::getId).collect(Collectors.toSet());
        Map<Long, Long> shotCounts =
                shotMapper.selectList(new LambdaQueryWrapper<AidShot>().in(AidShot::getEpisodeId, episodeIds)).stream()
                        .collect(Collectors.groupingBy(AidShot::getEpisodeId, Collectors.counting()));

        Page<AidEpisodeVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records.stream()
                .map(episode -> {
                    AidEpisodeVO vo = structMapper.toVO(episode);
                    vo.setShotCount(shotCounts.getOrDefault(episode.getId(), 0L).intValue());
                    return vo;
                })
                .toList());
        return result;
    }

    @Override
    public AidEpisodeVO selectById(Long id) {
        AidEpisode episode = episodeMapper.selectById(id);
        if (episode == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "分集不存在: " + id);
        }
        return structMapper.toVO(episode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertEpisode(AidEpisodeDTO dto) {
        requireProjectExists(dto.getProjectId());
        requireEpisodeNoUnique(dto.getProjectId(), dto.getEpisodeNo(), null);
        AidEpisode entity = structMapper.toEntity(dto);
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(EpisodeStatus.DRAFT.getCode());
        } else {
            EpisodeStatus.of(entity.getStatus());
        }
        episodeMapper.insert(entity);
        log.info("创建分集成功: id={}, projectId={}, no={}", entity.getId(), entity.getProjectId(), entity.getEpisodeNo());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEpisode(AidEpisodeDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "分集ID不能为空");
        }
        AidEpisode existing = episodeMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "分集不存在: " + dto.getId());
        }
        if (dto.getProjectId() != null && !dto.getProjectId().equals(existing.getProjectId())) {
            throw new BizException(ErrorCode.AID_TASK_STATE_INVALID, "不允许跨项目移动分集");
        }
        requireEpisodeNoUnique(existing.getProjectId(), dto.getEpisodeNo(), dto.getId());

        structMapper.updateEntity(existing, dto);
        if (StringUtils.hasText(existing.getStatus())) {
            EpisodeStatus.of(existing.getStatus());
        }
        episodeMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEpisodes(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        episodeMapper.deleteBatchIds(ids);
        // 级联清理分镜，避免留下孤儿数据
        shotMapper.delete(new LambdaQueryWrapper<AidShot>().in(AidShot::getEpisodeId, ids));
        log.info("删除分集 {} 个，并清理其下分镜", ids.size());
    }

    private void requireEpisodeNoUnique(Long projectId, Integer episodeNo, Long excludeId) {
        if (projectId == null || episodeNo == null) {
            return;
        }
        LambdaQueryWrapper<AidEpisode> wrapper = new LambdaQueryWrapper<AidEpisode>()
                .eq(AidEpisode::getProjectId, projectId)
                .eq(AidEpisode::getEpisodeNo, episodeNo);
        if (excludeId != null) {
            wrapper.ne(AidEpisode::getId, excludeId);
        }
        if (episodeMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.AID_ASSET_CODE_EXISTS, "集号已存在: " + episodeNo);
        }
    }

    private void requireProjectExists(Long projectId) {
        AidProject project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "所属项目不存在: " + projectId);
        }
    }
}
