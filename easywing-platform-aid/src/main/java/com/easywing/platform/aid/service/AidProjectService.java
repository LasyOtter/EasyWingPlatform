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
import com.easywing.platform.aid.domain.dto.AidProjectDTO;
import com.easywing.platform.aid.domain.entity.AidProject;
import com.easywing.platform.aid.domain.query.AidProjectQuery;
import com.easywing.platform.aid.domain.vo.AidProjectVO;
import java.util.List;

/**
 * 创作项目服务。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface AidProjectService extends IService<AidProject> {

    /**
     * 分页查询项目。
     *
     * @param current 页码
     * @param size    每页大小
     * @param query   过滤条件
     * @return 分页结果
     */
    Page<AidProjectVO> selectProjectPage(long current, long size, AidProjectQuery query);

    /**
     * 按 ID 查询项目详情。
     *
     * @param id 项目 ID
     * @return 项目出参
     */
    AidProjectVO selectProjectById(Long id);

    /**
     * 新增项目。
     *
     * @param dto 入参
     * @return 新项目 ID
     */
    Long insertProject(AidProjectDTO dto);

    /**
     * 修改项目。
     *
     * @param dto 入参，必须携带 id
     */
    void updateProject(AidProjectDTO dto);

    /**
     * 删除项目（逻辑删除，级联清理其下分集/资产/分镜）。
     *
     * @param ids 项目 ID 列表
     */
    void deleteProjects(List<Long> ids);

    /**
     * 查询全部项目的精简信息，供下拉选择。
     *
     * @return 项目精简列表
     */
    List<AidProjectVO> selectProjectOptions();
}
