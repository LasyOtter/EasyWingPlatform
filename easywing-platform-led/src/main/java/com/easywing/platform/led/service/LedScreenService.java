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
package com.easywing.platform.led.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.led.domain.dto.LedScreenDTO;
import com.easywing.platform.led.domain.entity.LedScreen;
import com.easywing.platform.led.domain.vo.LedScreenVO;
import com.easywing.platform.led.mapper.LedScreenMapper;
import com.easywing.platform.led.mapper.struct.LedScreenStructMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 屏元数据服务。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class LedScreenService extends ServiceImpl<LedScreenMapper, LedScreen> {

    private final LedScreenStructMapper structMapper;

    public Page<LedScreenVO> page(Page<LedScreen> page, String screenId, String sdkProvider) {
        LambdaQueryWrapper<LedScreen> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(screenId != null && !screenId.isBlank(), LedScreen::getScreenId, screenId)
                .eq(sdkProvider != null && !sdkProvider.isBlank(), LedScreen::getSdkProvider, sdkProvider)
                .orderByDesc(LedScreen::getUpdateTime);
        Page<LedScreen> result = baseMapper.selectPage(page, wrapper);
        Page<LedScreenVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(structMapper::toVO).toList());
        return voPage;
    }

    public List<LedScreenVO> listAll() {
        return baseMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .map(structMapper::toVO)
                .toList();
    }

    public LedScreenVO getById(Long id) {
        LedScreen entity = baseMapper.selectById(id);
        return entity == null ? null : structMapper.toVO(entity);
    }

    public LedScreenVO getByScreenId(String screenId) {
        LedScreen entity = baseMapper.selectOne(new LambdaQueryWrapper<LedScreen>()
                .eq(LedScreen::getScreenId, screenId)
                .last("limit 1"));
        return entity == null ? null : structMapper.toVO(entity);
    }

    @Transactional
    public Long create(LedScreenDTO dto) {
        if (getByScreenId(dto.getScreenId()) != null) {
            throw new BizException(ErrorCode.RESOURCE_ALREADY_EXISTS, "屏ID已存在: " + dto.getScreenId());
        }
        LedScreen entity = structMapper.toEntity(dto);
        baseMapper.insert(entity);
        return entity.getId();
    }

    @Transactional
    public void update(LedScreenDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "缺少主键ID");
        }
        LedScreen exists = baseMapper.selectById(dto.getId());
        if (exists == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "屏记录不存在: " + dto.getId());
        }
        LedScreen entity = structMapper.toEntity(dto);
        entity.setId(dto.getId());
        baseMapper.updateById(entity);
    }

    @Transactional
    public void delete(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            baseMapper.deleteBatchIds(ids);
        }
    }

    /** 更新下发结果（供引擎回写）。 */
    @Transactional
    public void recordDeploy(String screenId, int status, String lastError) {
        LedScreen entity = baseMapper.selectOne(new LambdaQueryWrapper<LedScreen>()
                .eq(LedScreen::getScreenId, screenId)
                .last("limit 1"));
        if (entity == null) {
            return;
        }
        entity.setLastDeployStatus(status);
        entity.setLastDeployTime(LocalDateTime.now());
        entity.setLastError(lastError);
        baseMapper.updateById(entity);
    }

    /** 更新在线状态。 */
    @Transactional
    public void recordOnline(String screenId, boolean online) {
        LedScreen entity = baseMapper.selectOne(new LambdaQueryWrapper<LedScreen>()
                .eq(LedScreen::getScreenId, screenId)
                .last("limit 1"));
        if (entity == null) {
            return;
        }
        entity.setOnline(online ? 1 : 0);
        baseMapper.updateById(entity);
    }
}
