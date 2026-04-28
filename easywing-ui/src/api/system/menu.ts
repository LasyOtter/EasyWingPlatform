import request from '@/api/core/request'
import type { Menu, PageQuery, PageResult } from '@/types'

/**
 * 获取路由菜单
 */
export function getRouters() {
  return request<{ menus: Menu[] }>({
    url: '/system/menu/getRouters',
    method: 'get'
  })
}

/**
 * 获取菜单列表
 */
export function getMenuList(params: PageQuery) {
  return request<PageResult<Menu>>({
    url: '/system/menu/list',
    method: 'get',
    params
  })
}

/**
 * 获取菜单详情
 */
export function getMenuInfo(menuId: number | string) {
  return request<Menu>({
    url: `/system/menu/${menuId}`,
    method: 'get'
  })
}

/**
 * 新增菜单
 */
export function addMenu(data: Menu) {
  return request({
    url: '/system/menu',
    method: 'post',
    data
  })
}

/**
 * 修改菜单
 */
export function updateMenu(data: Menu) {
  return request({
    url: '/system/menu',
    method: 'put',
    data
  })
}

/**
 * 删除菜单
 */
export function deleteMenu(menuId: number | string) {
  return request({
    url: `/system/menu/${menuId}`,
    method: 'delete'
  })
}
