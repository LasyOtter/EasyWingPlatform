package com.easywing.platform.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.easywing.platform.system.domain.dto.SysDictDataDTO;
import com.easywing.platform.system.domain.entity.SysDictData;
import com.easywing.platform.system.domain.vo.SysDictDataVO;
import java.util.List;

public interface SysDictDataService extends IService<SysDictData> {
    Page<SysDictDataVO> selectDictDataPage(Page<SysDictData> page, SysDictDataDTO dictDataDTO);
    List<SysDictDataVO> selectDictDataByType(String dictType);
    SysDictDataVO selectDictDataById(Long dictCode);
    Long insertDictData(SysDictDataDTO dictDataDTO);
    int updateDictData(SysDictDataDTO dictDataDTO);
    int deleteDictDataByIds(List<Long> dictCodes);
    String getDictLabel(String dictType, String dictValue);
    List<String> getDictValues(String dictType);
}
