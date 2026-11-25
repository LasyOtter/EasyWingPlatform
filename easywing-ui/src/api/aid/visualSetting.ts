import { request } from '@/api/core/request'
import type { AidPageResult, AidVisualSetting, AidVisualTemplate } from '@/types'

/**
 * 分页查询视觉设定
 *
 * @param scope 作用域 PROJECT/TEMPLATE；不传则查全部
 */
export function getVisualList(
  current: number,
  size: number,
  params: { scope?: string; name?: string } = {}
) {
  return request<AidPageResult<AidVisualSetting>>({
    url: '/api/v1/aid/visual-settings',
    method: 'get',
    params: { current, size, ...params }
  })
}

/**
 * 视觉设定详情
 */
export function getVisualInfo(id: number | string) {
  return request<AidVisualSetting>({
    url: `/api/v1/aid/visual-settings/${id}`,
    method: 'get'
  })
}

/**
 * 新增视觉设定
 */
export function addVisual(data: AidVisualSetting) {
  return request<number>({
    url: '/api/v1/aid/visual-settings',
    method: 'post',
    data
  })
}

/**
 * 修改视觉设定（版本号自增，项目侧快照随之失效重建）
 */
export function updateVisual(data: AidVisualSetting) {
  return request<void>({
    url: '/api/v1/aid/visual-settings',
    method: 'put',
    data
  })
}

/**
 * 删除视觉设定
 */
export function deleteVisuals(ids: (number | string)[]) {
  return request<void>({
    url: `/api/v1/aid/visual-settings/${ids.join(',')}`,
    method: 'delete'
  })
}

/**
 * 导出为 JSON 模板（AC-11）
 */
export function exportVisual(id: number | string) {
  return request<AidVisualTemplate>({
    url: `/api/v1/aid/visual-settings/${id}/export`,
    method: 'post'
  })
}

/**
 * 从 JSON 模板导入（AC-11）。
 *
 * 同名模板默认拒绝，需显式 overwrite 才会覆盖并自增版本。
 */
export function importVisual(data: {
  content: AidVisualTemplate
  name?: string
  overwrite?: boolean
}) {
  return request<number>({
    url: '/api/v1/aid/visual-settings/import',
    method: 'post',
    data
  })
}
