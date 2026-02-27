package com.easywing.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.system.domain.dto.SysDeptDTO;
import com.easywing.platform.system.domain.entity.SysDept;
import com.easywing.platform.system.domain.vo.SysDeptVO;
import com.easywing.platform.system.mapper.SysDeptMapper;
import com.easywing.platform.system.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    private final SysDeptMapper deptMapper;

    @Override
    public List<SysDeptVO> selectDeptList(SysDeptDTO deptDTO) {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        if (deptDTO != null) {
            wrapper.like(StrUtil.isNotBlank(deptDTO.getDeptName()), SysDept::getDeptName, deptDTO.getDeptName())
                    .eq(deptDTO.getStatus() != null, SysDept::getStatus, deptDTO.getStatus());
        }
        wrapper.orderByAsc(SysDept::getOrderNum);
        List<SysDept> depts = deptMapper.selectList(wrapper);
        return buildDeptTree(depts.stream().map(this::convertToVO).collect(Collectors.toList()));
    }

    @Override
    public List<SysDeptVO> selectDeptTreeList() {
        List<SysDept> depts = deptMapper.selectList(new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getOrderNum));
        return buildDeptTree(depts.stream().map(this::convertToVO).collect(Collectors.toList()));
    }

    @Override
    public SysDeptVO selectDeptById(Long deptId) {
        SysDept dept = deptMapper.selectById(deptId);
        return dept != null ? convertToVO(dept) : null;
    }

    @Override
    public SysDeptVO selectDeptByUserId(Long userId) {
        SysDept dept = deptMapper.selectDeptByUserId(userId);
        return dept != null ? convertToVO(dept) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertDept(SysDeptDTO deptDTO) {
        if (!checkDeptNameUnique(deptDTO.getDeptName(), deptDTO.getParentId(), null)) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "部门名称已存在");
        }
        SysDept dept = new SysDept();
        BeanUtils.copyProperties(deptDTO, dept);
        if (deptDTO.getParentId() != null && deptDTO.getParentId() != 0L) {
            SysDept parentDept = deptMapper.selectById(deptDTO.getParentId());
            if (parentDept != null) dept.setAncestors(parentDept.getAncestors() + "," + deptDTO.getParentId());
        } else {
            dept.setAncestors("0");
        }
        deptMapper.insert(dept);
        return dept.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDept(SysDeptDTO deptDTO) {
        if (deptDTO.getId() == null) throw new BizException(ErrorCode.INVALID_PARAMETER, "部门ID不能为空");
        if (!checkDeptNameUnique(deptDTO.getDeptName(), deptDTO.getParentId(), deptDTO.getId())) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "部门名称已存在");
        }
        SysDept dept = new SysDept();
        BeanUtils.copyProperties(deptDTO, dept);
        return deptMapper.updateById(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDeptById(Long deptId) {
        if (hasChildren(deptId)) throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "存在子部门,不允许删除");
        if (checkDeptExistUser(deptId)) throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "部门存在用户,不允许删除");
        return deptMapper.deleteById(deptId);
    }

    @Override
    public boolean checkDeptNameUnique(String deptName, Long parentId, Long deptId) {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDept::getDeptName, deptName)
                .eq(parentId != null, SysDept::getParentId, parentId == null ? 0L : parentId)
                .ne(deptId != null, SysDept::getId, deptId);
        return deptMapper.selectCount(wrapper) == 0;
    }

    @Override
    public boolean hasChildren(Long deptId) { return deptMapper.selectChildrenCountByParentId(deptId) > 0; }

    @Override
    public boolean checkDeptExistUser(Long deptId) { return deptMapper.checkDeptExistUser(deptId) > 0; }

    @Override
    public List<Long> selectDeptIdsByParentId(Long deptId) { return deptMapper.selectDeptIdsByParentId(deptId); }

    private SysDeptVO convertToVO(SysDept dept) {
        SysDeptVO vo = new SysDeptVO();
        BeanUtils.copyProperties(dept, vo);
        vo.setChildren(new ArrayList<>());
        return vo;
    }

    private List<SysDeptVO> buildDeptTree(List<SysDeptVO> depts) {
        List<SysDeptVO> returnList = new ArrayList<>();
        List<Long> tempList = depts.stream().map(SysDeptVO::getId).collect(Collectors.toList());
        for (SysDeptVO dept : depts) {
            if (dept.getParentId() == null || dept.getParentId() == 0L || !tempList.contains(dept.getParentId())) {
                recursionFn(depts, dept);
                returnList.add(dept);
            }
        }
        return returnList.isEmpty() ? depts : returnList;
    }

    private void recursionFn(List<SysDeptVO> list, SysDeptVO t) {
        List<SysDeptVO> childList = list.stream()
                .filter(m -> t.getId().equals(m.getParentId()))
                .sorted(Comparator.comparingInt(SysDeptVO::getOrderNum))
                .collect(Collectors.toList());
        t.setChildren(childList);
        for (SysDeptVO tChild : childList) {
            if (list.stream().anyMatch(m -> tChild.getId().equals(m.getParentId()))) recursionFn(list, tChild);
        }
    }
}
