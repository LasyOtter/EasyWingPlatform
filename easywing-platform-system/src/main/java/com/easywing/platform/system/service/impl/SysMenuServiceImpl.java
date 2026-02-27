package com.easywing.platform.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easywing.platform.core.exception.BizException;
import com.easywing.platform.core.exception.ErrorCode;
import com.easywing.platform.system.domain.dto.SysMenuDTO;
import com.easywing.platform.system.domain.entity.SysMenu;
import com.easywing.platform.system.domain.vo.RouterVO;
import com.easywing.platform.system.domain.vo.SysMenuVO;
import com.easywing.platform.system.mapper.SysMenuMapper;
import com.easywing.platform.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private final SysMenuMapper menuMapper;

    @Override
    public List<SysMenuVO> selectMenuList(SysMenuDTO menuDTO) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        if (menuDTO != null) {
            wrapper.like(StrUtil.isNotBlank(menuDTO.getMenuName()), SysMenu::getMenuName, menuDTO.getMenuName())
                    .eq(menuDTO.getStatus() != null, SysMenu::getStatus, menuDTO.getStatus());
        }
        wrapper.orderByAsc(SysMenu::getParentId, SysMenu::getOrderNum);
        List<SysMenu> menus = menuMapper.selectList(wrapper);
        List<SysMenuVO> menuVOs = menus.stream().map(this::convertToVO).collect(Collectors.toList());
        return buildMenuTree(menuVOs);
    }

    @Override
    public List<SysMenuVO> selectMenusByUserId(Long userId) {
        List<SysMenu> menus = menuMapper.selectMenusByUserId(userId);
        return buildMenuTree(menus.stream().map(this::convertToVO).collect(Collectors.toList()));
    }

    @Override
    public List<Long> selectMenuIdsByRoleId(Long roleId) {
        return menuMapper.selectMenusByRoleId(roleId).stream().map(SysMenu::getId).collect(Collectors.toList());
    }

    @Override
    public SysMenuVO selectMenuById(Long menuId) {
        SysMenu menu = menuMapper.selectById(menuId);
        return menu != null ? convertToVO(menu) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertMenu(SysMenuDTO menuDTO) {
        if (!checkMenuNameUnique(menuDTO.getMenuName(), menuDTO.getParentId(), null)) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "同级菜单名称已存在");
        }
        SysMenu menu = new SysMenu();
        BeanUtils.copyProperties(menuDTO, menu);
        menuMapper.insert(menu);
        return menu.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateMenu(SysMenuDTO menuDTO) {
        if (menuDTO.getId() == null) throw new BizException(ErrorCode.INVALID_PARAMETER, "菜单ID不能为空");
        if (!checkMenuNameUnique(menuDTO.getMenuName(), menuDTO.getParentId(), menuDTO.getId())) {
            throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "同级菜单名称已存在");
        }
        SysMenu menu = new SysMenu();
        BeanUtils.copyProperties(menuDTO, menu);
        return menuMapper.updateById(menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMenuById(Long menuId) {
        if (hasChildren(menuId)) throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "存在子菜单,不允许删除");
        if (checkMenuExistRole(menuId)) throw new BizException(ErrorCode.BUSINESS_RULE_VIOLATION, "菜单已分配,不允许删除");
        return menuMapper.deleteById(menuId);
    }

    @Override
    public List<RouterVO> buildRouters(List<SysMenuVO> menus) {
        List<RouterVO> routers = new LinkedList<>();
        for (SysMenuVO menu : menus) {
            RouterVO router = new RouterVO();
            router.setHidden(menu.getVisible() == 1);
            router.setName(StrUtil.upperFirst(menu.getPath()));
            router.setPath(getRouterPath(menu));
            router.setComponent(StrUtil.isNotBlank(menu.getComponent()) ? menu.getComponent() : "Layout");
            router.setMeta(buildMeta(menu));
            if (!menu.getChildren().isEmpty() && "M".equals(menu.getMenuType())) {
                router.setChildren(buildRouters(menu.getChildren()));
            }
            routers.add(router);
        }
        return routers;
    }

    @Override
    public List<SysMenuVO> buildMenuTree(List<SysMenuVO> menus) {
        List<SysMenuVO> returnList = new ArrayList<>();
        List<Long> tempList = menus.stream().map(SysMenuVO::getId).collect(Collectors.toList());
        for (SysMenuVO menu : menus) {
            if (menu.getParentId() == null || menu.getParentId() == 0L || !tempList.contains(menu.getParentId())) {
                recursionFn(menus, menu);
                returnList.add(menu);
            }
        }
        return returnList.isEmpty() ? menus : returnList;
    }

    @Override
    public List<String> selectPermsByUserId(Long userId) { return menuMapper.selectPermsByUserId(userId); }

    @Override
    public boolean checkMenuNameUnique(String menuName, Long parentId, Long menuId) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMenu::getMenuName, menuName)
                .eq(parentId != null, SysMenu::getParentId, parentId == null ? 0L : parentId)
                .ne(menuId != null, SysMenu::getId, menuId);
        return menuMapper.selectCount(wrapper) == 0;
    }

    @Override
    public boolean hasChildren(Long menuId) { return menuMapper.selectChildrenCountByParentId(menuId) > 0; }

    @Override
    public boolean checkMenuExistRole(Long menuId) { return menuMapper.checkMenuExistRole(menuId) > 0; }

    private SysMenuVO convertToVO(SysMenu menu) {
        SysMenuVO vo = new SysMenuVO();
        BeanUtils.copyProperties(menu, vo);
        vo.setChildren(new ArrayList<>());
        return vo;
    }

    private String getRouterPath(SysMenuVO menu) {
        String routerPath = menu.getPath();
        if (menu.getParentId() == null || menu.getParentId() == 0L) {
            if ("M".equals(menu.getMenuType())) routerPath = "/" + menu.getPath();
        }
        return routerPath;
    }

    private RouterVO.MetaVO buildMeta(SysMenuVO menu) {
        RouterVO.MetaVO meta = new RouterVO.MetaVO();
        meta.setTitle(menu.getMenuName());
        meta.setIcon(menu.getIcon());
        meta.setNoCache(menu.getIsCache() == 1);
        meta.setHidden(menu.getVisible() == 1);
        return meta;
    }

    private void recursionFn(List<SysMenuVO> list, SysMenuVO t) {
        List<SysMenuVO> childList = list.stream()
                .filter(m -> t.getId().equals(m.getParentId()))
                .sorted(Comparator.comparingInt(SysMenuVO::getOrderNum))
                .collect(Collectors.toList());
        t.setChildren(childList);
        for (SysMenuVO tChild : childList) {
            if (list.stream().anyMatch(m -> tChild.getId().equals(m.getParentId()))) {
                recursionFn(list, tChild);
            }
        }
    }
}
