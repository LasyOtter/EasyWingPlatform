package com.easywing.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.system.domain.dto.SysDictDataDTO;
import com.easywing.platform.system.domain.entity.SysDictData;
import com.easywing.platform.system.domain.vo.SysDictDataVO;
import com.easywing.platform.system.mapper.SysDictDataMapper;
import com.easywing.platform.system.mapper.struct.DictDataMapper;
import com.easywing.platform.system.service.SysDictDataService;
import com.easywing.platform.system.util.PageUtil;
import lombok.RequiredArgsConstructor;
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
    private final DictDataMapper dictDataMapperStruct;
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
        return PageUtil.convert(dictDataPage, dictDataMapperStruct::toVO);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'data:' + #dictType")
    public List<SysDictDataVO> selectDictDataByType(String dictType) {
        List<SysDictData> dictDataList = dictDataMapper.selectDictDataByType(dictType);
        return dictDataMapperStruct.toVOList(dictDataList);
    }

    @Override
    public SysDictDataVO selectDictDataById(Long dictCode) {
        SysDictData dictData = dictDataMapper.selectById(dictCode);
        return dictData != null ? dictDataMapperStruct.toVO(dictData) : null;
    }

    @Override
    @CacheEvict(value = CACHE_NAME, key = "'data:' + #dictDataDTO.dictType")
    public Long insertDictData(SysDictDataDTO dictDataDTO) {
        SysDictData dictData = new SysDictData();
        dictData.setDictSort(dictDataDTO.getDictSort());
        dictData.setDictLabel(dictDataDTO.getDictLabel());
        dictData.setDictValue(dictDataDTO.getDictValue());
        dictData.setDictType(dictDataDTO.getDictType());
        dictData.setCssClass(dictDataDTO.getCssClass());
        dictData.setListClass(dictDataDTO.getListClass());
        dictData.setIsDefault(dictDataDTO.getIsDefault());
        dictData.setStatus(dictDataDTO.getStatus());
        dictData.setRemark(dictDataDTO.getRemark());
        dictDataMapper.insert(dictData);
        return dictData.getId();
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public int updateDictData(SysDictDataDTO dictDataDTO) {
        if (dictDataDTO.getId() == null) throw new IllegalArgumentException("字典编码不能为空");
        SysDictData dictData = new SysDictData();
        dictData.setId(dictDataDTO.getId());
        dictData.setDictSort(dictDataDTO.getDictSort());
        dictData.setDictLabel(dictDataDTO.getDictLabel());
        dictData.setDictValue(dictDataDTO.getDictValue());
        dictData.setDictType(dictDataDTO.getDictType());
        dictData.setCssClass(dictDataDTO.getCssClass());
        dictData.setListClass(dictDataDTO.getListClass());
        dictData.setIsDefault(dictDataDTO.getIsDefault());
        dictData.setStatus(dictDataDTO.getStatus());
        dictData.setRemark(dictDataDTO.getRemark());
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
}
