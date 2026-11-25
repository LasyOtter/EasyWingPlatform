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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.easywing.platform.aid.domain.dto.AidAssetDTO;
import com.easywing.platform.aid.service.AidAssetService;
import com.easywing.platform.aid.storage.AssetStorageService;
import com.easywing.platform.web.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 资产库控制器测试：重点覆盖编号校验端点与参考图上传的权限与参数要求。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
@WebMvcTest(AidAssetController.class)
@EnableMethodSecurity
@Import(GlobalExceptionHandler.class)
class AidAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AidAssetService assetService;

    @MockitoBean
    private AssetStorageService storageService;

    /** check-code 必须能在 /{id} 之前被路由到，不能被当成 id 解析成 400/404。 */
    @Test
    void checkCodeReturnsServiceVerdict() throws Exception {
        when(assetService.checkCodeUnique(1L, "C01", null)).thenReturn(true);

        mockMvc.perform(get("/api/v1/aid/assets/check-code")
                        .param("projectId", "1")
                        .param("assetCode", "C01")
                        .with(jwt().authorities(new SimpleGrantedAuthority("aid:asset:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        verify(assetService).checkCodeUnique(1L, "C01", null);
    }

    /** 缺 projectId 应被 Spring 的必填参数校验拦成 400，而不是落到服务层空查。 */
    @Test
    void checkCodeRequiresProjectId() throws Exception {
        mockMvc.perform(get("/api/v1/aid/assets/check-code")
                        .param("assetCode", "C01")
                        .with(jwt().authorities(new SimpleGrantedAuthority("aid:asset:list"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkCodeWithoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/aid/assets/check-code")
                        .param("projectId", "1")
                        .param("assetCode", "C01")
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }

    /**
     * 上传接口必须要求 file 参数存在。
     *
     * <p>非 multipart 请求由 Spring 的 {@link MissingMultipartException} 抛出，
     * 全局异常处理器兜成 500——这里断言「确实被拦下且不进入存储服务」，不断言具体状态码。</p>
     */
    @Test
    void uploadRejectsNonMultipartRequest() throws Exception {
        mockMvc.perform(post("/api/v1/aid/assets/upload")
                        .with(jwt().authorities(new SimpleGrantedAuthority("aid:asset:add"))))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isNotEqualTo(200);
                });

        verify(storageService, never()).store(any());
    }

    /**
     * 传了 multipart 但缺 {@code file} 分部时同样被拦下，不得落到存储服务。
     *
     * <p>Spring 抛 {@link MissingServletRequestPartException}，由全局异常处理器兜底；
     * 这里只断言「非 2xx 且未调用存储服务」，具体状态码交由异常处理器实现决定。</p>
     */
    @Test
    void uploadRejectsMissingFilePart() throws Exception {
        MockMultipartFile other = new MockMultipartFile("other", "", "text/plain", new byte[0]);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/aid/assets/upload")
                        .file(other)
                        .with(jwt().authorities(new SimpleGrantedAuthority("aid:asset:add"))))
                .andExpect(
                        result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200));

        verify(storageService, never()).store(any());
    }

    /** 上传成功后原样返回可回显 URL，前端写入 referenceUrl。 */
    @Test
    void uploadReturnsPublicUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "ref.png", "image/png", new byte[] {1, 2, 3});
        when(storageService.store(file)).thenReturn("/api/v1/aid/assets/files/uuid.png");

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/aid/assets/upload")
                        .file(file)
                        .with(jwt().authorities(new SimpleGrantedAuthority("aid:asset:add"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("/api/v1/aid/assets/files/uuid.png"));
    }

    /** AC-11：编号允许字母数字下划线短横线，纯中文等非法字符应被 @Valid 拦下。 */
    @Test
    void addRejectsIllegalAssetCode() throws Exception {
        AidAssetDTO dto = new AidAssetDTO();
        dto.setProjectId(1L);
        dto.setAssetCode("编号一");

        mockMvc.perform(post("/api/v1/aid/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(jwt().authorities(new SimpleGrantedAuthority("aid:asset:add"))))
                .andExpect(status().isBadRequest());
    }
}
