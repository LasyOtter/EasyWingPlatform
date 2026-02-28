package com.easywing.platform.system.controller;

import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.dto.SysDeptDTO;
import com.easywing.platform.system.domain.vo.SysDeptVO;
import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.service.SysDeptService;
import com.easywing.platform.web.idempotent.Idempotent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/depts")
@Tag(name = "部门管理")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    @GetMapping
    @Operation(summary = "查询部门列表")
    @PreAuthorize("hasAuthority('system:dept:list')")
    public ResponseEntity<List<SysDeptVO>> list(SysDeptDTO deptDTO) {
        return ResponseEntity.ok(deptService.selectDeptList(deptDTO));
    }

    @GetMapping("/tree")
    @Operation(summary = "查询部门树结构")
    public ResponseEntity<List<SysDeptVO>> tree() {
        return ResponseEntity.ok(deptService.selectDeptTreeList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询部门")
    @PreAuthorize("hasAuthority('system:dept:query')")
    public ResponseEntity<SysDeptVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(deptService.selectDeptById(id));
    }

    @PostMapping
    @Operation(summary = "新增部门")
    @PreAuthorize("hasAuthority('system:dept:add')")
    @Log(title = "部门管理", businessType = BusinessType.INSERT)
    @Idempotent(key = "#deptDTO.deptName + ':' + #deptDTO.parentId", expire = 30, message = "该部门正在创建中，请勿重复提交")
    public ResponseEntity<Long> add(@Valid @RequestBody SysDeptDTO deptDTO) {
        return ResponseEntity.ok(deptService.insertDept(deptDTO));
    }

    @PutMapping
    @Operation(summary = "修改部门")
    @PreAuthorize("hasAuthority('system:dept:edit')")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    @Idempotent(key = "#deptDTO.id", expire = 30, message = "该部门正在更新中，请勿重复提交")
    public ResponseEntity<Void> edit(@Valid @RequestBody SysDeptDTO deptDTO) {
        deptService.updateDept(deptDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除部门")
    @PreAuthorize("hasAuthority('system:dept:remove')")
    @Log(title = "部门管理", businessType = BusinessType.DELETE)
    @Idempotent(key = "#id", expire = 30, message = "删除操作正在执行中，请勿重复提交")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        deptService.deleteDeptById(id);
        return ResponseEntity.ok().build();
    }
}
