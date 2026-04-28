<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getModelList, getProviders, type ModelInfo } from '@/api/ai/chat'

const loading = ref(false)
const modelList = ref<ModelInfo[]>([])
const providerList = ref<string[]>([])
const selectedProvider = ref('')

const getModels = async () => {
  try {
    loading.value = true
    const [models, providers] = await Promise.all([getModelList(), getProviders()])
    modelList.value = models || []
    providerList.value = providers || []
  } catch (error) {
    console.error('获取模型列表失败:', error)
    ElMessage.error('获取模型列表失败')
  } finally {
    loading.value = false
  }
}

const formatNumber = (num: number) => {
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toString()
}

const getCapabilityTags = (model: ModelInfo) => {
  const tags = []
  if (model.supportsStreaming) tags.push({ text: '流式输出', type: 'success' })
  if (model.supportsTools) tags.push({ text: '工具调用', type: 'warning' })
  if (model.supportsVision) tags.push({ text: '视觉理解', type: 'info' })
  if (model.supportsThinking) tags.push({ text: '思考模式', type: 'danger' })
  return tags
}

const filteredModels = () => {
  if (!selectedProvider.value) {
    return modelList.value
  }
  return modelList.value.filter((m) => m.provider === selectedProvider.value)
}

onMounted(() => {
  getModels()
})
</script>

<template>
  <div class="ai-model">
    <el-card
      shadow="never"
      class="search-card"
    >
      <el-form :inline="true">
        <el-form-item label="提供商">
          <el-select
            v-model="selectedProvider"
            placeholder="全部提供商"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="provider in providerList"
              :key="provider"
              :label="provider"
              :value="provider"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :icon="Refresh"
            @click="getModels"
          >
            刷新
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card
      shadow="never"
      class="table-card"
    >
      <template #header>
        <div class="card-header">
          <span class="title">AI 模型列表</span>
          <span class="total">共 {{ filteredModels().length }} 个模型</span>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="filteredModels()"
        stripe
        style="width: 100%"
      >
        <el-table-column
          label="模型名称"
          prop="name"
          width="180"
        >
          <template #default="{ row }">
            <div class="model-name">
              <span class="name">{{ row.name }}</span>
              <span class="version">v{{ row.version }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="模型ID"
          prop="id"
          width="200"
        >
          <template #default="{ row }">
            <code class="model-id">{{ row.id }}</code>
          </template>
        </el-table-column>
        <el-table-column
          label="提供商"
          prop="provider"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <el-tag type="primary">
              {{ row.provider }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="描述"
          prop="description"
          min-width="200"
        >
          <template #default="{ row }">
            <span class="description">{{ row.description || '暂无描述' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="上下文窗口"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ formatNumber(row.contextWindow) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="最大输出"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ formatNumber(row.maxOutputTokens) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="输入价格"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <span v-if="row.inputCostPer1MTokens > 0">
              ${{ row.inputCostPer1MTokens }}/1M
            </span>
            <span
              v-else
              class="free"
            >免费</span>
          </template>
        </el-table-column>
        <el-table-column
          label="输出价格"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <span v-if="row.outputCostPer1MTokens > 0">
              ${{ row.outputCostPer1MTokens }}/1M
            </span>
            <span
              v-else
              class="free"
            >免费</span>
          </template>
        </el-table-column>
        <el-table-column
          label="能力"
          width="180"
          align="center"
        >
          <template #default="{ row }">
            <el-space wrap>
              <el-tag
                v-for="tag in getCapabilityTags(row)"
                :key="tag.text"
                :type="tag.type"
                size="small"
              >
                {{ tag.text }}
              </el-tag>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.ai-model {
  .search-card {
    margin-bottom: 16px;
  }

  .table-card {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      .title {
        font-size: 16px;
        font-weight: 600;
      }
      .total {
        font-size: 13px;
        color: #999;
      }
    }

    .model-name {
      display: flex;
      flex-direction: column;
      .name {
        font-weight: 500;
      }
      .version {
        font-size: 12px;
        color: #999;
      }
    }

    .model-id {
      font-size: 12px;
      padding: 2px 6px;
      background: #f5f7fa;
      border-radius: 4px;
      color: #666;
    }

    .description {
      font-size: 13px;
      color: #666;
    }

    .free {
      color: #67c23a;
      font-weight: 500;
    }
  }
}
</style>