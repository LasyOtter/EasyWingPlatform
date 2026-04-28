<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Chat, Refresh, Delete } from '@element-plus/icons-vue'
import { getModelList, chatCompletions, type ChatMessage, type ModelInfo } from '@/api/ai/chat'

const loading = ref(false)
const sending = ref(false)
const modelList = ref<ModelInfo[]>([])
const selectedModel = ref('')
const systemPrompt = ref('You are a helpful AI assistant.')
const temperature = ref(0.7)
const maxTokens = ref(2048)

interface MessageItem {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp: Date
}

const messages = ref<MessageItem[]>([])
const inputMessage = ref('')
const chatContainer = ref<HTMLElement>()

const getModels = async () => {
  try {
    loading.value = true
    const res = await getModelList()
    modelList.value = res || []
    if (modelList.value.length > 0 && !selectedModel.value) {
      selectedModel.value = modelList.value[0].id
    }
  } catch (error) {
    console.error('获取模型列表失败:', error)
  } finally {
    loading.value = false
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

  if (!selectedModel.value) {
    ElMessage.warning('请选择模型')
    return
  }

  const userMessage: MessageItem = {
    id: Date.now().toString(),
    role: 'user',
    content: inputMessage.value,
    timestamp: new Date()
  }

  messages.value.push(userMessage)
  inputMessage.value = ''
  scrollToBottom()

  sending.value = true

  try {
    const chatMessages: ChatMessage[] = []

    if (systemPrompt.value) {
      chatMessages.push({
        role: 'system',
        content: systemPrompt.value
      })
    }

    chatMessages.push(
      ...messages.value
        .filter((m) => m.role !== 'system')
        .map((m) => ({
          role: m.role,
          content: m.content
        }))
    )

    const res = await chatCompletions({
      model: selectedModel.value,
      messages: chatMessages,
      temperature: temperature.value,
      maxTokens: maxTokens.value
    })

    if (res.choices && res.choices.length > 0) {
      const assistantMessage: MessageItem = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: res.choices[0].message.content || '',
        timestamp: new Date()
      }
      messages.value.push(assistantMessage)
      scrollToBottom()
    }
  } catch (error: any) {
    ElMessage.error(error.message || '发送消息失败')
  } finally {
    sending.value = false
  }
}

const clearHistory = () => {
  messages.value = []
  ElMessage.success('聊天记录已清空')
}

const formatTime = (date: Date) => {
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  getModels()
})
</script>

<template>
  <div class="ai-chat">
    <el-card
      shadow="never"
      class="config-card"
    >
      <el-form :inline="true">
        <el-form-item label="模型">
          <el-select
            v-model="selectedModel"
            placeholder="请选择模型"
            clearable
            style="width: 200px"
            :loading="loading"
          >
            <el-option
              v-for="model in modelList"
              :key="model.id"
              :label="model.name"
              :value="model.id"
            >
              <span>{{ model.name }}</span>
              <span class="model-provider"> - {{ model.provider }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="温度">
          <el-slider
            v-model="temperature"
            :min="0"
            :max="2"
            :step="0.1"
            style="width: 120px"
            :show-tooltip="true"
          />
          <span class="param-value">{{ temperature }}</span>
        </el-form-item>
        <el-form-item label="最大令牌">
          <el-input-number
            v-model="maxTokens"
            :min="100"
            :max="32000"
            :step="100"
            style="width: 130px"
          />
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input
            v-model="systemPrompt"
            placeholder="设置系统提示词"
            style="width: 300px"
          />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card
      shadow="never"
      class="chat-card"
    >
      <template #header>
        <div class="card-header">
          <span class="title">
            <el-icon><Chat /></el-icon>
            AI 对话
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
              @click="getModels"
            >
              刷新模型
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
            <Chat />
          </el-icon>
          <p>开始与 AI 对话吧</p>
          <p class="sub-tip">
            选择模型后输入消息发送
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
              icon="ChatDotRound"
            />
          </div>
          <div class="message-content">
            <div class="message-header">
              <span class="sender">{{ msg.role === 'user' ? '我' : 'AI' }}</span>
              <span class="time">{{ formatTime(msg.timestamp) }}</span>
            </div>
            <div class="message-body">
              <pre>{{ msg.content }}</pre>
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
              icon="ChatDotRound"
            />
          </div>
          <div class="message-content">
            <div class="message-header">
              <span class="sender">AI</span>
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
          placeholder="请输入消息，按 Ctrl+Enter 发送"
          resize="none"
          @keydown.ctrl.enter="sendMessage"
        />
        <div class="input-actions">
          <span class="tip">Ctrl+Enter 发送</span>
          <el-button
            type="primary"
            :loading="sending"
            :disabled="!selectedModel"
            @click="sendMessage"
          >
            发送
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.ai-chat {
  .config-card {
    margin-bottom: 16px;
    :deep(.el-form-item) {
      margin-bottom: 0;
    }
    .model-provider {
      color: #999;
      font-size: 12px;
    }
    .param-value {
      margin-left: 8px;
      color: #666;
    }
  }

  .chat-card {
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
      height: 500px;
      overflow-y: auto;
      padding: 16px;
      background: #f5f7fa;
      border-radius: 8px;
      margin-bottom: 16px;

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

        &.assistant {
          .message-body pre {
            background: #fff;
          }
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
              color: #333;
            }
            .time {
              font-size: 11px;
              color: #999;
            }
          }

          .message-body {
            pre {
              margin: 0;
              padding: 12px 16px;
              border-radius: 8px;
              font-family: inherit;
              font-size: 14px;
              line-height: 1.6;
              white-space: pre-wrap;
              word-break: break-word;
            }
            .loading {
              animation: rotate 1s linear infinite;
            }
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

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>