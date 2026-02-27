package com.easywing.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easywing.platform.system.domain.entity.SysDictType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictType> {
    SysDictType selectDictTypeByType(@Param("dictType") String dictType);
    int checkDictTypeUnique(@Param("dictType") String dictType, @Param("dictId") Long dictId);
}
