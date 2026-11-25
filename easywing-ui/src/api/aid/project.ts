import { request } from '@/api/core/request'
import type { AidPageResult, AidProject, AidProjectQuery } from '@/types'

/**
 * 分页查询创作项目
 */
export function getProjectList(current: number, size: number, params: AidProjectQuery) {
  return request<AidPageResult<AidProject>>({
    url: '/api/v1/aid/projects',
    method: 'get',
    params: { current, size, ...params }
  })
}

/**
 * 项目下拉选项（新建资产/分集时选择所属项目）
 */
export function getProjectOptions() {
  return request<AidProject[]>({
    url: '/api/v1/aid/projects/options',
    method: 'get'
  })
}

/**
 * 项目详情
 */
export function getProjectInfo(id: number | string) {
  return request<AidProject>({
    url: `/api/v1/aid/projects/${id}`,
    method: 'get'
  })
}

/**
 * 新增项目
 */
export function addProject(data: AidProject) {
  return request<number>({
    url: '/api/v1/aid/projects',
    method: 'post',
    data
  })
}

/**
 * 修改项目
 */
export function updateProject(data: AidProject) {
  return request<void>({
    url: '/api/v1/aid/projects',
    method: 'put',
    data
  })
}

/**
 * 删除项目（会级联清理其下分集与资产）
 */
export function deleteProjects(ids: (number | string)[]) {
  return request<void>({
    url: `/api/v1/aid/projects/${ids.join(',')}`,
    method: 'delete'
  })
}
