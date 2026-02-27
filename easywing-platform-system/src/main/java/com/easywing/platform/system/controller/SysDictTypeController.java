package com.easywing.platform.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.dto.SysDictTypeDTO;
import com.easywing.platform.system.domain.entity.SysDictType;
import com.easywing.platform.system.domain.vo.SysDictTypeVO;
import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.service.SysDictTypeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/dict/types")
@Tag(name = "字典类型管理")
@RequiredArgsConstructor
public class SysDictTypeController {

    private final SysDictTypeService dictTypeService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:dict:list')")
    public ResponseEntity<Page<SysDictTypeVO>> list(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "10") long size, SysDictTypeDTO dictTypeDTO) {
        return ResponseEntity.ok(dictTypeService.selectDictTypePage(new Page<>(current, size), dictTypeDTO));
    }

    @GetMapping("/all")
    public ResponseEntity<List<SysDictTypeVO>> listAll() {
        return ResponseEntity.ok(dictTypeService.selectDictTypeAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:query')")
    public ResponseEntity<SysDictTypeVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(dictTypeService.selectDictTypeById(id));
    }

    @GetMapping("/type/{dictType}")
    public ResponseEntity<SysDictTypeVO> getByType(@PathVariable String dictType) {
        return ResponseEntity.ok(dictTypeService.selectDictTypeByType(dictType));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:dict:add')")
    @Log(title = "字典类型管理", businessType = BusinessType.INSERT)
    public ResponseEntity<Long> add(@Valid @RequestBody SysDictTypeDTO dictTypeDTO) {
        return ResponseEntity.ok(dictTypeService.insertDictType(dictTypeDTO));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @Log(title = "字典类型管理", businessType = BusinessType.UPDATE)
    public ResponseEntity<Void> edit(@Valid @RequestBody SysDictTypeDTO dictTypeDTO) {
        dictTypeService.updateDictType(dictTypeDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:dict:remove')")
    @Log(title = "字典类型管理", businessType = BusinessType.DELETE)
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        dictTypeService.deleteDictTypeByIds(ids);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh-cache")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @Log(title = "字典类型管理", businessType = BusinessType.UPDATE)
    public ResponseEntity<Void> refreshCache() {
        dictTypeService.refreshCache();
        return ResponseEntity.ok().build();
    }
}
