import request from '@/api/core/request'
import type { PageQuery, PageResult } from '@/types'

/** 认证服务令牌结果 */
export interface TokenResult {
  access_token: string
  refresh_token: string
  token_type: string
  expires_in: number
}

/** 登录 */
export function loginApi(data: { username: string; password: string; tenantId?: string }) {
  return request<TokenResult>({
    url: '/v1/token/login',
    method: 'post',
    data
  })
}

/** 获取当前用户信息 */
export function getUserInfoApi() {
  return request<any>({
    url: '/system/users/me',
    method: 'get'
  })
}

/** 注销当前令牌 */
export function logoutApi() {
  return request<void>({
    url: '/v1/token/logout',
    method: 'post'
  })
}

/**
 * 获取用户列表
 */
export function getUserList(params: PageQuery) {
  // 后端返回 MyBatis-Plus Page（records/total/current/size），适配为前端分页结构
  return request<any>({
    url: '/system/users',
    method: 'get',
    params
  }).then((res) => ({
    rows: res?.records ?? [],
    total: res?.total ?? 0,
    pageNum: res?.current ?? params?.pageNum ?? 1,
    pageSize: res?.size ?? params?.pageSize ?? 10
  })) as Promise<PageResult>
}

/**
 * 获取用户详情
 */
export function getUserInfo(userId: number | string) {
  return request({
    url: `/system/users/${userId}`,
    method: 'get'
  })
}

/**
 * 新增用户
 */
export function addUser(data: any) {
  return request({
    url: '/system/users',
    method: 'post',
    data
  })
}

/**
 * 修改用户
 */
export function updateUser(data: any) {
  return request({
    url: '/system/users',
    method: 'put',
    data
  })
}

/**
 * 删除用户
 */
export function deleteUser(userId: number | string) {
  return request({
    url: `/system/users/${userId}`,
    method: 'delete'
  })
}

/**
 * 修改用户状态
 */
export function changeUserStatus(data: { userId: number | string; status: string }) {
  return request({
    url: `/system/users/${data.userId}/status`,
    method: 'put',
    params: { status: data.status }
  })
}

/**
 * 重置用户密码
 */
export function resetUserPwd(data: { userId: number | string; password: string }) {
  return request({
    url: `/system/users/${data.userId}/reset-password`,
    method: 'put',
    params: { password: data.password }
  })
}
