package com.easywing.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easywing.platform.system.domain.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Set;

@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {
    List<SysDept> selectDeptList();
    SysDept selectDeptByUserId(@Param("userId") Long userId);
    List<SysDept> selectChildrenByParentId(@Param("parentId") Long parentId);
    int selectChildrenCountByParentId(@Param("parentId") Long parentId);
    int checkDeptExistUser(@Param("deptId") Long deptId);
    int checkDeptNameUnique(@Param("deptName") String deptName, @Param("parentId") Long parentId, @Param("deptId") Long deptId);
    List<Long> selectDeptIdsByParentId(@Param("deptId") Long deptId);

    /**
     * 查询部门的所有子部门ID（递归）
     */
    @Select("WITH RECURSIVE dept_tree AS (" +
            "    SELECT dept_id, parent_id FROM sys_dept WHERE dept_id = #{deptId} " +
            "    UNION ALL " +
            "    SELECT d.dept_id, d.parent_id FROM sys_dept d " +
            "    INNER JOIN dept_tree dt ON d.parent_id = dt.dept_id " +
            ") SELECT dept_id FROM dept_tree WHERE dept_id != #{deptId}")
    Set<Long> selectChildDeptIds(@Param("deptId") Long deptId);
}
