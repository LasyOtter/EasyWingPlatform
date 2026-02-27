package com.easywing.platform.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.easywing.platform.system.domain.dto.SysPostDTO;
import com.easywing.platform.system.domain.entity.SysPost;
import com.easywing.platform.system.domain.vo.SysPostVO;
import java.util.List;

public interface SysPostService extends IService<SysPost> {
    Page<SysPostVO> selectPostPage(Page<SysPost> page, SysPostDTO postDTO);
    List<SysPostVO> selectPostAll();
    SysPostVO selectPostById(Long postId);
    List<SysPostVO> selectPostsByUserId(Long userId);
    Long insertPost(SysPostDTO postDTO);
    int updatePost(SysPostDTO postDTO);
    int deletePostByIds(List<Long> postIds);
    boolean checkPostCodeUnique(String postCode, Long postId);
}
