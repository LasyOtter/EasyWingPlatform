package com.easywing.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easywing.platform.system.domain.entity.SysDictData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SysDictDataMapper extends BaseMapper<SysDictData> {
    List<SysDictData> selectDictDataByType(@Param("dictType") String dictType);
    String selectDictLabel(@Param("dictType") String dictType, @Param("dictValue") String dictValue);
}
