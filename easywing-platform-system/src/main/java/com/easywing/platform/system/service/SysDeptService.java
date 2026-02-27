package com.easywing.platform.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.easywing.platform.system.domain.dto.SysDeptDTO;
import com.easywing.platform.system.domain.entity.SysDept;
import com.easywing.platform.system.domain.vo.SysDeptVO;
import java.util.List;

public interface SysDeptService extends IService<SysDept> {
    List<SysDeptVO> selectDeptList(SysDeptDTO deptDTO);
    List<SysDeptVO> selectDeptTreeList();
    SysDeptVO selectDeptById(Long deptId);
    SysDeptVO selectDeptByUserId(Long userId);
    Long insertDept(SysDeptDTO deptDTO);
    int updateDept(SysDeptDTO deptDTO);
    int deleteDeptById(Long deptId);
    boolean checkDeptNameUnique(String deptName, Long parentId, Long deptId);
    boolean hasChildren(Long deptId);
    boolean checkDeptExistUser(Long deptId);
    List<Long> selectDeptIdsByParentId(Long deptId);
}
