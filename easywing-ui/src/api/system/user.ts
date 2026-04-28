import request from '@/api/core/request'
import type { PageQuery, PageResult } from '@/types'

/**
 * 获取用户列表
 */
export function getUserList(params: PageQuery) {
  return request<PageResult>({
    url: '/system/user/list',
    method: 'get',
    params
  })
}

/**
 * 获取用户详情
 */
export function getUserInfo(userId: number | string) {
  return request({
    url: `/system/user/${userId}`,
    method: 'get'
  })
}

/**
 * 新增用户
 */
export function addUser(data: any) {
  return request({
    url: '/system/user',
    method: 'post',
    data
  })
}

/**
 * 修改用户
 */
export function updateUser(data: any) {
  return request({
    url: '/system/user',
    method: 'put',
    data
  })
}

/**
 * 删除用户
 */
export function deleteUser(userId: number | string) {
  return request({
    url: `/system/user/${userId}`,
    method: 'delete'
  })
}

/**
 * 修改用户状态
 */
export function changeUserStatus(data: { userId: number | string; status: string }) {
  return request({
    url: '/system/user/changeStatus',
    method: 'put',
    data
  })
}

/**
 * 重置用户密码
 */
export function resetUserPwd(data: { userId: number | string; password: string }) {
  return request({
    url: '/system/user/resetPwd',
    method: 'put',
    data
  })
}
