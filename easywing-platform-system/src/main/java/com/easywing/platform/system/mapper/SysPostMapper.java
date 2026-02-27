package com.easywing.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easywing.platform.system.domain.entity.SysPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SysPostMapper extends BaseMapper<SysPost> {
    List<SysPost> selectPostsByUserId(@Param("userId") Long userId);
    int checkPostCodeUnique(@Param("postCode") String postCode, @Param("postId") Long postId);
    int deleteUserPostByUserId(@Param("userId") Long userId);
    int batchInsertUserPost(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
