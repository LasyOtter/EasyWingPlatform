package com.easywing.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easywing.platform.system.domain.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);
    List<SysMenu> selectMenusByRoleId(@Param("roleId") Long roleId);
    List<String> selectPermsByUserId(@Param("userId") Long userId);
    List<String> selectPermsByRoleId(@Param("roleId") Long roleId);
    List<SysMenu> selectMenuList();
    int selectChildrenCountByParentId(@Param("parentId") Long parentId);
    int checkMenuExistRole(@Param("menuId") Long menuId);
}
