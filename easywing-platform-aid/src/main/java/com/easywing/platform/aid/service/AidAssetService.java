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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.easywing.platform.aid.domain.dto.AidAssetDTO;
import com.easywing.platform.aid.domain.entity.AidAsset;
import com.easywing.platform.aid.domain.query.AidAssetQuery;
import com.easywing.platform.aid.domain.vo.AidAssetVO;
import java.util.List;
import java.util.Map;

/**
 * 资产库服务（PRD FR-6，AC-11 资产编号自定义）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface AidAssetService extends IService<AidAsset> {

    /**
     * 分页查询资产。
     *
     * @param current 页码
     * @param size    每页大小
     * @param query   过滤条件（projectId 必传）
     * @return 分页结果
     */
    Page<AidAssetVO> selectAssetPage(long current, long size, AidAssetQuery query);

    /**
     * 按 ID 查询资产。
     *
     * @param id 资产 ID
     * @return 资产出参
     */
    AidAssetVO selectAssetById(Long id);

    /**
     * 校验资产编号在项目内唯一。
     *
     * @param projectId 项目 ID
     * @param assetCode 资产编号
     * @param excludeId 修改时排除自身 ID，新增时传 null
     * @return 未占用返回 true
     */
    boolean checkCodeUnique(Long projectId, String assetCode, Long excludeId);

    /**
     * 新增资产。
     *
     * @param dto 入参
     * @return 新资产 ID
     */
    Long insertAsset(AidAssetDTO dto);

    /**
     * 修改资产。
     *
     * @param dto 入参，必须携带 id
     */
    void updateAsset(AidAssetDTO dto);

    /**
     * 删除资产。
     *
     * @param ids 资产 ID 列表
     */
    void deleteAssets(List<Long> ids);

    /**
     * 按项目编号批量取资产键，供提示词编译器做引用解析。
     *
     * @param projectId 项目 ID
     * @return assetCode → 资产描述（含名称与特征）
     */
    Map<String, String> selectAnnotationByCodes(Long projectId);
}
