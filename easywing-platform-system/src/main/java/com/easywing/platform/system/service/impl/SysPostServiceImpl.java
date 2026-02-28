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
import com.easywing.platform.system.mapper.struct.PostMapper;
import com.easywing.platform.system.service.SysPostService;
import com.easywing.platform.system.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysPostServiceImpl extends ServiceImpl<SysPostMapper, SysPost> implements SysPostService {

    private final SysPostMapper postMapper;
    private final PostMapper postMapperStruct;

    @Override
    public Page<SysPostVO> selectPostPage(Page<SysPost> page, SysPostDTO postDTO) {
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        if (postDTO != null) {
            wrapper.like(postDTO.getPostName() != null, SysPost::getPostName, postDTO.getPostName())
                    .eq(postDTO.getStatus() != null, SysPost::getStatus, postDTO.getStatus());
        }
        wrapper.orderByAsc(SysPost::getOrderNum);
        Page<SysPost> postPage = postMapper.selectPage(page, wrapper);
        return PageUtil.convert(postPage, postMapperStruct::toVO);
    }

    @Override
    public List<SysPostVO> selectPostAll() {
        List<SysPost> posts = postMapper.selectList(new LambdaQueryWrapper<SysPost>().eq(SysPost::getStatus, 0).orderByAsc(SysPost::getOrderNum));
        return postMapperStruct.toVOList(posts);
    }

    @Override
    public SysPostVO selectPostById(Long postId) {
        SysPost post = postMapper.selectById(postId);
        return post != null ? postMapperStruct.toVO(post) : null;
    }

    @Override
    public List<SysPostVO> selectPostsByUserId(Long userId) {
        List<SysPost> posts = postMapper.selectPostsByUserId(userId);
        return postMapperStruct.toVOList(posts);
    }

    @Override
    public Long insertPost(SysPostDTO postDTO) {
        if (!checkPostCodeUnique(postDTO.getPostCode(), null)) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "岗位编码已存在");
        }
        SysPost post = new SysPost();
        post.setPostCode(postDTO.getPostCode());
        post.setPostName(postDTO.getPostName());
        post.setPostCategory(postDTO.getPostCategory());
        post.setOrderNum(postDTO.getOrderNum());
        post.setStatus(postDTO.getStatus());
        post.setRemark(postDTO.getRemark());
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
        post.setId(postDTO.getId());
        post.setPostCode(postDTO.getPostCode());
        post.setPostName(postDTO.getPostName());
        post.setPostCategory(postDTO.getPostCategory());
        post.setOrderNum(postDTO.getOrderNum());
        post.setStatus(postDTO.getStatus());
        post.setRemark(postDTO.getRemark());
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
}
