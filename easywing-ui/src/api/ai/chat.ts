import { request } from '@/api/core/request'

export interface ChatMessage {
  id?: string
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string
  name?: string
  /** 思考过程（推理模型），后端 ChatMessage#thinking。 */
  thinking?: string
  toolCallId?: string
  toolCalls?: ToolCall[]
}

export interface ToolCall {
  id: string
  type: string
  function: {
    name: string
    arguments: string
  }
}

export interface ModelInfo {
  id: string
  provider: string
  name: string
  version: string
  description: string
  contextWindow: number
  maxOutputTokens: number
  supportsStreaming: boolean
  supportsTools: boolean
  supportsVision: boolean
  supportsThinking: boolean
  inputCostPer1MTokens: number
  outputCostPer1MTokens: number
  pricingUnit: string
}

export interface ChatCompletionRequest {
  model: string
  /** 指定 Provider；为空时由后端按模型路由。 */
  provider?: string
  messages: ChatMessage[]
  temperature?: number
  maxTokens?: number
  stream?: boolean
  systemPrompt?: string
  userId?: string
  tenantId?: string
}

export interface ChatCompletionResponse {
  choices: Choice[]
  usage?: {
    promptTokens: number
    completionTokens: number
    totalTokens: number
  }
}

export interface Choice {
  message: ChatMessage
  finishReason?: string
}

export function getModelList() {
  return request<ModelInfo[]>({
    url: '/api/v1/ai/models',
    method: 'get'
  })
}

export function getProviders() {
  return request<string[]>({
    url: '/api/v1/ai/providers',
    method: 'get'
  })
}

export function getModel(model: string) {
  return request<ModelInfo>({
    url: `/api/v1/ai/models/${model}`,
    method: 'get'
  })
}

export function chatCompletions(data: ChatCompletionRequest) {
  return request<ChatCompletionResponse>({
    url: '/api/v1/ai/chat/completions',
    method: 'post',
    data
  })
}

export function createMessage(data: {
  model: string
  messages: ChatMessage[]
  temperature?: number
  maxTokens?: number
}) {
  return request<ChatCompletionResponse>({
    url: '/api/v1/ai/messages',
    method: 'post',
    data
  })
}

export function checkHealth() {
  return request<{ status: string }>({
    url: '/api/v1/ai/health',
    method: 'get'
  })
}
