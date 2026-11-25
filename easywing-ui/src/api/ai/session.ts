import { request } from '@/api/core/request'
import type { ChatMessage, ModelInfo } from './chat'

export interface ConversationSession {
  id: string
  userId?: string
  tenantId?: string
  name?: string
  model?: string
  provider?: string
  parentSessionId?: string | null
  forkedAtMessageIndex?: number | null
  messages: ChatMessage[]
  messageCount: number
  createdAt: number
  updatedAt: number
  lastActiveAt: number
  archived: boolean
}

/** 后端 SSE 帧：event 为 message / error，data 为 ChatCompletionResponse。 */
export interface StreamFrame {
  event: string
  data: string
}

export interface ContextUsageBreakdown {
  systemTokens: number
  conversationTokens: number
  toolTokens: number
  cacheReadTokens: number
  totalTokens: number
  contextWindow: number
  remainingTokens: number
  usagePercent: number
  estimatedCost: number
  maxOutputTokens: number
}

export function getSessions() {
  return request<ConversationSession[]>({
    url: '/api/v1/ai/sessions',
    method: 'get'
  })
}

export function getSession(sessionId: string) {
  return request<ConversationSession>({
    url: `/api/v1/ai/sessions/${encodeURIComponent(sessionId)}`,
    method: 'get'
  })
}

export function createSession(sessionName?: string) {
  return request<ConversationSession>({
    url: '/api/v1/ai/sessions',
    method: 'post',
    data: { sessionName }
  })
}

export function deleteSession(sessionId: string) {
  return request<void>({
    url: `/api/v1/ai/sessions/${encodeURIComponent(sessionId)}`,
    method: 'delete'
  })
}

/**
 * 在指定会话中发送消息（非流式）。后端会把用户消息与模型回复落库。
 */
export function chatInSession(sessionId: string, data: ChatInSessionRequest) {
  return request<ChatInSessionResult>({
    url: `/api/v1/ai/sessions/${encodeURIComponent(sessionId)}/chat`,
    method: 'post',
    data
  })
}

export interface ChatInSessionRequest {
  model: string
  provider?: string
  messages: ChatMessage[]
  temperature?: number
  maxTokens?: number
}

export interface ChatInSessionResult {
  choices: Choice[]
  usage?: Usage
}

export interface Choice {
  message: ChatMessage
  finishReason?: string
}

export interface Usage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

/**
 * 继续生成：让模型基于会话已有消息再产出一轮回复。
 */
export function continueSession(sessionId: string) {
  return request<ChatInSessionResult>({
    url: `/api/v1/ai/sessions/${encodeURIComponent(sessionId)}/continue`,
    method: 'post'
  })
}

/**
 * 编辑某条用户消息并重提，后端会丢弃该消息之后的旧回复。
 */
export function editAndResubmit(sessionId: string, messageIndex: number, message: ChatMessage) {
  return request<ChatInSessionResult>({
    url: `/api/v1/ai/sessions/${encodeURIComponent(sessionId)}/messages/${messageIndex}`,
    method: 'put',
    data: message
  })
}

export function changeSessionModel(sessionId: string, model: string, provider?: string) {
  return request<ConversationSession>({
    url: `/api/v1/ai/sessions/${encodeURIComponent(sessionId)}/model`,
    method: 'patch',
    data: { model, provider }
  })
}

export function getContextUsage(sessionId: string) {
  return request<ContextUsageBreakdown>({
    url: `/api/v1/ai/sessions/${encodeURIComponent(sessionId)}/context-usage`,
    method: 'get'
  })
}

export function compactSession(
  sessionId: string,
  options?: { targetTokens?: number; keepLastMessages?: number }
) {
  return request<{ compacted: boolean; reason?: string }>({
    url: `/api/v1/ai/sessions/${encodeURIComponent(sessionId)}/compact`,
    method: 'post',
    data: options ?? {}
  })
}

export function forkSession(sessionId: string, options?: { forkPointIndex?: number; name?: string }) {
  return request<ConversationSession>({
    url: `/api/v1/ai/sessions/${encodeURIComponent(sessionId)}/fork`,
    method: 'post',
    data: options ?? {}
  })
}

export function searchSessions(q: string, limit = 20) {
  return request<{ sessionId: string; sessionName: string; messageIndex: number; role: string; snippet: string }[]>({
    url: '/api/v1/ai/sessions/search',
    method: 'get',
    params: { q, limit }
  })
}

/**
 * 下载会话导出文件。后端返回 JSON / Markdown 文本并带 Content-Disposition。
 */
export async function exportSession(
  sessionId: string,
  format: 'json' | 'markdown' = 'markdown'
): Promise<void> {
  const token = localStorage.getItem(import.meta.env.VITE_APP_TOKEN_NAME || 'X-Access-Token')
  const res = await fetch(`/api/v1/ai/sessions/${encodeURIComponent(sessionId)}/export?format=${format}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!res.ok) {
    throw new Error(`export failed: ${res.status}`)
  }

  const blob = await res.blob()
  const disposition = res.headers.get('Content-Disposition') ?? ''
  const matched = /filename="?([^";]+)"?/i.exec(disposition)
  const fileName = matched?.[1] ?? `session-${sessionId}.${format === 'json' ? 'json' : 'md'}`

  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

export type { ChatMessage, ModelInfo }
