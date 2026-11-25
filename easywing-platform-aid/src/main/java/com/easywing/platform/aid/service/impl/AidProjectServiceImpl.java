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
import com.easywing.platform.aid.domain.dto.AidProjectDTO;
import com.easywing.platform.aid.domain.entity.AidAsset;
import com.easywing.platform.aid.domain.entity.AidEpisode;
import com.easywing.platform.aid.domain.entity.AidProject;
import com.easywing.platform.aid.domain.query.AidProjectQuery;
import com.easywing.platform.aid.domain.vo.AidProjectVO;
import com.easywing.platform.aid.enums.ProjectStatus;
import com.easywing.platform.aid.mapper.AidAssetMapper;
import com.easywing.platform.aid.mapper.AidEpisodeMapper;
import com.easywing.platform.aid.mapper.AidProjectMapper;
import com.easywing.platform.aid.mapper.struct.AidProjectStructMapper;
import com.easywing.platform.aid.service.AidProjectService;
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
 * 创作项目服务实现。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AidProjectServiceImpl extends ServiceImpl<AidProjectMapper, AidProject> implements AidProjectService {

    private final AidProjectMapper projectMapper;
    private final AidEpisodeMapper episodeMapper;
    private final AidAssetMapper assetMapper;
    private final AidProjectStructMapper structMapper;

    @Override
    public Page<AidProjectVO> selectProjectPage(long current, long size, AidProjectQuery query) {
        LambdaQueryWrapper<AidProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getName()), AidProject::getName, query.getName())
                .eq(StringUtils.hasText(query.getStatus()), AidProject::getStatus, query.getStatus())
                .orderByDesc(AidProject::getCreateTime);

        Page<AidProject> page = projectMapper.selectPage(new Page<>(current, size), wrapper);
        List<AidProject> records = page.getRecords();
        if (records.isEmpty()) {
            return new Page<>(current, size, page.getTotal());
        }

        // 一次性批量统计，避免逐行查库（N+1）
        Set<Long> projectIds = records.stream().map(AidProject::getId).collect(Collectors.toSet());
        Map<Long, Long> episodeCounts =
                episodeMapper
                        .selectList(new LambdaQueryWrapper<AidEpisode>().in(AidEpisode::getProjectId, projectIds))
                        .stream()
                        .collect(Collectors.groupingBy(AidEpisode::getProjectId, Collectors.counting()));
        Map<Long, Long> assetCounts =
                assetMapper
                        .selectList(new LambdaQueryWrapper<AidAsset>().in(AidAsset::getProjectId, projectIds))
                        .stream()
                        .collect(Collectors.groupingBy(AidAsset::getProjectId, Collectors.counting()));

        Page<AidProjectVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records.stream()
                .map(project -> {
                    AidProjectVO vo = structMapper.toVO(project);
                    vo.setEpisodeCount(
                            episodeCounts.getOrDefault(project.getId(), 0L).intValue());
                    vo.setAssetCount(
                            assetCounts.getOrDefault(project.getId(), 0L).intValue());
                    return vo;
                })
                .toList());
        return result;
    }

    @Override
    public AidProjectVO selectProjectById(Long id) {
        AidProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "项目不存在: " + id);
        }
        return structMapper.toVO(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertProject(AidProjectDTO dto) {
        AidProject entity = structMapper.toEntity(dto);
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(ProjectStatus.DRAFT.getCode());
        } else {
            ProjectStatus.of(entity.getStatus());
        }
        projectMapper.insert(entity);
        log.info("创建项目成功: id={}, name={}", entity.getId(), entity.getName());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProject(AidProjectDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "项目ID不能为空");
        }
        AidProject existing = projectMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "项目不存在: " + dto.getId());
        }
        // 已归档项目只读：避免历史作品被改动
        if (ProjectStatus.ARCHIVED == ProjectStatus.of(existing.getStatus())) {
            throw new BizException(ErrorCode.AID_TASK_STATE_INVALID, "已归档项目不允许修改");
        }
        structMapper.updateEntity(existing, dto);
        if (StringUtils.hasText(existing.getStatus())) {
            ProjectStatus.of(existing.getStatus());
        }
        projectMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProjects(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 逻辑删除由 @TableLogic 自动补条件；先级联清掉子实体，保持数据一致性
        projectMapper.deleteBatchIds(ids);
        episodeMapper.delete(new LambdaQueryWrapper<AidEpisode>().in(AidEpisode::getProjectId, ids));
        assetMapper.delete(new LambdaQueryWrapper<AidAsset>().in(AidAsset::getProjectId, ids));
        log.info("删除项目 {} 个，并清理其下分集与资产", ids.size());
    }

    @Override
    public List<AidProjectVO> selectProjectOptions() {
        List<AidProject> projects =
                projectMapper.selectList(new LambdaQueryWrapper<AidProject>().orderByDesc(AidProject::getCreateTime));
        return structMapper.toVOList(projects);
    }
}
