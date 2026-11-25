import { request } from '@/api/core/request'
import type { AidPageResult, AidShot } from '@/types'

/**
 * 分页查询分镜（可按分集过滤）
 */
export function getShotList(current: number, size: number, episodeId?: number) {
  return request<AidPageResult<AidShot>>({
    url: '/api/v1/aid/shots',
    method: 'get',
    params: { current, size, episodeId }
  })
}

/**
 * 分镜详情
 */
export function getShotInfo(id: number | string) {
  return request<AidShot>({
    url: `/api/v1/aid/shots/${id}`,
    method: 'get'
  })
}

/**
 * 新增分镜。description 支持 {@code @资产[C01]} 占位符，由服务层解析成可读标注。
 */
export function addShot(data: AidShot) {
  return request<number>({
    url: '/api/v1/aid/shots',
    method: 'post',
    data
  })
}

/**
 * 修改分镜
 */
export function updateShot(data: AidShot) {
  return request<void>({
    url: '/api/v1/aid/shots',
    method: 'put',
    data
  })
}

/**
 * 删除分镜
 */
export function deleteShots(ids: (number | string)[]) {
  return request<void>({
    url: `/api/v1/aid/shots/${ids.join(',')}`,
    method: 'delete'
  })
}
