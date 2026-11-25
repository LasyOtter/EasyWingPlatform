import { request } from '@/api/core/request'
import type { AidGenerationTask, AidPageResult } from '@/types'

export interface AidTaskQuery {
  targetType?: string
  targetId?: number
  taskType?: string
  status?: string
  idempotencyKey?: string
}

/**
 * 分页查询生成任务
 */
export function getTaskList(current: number, size: number, params: AidTaskQuery = {}) {
  return request<AidPageResult<AidGenerationTask>>({
    url: '/api/v1/aid/tasks',
    method: 'get',
    params: { current, size, ...params }
  })
}

/**
 * 任务详情
 */
export function getTaskInfo(id: number | string) {
  return request<AidGenerationTask>({
    url: `/api/v1/aid/tasks/${id}`,
    method: 'get'
  })
}

/**
 * 按幂等键查询（重复提交后恢复已有任务），未命中返回 null
 */
export function getTaskByKey(key: string) {
  return request<AidGenerationTask | null>({
    url: '/api/v1/aid/tasks/by-key',
    method: 'get',
    params: { key }
  }).catch(() => null)
}

/**
 * 创建生成任务。后端按 targetType:targetId:taskType 做幂等，重复提交返回同一个任务。
 */
export function addTask(data: {
  targetType: string
  targetId: number
  taskType: string
  provider?: string
  model?: string
  prompt?: string
  visualSnapshotVersion?: number
}) {
  return request<number>({
    url: '/api/v1/aid/tasks',
    method: 'post',
    data
  })
}

/**
 * 删除终态任务（未结束的任务不允许删除）
 */
export function deleteTasks(ids: (number | string)[]) {
  return request<void>({
    url: `/api/v1/aid/tasks/${ids.join(',')}`,
    method: 'delete'
  })
}
