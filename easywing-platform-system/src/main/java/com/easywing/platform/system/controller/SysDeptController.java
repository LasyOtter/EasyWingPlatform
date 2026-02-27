package com.easywing.platform.system.controller;

import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.dto.SysDeptDTO;
import com.easywing.platform.system.domain.vo.SysDeptVO;
import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.service.SysDeptService;
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
    @PreAuthorize("hasAuthority('system:dept:list')")
    public ResponseEntity<List<SysDeptVO>> list(SysDeptDTO deptDTO) {
        return ResponseEntity.ok(deptService.selectDeptList(deptDTO));
    }

    @GetMapping("/tree")
    public ResponseEntity<List<SysDeptVO>> tree() {
        return ResponseEntity.ok(deptService.selectDeptTreeList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:query')")
    public ResponseEntity<SysDeptVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(deptService.selectDeptById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:dept:add')")
    @Log(title = "部门管理", businessType = BusinessType.INSERT)
    public ResponseEntity<Long> add(@Valid @RequestBody SysDeptDTO deptDTO) {
        return ResponseEntity.ok(deptService.insertDept(deptDTO));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:dept:edit')")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    public ResponseEntity<Void> edit(@Valid @RequestBody SysDeptDTO deptDTO) {
        deptService.updateDept(deptDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:remove')")
    @Log(title = "部门管理", businessType = BusinessType.DELETE)
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        deptService.deleteDeptById(id);
        return ResponseEntity.ok().build();
    }
}
