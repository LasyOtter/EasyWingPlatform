package com.easywing.platform.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.system.domain.dto.SysPostDTO;
import com.easywing.platform.system.domain.entity.SysPost;
import com.easywing.platform.system.domain.vo.SysPostVO;
import com.easywing.platform.system.mapper.SysPostMapper;
import com.easywing.platform.system.service.SysPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysPostServiceImpl extends ServiceImpl<SysPostMapper, SysPost> implements SysPostService {

    private final SysPostMapper postMapper;

    @Override
    public Page<SysPostVO> selectPostPage(Page<SysPost> page, SysPostDTO postDTO) {
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        if (postDTO != null) {
            wrapper.like(postDTO.getPostName() != null, SysPost::getPostName, postDTO.getPostName())
                    .eq(postDTO.getStatus() != null, SysPost::getStatus, postDTO.getStatus());
        }
        wrapper.orderByAsc(SysPost::getOrderNum);
        Page<SysPost> postPage = postMapper.selectPage(page, wrapper);
        Page<SysPostVO> voPage = new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());
        voPage.setRecords(postPage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public List<SysPostVO> selectPostAll() {
        return postMapper.selectList(new LambdaQueryWrapper<SysPost>().eq(SysPost::getStatus, 0).orderByAsc(SysPost::getOrderNum))
                .stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public SysPostVO selectPostById(Long postId) {
        SysPost post = postMapper.selectById(postId);
        return post != null ? convertToVO(post) : null;
    }

    @Override
    public List<SysPostVO> selectPostsByUserId(Long userId) {
        return postMapper.selectPostsByUserId(userId).stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public Long insertPost(SysPostDTO postDTO) {
        if (!checkPostCodeUnique(postDTO.getPostCode(), null)) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "岗位编码已存在");
        }
        SysPost post = new SysPost();
        BeanUtils.copyProperties(postDTO, post);
        postMapper.insert(post);
        return post.getId();
    }

    @Override
    public int updatePost(SysPostDTO postDTO) {
        if (postDTO.getId() == null) throw new BizException(ErrorCode.INVALID_PARAMETER, "岗位ID不能为空");
        if (!checkPostCodeUnique(postDTO.getPostCode(), postDTO.getId())) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "岗位编码已存在");
        }
        SysPost post = new SysPost();
        BeanUtils.copyProperties(postDTO, post);
        return postMapper.updateById(post);
    }

    @Override
    public int deletePostByIds(List<Long> postIds) {
        if (CollectionUtils.isEmpty(postIds)) return 0;
        return postMapper.deleteBatchIds(postIds);
    }

    @Override
    public boolean checkPostCodeUnique(String postCode, Long postId) {
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPost::getPostCode, postCode).ne(postId != null, SysPost::getId, postId);
        return postMapper.selectCount(wrapper) == 0;
    }

    private SysPostVO convertToVO(SysPost post) {
        SysPostVO vo = new SysPostVO();
        BeanUtils.copyProperties(post, vo);
        return vo;
    }
}
