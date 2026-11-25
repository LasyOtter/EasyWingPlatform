<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Edit, Delete, Search, Refresh } from '@element-plus/icons-vue'
import {
  getEpisodeList,
  getProjectOptions,
  addEpisode,
  updateEpisode,
  deleteEpisodes
} from '@/api/aid'
import type { AidEpisode, AidProject } from '@/types'
import { FLOW_STATUSES, formatDuration, statusTagType } from '@/utils/aid'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<AidEpisode[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const projectOptions = ref<AidProject[]>([])
const queryFormRef = ref<FormInstance>()

const queryParams = reactive({
  current: 1,
  size: 10,
  projectId: undefined as number | undefined
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const submitting = ref(false)

const emptyEpisode = (): AidEpisode => ({
  projectId: queryParams.projectId,
  episodeNo: undefined,
  title: '',
  script: '',
  duration: undefined,
  status: 'DRAFT'
})

const formData = reactive<AidEpisode>(emptyEpisode())

const rules: FormRules = {
  projectId: [{ required: true, message: t('aid.selectProject'), trigger: 'change' }],
  episodeNo: [{ required: true, message: t('aid.episodeNoRequired'), trigger: 'blur' }]
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

const getList = async () => {
  if (!queryParams.projectId) {
    tableData.value = []
    total.value = 0
    return
  }
  try {
    loading.value = true
    const res = await getEpisodeList(queryParams.current, queryParams.size, queryParams.projectId)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error(t('aid.episodeListFailed'), error)
    ElMessage.error(t('aid.episodeListFailed'))
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

const handleSelectionChange = (rows: AidEpisode[]) => {
  selectedIds.value = rows
    .map((row) => row.id)
    .filter((id): id is number => typeof id === 'number')
}

const resetForm = () => {
  Object.assign(formData, emptyEpisode())
  formRef.value?.clearValidate()
}

const handleAdd = () => {
  resetForm()
  dialogTitle.value = t('aid.addEpisode')
  dialogVisible.value = true
}

const handleEdit = (row: AidEpisode) => {
  Object.assign(formData, emptyEpisode(), row)
  dialogTitle.value = t('aid.editEpisode')
  dialogVisible.value = true
}

const handleDelete = async (rows: AidEpisode[]) => {
  const ids = rows.map((row) => row.id).filter((id): id is number => typeof id === 'number')
  if (ids.length === 0) {
    ElMessage.warning(t('common.empty'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('aid.deleteEpisodeConfirm', { count: ids.length }),
      t('common.tip'),
      { type: 'warning' }
    )
    await deleteEpisodes(ids)
    ElMessage.success(t('aid.episodeDeleted'))
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
      await updateEpisode({ ...formData })
      ElMessage.success(t('aid.episodeUpdated'))
    } else {
      await addEpisode({ ...formData })
      ElMessage.success(t('aid.episodeCreated'))
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
  <div class="aid-episode">
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
            style="width: 240px"
            @change="handleQuery"
          >
            <el-option
              v-for="item in projectOptions"
              :key="item.id"
              :label="item.name"
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
          <span class="title">{{ t('route.aidEpisode') }}</span>
          <div>
            <el-button
              v-auth="['aid:episode:add']"
              type="primary"
              :icon="Plus"
              :disabled="!queryParams.projectId"
              @click="handleAdd"
            >
              {{ t('aid.addEpisode') }}
            </el-button>
            <el-button
              v-auth="['aid:episode:remove']"
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
        v-if="!queryParams.projectId"
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
          prop="episodeNo"
          :label="t('aid.episodeNo')"
          width="90"
          align="center"
        />
        <el-table-column
          prop="title"
          :label="t('aid.title')"
          min-width="160"
          show-overflow-tooltip
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
          :label="t('aid.duration')"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            {{ formatDuration(row.duration) }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('aid.shotCount')"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            {{ row.shotCount ?? 0 }}
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
          width="180"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-auth="['aid:episode:edit']"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-auth="['aid:episode:remove']"
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
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="110px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item
              :label="t('aid.projectName')"
              prop="projectId"
            >
              <el-select
                v-model="formData.projectId"
                style="width: 100%"
                filterable
              >
                <el-option
                  v-for="item in projectOptions"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id ?? 0"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item
              :label="t('aid.episodeNo')"
              prop="episodeNo"
            >
              <el-input-number
                v-model="formData.episodeNo"
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
          :label="t('aid.title')"
          prop="title"
        >
          <el-input v-model="formData.title" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item
              :label="t('aid.duration')"
              prop="duration"
            >
              <el-input-number
                v-model="formData.duration"
                :min="0"
                :step="10"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item
          :label="t('aid.script')"
          prop="script"
        >
          <el-input
            v-model="formData.script"
            type="textarea"
            :rows="5"
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
.aid-episode {
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
}
</style>
