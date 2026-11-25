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
package com.easywing.platform.aid.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.aid.domain.dto.AidProjectDTO;
import com.easywing.platform.aid.domain.query.AidProjectQuery;
import com.easywing.platform.aid.domain.vo.AidProjectVO;
import com.easywing.platform.aid.service.AidProjectService;
import com.easywing.platform.web.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 项目管理控制器测试：验证权限注解生效与入参校验，服务层逻辑由服务测试覆盖。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@WebMvcTest(AidProjectController.class)
@EnableMethodSecurity
@Import(GlobalExceptionHandler.class)
class AidProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AidProjectService projectService;

    @Test
    void listWithPermissionReturnsPage() throws Exception {
        Page<AidProjectVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of());
        when(projectService.selectProjectPage(eq(1L), eq(10L), any(AidProjectQuery.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/aid/projects")
                        .param("current", "1")
                        .param("size", "10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("aid:project:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    /** 缺权限必须 403，而不是静默返回空列表。 */
    @Test
    void listWithoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/aid/projects")
                        .param("current", "1")
                        .param("size", "10")
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }

    /** 未认证直接 401。 */
    @Test
    void listWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/aid/projects")).andExpect(status().isUnauthorized());
    }

    /** 项目名为空应被 @Valid 拦下，返回 RFC 9457 的 400。 */
    @Test
    void addRejectsBlankName() throws Exception {
        AidProjectDTO dto = new AidProjectDTO();
        dto.setName("  ");

        mockMvc.perform(post("/api/v1/aid/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(jwt().authorities(new SimpleGrantedAuthority("aid:project:add"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addReturnsCreatedId() throws Exception {
        AidProjectDTO dto = new AidProjectDTO();
        dto.setName("夸父追日");
        when(projectService.insertProject(any(AidProjectDTO.class))).thenReturn(88L);

        mockMvc.perform(post("/api/v1/aid/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(jwt().authorities(new SimpleGrantedAuthority("aid:project:add"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(88));
    }
}
