package com.easywing.platform.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easywing.platform.system.annotation.Log;
import com.easywing.platform.system.domain.dto.SysPostDTO;
import com.easywing.platform.system.domain.entity.SysPost;
import com.easywing.platform.system.domain.vo.SysPostVO;
import com.easywing.platform.system.enums.BusinessType;
import com.easywing.platform.system.service.SysPostService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/posts")
@Tag(name = "岗位管理")
@RequiredArgsConstructor
public class SysPostController {

    private final SysPostService postService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:post:list')")
    public ResponseEntity<Page<SysPostVO>> list(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long size, SysPostDTO postDTO) {
        return ResponseEntity.ok(postService.selectPostPage(new Page<>(current, size), postDTO));
    }

    @GetMapping("/all")
    public ResponseEntity<List<SysPostVO>> listAll() {
        return ResponseEntity.ok(postService.selectPostAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:post:query')")
    public ResponseEntity<SysPostVO> getInfo(@PathVariable Long id) {
        return ResponseEntity.ok(postService.selectPostById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:post:add')")
    @Log(title = "岗位管理", businessType = BusinessType.INSERT)
    public ResponseEntity<Long> add(@Valid @RequestBody SysPostDTO postDTO) {
        return ResponseEntity.ok(postService.insertPost(postDTO));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:post:edit')")
    @Log(title = "岗位管理", businessType = BusinessType.UPDATE)
    public ResponseEntity<Void> edit(@Valid @RequestBody SysPostDTO postDTO) {
        postService.updatePost(postDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:post:remove')")
    @Log(title = "岗位管理", businessType = BusinessType.DELETE)
    public ResponseEntity<Void> remove(@PathVariable List<Long> ids) {
        postService.deletePostByIds(ids);
        return ResponseEntity.ok().build();
    }
}
