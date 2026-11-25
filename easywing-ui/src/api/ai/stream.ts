import type { ChatCompletionRequest } from './chat'

const BASE_API = import.meta.env.VITE_APP_BASE_API || '/api'
const TOKEN_KEY = import.meta.env.VITE_APP_TOKEN_NAME || 'X-Access-Token'

export interface StreamDelta {
  /** 本次帧追加的文本片段（可能为空，例如仅 finishReason 变化）。 */
  content: string
  /** 思考过程片段（reasoning / thinking），无则为空串。 */
  thinking: string
  finishReason?: string
}

export interface StreamHandlers {
  onDelta: (delta: StreamDelta) => void
  onFinish: (finishReason: string | undefined) => void
  onError: (error: Error) => void
}

/**
 * 以 POST 方式消费 `/chat/completions/stream` 的 SSE 流。
 *
 * 使用 fetch + ReadableStream 而非 axios：axios 在浏览器端不支持流式响应体。
 * 返回的 abort 函数用于中途停止生成。
 */
export async function streamChatCompletions(
  request: ChatCompletionRequest,
  handlers: StreamHandlers
): Promise<() => void> {
  const controller = new AbortController()

  const token = localStorage.getItem(TOKEN_KEY)
  const response = await fetch(`${BASE_API}/v1/ai/chat/completions/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=utf-8',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ ...request, stream: true }),
    signal: controller.signal
  })

  if (!response.ok || !response.body) {
    throw new Error(`stream request failed: ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let finished = false

  const finish = () => {
    if (finished) {
      return
    }
    finished = true
    handlers.onFinish(undefined)
  }

  const consume = async () => {
    try {
      while (!finished) {
        const { done, value } = await reader.read()
        if (done) {
          finish()
          return
        }

        buffer += decoder.decode(value, { stream: true })
        // SSE 以空行分隔事件块
        let boundary = buffer.indexOf('\n\n')
        while (boundary !== -1) {
          const rawEvent = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          handleEvent(rawEvent, handlers)
          boundary = buffer.indexOf('\n\n')
        }
      }
    } catch (error) {
      if ((error as Error)?.name === 'AbortError') {
        finish()
        return
      }
      handlers.onError(error instanceof Error ? error : new Error(String(error)))
    }
  }

  void consume()

  return () => {
    finished = true
    try {
      controller.abort()
    } catch {
      // 忽略 abort 竞态
    }
  }
}

function handleEvent(rawEvent: string, handlers: StreamHandlers): void {
  let eventName = 'message'
  const dataLines: string[] = []

  for (const line of rawEvent.split('\n')) {
    if (line.startsWith(':')) {
      // 注释行（心跳）
      continue
    }
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }

  if (eventName === 'error') {
    handlers.onError(new Error(dataLines.join('\n') || 'stream error'))
    return
  }

  if (dataLines.length === 0) {
    return
  }

  let payload: any
  try {
    payload = JSON.parse(dataLines.join('\n'))
  } catch {
    return
  }

  const choice = payload?.choices?.[0]
  const message = choice?.delta ?? choice?.message ?? payload?.message
  if (!message) {
    return
  }

  // reasoning 在部分 Provider 中单独成字段（ChatCompletionResponse.Choice#reasoning）
  const reasoning = choice?.reasoning
  const thinking =
    message.thinking ??
    (typeof reasoning === 'string' ? reasoning : reasoning?.thinking) ??
    ''

  const content = message.content ?? ''
  const finishReason = choice?.finishReason

  if (content || thinking || finishReason) {
    handlers.onDelta({ content, thinking, finishReason })
  }

  if (finishReason) {
    handlers.onFinish(finishReason)
  }
}
