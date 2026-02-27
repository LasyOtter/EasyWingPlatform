package com.easywing.platform.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.easywing.platform.system.domain.dto.SysMenuDTO;
import com.easywing.platform.system.domain.entity.SysMenu;
import com.easywing.platform.system.domain.vo.RouterVO;
import com.easywing.platform.system.domain.vo.SysMenuVO;
import java.util.List;

public interface SysMenuService extends IService<SysMenu> {
    List<SysMenuVO> selectMenuList(SysMenuDTO menuDTO);
    List<SysMenuVO> selectMenusByUserId(Long userId);
    List<Long> selectMenuIdsByRoleId(Long roleId);
    SysMenuVO selectMenuById(Long menuId);
    Long insertMenu(SysMenuDTO menuDTO);
    int updateMenu(SysMenuDTO menuDTO);
    int deleteMenuById(Long menuId);
    List<RouterVO> buildRouters(List<SysMenuVO> menus);
    List<SysMenuVO> buildMenuTree(List<SysMenuVO> menus);
    List<String> selectPermsByUserId(Long userId);
    boolean checkMenuNameUnique(String menuName, Long parentId, Long menuId);
    boolean hasChildren(Long menuId);
    boolean checkMenuExistRole(Long menuId);
}
