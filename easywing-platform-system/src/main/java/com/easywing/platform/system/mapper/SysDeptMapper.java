package com.easywing.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easywing.platform.system.domain.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {
    List<SysDept> selectDeptList();
    SysDept selectDeptByUserId(@Param("userId") Long userId);
    List<SysDept> selectChildrenByParentId(@Param("parentId") Long parentId);
    int selectChildrenCountByParentId(@Param("parentId") Long parentId);
    int checkDeptExistUser(@Param("deptId") Long deptId);
    int checkDeptNameUnique(@Param("deptName") String deptName, @Param("parentId") Long parentId, @Param("deptId") Long deptId);
    List<Long> selectDeptIdsByParentId(@Param("deptId") Long deptId);
}
