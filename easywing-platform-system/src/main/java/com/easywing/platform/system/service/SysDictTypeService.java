package com.easywing.platform.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.easywing.platform.system.domain.dto.SysDictTypeDTO;
import com.easywing.platform.system.domain.entity.SysDictType;
import com.easywing.platform.system.domain.vo.SysDictTypeVO;
import java.util.List;

public interface SysDictTypeService extends IService<SysDictType> {
    Page<SysDictTypeVO> selectDictTypePage(Page<SysDictType> page, SysDictTypeDTO dictTypeDTO);
    List<SysDictTypeVO> selectDictTypeAll();
    SysDictTypeVO selectDictTypeByType(String dictType);
    SysDictTypeVO selectDictTypeById(Long dictId);
    Long insertDictType(SysDictTypeDTO dictTypeDTO);
    int updateDictType(SysDictTypeDTO dictTypeDTO);
    int deleteDictTypeByIds(List<Long> dictIds);
    void refreshCache();
    boolean checkDictTypeUnique(String dictType, Long dictId);
}
