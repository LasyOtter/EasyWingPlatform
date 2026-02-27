package com.easywing.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.system.domain.dto.SysDictDataDTO;
import com.easywing.platform.system.domain.entity.SysDictData;
import com.easywing.platform.system.domain.vo.SysDictDataVO;
import com.easywing.platform.system.mapper.SysDictDataMapper;
import com.easywing.platform.system.service.SysDictDataService;
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
public class SysDictDataServiceImpl extends ServiceImpl<SysDictDataMapper, SysDictData> implements SysDictDataService {

    private final SysDictDataMapper dictDataMapper;
    private static final String CACHE_NAME = "sys_dict";

    @Override
    public Page<SysDictDataVO> selectDictDataPage(Page<SysDictData> page, SysDictDataDTO dictDataDTO) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        if (dictDataDTO != null) {
            wrapper.like(StrUtil.isNotBlank(dictDataDTO.getDictLabel()), SysDictData::getDictLabel, dictDataDTO.getDictLabel())
                    .eq(StrUtil.isNotBlank(dictDataDTO.getDictType()), SysDictData::getDictType, dictDataDTO.getDictType())
                    .eq(dictDataDTO.getStatus() != null, SysDictData::getStatus, dictDataDTO.getStatus());
        }
        wrapper.orderByAsc(SysDictData::getDictSort);
        Page<SysDictData> dictDataPage = dictDataMapper.selectPage(page, wrapper);
        Page<SysDictDataVO> voPage = new Page<>(dictDataPage.getCurrent(), dictDataPage.getSize(), dictDataPage.getTotal());
        voPage.setRecords(dictDataPage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'data:' + #dictType")
    public List<SysDictDataVO> selectDictDataByType(String dictType) {
        return dictDataMapper.selectDictDataByType(dictType).stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public SysDictDataVO selectDictDataById(Long dictCode) {
        SysDictData dictData = dictDataMapper.selectById(dictCode);
        return dictData != null ? convertToVO(dictData) : null;
    }

    @Override
    @CacheEvict(value = CACHE_NAME, key = "'data:' + #dictDataDTO.dictType")
    public Long insertDictData(SysDictDataDTO dictDataDTO) {
        SysDictData dictData = new SysDictData();
        BeanUtils.copyProperties(dictDataDTO, dictData);
        dictDataMapper.insert(dictData);
        return dictData.getId();
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public int updateDictData(SysDictDataDTO dictDataDTO) {
        if (dictDataDTO.getId() == null) throw new IllegalArgumentException("字典编码不能为空");
        SysDictData dictData = new SysDictData();
        BeanUtils.copyProperties(dictDataDTO, dictData);
        return dictDataMapper.updateById(dictData);
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public int deleteDictDataByIds(List<Long> dictCodes) {
        if (CollectionUtils.isEmpty(dictCodes)) return 0;
        return dictDataMapper.deleteBatchIds(dictCodes);
    }

    @Override
    public String getDictLabel(String dictType, String dictValue) { return dictDataMapper.selectDictLabel(dictType, dictValue); }

    @Override
    public List<String> getDictValues(String dictType) {
        return selectDictDataByType(dictType).stream().map(SysDictDataVO::getDictValue).collect(Collectors.toList());
    }

    private SysDictDataVO convertToVO(SysDictData dictData) {
        SysDictDataVO vo = new SysDictDataVO();
        BeanUtils.copyProperties(dictData, vo);
        return vo;
    }
}
