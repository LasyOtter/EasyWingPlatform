package com.easywing.platform.system.mapper.struct;

import com.easywing.platform.system.domain.dto.SysMenuDTO;
import com.easywing.platform.system.domain.entity.SysMenu;
import com.easywing.platform.system.domain.vo.RouterVO;
import com.easywing.platform.system.domain.vo.SysMenuVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MenuMapper {

    MenuMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(MenuMapper.class);

    SysMenuVO toVO(SysMenu menu);

    List<SysMenuVO> toVOList(List<SysMenu> menus);

    SysMenu toEntity(SysMenuDTO dto);

    @Mapping(target = "name", expression = "java(com.hutool.core.util.StrUtil.upperFirst(menu.getPath()))")
    @Mapping(target = "path", expression = "java(getRouterPath(menu))")
    @Mapping(target = "component", expression = "java(com.hutool.core.util.StrUtil.isNotBlank(menu.getComponent()) ? menu.getComponent() : \"Layout\")")
    @Mapping(target = "hidden", expression = "java(menu.getVisible() == 1)")
    RouterVO toRouterVO(SysMenu menu);

    default List<RouterVO> toRouterVOList(List<SysMenu> menus) {
        return menus.stream()
            .filter(menu -> "M".equals(menu.getMenuType()) || "C".equals(menu.getMenuType()))
            .map(this::toRouterVO)
            .collect(Collectors.toList());
    }

    default String getRouterPath(SysMenu menu) {
        String routerPath = menu.getPath();
        if (menu.getParentId() == null || menu.getParentId() == 0L) {
            if ("M".equals(menu.getMenuType())) {
                routerPath = "/" + menu.getPath();
            }
        }
        return routerPath;
    }
}
