package com.easywing.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.system.domain.dto.SysDictTypeDTO;
import com.easywing.platform.system.domain.entity.SysDictType;
import com.easywing.platform.system.domain.vo.SysDictTypeVO;
import com.easywing.platform.system.mapper.SysDictTypeMapper;
import com.easywing.platform.system.service.SysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysDictTypeServiceImpl extends ServiceImpl<SysDictTypeMapper, SysDictType> implements SysDictTypeService {

    private final SysDictTypeMapper dictTypeMapper;
    private static final String CACHE_NAME = "sys_dict";

    @Override
    public Page<SysDictTypeVO> selectDictTypePage(Page<SysDictType> page, SysDictTypeDTO dictTypeDTO) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        if (dictTypeDTO != null) {
            wrapper.like(StrUtil.isNotBlank(dictTypeDTO.getDictName()), SysDictType::getDictName, dictTypeDTO.getDictName())
                    .eq(dictTypeDTO.getStatus() != null, SysDictType::getStatus, dictTypeDTO.getStatus());
        }
        Page<SysDictType> dictTypePage = dictTypeMapper.selectPage(page, wrapper);
        Page<SysDictTypeVO> voPage = new Page<>(dictTypePage.getCurrent(), dictTypePage.getSize(), dictTypePage.getTotal());
        voPage.setRecords(dictTypePage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public List<SysDictTypeVO> selectDictTypeAll() {
        return dictTypeMapper.selectList(new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getStatus, 0))
                .stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'type:' + #dictType")
    public SysDictTypeVO selectDictTypeByType(String dictType) {
        SysDictType type = dictTypeMapper.selectDictTypeByType(dictType);
        return type != null ? convertToVO(type) : null;
    }

    @Override
    public SysDictTypeVO selectDictTypeById(Long dictId) {
        SysDictType type = dictTypeMapper.selectById(dictId);
        return type != null ? convertToVO(type) : null;
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public Long insertDictType(SysDictTypeDTO dictTypeDTO) {
        if (!checkDictTypeUnique(dictTypeDTO.getDictType(), null)) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "字典类型已存在");
        }
        SysDictType dictType = new SysDictType();
        BeanUtils.copyProperties(dictTypeDTO, dictType);
        dictTypeMapper.insert(dictType);
        return dictType.getId();
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public int updateDictType(SysDictTypeDTO dictTypeDTO) {
        if (dictTypeDTO.getId() == null) throw new BizException(ErrorCode.INVALID_PARAMETER, "字典ID不能为空");
        if (!checkDictTypeUnique(dictTypeDTO.getDictType(), dictTypeDTO.getId())) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "字典类型已存在");
        }
        SysDictType dictType = new SysDictType();
        BeanUtils.copyProperties(dictTypeDTO, dictType);
        return dictTypeMapper.updateById(dictType);
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public int deleteDictTypeByIds(List<Long> dictIds) {
        if (CollectionUtils.isEmpty(dictIds)) return 0;
        return dictTypeMapper.deleteBatchIds(dictIds);
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void refreshCache() {}

    @Override
    public boolean checkDictTypeUnique(String dictType, Long dictId) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictType::getDictType, dictType).ne(dictId != null, SysDictType::getId, dictId);
        return dictTypeMapper.selectCount(wrapper) == 0;
    }

    private SysDictTypeVO convertToVO(SysDictType dictType) {
        SysDictTypeVO vo = new SysDictTypeVO();
        BeanUtils.copyProperties(dictType, vo);
        return vo;
    }
}
