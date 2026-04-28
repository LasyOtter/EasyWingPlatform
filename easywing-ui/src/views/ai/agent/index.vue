<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Plus,
  Delete,
  Refresh,
  MagicStick,
  Tool,
  Collection,
  Memory,
  ArrowRight,
  Loading
} from '@element-plus/icons-vue'
import {
  getAgentConfig,
  executeAgent,
  getSessions,
  createSession,
  deleteSession,
  getMemory,
  saveMemory,
  addMemoryMessage,
  type ModelInfo,
  type ToolDefinition,
  type AgentSkill,
  type ConversationSession,
  type ConversationContext,
  type ChatMessage
} from '@/api/ai/agent'

const loading = ref(false)
const sending = ref(false)
const configLoading = ref(false)
const sessionsLoading = ref(false)
const memoryLoading = ref(false)

const agentConfig = ref<{
  availableModels: ModelInfo[]
  availableTools: ToolDefinition[]
  availableSkills: AgentSkill[]
}>({
  availableModels: [],
  availableTools: [],
  availableSkills: []
})

const sessions = ref<ConversationSession[]>([])
const currentSessionId = ref<string>('')
const currentConversation = ref<ConversationContext | null>(null)

interface MessageItem {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp: Date
  toolResults?: any[]
}

const messages = ref<MessageItem[]>([])
const inputMessage = ref('')
const chatContainer = ref<HTMLElement>()

const agentSettings = reactive({
  model: '',
  temperature: 0.7,
  maxIterations: 10,
  maxToolCalls: 5,
  systemPrompt: 'You are a helpful AI agent with access to various tools and skills.',
  enabledTools: [] as string[],
  enabledSkills: [] as string[],
  memoryEnabled: true
})

const activeTab = ref('config')
const showSessionDialog = ref(false)
const newSessionName = ref('')

const filteredTools = computed(() => {
  if (!agentSettings.enabledTools.length) return agentConfig.value.availableTools
  return agentConfig.value.availableTools.filter(t => agentSettings.enabledTools.includes(t.name))
})

const filteredSkills = computed(() => {
  if (!agentSettings.enabledSkills.length) return agentConfig.value.availableSkills
  return agentConfig.value.availableSkills.filter(s => agentSettings.enabledSkills.includes(s.name))
})

const getAgentConfigData = async () => {
  try {
    configLoading.value = true
    const res = await getAgentConfig()
    agentConfig.value = res
    if (res.availableModels.length > 0 && !agentSettings.model) {
      agentSettings.model = res.availableModels[0].id
    }
  } catch (error) {
    console.error('获取Agent配置失败:', error)
  } finally {
    configLoading.value = false
  }
}

const getSessionsData = async () => {
  try {
    sessionsLoading.value = true
    const res = await getSessions('default')
    sessions.value = res || []
  } catch (error) {
    console.error('获取会话列表失败:', error)
  } finally {
    sessionsLoading.value = false
  }
}

const loadConversation = async (sessionId: string) => {
  try {
    memoryLoading.value = true
    currentSessionId.value = sessionId
    const res = await getMemory(sessionId)
    currentConversation.value = res
    messages.value = (res.messages || []).map((m: ChatMessage) => ({
      id: Date.now().toString() + Math.random(),
      role: m.role as 'user' | 'assistant' | 'system',
      content: m.content || '',
      timestamp: new Date()
    }))
    scrollToBottom()
  } catch (error) {
    console.error('加载会话失败:', error)
  } finally {
    memoryLoading.value = false
  }
}

const handleCreateSession = async () => {
  if (!newSessionName.value.trim()) {
    ElMessage.warning('请输入会话名称')
    return
  }
  try {
    const res = await createSession('default', newSessionName.value)
    sessions.value.unshift(res)
    currentSessionId.value = res.id
    currentConversation.value = null
    messages.value = []
    showSessionDialog.value = false
    newSessionName.value = ''
    ElMessage.success('会话创建成功')
  } catch (error) {
    console.error('创建会话失败:', error)
    ElMessage.error('创建会话失败')
  }
}

const handleDeleteSession = async (session: ConversationSession) => {
  try {
    await ElMessageBox.confirm(`确定要删除会话"${session.name}"吗？`, '提示', { type: 'warning' })
    await deleteSession(session.id)
    sessions.value = sessions.value.filter(s => s.id !== session.id)
    if (currentSessionId.value === session.id) {
      currentSessionId.value = ''
      messages.value = []
      currentConversation.value = null
    }
    ElMessage.success('删除成功')
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除会话失败:', error)
    }
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

const sendMessage = async () => {
  if (!inputMessage.value.trim()) {
    ElMessage.warning('请输入消息')
    return
  }

  const userMessage: MessageItem = {
    id: Date.now().toString(),
    role: 'user',
    content: inputMessage.value,
    timestamp: new Date()
  }

  messages.value.push(userMessage)
  const userInput = inputMessage.value
  inputMessage.value = ''
  scrollToBottom()

  sending.value = true

  try {
    const chatMessages: ChatMessage[] = []

    if (agentSettings.systemPrompt) {
      chatMessages.push({
        role: 'system',
        content: agentSettings.systemPrompt
      })
    }

    chatMessages.push(
      ...messages.value.map(m => ({
        role: m.role,
        content: m.content
      }))
    )

    const res = await executeAgent({
      model: agentSettings.model,
      sessionId: currentSessionId.value || undefined,
      prompt: userInput,
      messages: chatMessages,
      config: {
        model: agentSettings.model,
        temperature: agentSettings.temperature,
        maxIterations: agentSettings.maxIterations,
        maxToolCalls: agentSettings.maxToolCalls,
        timeoutMs: 60000,
        streamingEnabled: false
      }
    })

    const assistantMessage: MessageItem = {
      id: (Date.now() + 1).toString(),
      role: 'assistant',
      content: res.message?.content || '',
      timestamp: new Date()
    }
    messages.value.push(assistantMessage)
    scrollToBottom()

    if (agentSettings.memoryEnabled && res.sessionId && !currentSessionId.value) {
      currentSessionId.value = res.sessionId
      const session: ConversationSession = {
        id: res.sessionId,
        userId: 'default',
        name: `会话 ${new Date().toLocaleString()}`,
        messageCount: messages.value.length,
        createdAt: Date.now(),
        updatedAt: Date.now(),
        lastActiveAt: Date.now(),
        archived: false
      }
      sessions.value.unshift(session)
    }

    if (currentSessionId.value) {
      await saveCurrentMemory()
    }
  } catch (error: any) {
    ElMessage.error(error.message || '发送消息失败')
  } finally {
    sending.value = false
  }
}

const saveCurrentMemory = async () => {
  if (!currentSessionId.value) return
  try {
    const context: ConversationContext = {
      conversationId: currentSessionId.value,
      userId: 'default',
      sessionId: currentSessionId.value,
      messages: messages.value.map(m => ({
        role: m.role,
        content: m.content
      })),
      model: agentSettings.model,
      temperature: agentSettings.temperature,
      messageCount: messages.value.length,
      createdAt: currentConversation.value?.createdAt || Date.now(),
      updatedAt: Date.now()
    }
    await saveMemory(currentSessionId.value, context)
  } catch (error) {
    console.error('保存记忆失败:', error)
  }
}

const clearHistory = () => {
  messages.value = []
  ElMessage.success('聊天记录已清空')
}

const clearMemory = async () => {
  if (!currentSessionId.value) {
    ElMessage.warning('当前没有活动的会话')
    return
  }
  try {
    await ElMessageBox.confirm('确定要清空当前会话的记忆吗？', '提示', { type: 'warning' })
    await deleteMemory(currentSessionId.value)
    messages.value = []
    currentConversation.value = null
    ElMessage.success('记忆已清空')
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('清空记忆失败:', error)
    }
  }
}

const formatTime = (timestamp: number) => {
  return new Date(timestamp).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatDate = (date: Date) => {
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const handleToolExecute = async (toolName: string) => {
  try {
    const { executeTool } = await import('@/api/ai/agent')
    const res = await executeTool({
      toolName,
      arguments: {}
    })
    if (res.success) {
      ElMessage.success(`工具 ${toolName} 执行成功`)
    } else {
      ElMessage.error(`工具 ${toolName} 执行失败: ${res.error}`)
    }
  } catch (error: any) {
    ElMessage.error(`工具执行失败: ${error.message}`)
  }
}

onMounted(() => {
  getAgentConfigData()
  getSessionsData()
})
</script>

<template>
  <div class="ai-agent">
    <div class="agent-sidebar">
      <div class="sidebar-header">
        <span class="title">会话列表</span>
        <el-button
          type="primary"
          :icon="Plus"
          circle
          size="small"
          @click="showSessionDialog = true"
        />
      </div>
      <div class="session-list">
        <div
          v-if="sessionsLoading"
          class="loading-tip"
        >
          <el-icon class="is-loading">
            <Loading />
          </el-icon>
          加载中...
        </div>
        <div
          v-else-if="sessions.length === 0"
          class="empty-tip"
        >
          暂无会话
        </div>
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: session.id === currentSessionId }"
          @click="loadConversation(session.id)"
        >
          <div class="session-info">
            <span class="session-name">{{ session.name }}</span>
            <span class="session-time">{{ formatTime(session.lastActiveAt) }}</span>
          </div>
          <el-button
            type="danger"
            :icon="Delete"
            circle
            size="small"
            text
            class="delete-btn"
            @click.stop="handleDeleteSession(session)"
          />
        </div>
      </div>
    </div>

    <div class="agent-main">
      <el-card
        shadow="never"
        class="chat-card"
      >
        <template #header>
          <div class="card-header">
            <span class="title">
              <el-icon><MagicStick /></el-icon>
              智能体对话
            </span>
            <div class="actions">
              <el-button
                type="danger"
                link
                :icon="Delete"
                @click="clearHistory"
              >
                清空对话
              </el-button>
              <el-button
                type="primary"
                link
                :icon="Refresh"
                @click="getAgentConfigData"
              >
                刷新配置
              </el-button>
            </div>
          </div>
        </template>

        <div
          ref="chatContainer"
          class="chat-container"
        >
          <div
            v-if="messages.length === 0"
            class="empty-tip"
          >
            <el-icon size="48">
              <ChatDotRound />
            </el-icon>
            <p>开始与智能体对话</p>
            <p class="sub-tip">
              智能体可以调用工具、使用技能、记忆上下文
            </p>
          </div>

          <div
            v-for="msg in messages"
            :key="msg.id"
            class="message-item"
            :class="msg.role"
          >
            <div class="message-avatar">
              <el-avatar
                v-if="msg.role === 'user'"
                :size="32"
                icon="User"
              />
              <el-avatar
                v-else
                :size="32"
                type="primary"
                icon="MagicStick"
              />
            </div>
            <div class="message-content">
              <div class="message-header">
                <span class="sender">{{ msg.role === 'user' ? '我' : '智能体' }}</span>
                <span class="time">{{ formatDate(msg.timestamp) }}</span>
              </div>
              <div class="message-body">
                <pre>{{ msg.content }}</pre>
              </div>
              <div
                v-if="msg.toolResults && msg.toolResults.length"
                class="tool-results"
              >
                <div
                  v-for="(result, idx) in msg.toolResults"
                  :key="idx"
                  class="tool-result"
                >
                  <el-tag
                    size="small"
                    type="info"
                  >
                    {{ result.toolName }}
                  </el-tag>
                  <span>{{ result.result }}</span>
                </div>
              </div>
            </div>
          </div>

          <div
            v-if="sending"
            class="message-item assistant"
          >
            <div class="message-avatar">
              <el-avatar
                :size="32"
                type="primary"
                icon="MagicStick"
              />
            </div>
            <div class="message-content">
              <div class="message-header">
                <span class="sender">智能体</span>
                <span class="time">思考中...</span>
              </div>
              <div class="message-body">
                <el-icon class="loading">
                  <Loading />
                </el-icon>
              </div>
            </div>
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="3"
            placeholder="输入消息，按 Ctrl+Enter 发送"
            resize="none"
            @keydown.ctrl.enter="sendMessage"
          />
          <div class="input-actions">
            <span class="tip">Ctrl+Enter 发送 | 当前模型: {{ agentSettings.model || '未选择' }}</span>
            <el-button
              type="primary"
              :loading="sending"
              @click="sendMessage"
            >
              发送
            </el-button>
          </div>
        </div>
      </el-card>
    </div>

    <div class="agent-config">
      <el-tabs
        v-model="activeTab"
        class="config-tabs"
      >
        <el-tab-pane
          label="配置"
          name="config"
        >
          <div class="config-section">
            <h4><el-icon><ChatDotRound /></el-icon> 模型设置</h4>
            <el-form
              label-width="80px"
              label-position="left"
            >
              <el-form-item label="AI 模型">
                <el-select
                  v-model="agentSettings.model"
                  placeholder="选择模型"
                  style="width: 100%"
                >
                  <el-option
                    v-for="model in agentConfig.availableModels"
                    :key="model.id"
                    :label="model.name"
                    :value="model.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="温度">
                <el-slider
                  v-model="agentSettings.temperature"
                  :min="0"
                  :max="2"
                  :step="0.1"
                  show-tooltip
                />
              </el-form-item>
            </el-form>
          </div>

          <div class="config-section">
            <h4><el-icon><MagicStick /></el-icon> Agent 设置</h4>
            <el-form
              label-width="80px"
              label-position="left"
            >
              <el-form-item label="最大迭代">
                <el-input-number
                  v-model="agentSettings.maxIterations"
                  :min="1"
                  :max="50"
                />
              </el-form-item>
              <el-form-item label="最大工具调用">
                <el-input-number
                  v-model="agentSettings.maxToolCalls"
                  :min="0"
                  :max="20"
                />
              </el-form-item>
              <el-form-item label="系统提示词">
                <el-input
                  v-model="agentSettings.systemPrompt"
                  type="textarea"
                  :rows="3"
                />
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <el-tab-pane
          label="工具"
          name="tools"
        >
          <div class="config-section">
            <h4><el-icon><Tool /></el-icon> 可用工具</h4>
            <el-checkbox-group
              v-model="agentSettings.enabledTools"
              class="tool-list"
            >
              <el-checkbox
                v-for="tool in agentConfig.availableTools"
                :key="tool.name"
                :value="tool.name"
                class="tool-item"
              >
                <div class="tool-info">
                  <span class="tool-name">{{ tool.name }}</span>
                  <span class="tool-desc">{{ tool.description || '无描述' }}</span>
                </div>
                <el-button
                  size="small"
                  type="primary"
                  link
                  @click.stop="handleToolExecute(tool.name)"
                >
                  测试
                </el-button>
              </el-checkbox>
            </el-checkbox-group>
            <div
              v-if="agentConfig.availableTools.length === 0"
              class="empty-tip"
            >
              暂无可用工具
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane
          label="技能"
          name="skills"
        >
          <div class="config-section">
            <h4><el-icon><Collection /></el-icon> 可用技能</h4>
            <el-checkbox-group
              v-model="agentSettings.enabledSkills"
              class="skill-list"
            >
              <el-checkbox
                v-for="skill in agentConfig.availableSkills"
                :key="skill.name"
                :value="skill.name"
                class="skill-item"
              >
                <div class="skill-info">
                  <span class="skill-name">{{ skill.name }}</span>
                  <span class="skill-desc">{{ skill.description || '无描述' }}</span>
                  <el-tag
                    v-if="skill.category"
                    size="small"
                    type="info"
                  >
                    {{ skill.category }}
                  </el-tag>
                </div>
              </el-checkbox>
            </el-checkbox-group>
            <div
              v-if="agentConfig.availableSkills.length === 0"
              class="empty-tip"
            >
              暂无可用技能
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane
          label="记忆"
          name="memory"
        >
          <div class="config-section">
            <h4><el-icon><Memory /></el-icon> 记忆管理</h4>
            <el-form
              label-width="100px"
              label-position="left"
            >
              <el-form-item label="启用记忆">
                <el-switch v-model="agentSettings.memoryEnabled" />
              </el-form-item>
              <el-form-item label="当前会话">
                <span>{{ currentSessionId || '无活动会话' }}</span>
              </el-form-item>
              <el-form-item label="消息数量">
                <span>{{ currentConversation?.messageCount || 0 }}</span>
              </el-form-item>
              <el-form-item label="创建时间">
                <span>{{ currentConversation?.createdAt ? formatTime(currentConversation.createdAt) : '-' }}</span>
              </el-form-item>
            </el-form>
            <el-button
              type="danger"
              :icon="Delete"
              @click="clearMemory"
            >
              清空记忆
            </el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog
      v-model="showSessionDialog"
      title="创建新会话"
      width="400px"
    >
      <el-form>
        <el-form-item label="会话名称">
          <el-input
            v-model="newSessionName"
            placeholder="请输入会话名称"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSessionDialog = false">
          取消
        </el-button>
        <el-button
          type="primary"
          @click="handleCreateSession"
        >
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.ai-agent {
  display: flex;
  height: calc(100vh - 140px);
  gap: 16px;

  .agent-sidebar {
    width: 260px;
    background: #fff;
    border-radius: 8px;
    display: flex;
    flex-direction: column;

    .sidebar-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px;
      border-bottom: 1px solid #eee;

      .title {
        font-size: 14px;
        font-weight: 600;
      }
    }

    .session-list {
      flex: 1;
      overflow-y: auto;
      padding: 8px;

      .loading-tip,
      .empty-tip {
        text-align: center;
        color: #999;
        padding: 20px;
        font-size: 13px;
      }

      .session-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 12px;
        border-radius: 6px;
        cursor: pointer;
        transition: background 0.2s;

        &:hover {
          background: #f5f7fa;

          .delete-btn {
            opacity: 1;
          }
        }

        &.active {
          background: #e3f2fd;
        }

        .session-info {
          display: flex;
          flex-direction: column;
          gap: 4px;
          overflow: hidden;

          .session-name {
            font-size: 13px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }

          .session-time {
            font-size: 11px;
            color: #999;
          }
        }

        .delete-btn {
          opacity: 0;
          transition: opacity 0.2s;
        }
      }
    }
  }

  .agent-main {
    flex: 1;
    display: flex;
    flex-direction: column;

    .chat-card {
      height: 100%;
      display: flex;
      flex-direction: column;

      .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .title {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 16px;
          font-weight: 600;
        }

        .actions {
          display: flex;
          gap: 16px;
        }
      }

      .chat-container {
        flex: 1;
        overflow-y: auto;
        padding: 16px;
        background: #f5f7fa;
        border-radius: 8px;
        margin-bottom: 16px;
        min-height: 300px;

        .empty-tip {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          height: 100%;
          color: #999;

          .sub-tip {
            font-size: 12px;
            margin-top: 8px;
          }
        }

        .message-item {
          display: flex;
          gap: 12px;
          margin-bottom: 16px;

          &.user {
            flex-direction: row-reverse;

            .message-content {
              align-items: flex-end;
            }

            .message-body pre {
              background: #e3f2fd;
            }
          }

          &.assistant .message-body pre {
            background: #fff;
          }

          .message-content {
            display: flex;
            flex-direction: column;
            max-width: 70%;

            .message-header {
              display: flex;
              align-items: center;
              gap: 8px;
              margin-bottom: 4px;

              .sender {
                font-size: 13px;
                font-weight: 500;
              }

              .time {
                font-size: 11px;
                color: #999;
              }
            }

            .message-body pre {
              margin: 0;
              padding: 12px 16px;
              border-radius: 8px;
              font-family: inherit;
              font-size: 14px;
              line-height: 1.6;
              white-space: pre-wrap;
              word-break: break-word;
            }

            .tool-results {
              margin-top: 8px;
              display: flex;
              flex-direction: column;
              gap: 4px;

              .tool-result {
                display: flex;
                align-items: center;
                gap: 8px;
                font-size: 12px;
                color: #666;
              }
            }

            .loading {
              animation: rotate 1s linear infinite;
            }
          }
        }
      }

      .chat-input {
        .input-actions {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-top: 12px;

          .tip {
            font-size: 12px;
            color: #999;
          }
        }
      }
    }
  }

  .agent-config {
    width: 320px;
    background: #fff;
    border-radius: 8px;

    .config-tabs {
      height: 100%;
      padding: 16px;

      .config-section {
        margin-bottom: 24px;

        h4 {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 14px;
          font-weight: 600;
          margin-bottom: 16px;
          color: #333;
        }

        .tool-list,
        .skill-list {
          display: flex;
          flex-direction: column;
          gap: 8px;

          .tool-item,
          .skill-item {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            padding: 8px 12px;
            border-radius: 6px;
            border: 1px solid #eee;
            margin-right: 0;

            .tool-info,
            .skill-info {
              display: flex;
              flex-direction: column;
              gap: 4px;
              flex: 1;

              .tool-name,
              .skill-name {
                font-size: 13px;
                font-weight: 500;
              }

              .tool-desc,
              .skill-desc {
                font-size: 11px;
                color: #999;
              }
            }
          }
        }

        .empty-tip {
          text-align: center;
          color: #999;
          font-size: 13px;
          padding: 20px;
        }
      }
    }
  }
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

:deep(.el-tabs__content) {
  overflow-y: auto;
  max-height: calc(100vh - 240px);
}
</style>