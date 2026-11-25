<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus, Delete, Search, Refresh } from '@element-plus/icons-vue'
import {
  getTaskList,
  addTask,
  deleteTasks,
  getProjectOptions,
  getEpisodeList,
  getShotList
} from '@/api/aid'
import type { AidEpisode, AidGenerationTask, AidProject, AidShot } from '@/types'
import { TASK_STATUSES, TASK_TYPES, statusTagType } from '@/utils/aid'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<AidGenerationTask[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const projectOptions = ref<AidProject[]>([])
const episodeOptions = ref<AidEpisode[]>([])
const shotOptions = ref<AidShot[]>([])
const queryFormRef = ref<FormInstance>()

const queryParams = reactive({
  current: 1,
  size: 10,
  targetType: '',
  taskType: '',
  status: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref(t('aid.addTask'))
const submitting = ref(false)

/** 关联对象选择：先选类型，再从项目→分集→分镜里挑具体对象 */
const target = reactive({
  type: 'SHOT' as string,
  projectId: undefined as number | undefined,
  episodeId: undefined as number | undefined,
  targetId: undefined as number | undefined
})

const createForm = reactive({
  taskType: 'IMAGE',
  provider: '',
  model: '',
  prompt: ''
})

const statusLabel = (code?: string) => {
  if (!code) return '-'
  const key = `aid.statusCodes.${code}`
  return t(key) === key ? code : t(key)
}

const targetTypeLabel = (code?: string) => {
  if (!code) return '-'
  const map: Record<string, string> = {
    SHOT: t('route.aidShot'),
    ASSET: t('route.aidAsset'),
    EPISODE: t('route.aidEpisode')
  }
  return map[code] || code
}

/** 终态任务才允许删除，与后端 deleteTasks 的校验保持一致 */
const isTerminal = (status?: string) =>
  status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELED'

const loadProjects = async () => {
  try {
    projectOptions.value = (await getProjectOptions()) || []
  } catch (error) {
    console.error(t('aid.projectListFailed'), error)
  }
}

const handleTargetTypeChange = () => {
  target.projectId = undefined
  target.episodeId = undefined
  target.targetId = undefined
  episodeOptions.value = []
  shotOptions.value = []
}

const handleProjectChange = async () => {
  target.episodeId = undefined
  target.targetId = undefined
  shotOptions.value = []
  if (!target.projectId || target.type !== 'SHOT') {
    episodeOptions.value = []
    return
  }
  try {
    const res = await getEpisodeList(1, 200, target.projectId)
    episodeOptions.value = res.records || []
  } catch (error) {
    console.error(t('aid.episodeListFailed'), error)
  }
}

const handleEpisodeChange = async () => {
  target.targetId = undefined
  if (!target.episodeId) {
    shotOptions.value = []
    return
  }
  try {
    const res = await getShotList(1, 500, target.episodeId)
    shotOptions.value = res.records || []
  } catch (error) {
    console.error(t('aid.shotListFailed'), error)
  }
}

const getList = async () => {
  try {
    loading.value = true
    const res = await getTaskList(queryParams.current, queryParams.size, {
      targetType: queryParams.targetType || undefined,
      taskType: queryParams.taskType || undefined,
      status: queryParams.status || undefined
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error(t('aid.taskListFailed'), error)
    ElMessage.error(t('aid.taskListFailed'))
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.current = 1
  getList()
}

const handleReset = () => {
  queryFormRef.value?.resetFields()
  queryParams.targetType = ''
  queryParams.taskType = ''
  queryParams.status = ''
  handleQuery()
}

const handlePageChange = (page: number) => {
  queryParams.current = page
  getList()
}

const handleSizeChange = (size: number) => {
  queryParams.size = size
  handleQuery()
}

const handleSelectionChange = (rows: AidGenerationTask[]) => {
  selectedIds.value = rows
    .map((row) => row.id)
    .filter((id): id is number => typeof id === 'number')
}

const handleAdd = () => {
  Object.assign(target, {
    type: 'SHOT',
    projectId: undefined,
    episodeId: undefined,
    targetId: undefined
  })
  Object.assign(createForm, { taskType: 'IMAGE', provider: '', model: '', prompt: '' })
  episodeOptions.value = []
  shotOptions.value = []
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!target.targetId) {
    ElMessage.warning(t('aid.selectProject'))
    return
  }
  try {
    submitting.value = true
    // 后端按 targetType:targetId:taskType 幂等，重复点击返回同一个任务
    await addTask({
      targetType: target.type,
      targetId: target.targetId,
      taskType: createForm.taskType,
      provider: createForm.provider || undefined,
      model: createForm.model || undefined,
      prompt: createForm.prompt || undefined
    })
    ElMessage.success(t('aid.taskCreated'))
    dialogVisible.value = false
    getList()
  } catch (error) {
    console.error(t('aid.taskCreated'), error)
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (rows: AidGenerationTask[]) => {
  const ids = rows.map((row) => row.id).filter((id): id is number => typeof id === 'number')
  if (ids.length === 0) {
    ElMessage.warning(t('common.empty'))
    return
  }
  const running = rows.filter((row) => !isTerminal(row.status))
  if (running.length > 0) {
    ElMessage.warning(t('aid.deleteTaskConfirm'))
    return
  }
  try {
    await ElMessageBox.confirm(t('aid.deleteTaskConfirm', { count: ids.length }), t('common.tip'), {
      type: 'warning'
    })
    await deleteTasks(ids)
    ElMessage.success(t('aid.taskDeleted'))
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(t('common.delete'), error)
    }
  }
}

const handleBatchDelete = () => {
  handleDelete(tableData.value.filter((row) => selectedIds.value.includes(row.id as number)))
}

onMounted(() => {
  loadProjects()
  getList()
})
</script>

<template>
  <div class="aid-task">
    <el-card
      shadow="never"
      class="search-card"
    >
      <el-form
        ref="queryFormRef"
        :model="queryParams"
        inline
      >
        <el-form-item
          :label="t('aid.targetType')"
          prop="targetType"
        >
          <el-select
            v-model="queryParams.targetType"
            clearable
            style="width: 140px"
          >
            <el-option
              label="SHOT"
              value="SHOT"
            />
            <el-option
              label="ASSET"
              value="ASSET"
            />
            <el-option
              label="EPISODE"
              value="EPISODE"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          :label="t('aid.taskType')"
          prop="taskType"
        >
          <el-select
            v-model="queryParams.taskType"
            :placeholder="t('aid.selectTaskType')"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="type in TASK_TYPES"
              :key="type"
              :label="type"
              :value="type"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          :label="t('common.status')"
          prop="status"
        >
          <el-select
            v-model="queryParams.status"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="status in TASK_STATUSES"
              :key="status"
              :label="statusLabel(status)"
              :value="status"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :icon="Search"
            @click="handleQuery"
          >
            {{ t('common.search') }}
          </el-button>
          <el-button
            :icon="Refresh"
            @click="handleReset"
          >
            {{ t('common.reset') }}
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
          <span class="title">{{ t('route.aidTask') }}</span>
          <div>
            <el-button
              v-auth="['aid:task:add']"
              type="primary"
              :icon="Plus"
              @click="handleAdd"
            >
              {{ t('aid.addTask') }}
            </el-button>
            <el-button
              v-auth="['aid:task:remove']"
              type="danger"
              :icon="Delete"
              :disabled="selectedIds.length === 0"
              @click="handleBatchDelete"
            >
              {{ t('common.delete') }}
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column
          type="selection"
          width="50"
          align="center"
        />
        <el-table-column
          prop="id"
          :label="t('system.userId')"
          width="90"
          align="center"
        />
        <el-table-column
          :label="t('aid.taskType')"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.taskType)">
              {{ row.taskType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('aid.targetType')"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            {{ targetTypeLabel(row.targetType) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="targetId"
          :label="t('aid.targetId')"
          width="100"
          align="center"
        />
        <el-table-column
          :label="t('common.status')"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('aid.progress')"
          width="170"
        >
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress ?? 0"
              :status="row.status === 'FAILED' ? 'exception' : undefined"
            />
          </template>
        </el-table-column>
        <el-table-column
          prop="model"
          :label="t('aid.model')"
          width="140"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.model || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="costAmount"
          :label="t('aid.costAmount')"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ row.costAmount ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="errorMessage"
          :label="t('aid.errorMessage')"
          min-width="160"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.errorMessage || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="createTime"
          :label="t('system.createTime')"
          width="170"
          align="center"
        />
        <el-table-column
          :label="t('common.actions')"
          width="120"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-if="isTerminal(row.status)"
              v-auth="['aid:task:remove']"
              type="danger"
              link
              :icon="Delete"
              @click="handleDelete([row])"
            >
              {{ t('common.delete') }}
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <template #empty>
          {{ t('common.empty') }}
        </template>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.current"
          v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      draggable
    >
      <el-form label-width="110px">
        <el-form-item :label="t('aid.targetType')">
          <el-radio-group
            v-model="target.type"
            @change="handleTargetTypeChange"
          >
            <el-radio-button
              v-for="type in ['SHOT', 'ASSET', 'EPISODE']"
              :key="type"
              :value="type"
            >
              {{ targetTypeLabel(type) }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('aid.projectName')">
          <el-select
            v-model="target.projectId"
            filterable
            style="width: 100%"
            @change="handleProjectChange"
          >
            <el-option
              v-for="item in projectOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id ?? 0"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="target.type === 'SHOT'"
          :label="t('aid.episodeNo')"
        >
          <el-select
            v-model="target.episodeId"
            filterable
            clearable
            style="width: 100%"
            @change="handleEpisodeChange"
          >
            <el-option
              v-for="item in episodeOptions"
              :key="item.id"
              :label="`${item.episodeNo}. ${item.title || '-'}`"
              :value="item.id ?? 0"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('aid.targetId')">
          <el-select
            v-model="target.targetId"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="item in shotOptions"
              :key="item.id"
              :label="`#${item.shotNo} ${item.description || ''}`"
              :value="item.id ?? 0"
            />
          </el-select>
          <div
            v-if="target.type !== 'SHOT'"
            class="form-tip"
          >
            {{ t('aid.selectProject') }}
          </div>
        </el-form-item>
        <el-form-item
          :label="t('aid.taskType')"
          required
        >
          <el-select
            v-model="createForm.taskType"
            style="width: 100%"
          >
            <el-option
              v-for="type in TASK_TYPES"
              :key="type"
              :label="type"
              :value="type"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('aid.provider')">
          <el-input v-model="createForm.provider" />
        </el-form-item>
        <el-form-item :label="t('aid.model')">
          <el-input v-model="createForm.model" />
        </el-form-item>
        <el-form-item :label="t('aid.promptTemplate')">
          <el-input
            v-model="createForm.prompt"
            type="textarea"
            :rows="3"
            :placeholder="t('aid.promptTemplatePlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.aid-task {
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
    }

    .pagination {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }

  .form-tip {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
