package com.easywing.platform.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.dto.SysDictDataDTO;
import com.easywing.platform.system.domain.entity.SysDictData;
import com.easywing.platform.system.domain.vo.SysDictDataVO;
import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.service.SysDictDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/dict/data")
@Tag(name = "字典数据管理")
@RequiredArgsConstructor
public class SysDictDataController {

    private final SysDictDataService dictDataService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:dict:list')")
    public ResponseEntity<Page<SysDictDataVO>> list(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "10") long size, SysDictDataDTO dictDataDTO) {
        return ResponseEntity.ok(dictDataService.selectDictDataPage(new Page<>(current, size), dictDataDTO));
    }

    @GetMapping("/type/{dictType}")
    public ResponseEntity<List<SysDictDataVO>> getByType(@PathVariable String dictType) {
        return ResponseEntity.ok(dictDataService.selectDictDataByType(dictType));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dict:query')")
    public ResponseEntity<SysDictDataVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(dictDataService.selectDictDataById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:dict:add')")
    @Log(title = "字典数据管理", businessType = BusinessType.INSERT)
    public ResponseEntity<Long> add(@Valid @RequestBody SysDictDataDTO dictDataDTO) {
        return ResponseEntity.ok(dictDataService.insertDictData(dictDataDTO));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @Log(title = "字典数据管理", businessType = BusinessType.UPDATE)
    public ResponseEntity<Void> edit(@Valid @RequestBody SysDictDataDTO dictDataDTO) {
        dictDataService.updateDictData(dictDataDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:dict:remove')")
    @Log(title = "字典数据管理", businessType = BusinessType.DELETE)
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        dictDataService.deleteDictDataByIds(ids);
        return ResponseEntity.ok().build();
    }
}
