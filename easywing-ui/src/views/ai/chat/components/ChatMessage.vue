<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ChatDotRound, User, CopyDocument, Refresh, Close } from '@element-plus/icons-vue'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{
  message: ChatMessageItem
  /** 流式生成中时禁用编辑与重新生成。 */
  streaming?: boolean
}>()

const emit = defineEmits<{
  (e: 'edit', message: ChatMessageItem): void
  (e: 'retry'): void
  (e: 'stop'): void
  (e: 'toggle-thinking', value: boolean): void
}>()

const { t } = useI18n()

const isUser = computed(() => props.message.role === 'user')

/** 内容区采用 Markdown；思考过程保持纯文本，避免流式中间态被解析破坏。 */
const renderedContent = computed(() => renderMarkdown(props.message.content))
const renderedThinking = computed(() =>
  props.message.thinking ? renderMarkdown(props.message.thinking) : ''
)

const editing = ref(false)
const draft = ref('')

const startEdit = () => {
  if (props.streaming || !isUser.value) {
    return
  }
  draft.value = props.message.content
  editing.value = true
}

const submitEdit = () => {
  const next = draft.value.trim()
  if (!next) {
    return
  }
  emit('edit', { ...props.message, content: next })
  editing.value = false
}

const cancelEdit = () => {
  editing.value = false
}

const copyContent = async () => {
  try {
    await navigator.clipboard.writeText(props.message.content)
    ElMessage.success(t('common.copied'))
  } catch {
    // 非 HTTPS / 权限受限环境下 clipboard 可能被拒，退化为选中提示
    ElMessage.warning(t('chat.copyMessage'))
  }
}

watch(
  () => props.message.content,
  () => {
    if (editing.value && draft.value === props.message.content) {
      editing.value = false
    }
  }
)
</script>

<template>
  <div
    class="message-item"
    :class="[message.role, { streaming }]"
  >
    <div class="message-avatar">
      <el-avatar
        :size="32"
        :type="isUser ? undefined : 'primary'"
        :icon="isUser ? User : ChatDotRound"
      />
    </div>

    <div class="message-content">
      <div class="message-header">
        <span class="sender">{{ isUser ? t('chat.you') : t('chat.ai') }}</span>
        <span class="time">{{ message.timeText }}</span>
      </div>

      <!-- 思考过程：默认折叠，可展开 -->
      <div
        v-if="message.thinking"
        class="thinking-block"
      >
        <div
          class="thinking-toggle"
          @click="emit('toggle-thinking', !message.thinkingExpanded)"
        >
          <el-icon>
            <component :is="message.thinkingExpanded ? ArrowDown : ArrowRight" />
          </el-icon>
          <span>
            {{
              message.thinkingExpanded ? t('chat.collapseThinking') : t('chat.expandThinking')
            }}
          </span>
        </div>
        <div
          v-show="message.thinkingExpanded"
          class="thinking-body"
          v-html="renderedThinking"
        ></div>
      </div>

      <!-- 正文 -->
      <div class="message-body">
        <div
          v-if="!editing"
          class="markdown-body"
          v-html="renderedContent"
        ></div>

        <div
          v-else
          class="edit-area"
        >
          <el-input
            v-model="draft"
            type="textarea"
            :rows="4"
            resize="none"
          />
          <div class="edit-actions">
            <el-button
              size="small"
              @click="cancelEdit"
            >
              {{ t('common.cancel') }}
            </el-button>
            <el-button
              size="small"
              type="primary"
              @click="submitEdit"
            >
              {{ t('chat.editMessage') }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- 操作条 -->
      <div
        v-if="!streaming && message.content"
        class="message-actions"
      >
        <el-button
          link
          size="small"
          :icon="CopyDocument"
          @click="copyContent"
        >
          {{ t('common.copy') }}
        </el-button>

        <el-button
          v-if="isUser && !editing"
          link
          size="small"
          :icon="Edit"
          @click="startEdit"
        >
          {{ t('chat.editMessage') }}
        </el-button>

        <el-button
          v-if="!isUser"
          link
          size="small"
          :icon="Refresh"
          @click="emit('retry')"
        >
          {{ t('chat.retry') }}
        </el-button>

        <el-button
          v-if="streaming"
          link
          size="small"
          type="danger"
          :icon="Close"
          @click="emit('stop')"
        >
          {{ t('chat.stop') }}
        </el-button>
      </div>

      <div
        v-if="streaming"
        class="streaming-indicator"
      >
        <span class="dot"></span>
        <span class="dot"></span>
        <span class="dot"></span>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'
import { ArrowDown, ArrowRight, Edit } from '@element-plus/icons-vue'

export interface ChatMessageItem {
  id: string
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string
  thinking?: string
  thinkingExpanded?: boolean
  timeText: string
}

export default defineComponent({
  components: { ArrowDown, ArrowRight, Edit }
})
</script>

<style lang="scss" scoped>
.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;

  &.user {
    flex-direction: row-reverse;

    .message-content {
      align-items: flex-end;
    }

    .message-body .markdown-body {
      background: var(--el-color-primary-light-9);
      color: var(--el-text-color-primary);
    }

    .message-actions {
      justify-content: flex-end;
    }
  }

  &.assistant .message-body .markdown-body {
    background: var(--el-bg-color);
  }

  .message-content {
    display: flex;
    flex-direction: column;
    max-width: min(760px, 78%);
    min-width: 0;

    .message-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 4px;

      .sender {
        font-size: 13px;
        font-weight: 500;
        color: var(--el-text-color-primary);
      }

      .time {
        font-size: 11px;
        color: var(--el-text-color-secondary);
      }
    }

    .thinking-block {
      margin-bottom: 8px;

      .thinking-toggle {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        cursor: pointer;
        font-size: 12px;
        color: var(--el-text-color-secondary);

        &:hover {
          color: var(--el-color-primary);
        }
      }

      .thinking-body {
        margin-top: 6px;
        padding: 10px 12px;
        border-left: 3px solid var(--el-border-color);
        background: var(--el-fill-color-lighter);
        font-size: 13px;
        color: var(--el-text-color-regular);
        white-space: pre-wrap;
        word-break: break-word;
      }
    }

    .message-body {
      .markdown-body {
        margin: 0;
        padding: 12px 16px;
        border-radius: 8px;
        font-size: 14px;
        line-height: 1.7;
        overflow-wrap: anywhere;

        :deep(p) {
          margin: 0 0 8px;

          &:last-child {
            margin-bottom: 0;
          }
        }

        :deep(pre) {
          position: relative;
          margin: 8px 0;
          padding: 12px;
          border-radius: 6px;
          background: var(--el-fill-color-dark);
          overflow-x: auto;
          font-size: 13px;
        }

        :deep(code) {
          font-family: 'JetBrains Mono', Consolas, Monaco, monospace;
        }

        :deep(:not(pre) > code) {
          padding: 2px 4px;
          border-radius: 4px;
          background: var(--el-fill-color-light);
          font-size: 13px;
        }

        :deep(table) {
          width: 100%;
          border-collapse: collapse;
          margin: 8px 0;
        }

        :deep(th),
        :deep(td) {
          padding: 6px 10px;
          border: 1px solid var(--el-border-color-lighter);
        }

        :deep(blockquote) {
          margin: 8px 0;
          padding-left: 12px;
          border-left: 3px solid var(--el-border-color);
          color: var(--el-text-color-secondary);
        }

        :deep(img) {
          max-width: 100%;
        }
      }

      .edit-area {
        .edit-actions {
          display: flex;
          gap: 8px;
          margin-top: 8px;
          justify-content: flex-end;
        }
      }
    }

    .message-actions {
      display: flex;
      gap: 4px;
      margin-top: 4px;
      opacity: 0;
      transition: opacity 0.15s ease;

      .message-item:hover &,
      .streaming & {
        opacity: 1;
      }
    }

    .streaming-indicator {
      display: inline-flex;
      gap: 4px;
      align-items: center;
      margin-top: 6px;
      padding-left: 4px;

      .dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: var(--el-color-primary);
        animation: blink 1.2s infinite both;

        &:nth-child(2) {
          animation-delay: 0.2s;
        }

        &:nth-child(3) {
          animation-delay: 0.4s;
        }
      }
    }
  }
}

@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.25;
  }
  40% {
    opacity: 1;
  }
}
</style>
