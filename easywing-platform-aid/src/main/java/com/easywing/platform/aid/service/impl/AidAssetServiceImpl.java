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
import com.easywing.platform.aid.domain.dto.AidAssetDTO;
import com.easywing.platform.aid.domain.entity.AidAsset;
import com.easywing.platform.aid.domain.query.AidAssetQuery;
import com.easywing.platform.aid.domain.vo.AidAssetVO;
import com.easywing.platform.aid.enums.AssetType;
import com.easywing.platform.aid.mapper.AidAssetMapper;
import com.easywing.platform.aid.mapper.AidProjectMapper;
import com.easywing.platform.aid.mapper.struct.AidAssetStructMapper;
import com.easywing.platform.aid.service.AidAssetService;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 资产库服务实现。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AidAssetServiceImpl extends ServiceImpl<AidAssetMapper, AidAsset> implements AidAssetService {

    private final AidProjectMapper projectMapper;
    private final AidAssetMapper assetMapper;
    private final AidAssetStructMapper structMapper;

    @Override
    public Page<AidAssetVO> selectAssetPage(long current, long size, AidAssetQuery query) {
        Long projectId = query.getProjectId();
        if (projectId == null) {
            // 缺 projectId 时拒绝全表扫描：资产必须按项目隔离查询
            throw new BizException(ErrorCode.INVALID_PARAMETER, "项目ID不能为空");
        }
        LambdaQueryWrapper<AidAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AidAsset::getProjectId, projectId)
                .eq(StringUtils.hasText(query.getAssetType()), AidAsset::getAssetType, query.getAssetType())
                .and(StringUtils.hasText(query.getKeyword()), w -> w.like(AidAsset::getAssetCode, query.getKeyword())
                        .or()
                        .like(AidAsset::getName, query.getKeyword()))
                .orderByAsc(AidAsset::getAssetCode);

        Page<AidAsset> page = assetMapper.selectPage(new Page<>(current, size), wrapper);
        return convertPage(page, structMapper::toVO);
    }

    @Override
    public AidAssetVO selectAssetById(Long id) {
        AidAsset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "资产不存在: " + id);
        }
        return structMapper.toVO(asset);
    }

    @Override
    public boolean checkCodeUnique(Long projectId, String assetCode, Long excludeId) {
        if (projectId == null || !StringUtils.hasText(assetCode)) {
            return false;
        }
        LambdaQueryWrapper<AidAsset> wrapper = new LambdaQueryWrapper<AidAsset>()
                .eq(AidAsset::getProjectId, projectId)
                .eq(AidAsset::getAssetCode, assetCode.trim());
        if (excludeId != null) {
            wrapper.ne(AidAsset::getId, excludeId);
        }
        return assetMapper.selectCount(wrapper) == 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertAsset(AidAssetDTO dto) {
        requireProjectExists(dto.getProjectId());
        if (!checkCodeUnique(dto.getProjectId(), dto.getAssetCode(), null)) {
            throw new BizException(ErrorCode.AID_ASSET_CODE_EXISTS, "资产编号已存在: " + dto.getAssetCode());
        }
        AidAsset entity = structMapper.toEntity(dto);
        normalize(entity);
        assetMapper.insert(entity);
        log.info("创建资产成功: id={}, code={}", entity.getId(), entity.getAssetCode());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAsset(AidAssetDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "资产ID不能为空");
        }
        AidAsset existing = assetMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "资产不存在: " + dto.getId());
        }
        if (dto.getProjectId() != null && !dto.getProjectId().equals(existing.getProjectId())) {
            throw new BizException(ErrorCode.AID_TASK_STATE_INVALID, "不允许跨项目移动资产");
        }
        if (!checkCodeUnique(existing.getProjectId(), dto.getAssetCode(), dto.getId())) {
            throw new BizException(ErrorCode.AID_ASSET_CODE_EXISTS, "资产编号已存在: " + dto.getAssetCode());
        }
        String originalCode = existing.getAssetCode();
        structMapper.updateEntity(existing, dto);
        normalize(existing);
        assetMapper.updateById(existing);

        // 编号变更会影响提示词里的 @资产[code] 引用，必须同步提示调用方
        if (!originalCode.equals(existing.getAssetCode())) {
            log.warn(
                    "资产编号发生变更: id={}, {} -> {}，历史分镜的 @资产[{}] 引用需人工确认",
                    existing.getId(),
                    originalCode,
                    existing.getAssetCode(),
                    originalCode);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAssets(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        assetMapper.deleteBatchIds(ids);
        log.info("删除资产 {} 个", ids.size());
    }

    @Override
    public Map<String, String> selectAnnotationByCodes(Long projectId) {
        List<AidAsset> assets =
                assetMapper.selectList(new LambdaQueryWrapper<AidAsset>().eq(AidAsset::getProjectId, projectId));
        return assets.stream()
                .filter(asset -> StringUtils.hasText(asset.getAssetCode()))
                .collect(Collectors.toMap(AidAsset::getAssetCode, this::buildAnnotation, (left, right) -> left));
    }

    /**
     * 把资产编译成提示词里的可读标注。
     * <p>格式为 {@code C01:夸父·标准像(角色一致性主参考)} 的简化形态：编号:名称(类型说明)。</p>
     *
     * @param asset 资产实体
     * @return 可读标注文本
     */
    private String buildAnnotation(AidAsset asset) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(asset.getName())) {
            sb.append(asset.getName().trim());
        }
        if (StringUtils.hasText(asset.getDescription())) {
            if (sb.length() > 0) {
                sb.append('·');
            }
            // 只取首句，避免整段描述把提示词撑爆
            String description = asset.getDescription().trim();
            int newline = description.indexOf('\n');
            sb.append(newline > 0 ? description.substring(0, newline) : description);
        }
        if (sb.length() == 0) {
            return null;
        }
        return asset.getAssetCode() + ":" + sb;
    }

    /** 校验并补默认值：类型为空时回落 CHARACTER，编号去空白。 */
    private void normalize(AidAsset entity) {
        if (!StringUtils.hasText(entity.getAssetType())) {
            entity.setAssetType(AssetType.CHARACTER.getCode());
        } else {
            AssetType.of(entity.getAssetType());
        }
        entity.setAssetCode(entity.getAssetCode().trim());
        if (entity.getVersion() == null) {
            entity.setVersion(1);
        }
    }

    private void requireProjectExists(Long projectId) {
        if (projectId == null || projectMapper.selectById(projectId) == null) {
            throw new BizException(ErrorCode.AID_RESOURCE_NOT_FOUND, "所属项目不存在: " + projectId);
        }
    }

    /**
     * 实体分页 → VO 分页。
     *
     * @param source    原始分页
     * @param converter VO 转换器
     * @return 转换后的分页
     */
    private static <T> Page<T> convertPage(Page<AidAsset> source, Function<AidAsset, T> converter) {
        Page<T> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(source.getRecords().stream().map(converter).toList());
        return target;
    }
}
