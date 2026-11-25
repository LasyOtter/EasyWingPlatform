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
import com.easywing.platform.aid.domain.dto.AidVisualSettingDTO;
import com.easywing.platform.aid.domain.entity.AidVisualSetting;
import com.easywing.platform.aid.domain.vo.AidVisualSettingVO;
import java.util.List;
import java.util.Map;

/**
 * 视觉设定服务（PRD FR-4.7 / FR-11，AC-11）。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface AidVisualSettingService extends IService<AidVisualSetting> {

    /**
     * 分页查询视觉设定。
     *
     * @param current 页码
     * @param size    每页大小
     * @param scope   作用域过滤，可为空
     * @param name    名称模糊匹配，可为空
     * @return 分页结果
     */
    Page<AidVisualSettingVO> selectPage(long current, long size, String scope, String name);

    /**
     * 按 ID 查询视觉设定。
     *
     * @param id 设定 ID
     * @return 出参
     */
    AidVisualSettingVO selectById(Long id);

    /**
     * 新增视觉设定。
     *
     * @param dto 入参
     * @return 新设定 ID
     */
    Long insertSetting(AidVisualSettingDTO dto);

    /**
     * 修改视觉设定，版本号自增。
     * <p>版本自增是下游产物判定失效的依据，因此即使是「内容完全没变」的保存也 +1。</p>
     *
     * @param dto 入参，必须携带 id
     */
    void updateSetting(AidVisualSettingDTO dto);

    /**
     * 删除视觉设定。
     *
     * @param ids 设定 ID 列表
     */
    void deleteSettings(List<Long> ids);

    /**
     * 导出为可移植的模板 JSON（AC-11「视觉设定可导出为 JSON 模板」）。
     *
     * @param id 设定 ID
     * @return 模板内容，键值结构稳定，可直接被导入接口消费
     */
    Map<String, Object> exportTemplate(Long id);

    /**
     * 从模板 JSON 导入（AC-11「新项目可导入模板，配置生效」）。
     *
     * @param templateJson 模板内容
     * @param name         导入后的名称，为空则沿用模板内名称
     * @param overwrite    同名时是否覆盖已有 TEMPLATE
     * @return 新设定 ID
     */
    Long importTemplate(Map<String, Object> templateJson, String name, boolean overwrite);

    /**
     * 把视觉设定转成提示词编译器需要的快照对象。
     *
     * @param id 设定 ID
     * @return 快照；设定不存在返回 null
     */
    com.easywing.platform.aid.prompt.VisualSnapshot toSnapshot(Long id);
}
