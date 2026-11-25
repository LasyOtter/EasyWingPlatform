/**
 * AID 漫剧工坊前端工具函数。
 */

/** 资产类型 */
export const ASSET_TYPES = ['CHARACTER', 'SCENE', 'PROP', 'STYLE'] as const

/** 项目状态 */
export const PROJECT_STATUSES = ['DRAFT', 'RUNNING', 'PAUSED', 'COMPLETED', 'ARCHIVED'] as const

/** 分集 / 分镜状态 */
export const FLOW_STATUSES = ['DRAFT', 'GENERATING', 'COMPLETED', 'FAILED'] as const

/** 任务状态 */
export const TASK_STATUSES = ['PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELED'] as const

/** 任务类型 */
export const TASK_TYPES = ['IMAGE', 'VIDEO', 'AUDIO', 'COMPOSE'] as const

/** 视觉设定作用域 */
export const SETTING_SCOPES = ['PROJECT', 'TEMPLATE'] as const

/** 图片扩展名白名单，与后端 easywing.aid.storage.allowed-extensions 保持一致 */
export const ALLOWED_IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp', 'gif']

/** 参考图大小上限（MB），与后端 easywing.aid.storage.max-file-size 默认值一致 */
export const MAX_IMAGE_SIZE_MB = 20

/** el-tag 语义色，避免每个页面各写一套映射 */
const TAG_TYPES = {
  DRAFT: 'info',
  GENERATING: 'warning',
  RUNNING: 'warning',
  PENDING: 'info',
  COMPLETED: 'success',
  FAILED: 'danger',
  CANCELED: 'danger',
  PAUSED: 'warning',
  ARCHIVED: 'info',
  CHARACTER: 'primary',
  SCENE: 'success',
  PROP: 'warning',
  STYLE: 'danger',
  PROJECT: 'primary',
  TEMPLATE: 'success',
  IMAGE: 'primary',
  VIDEO: 'success',
  AUDIO: 'warning',
  COMPOSE: 'danger'
} as const

export type AidTagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/**
 * 状态/类型 → el-tag 类型
 *
 * 未知值回落 info，避免新增枚举时整列渲染失败。
 */
export function statusTagType(code?: string): AidTagType {
  if (!code) return 'info'
  return (TAG_TYPES as Record<string, AidTagType>)[code] ?? 'info'
}

/**
 * 秒 → mm:ss，用于时长列展示
 */
export function formatDuration(seconds?: number): string {
  if (!seconds || seconds <= 0) return '-'
  const min = Math.floor(seconds / 60)
  const sec = seconds % 60
  return `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

/**
 * 文件扩展名（小写，不含点）
 */
export function fileExtension(name: string): string {
  const index = name.lastIndexOf('.')
  return index < 0 ? '' : name.slice(index + 1).toLowerCase()
}

/**
 * 校验参考图是否可上传：扩展名白名单 + 大小上限。
 *
 * @returns 错误信息；可上传时返回空字符串
 */
export function validateImage(file: File): string {
  const ext = fileExtension(file.name)
  if (!ALLOWED_IMAGE_EXTENSIONS.includes(ext)) {
    return `不支持的图片格式：${ext || '未知'}，仅支持 ${ALLOWED_IMAGE_EXTENSIONS.join(' / ')}`
  }
  if (file.size <= 0) {
    return '文件为空，请重新选择'
  }
  if (file.size > MAX_IMAGE_SIZE_MB * 1024 * 1024) {
    return `文件大小不能超过 ${MAX_IMAGE_SIZE_MB}MB`
  }
  return ''
}
