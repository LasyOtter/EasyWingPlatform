<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Plus, Delete, Refresh, Setting } from '@element-plus/icons-vue'
import {
  getModelList,
  type ChatCompletionRequest,
  type ChatMessage,
  type ModelInfo
} from '@/api/ai/chat'
import {
  changeSessionModel,
  chatInSession,
  compactSession,
  createSession,
  deleteSession,
  editAndResubmit,
  getContextUsage,
  getSession,
  getSessions,
  exportSession,
  forkSession,
  continueSession,
  type ConversationSession,
  type ContextUsageBreakdown
} from '@/api/ai/session'
import { streamChatCompletions } from '@/api/ai/stream'
import { extractFirstLine } from '@/utils/markdown'
import ChatMessageBubble, { type ChatMessageItem } from './components/ChatMessage.vue'

const { t } = useI18n()

// ========== 基础状态 ==========

const loadingModels = ref(false)
const models = ref<ModelInfo[]>([])
const selectedModelId = ref('')
const provider = ref('')

const sessions = ref<ConversationSession[]>([])
const currentSessionId = ref('')
const currentSession = ref<ConversationSession | null>(null)

const messages = ref<ChatMessageItem[]>([])
const inputMessage = ref('')
const sending = ref(false)
const chatContainer = ref<HTMLElement>()

const systemPrompt = ref('')
const temperature = ref(0.7)
const maxTokens = ref(2048)
const contextUsage = ref<ContextUsageBreakdown | null>(null)

const showSettings = ref(false)
const actionBusy = ref(false)
let abortStream: (() => void) | null = null

// ========== 计算属性 ==========

/** 模型下拉项：优先展示用户可读名称，附带服务商与上下文窗口。 */
const modelOptions = computed(() =>
  models.value.map((model) => ({
    id: model.id,
    label: model.name || model.id,
    provider: model.provider,
    contextWindow: model.contextWindow
  }))
)

const hasMessages = computed(() => messages.value.length > 0)

const usagePercentText = computed(() => {
  const usage = contextUsage.value
  if (!usage) {
    return ''
  }
  return `${t('chat.usagePercent', { percent: usage.usagePercent.toFixed(1) })}`
})

// ========== 生命周期 ==========

onMounted(async () => {
  await Promise.all([loadModels(), loadSessions()])
})

onBeforeUnmount(() => {
  stopStreaming()
})

// ========== 数据加载 ==========

const loadModels = async () => {
  try {
    loadingModels.value = true
    const res = await getModelList()
    models.value = res || []
    if (models.value.length && !selectedModelId.value) {
      selectedModelId.value = models.value[0].id
    }
  } catch (error) {
    console.error(t('chat.loadFailed'), error)
  } finally {
    loadingModels.value = false
  }
}

const loadSessions = async () => {
  try {
    const res = await getSessions()
    sessions.value = res || []
  } catch (error) {
    console.error(t('chat.loadFailed'), error)
  }
}

const loadSession = async (sessionId: string) => {
  try {
    const res = await getSession(sessionId)
    currentSessionId.value = sessionId
    currentSession.value = res
    selectedModelId.value = res.model ?? selectedModelId.value
    provider.value = res.provider ?? ''
    messages.value = (res.messages || []).map((message, index) => toMessageItem(message, index))
    void refreshContextUsage()
    scrollToBottom()
  } catch (error) {
    console.error(t('chat.loadFailed'), error)
  }
}

const toMessageItem = (message: ChatMessage, index: number): ChatMessageItem => ({
  id: message.id || `msg-${index}-${Date.now()}`,
  role: message.role as ChatMessageItem['role'],
  content: message.content || '',
  thinking: message.thinking ?? '',
  thinkingExpanded: false,
  timeText: formatTimestamp(Date.now())
})

// ========== 发送 / 流式 ==========

const buildRequestMessages = (): ChatMessage[] => {
  const payload: ChatMessage[] = []
  if (systemPrompt.value.trim()) {
    payload.push({ role: 'system', content: systemPrompt.value.trim() })
  }
  for (const item of messages.value) {
    if (item.role !== 'system') {
      payload.push({ role: item.role, content: item.content })
    }
  }
  return payload
}

/**
 * 发送消息：优先在已持久化会话中走流式接口；没有会话时先创建会话再流式续聊。
 * 后端 `/chat/completions/stream` 本身不写库，因此发送后需要同步会话消息列表。
 */
const sendMessage = async () => {
  const text = inputMessage.value.trim()
  if (!text) {
    ElMessage.warning(t('chat.messageRequired'))
    return
  }
  if (sending.value) {
    return
  }
  if (!selectedModelId.value) {
    ElMessage.warning(t('chat.modelRequired'))
    return
  }

  inputMessage.value = ''

  const userItem: ChatMessageItem = {
    id: `user-${Date.now()}`,
    role: 'user',
    content: text,
    timeText: formatTimestamp(Date.now())
  }
  messages.value.push(userItem)
  scrollToBottom()

  // 没有会话则先建一个，并把首条消息落库
  if (!currentSessionId.value) {
    try {
      const created = await createSession(extractFirstLine(text))
      currentSessionId.value = created.id
      currentSession.value = created
      await loadSessions()
    } catch (error) {
      console.error(t('chat.sessionCreated'), error)
    }
  }

  sending.value = true
  const assistantItem: ChatMessageItem = {
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    content: '',
    thinking: '',
    timeText: formatTimestamp(Date.now())
  }
  messages.value.push(assistantItem)
  scrollToBottom()

  const request: ChatCompletionRequest = {
    model: selectedModelId.value,
    provider: provider.value || undefined,
    messages: buildRequestMessages(),
    temperature: temperature.value,
    maxTokens: maxTokens.value,
    stream: true
  }

  try {
    await runStream(request, assistantItem)
  } finally {
    sending.value = false
    await afterSendPersist()
  }
}

const runStream = (
  request: ChatCompletionRequest,
  target: ChatMessageItem
): Promise<void> => {
  return new Promise((resolve) => {
    let settled = false
    const finish = () => {
      if (settled) {
        return
      }
      settled = true
      abortStream = null
      resolve()
    }

    void streamChatCompletions(request, {
      onDelta: (delta) => {
        target.content += delta.content
        if (delta.thinking) {
          target.thinking += delta.thinking
        }
        scrollToBottom()
      },
      onFinish: finish,
      onError: (error) => {
        ElMessage.error(error.message || t('chat.sendFailed'))
        finish()
      }
    }).then((abort) => {
      abortStream = abort
    })
  })
}

const stopStreaming = () => {
  abortStream?.()
  abortStream = null
  sending.value = false
}

/**
 * 把当前消息列表回写到会话，保证刷新页面后仍能恢复。
 * 失败不阻断对话，仅提示。
 */
const afterSendPersist = async () => {
  if (!currentSessionId.value) {
    return
  }
  const payload: ChatMessage[] = messages.value
    .filter((item) => item.content.trim())
    .map((item) => ({ role: item.role, content: item.content }))

  if (payload.length === 0) {
    return
  }

  try {
    await chatInSession(currentSessionId.value, {
      model: selectedModelId.value,
      provider: provider.value || undefined,
      messages: payload
    })
    await Promise.all([refreshSession(), refreshContextUsage(), loadSessions()])
  } catch (error) {
    console.error(t('chat.sendFailed'), error)
  }
}

const refreshSession = async () => {
  if (!currentSessionId.value) {
    return
  }
  try {
    const res = await getSession(currentSessionId.value)
    currentSession.value = res
    selectedModelId.value = res.model ?? selectedModelId.value
  } catch {
    // 忽略刷新失败
  }
}

const refreshContextUsage = async () => {
  if (!currentSessionId.value) {
    contextUsage.value = null
    return
  }
  try {
    contextUsage.value = await getContextUsage(currentSessionId.value)
  } catch {
    contextUsage.value = null
  }
}

// ========== 会话操作 ==========

const newConversation = async () => {
  if (sending.value) {
    stopStreaming()
  }
  currentSessionId.value = ''
  currentSession.value = null
  messages.value = []
  contextUsage.value = null
  inputMessage.value = ''
  ElMessage.success(t('chat.sessionCreated'))
}

const removeSession = async (session: ConversationSession) => {
  try {
    await ElMessageBox.confirm(t('chat.deleteSessionConfirm'), t('common.tip'), {
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await deleteSession(session.id)
    if (currentSessionId.value === session.id) {
      currentSessionId.value = ''
      currentSession.value = null
      messages.value = []
      contextUsage.value = null
    }
    await loadSessions()
    ElMessage.success(t('chat.deleted'))
  } catch (error) {
    console.error(t('chat.deleted'), error)
  }
}

const switchModel = async (modelId: string) => {
  selectedModelId.value = modelId
  const found = models.value.find((m) => m.id === modelId)
  provider.value = found?.provider ?? ''
  if (!currentSessionId.value) {
    return
  }
  try {
    await changeSessionModel(currentSessionId.value, modelId, provider.value || undefined)
    await refreshSession()
  } catch (error) {
    console.error(t('chat.model'), error)
  }
}

const clearHistory = async () => {
  try {
    await ElMessageBox.confirm(t('chat.clearConfirm'), t('common.tip'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
  } catch {
    return
  }

  stopStreaming()
  messages.value = []
  contextUsage.value = null
  ElMessage.success(t('chat.cleared'))
}

const retryLast = async () => {
  if (sending.value || messages.value.length === 0) {
    return
  }
  // 去掉末尾的助手回复，基于最近一条用户消息重新生成
  const lastUserIndex = findLastUserIndex()
  if (lastUserIndex < 0) {
    return
  }
  messages.value = messages.value.slice(0, lastUserIndex + 1)
  inputMessage.value = ''
  sending.value = true

  const assistantItem: ChatMessageItem = {
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    content: '',
    thinking: '',
    timeText: formatTimestamp(Date.now())
  }
  messages.value.push(assistantItem)
  scrollToBottom()

  try {
    await runStream(buildStreamRequest(), assistantItem)
  } finally {
    sending.value = false
    await afterSendPersist()
  }
}

const continueGeneration = async () => {
  if (sending.value || !currentSessionId.value) {
    return
  }
  sending.value = true
  try {
    await continueSession(currentSessionId.value)
    await loadSession(currentSessionId.value)
  } catch (error) {
    console.error(t('chat.sendFailed'), error)
  } finally {
    sending.value = false
  }
}

const editMessage = async (item: ChatMessageItem) => {
  if (!currentSessionId.value || sending.value) {
    return
  }
  // 后端按会话消息数组的下标定位；system 消息不在本地列表中，需要换算。
  const session = currentSession.value?.messages ?? []
  const sessionIndex = session.findIndex(
    (message) =>
      message.role === item.role &&
      message.content === item.content
  )
  if (sessionIndex < 0) {
    ElMessage.warning(t('chat.messageNotFound'))
    return
  }

  try {
    await editAndResubmit(currentSessionId.value, sessionIndex, {
      role: 'user',
      content: item.content
    })
    await loadSession(currentSessionId.value)
  } catch (error) {
    console.error(t('chat.editMessage'), error)
  }
}

const compactContext = async () => {
  if (!currentSessionId.value || actionBusy.value) {
    return
  }
  actionBusy.value = true
  try {
    const res = await compactSession(currentSessionId.value)
    if (res.compacted) {
      ElMessage.success(t('chat.compacted'))
      await loadSession(currentSessionId.value)
    } else {
      ElMessage.info(res.reason ?? t('chat.compactFailed'))
    }
  } catch (error) {
    console.error(t('chat.compactFailed'), error)
    ElMessage.error(t('chat.compactFailed'))
  } finally {
    actionBusy.value = false
  }
}

const handleFork = async () => {
  if (!currentSessionId.value || actionBusy.value) {
    return
  }
  actionBusy.value = true
  try {
    await forkSession(currentSessionId.value, {})
    await loadSessions()
  } catch (error) {
    console.error(t('chat.fork'), error)
  } finally {
    actionBusy.value = false
  }
}

const handleExport = async (format: 'json' | 'markdown') => {
  if (!currentSessionId.value) {
    return
  }
  try {
    await exportSession(currentSessionId.value, format)
  } catch (error) {
    console.error(t('common.export'), error)
    ElMessage.error(t('common.failed'))
  }
}

// ========== 工具函数 ==========

const findLastUserIndex = (): number => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].role === 'user') {
      return i
    }
  }
  return -1
}

const buildStreamRequest = (): ChatCompletionRequest => ({
  model: selectedModelId.value,
  provider: provider.value || undefined,
  messages: buildRequestMessages(),
  temperature: temperature.value,
  maxTokens: maxTokens.value,
  stream: true
})

/** 展开/收起指定消息的思考过程。 */
const toggleThinking = (messageId: string, expanded: boolean) => {
  const target = messages.value.find((item) => item.id === messageId)
  if (target) {
    target.thinkingExpanded = expanded
  }
}

const scrollToBottom = () => {
  void nextTick(() => {
    const container = chatContainer.value
    if (container) {
      container.scrollTop = container.scrollHeight
    }
  })
}

const formatTimestamp = (timestamp: number): string =>
  new Date(timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const formatSessionTime = (timestamp?: number): string => {
  if (!timestamp) {
    return ''
  }
  return new Date(timestamp).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<template>
  <div class="ai-chat">
    <!-- 会话侧栏 -->
    <aside class="session-panel">
      <div class="panel-header">
        <span class="panel-title">{{ t('chat.conversations') }}</span>
        <el-button
          type="primary"
          :icon="Plus"
          size="small"
          @click="newConversation"
        >
          {{ t('chat.newConversation') }}
        </el-button>
      </div>

      <div class="session-list">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: session.id === currentSessionId }"
          @click="loadSession(session.id)"
        >
          <div class="session-meta">
            <span class="session-name">
              {{ session.name || t('chat.title') }}
            </span>
            <span class="session-time">{{ formatSessionTime(session.updatedAt) }}</span>
          </div>
          <div class="session-sub">
            <span>{{ t('chat.messageCount', { count: session.messageCount }) }}</span>
            <el-icon
              class="session-delete"
              @click.stop="removeSession(session)"
            >
              <Delete />
            </el-icon>
          </div>
        </div>

        <el-empty
          v-if="sessions.length === 0"
          :description="t('common.empty')"
          :image-size="60"
        />
      </div>
    </aside>

    <!-- 对话主区 -->
    <section class="chat-main">
      <header class="chat-toolbar">
        <div class="toolbar-left">
          <span class="toolbar-title">
            <el-icon><ChatDotRound /></el-icon>
            {{ currentSession?.name || t('chat.title') }}
          </span>

          <el-select
            :model-value="selectedModelId"
            :placeholder="t('chat.selectModel')"
            :loading="loadingModels"
            style="width: 240px"
            @change="switchModel"
          >
            <el-option
              v-for="option in modelOptions"
              :key="option.id"
              :label="`${option.label} · ${option.provider}`"
              :value="option.id"
            />
          </el-select>
        </div>

        <div class="toolbar-right">
          <el-tooltip :content="t('chat.contextUsage')">
            <el-tag
              v-if="contextUsage"
              size="small"
              type="info"
            >
              {{ usagePercentText }}
            </el-tag>
          </el-tooltip>

          <el-dropdown trigger="click">
            <el-button
              link
              :icon="Setting"
            >
              {{ t('common.more') }}
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="continueGeneration">
                  {{ t('chat.continueGenerate') }}
                </el-dropdown-item>
                <el-dropdown-item @click="compactContext">
                  {{ t('chat.compact') }}
                </el-dropdown-item>
                <el-dropdown-item @click="handleFork">
                  {{ t('chat.fork') }}
                </el-dropdown-item>
                <el-dropdown-item divided>
                  <el-dropdown placement="right-start">
                    <span>{{ t('common.export') }}</span>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item @click="handleExport('json')">
                          {{ t('chat.exportJson') }}
                        </el-dropdown-item>
                        <el-dropdown-item @click="handleExport('markdown')">
                          {{ t('chat.exportMarkdown') }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </el-dropdown-item>
                <el-dropdown-item divided @click="clearHistory">
                  <span class="danger-text">{{ t('chat.clear') }}</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-button
            link
            :icon="Refresh"
            @click="loadModels"
          >
            {{ t('chat.refreshModels') }}
          </el-button>
        </div>
      </header>

      <!-- 消息流 -->
      <div
        ref="chatContainer"
        class="chat-container"
      >
        <div
          v-if="!hasMessages"
          class="empty-tip"
        >
          <el-icon size="52">
            <ChatDotRound />
          </el-icon>
          <p class="empty-title">{{ t('chat.emptyTitle') }}</p>
          <p class="empty-subtitle">{{ t('chat.emptySubtitle') }}</p>
        </div>

        <ChatMessageBubble
          v-for="item in messages"
          :key="item.id"
          :message="item"
          :streaming="sending && item.id === messages[messages.length - 1]?.id"
          @edit="editMessage"
          @retry="retryLast"
          @stop="stopStreaming"
          @toggle-thinking="(expanded: boolean) => toggleThinking(item.id, expanded)"
        />
      </div>

      <!-- 输入区 -->
      <div class="chat-input">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="3"
          resize="none"
          :placeholder="t('chat.inputPlaceholder')"
          :disabled="sending"
          @keydown.ctrl.enter.prevent="sendMessage"
        />
        <div class="input-actions">
          <span class="tip">Ctrl+Enter</span>
          <el-button
            type="primary"
            :loading="sending"
            :disabled="!selectedModelId"
            @click="sendMessage"
          >
            {{ sending ? t('chat.sending') : t('chat.send') }}
          </el-button>
        </div>
      </div>

      <!-- 参数抽屉 -->
      <el-drawer
        v-model="showSettings"
        :title="t('agent.config')"
        size="320px"
      >
        <el-form label-position="top">
          <el-form-item :label="t('chat.temperature')">
            <el-slider
              v-model="temperature"
              :min="0"
              :max="2"
              :step="0.1"
              show-input
            />
          </el-form-item>
          <el-form-item :label="t('chat.maxTokens')">
            <el-input-number
              v-model="maxTokens"
              :min="100"
              :max="32000"
              :step="100"
            />
          </el-form-item>
          <el-form-item :label="t('chat.systemPrompt')">
            <el-input
              v-model="systemPrompt"
              type="textarea"
              :rows="4"
              :placeholder="t('chat.systemPromptPlaceholder')"
            />
          </el-form-item>
        </el-form>
      </el-drawer>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.ai-chat {
  display: flex;
  height: calc(100vh - $header-height - $tags-view-height - 32px);
  gap: 16px;

  .session-panel {
    width: 260px;
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    padding: 12px;
    border-radius: 8px;
    background: var(--el-bg-color);

    .panel-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;

      .panel-title {
        font-size: 14px;
        font-weight: 600;
        color: var(--el-text-color-primary);
      }
    }

    .session-list {
      flex: 1;
      overflow-y: auto;

      .session-item {
        padding: 10px 12px;
        margin-bottom: 6px;
        border-radius: 6px;
        cursor: pointer;
        transition: background 0.15s ease;

        &:hover {
          background: var(--el-fill-color-light);
        }

        &.active {
          background: var(--el-color-primary-light-9);
        }

        .session-meta {
          display: flex;
          justify-content: space-between;
          align-items: baseline;
          gap: 8px;

          .session-name {
            font-size: 13px;
            color: var(--el-text-color-primary);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .session-time {
            flex-shrink: 0;
            font-size: 11px;
            color: var(--el-text-color-secondary);
          }
        }

        .session-sub {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-top: 4px;
          font-size: 11px;
          color: var(--el-text-color-secondary);

          .session-delete {
            opacity: 0;

            &:hover {
              color: var(--el-color-danger);
            }
          }
        }

        &:hover .session-delete {
          opacity: 1;
        }
      }
    }
  }

  .chat-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    padding: 12px;
    border-radius: 8px;
    background: var(--el-bg-color);

    .chat-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-bottom: 12px;
      margin-bottom: 12px;
      border-bottom: 1px solid var(--el-border-color-lighter);

      .toolbar-left,
      .toolbar-right {
        display: flex;
        align-items: center;
        gap: 12px;
      }

      .toolbar-title {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-size: 15px;
        font-weight: 600;
        max-width: 240px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .chat-container {
      flex: 1;
      overflow-y: auto;
      padding: 8px 4px;
      margin-bottom: 12px;

      .empty-tip {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        height: 100%;
        color: var(--el-text-color-secondary);

        .empty-title {
          margin: 12px 0 4px;
          font-size: 15px;
        }

        .empty-subtitle {
          margin: 0;
          font-size: 12px;
        }
      }
    }

    .chat-input {
      border-top: 1px solid var(--el-border-color-lighter);
      padding-top: 12px;

      .input-actions {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-top: 10px;

        .tip {
          font-size: 12px;
          color: var(--el-text-color-secondary);
        }
      }
    }
  }

  .danger-text {
    color: var(--el-color-danger);
  }
}
</style>
