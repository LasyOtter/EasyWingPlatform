import { request } from '@/api/core/request'
import type { AidAsset, AidAssetQuery, AidPageResult } from '@/types'

/**
 * 分页查询资产库。
 *
 * 后端强制要求 projectId，避免跨项目遍历，调用方必须传。
 */
export function getAssetList(current: number, size: number, params: AidAssetQuery) {
  return request<AidPageResult<AidAsset>>({
    url: '/api/v1/aid/assets',
    method: 'get',
    params: { current, size, ...params }
  })
}

/**
 * 资产详情
 */
export function getAssetInfo(id: number | string) {
  return request<AidAsset>({
    url: `/api/v1/aid/assets/${id}`,
    method: 'get'
  })
}

/**
 * 校验编号在项目内是否可用；返回 true 表示未被占用
 */
export function checkAssetCode(projectId: number, assetCode: string, excludeId?: number) {
  return request<boolean>({
    url: '/api/v1/aid/assets/check-code',
    method: 'get',
    params: { projectId, assetCode, excludeId }
  })
}

/**
 * 新增资产
 */
export function addAsset(data: AidAsset) {
  return request<number>({
    url: '/api/v1/aid/assets',
    method: 'post',
    data
  })
}

/**
 * 修改资产
 */
export function updateAsset(data: AidAsset) {
  return request<void>({
    url: '/api/v1/aid/assets',
    method: 'put',
    data
  })
}

/**
 * 删除资产
 */
export function deleteAssets(ids: (number | string)[]) {
  return request<void>({
    url: `/api/v1/aid/assets/${ids.join(',')}`,
    method: 'delete'
  })
}

/**
 * 上传参考图，返回可回显的相对路径（如 /api/v1/aid/assets/files/xxx.png）
 */
export function uploadAssetFile(file: File, onProgress?: (percent: number) => void) {
  const formData = new FormData()
  formData.append('file', file)
  return request<string>({
    url: '/api/v1/aid/assets/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (onProgress && event.total) {
        onProgress(Math.round((event.loaded * 100) / event.total))
      }
    }
  })
}
