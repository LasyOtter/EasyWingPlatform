import { request } from '@/api/core/request'
import type { AidEpisode, AidPageResult } from '@/types'

/**
 * 分页查询分集（可按项目过滤）
 */
export function getEpisodeList(current: number, size: number, projectId?: number) {
  return request<AidPageResult<AidEpisode>>({
    url: '/api/v1/aid/episodes',
    method: 'get',
    params: { current, size, projectId }
  })
}

/**
 * 分集详情
 */
export function getEpisodeInfo(id: number | string) {
  return request<AidEpisode>({
    url: `/api/v1/aid/episodes/${id}`,
    method: 'get'
  })
}

/**
 * 新增分集
 */
export function addEpisode(data: AidEpisode) {
  return request<number>({
    url: '/api/v1/aid/episodes',
    method: 'post',
    data
  })
}

/**
 * 修改分集
 */
export function updateEpisode(data: AidEpisode) {
  return request<void>({
    url: '/api/v1/aid/episodes',
    method: 'put',
    data
  })
}

/**
 * 删除分集（会级联清理其下分镜）
 */
export function deleteEpisodes(ids: (number | string)[]) {
  return request<void>({
    url: `/api/v1/aid/episodes/${ids.join(',')}`,
    method: 'delete'
  })
}
