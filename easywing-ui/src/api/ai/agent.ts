import request from '@/api/core/request'
import type { ChatMessage, ModelInfo } from './chat'

export interface AgentConfig {
  maxIterations: number
  maxToolCalls: number
  timeoutMs: number
  streamingEnabled: boolean
  model: string
  temperature: number
}

export interface AgentRequest {
  agentId?: string
  userId?: string
  sessionId?: string
  prompt?: string
  messages?: ChatMessage[]
  context?: Record<string, any>
  config?: AgentConfig
  streaming?: boolean
}

export interface AgentResponse {
  id: string
  agentId: string
  sessionId: string
  message?: ChatMessage
  messages?: ChatMessage[]
  completed: boolean
  finishReason: string
  metadata?: Record<string, any>
}

export interface ToolDefinition {
  name: string
  description: string
  category?: string
}

export interface ToolExecuteRequest {
  toolName: string
  arguments: Record<string, any>
}

export interface ToolExecuteResponse {
  success: boolean
  result?: any
  error?: string
}

export interface AgentSkill {
  id: string
  name: string
  description: string
  category: string
  enabled: boolean
  priority: number
  toolNames?: string[]
}

export interface ConversationContext {
  conversationId: string
  userId?: string
  sessionId?: string
  messages: ChatMessage[]
  toolResults?: ToolResult[]
  variables?: Record<string, any>
  model?: string
  temperature?: number
  maxTokens?: number
  createdAt?: number
  updatedAt?: number
  expiresAt?: number
  messageCount?: number
  totalTokens?: number
  summary?: string
  archived?: boolean
  metadata?: Record<string, string>
}

export interface ToolResult {
  toolCallId: string
  toolName: string
  result: string
  success: boolean
  executionTimeMs: number
  error?: string
}

export interface ConversationSession {
  id: string
  userId: string
  name: string
  model?: string
  messageCount: number
  createdAt: number
  updatedAt: number
  lastActiveAt: number
  archived: boolean
}

export interface AgentConfigResponse {
  availableModels: ModelInfo[]
  availableTools: ToolDefinition[]
  availableSkills: AgentSkill[]
}

export function executeAgent(data: AgentRequest) {
  return request<AgentResponse>({
    url: '/api/v1/ai/agent/execute',
    method: 'post',
    data
  })
}

export function getAgentConfig() {
  return request<AgentConfigResponse>({
    url: '/api/v1/ai/agent/config',
    method: 'get'
  })
}

export function getSkills(category?: string) {
  return request<AgentSkill[]>({
    url: '/api/v1/ai/skills',
    method: 'get',
    params: category ? { category } : undefined
  })
}

export function registerSkill(data: AgentSkill) {
  return request<AgentSkill>({
    url: '/api/v1/ai/skills',
    method: 'post',
    data
  })
}

export function unregisterSkill(name: string) {
  return request({
    url: `/api/v1/ai/skills/${encodeURIComponent(name)}`,
    method: 'delete'
  })
}

export function getTools() {
  return request<ToolDefinition[]>({
    url: '/api/v1/ai/tools',
    method: 'get'
  })
}

export function executeTool(data: ToolExecuteRequest) {
  return request<ToolExecuteResponse>({
    url: '/api/v1/ai/tools/execute',
    method: 'post',
    data
  })
}

export function getMemory(conversationId: string) {
  return request<ConversationContext>({
    url: `/api/v1/ai/memory/${encodeURIComponent(conversationId)}`,
    method: 'get'
  })
}

export function saveMemory(conversationId: string, data: ConversationContext) {
  return request<ConversationContext>({
    url: `/api/v1/ai/memory/${encodeURIComponent(conversationId)}`,
    method: 'post',
    data
  })
}

export function deleteMemory(conversationId: string) {
  return request({
    url: `/api/v1/ai/memory/${encodeURIComponent(conversationId)}`,
    method: 'delete'
  })
}

export function addMemoryMessage(conversationId: string, message: ChatMessage) {
  return request<ConversationContext>({
    url: `/api/v1/ai/memory/${encodeURIComponent(conversationId)}/messages`,
    method: 'post',
    data: message
  })
}

export function getSessions(userId: string) {
  return request<ConversationSession[]>({
    url: '/api/v1/ai/sessions',
    method: 'get',
    params: { userId }
  })
}

export function createSession(userId: string, sessionName: string) {
  return request<ConversationSession>({
    url: '/api/v1/ai/sessions',
    method: 'post',
    data: { userId, sessionName }
  })
}

export function getSession(sessionId: string) {
  return request<ConversationSession>({
    url: `/api/v1/ai/sessions/${encodeURIComponent(sessionId)}`,
    method: 'get'
  })
}

export function deleteSession(sessionId: string) {
  return request({
    url: `/api/v1/ai/sessions/${encodeURIComponent(sessionId)}`,
    method: 'delete'
  })
}