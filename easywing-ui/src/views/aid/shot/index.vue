<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Edit, Delete, Search, Refresh } from '@element-plus/icons-vue'
import {
  getShotList,
  getProjectOptions,
  getEpisodeList,
  addShot,
  updateShot,
  deleteShots
} from '@/api/aid'
import type { AidEpisode, AidProject, AidShot } from '@/types'
import { FLOW_STATUSES, formatDuration, statusTagType } from '@/utils/aid'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<AidShot[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const projectOptions = ref<AidProject[]>([])
const episodeOptions = ref<AidEpisode[]>([])
const queryFormRef = ref<FormInstance>()

const queryParams = reactive({
  current: 1,
  size: 10,
  projectId: undefined as number | undefined,
  episodeId: undefined as number | undefined
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const submitting = ref(false)

const emptyShot = (): AidShot => ({
  episodeId: queryParams.episodeId,
  shotNo: undefined,
  description: '',
  dialogue: '',
  cameraMovement: '',
  duration: undefined,
  status: 'DRAFT'
})

const formData = reactive<AidShot>(emptyShot())

const rules: FormRules = {
  episodeId: [{ required: true, message: t('aid.selectProject'), trigger: 'change' }],
  shotNo: [{ required: true, message: t('aid.shotNoRequired'), trigger: 'blur' }]
}

const statusLabel = (code?: string) => {
  if (!code) return '-'
  const key = `aid.statusCodes.${code}`
  return t(key) === key ? code : t(key)
}

const loadProjects = async () => {
  try {
    projectOptions.value = (await getProjectOptions()) || []
  } catch (error) {
    console.error(t('aid.projectListFailed'), error)
  }
}

/** 项目变化时重拉分集候选，并清空已选分集 */
const handleProjectChange = () => {
  queryParams.episodeId = undefined
  formData.episodeId = undefined
  loadEpisodes()
  handleQuery()
}

const loadEpisodes = async () => {
  if (!queryParams.projectId) {
    episodeOptions.value = []
    return
  }
  try {
    // 候选最多取 200 条：M1 阶段单项目分集数有限，避免额外做下拉里分页
    const res = await getEpisodeList(1, 200, queryParams.projectId)
    episodeOptions.value = res.records || []
  } catch (error) {
    console.error(t('aid.episodeListFailed'), error)
    episodeOptions.value = []
  }
}

const getList = async () => {
  if (!queryParams.episodeId) {
    tableData.value = []
    total.value = 0
    return
  }
  try {
    loading.value = true
    const res = await getShotList(queryParams.current, queryParams.size, queryParams.episodeId)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error(t('aid.shotListFailed'), error)
    ElMessage.error(t('aid.shotListFailed'))
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
  queryParams.projectId = undefined
  queryParams.episodeId = undefined
  episodeOptions.value = []
  tableData.value = []
  total.value = 0
}

const handlePageChange = (page: number) => {
  queryParams.current = page
  getList()
}

const handleSizeChange = (size: number) => {
  queryParams.size = size
  handleQuery()
}

const handleSelectionChange = (rows: AidShot[]) => {
  selectedIds.value = rows
    .map((row) => row.id)
    .filter((id): id is number => typeof id === 'number')
}

const resetForm = () => {
  Object.assign(formData, emptyShot())
  formRef.value?.clearValidate()
}

const handleAdd = () => {
  resetForm()
  dialogTitle.value = t('aid.addShot')
  dialogVisible.value = true
}

const handleEdit = (row: AidShot) => {
  Object.assign(formData, emptyShot(), row)
  dialogTitle.value = t('aid.editShot')
  dialogVisible.value = true
}

const handleDelete = async (rows: AidShot[]) => {
  const ids = rows.map((row) => row.id).filter((id): id is number => typeof id === 'number')
  if (ids.length === 0) {
    ElMessage.warning(t('common.empty'))
    return
  }
  try {
    await ElMessageBox.confirm(t('aid.deleteShotConfirm', { count: ids.length }), t('common.tip'), {
      type: 'warning'
    })
    await deleteShots(ids)
    ElMessage.success(t('aid.shotDeleted'))
    if (tableData.value.length === ids.length && queryParams.current > 1) {
      queryParams.current -= 1
    }
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

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
    submitting.value = true
    if (formData.id) {
      await updateShot({ ...formData })
      ElMessage.success(t('aid.shotUpdated'))
    } else {
      await addShot({ ...formData })
      ElMessage.success(t('aid.shotCreated'))
    }
    dialogVisible.value = false
    getList()
  } catch (error) {
    if (error !== false) {
      console.error(t('aid.formInvalid'), error)
    }
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadProjects()
})
</script>

<template>
  <div class="aid-shot">
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
          :label="t('aid.projectName')"
          prop="projectId"
        >
          <el-select
            v-model="queryParams.projectId"
            :placeholder="t('aid.selectProject')"
            filterable
            style="width: 200px"
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
          :label="t('aid.episodeNo')"
          prop="episodeId"
        >
          <el-select
            v-model="queryParams.episodeId"
            :placeholder="t('aid.selectProject')"
            filterable
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="item in episodeOptions"
              :key="item.id"
              :label="`${item.episodeNo}. ${item.title || '-'}`"
              :value="item.id ?? 0"
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
          <span class="title">{{ t('route.aidShot') }}</span>
          <div>
            <el-button
              v-auth="['aid:shot:add']"
              type="primary"
              :icon="Plus"
              :disabled="!queryParams.episodeId"
              @click="handleAdd"
            >
              {{ t('aid.addShot') }}
            </el-button>
            <el-button
              v-auth="['aid:shot:remove']"
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

      <div
        v-if="!queryParams.episodeId"
        class="select-project-tip"
      >
        {{ t('aid.selectProject') }}
      </div>

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
          prop="shotNo"
          :label="t('aid.shotNo')"
          width="90"
          align="center"
        />
        <el-table-column
          prop="description"
          :label="t('aid.description')"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column
          prop="dialogue"
          :label="t('aid.dialogue')"
          min-width="140"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.dialogue || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="cameraMovement"
          :label="t('aid.cameraMovement')"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            {{ row.cameraMovement || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('aid.shotDuration')"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            {{ formatDuration(row.duration) }}
          </template>
        </el-table-column>
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
          :label="t('common.actions')"
          width="180"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-auth="['aid:shot:edit']"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-auth="['aid:shot:remove']"
              type="danger"
              link
              :icon="Delete"
              @click="handleDelete([row])"
            >
              {{ t('common.delete') }}
            </el-button>
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
      width="680px"
      draggable
      top="8vh"
    >
      <div class="shot-tip">
        {{ t('aid.promptTemplatePlaceholder') }}
      </div>
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="110px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item
              :label="t('aid.episodeNo')"
              prop="episodeId"
            >
              <el-select
                v-model="formData.episodeId"
                style="width: 100%"
                filterable
              >
                <el-option
                  v-for="item in episodeOptions"
                  :key="item.id"
                  :label="`${item.episodeNo}. ${item.title || '-'}`"
                  :value="item.id ?? 0"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item
              :label="t('aid.shotNo')"
              prop="shotNo"
            >
              <el-input-number
                v-model="formData.shotNo"
                :min="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item
              :label="t('common.status')"
              prop="status"
            >
              <el-select
                v-model="formData.status"
                style="width: 100%"
              >
                <el-option
                  v-for="status in FLOW_STATUSES"
                  :key="status"
                  :label="statusLabel(status)"
                  :value="status"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item
          :label="t('aid.description')"
          prop="description"
        >
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            :placeholder="t('aid.promptTemplatePlaceholder')"
          />
        </el-form-item>
        <el-form-item
          :label="t('aid.dialogue')"
          prop="dialogue"
        >
          <el-input
            v-model="formData.dialogue"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item
              :label="t('aid.cameraMovement')"
              prop="cameraMovement"
            >
              <el-input
                v-model="formData.cameraMovement"
                placeholder="跟拍 / 推近 / 固定"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              :label="t('aid.shotDuration')"
              prop="duration"
            >
              <el-input-number
                v-model="formData.duration"
                :min="0"
                :step="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
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
.aid-shot {
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

    .select-project-tip {
      padding: 8px 12px;
      margin-bottom: 12px;
      font-size: 13px;
      color: var(--el-text-color-secondary);
      background: var(--el-fill-color-light);
      border-radius: 4px;
    }

    .pagination {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }

  .shot-tip {
    padding: 8px 12px;
    margin-bottom: 12px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-light);
    border-radius: 4px;
  }
}
</style>
