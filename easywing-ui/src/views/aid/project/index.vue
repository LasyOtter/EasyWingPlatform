<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Edit, Delete, Search, Refresh } from '@element-plus/icons-vue'
import {
  getProjectList,
  addProject,
  updateProject,
  deleteProjects,
  getVisualInfo,
  getVisualList
} from '@/api/aid'
import type { AidProject, AidVisualSetting } from '@/types'
import { PROJECT_STATUSES, formatDuration, statusTagType } from '@/utils/aid'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<AidProject[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const queryFormRef = ref<FormInstance>()

const queryParams = reactive({
  current: 1,
  size: 10,
  name: '',
  status: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const submitting = ref(false)
/** 表单里已解析出的视觉设定名称，避免为了显示名称再发一次请求 */
const visualNames = ref<Record<string, string>>({})
/** 项目表单里的视觉设定候选项（作用域 PROJECT） */
const visualOptions = ref<AidVisualSetting[]>([])

const emptyProject = (): AidProject => ({
  name: '',
  description: '',
  coverUrl: '',
  status: 'DRAFT',
  targetDuration: undefined,
  remark: ''
})

const formData = reactive<AidProject>(emptyProject())

const rules: FormRules = {
  name: [{ required: true, message: t('aid.projectNameRequired'), trigger: 'blur' }]
}

const statusOptions = computed(() =>
  PROJECT_STATUSES.map((code) => ({ code, label: t(`aid.statusCodes.${code}`) }))
)

/**
 * 把表格里的记录映射成 i18n key 列表。
 *
 * 语言包按 aid.statusCodes.* 组织；缺失时回落原始 code，保证新增枚举不会让整列空白。
 */
const statusLabel = (code?: string) => {
  if (!code) return '-'
  const key = `aid.statusCodes.${code}`
  return t(key) === key ? code : t(key)
}

const getList = async () => {
  try {
    loading.value = true
    const res = await getProjectList(queryParams.current, queryParams.size, {
      name: queryParams.name || undefined,
      status: queryParams.status || undefined
    })
    tableData.value = res.records || []
    total.value = res.total || 0
    await resolveVisualNames()
  } catch (error) {
    console.error(t('aid.projectListFailed'), error)
    ElMessage.error(t('aid.projectListFailed'))
  } finally {
    loading.value = false
  }
}

/**
 * 列表接口不带 visualSettingName 时（老数据或后端裁剪字段），按需补查名称。
 */
const resolveVisualNames = async () => {
  const missing = tableData.value.filter(
    (row) => row.visualSettingId && !row.visualSettingName
  )
  if (missing.length === 0) return
  await Promise.all(
    missing.map(async (row) => {
      try {
        const detail: AidVisualSetting = await getVisualInfo(row.visualSettingId as number)
        visualNames.value[String(row.visualSettingId)] = detail.name || ''
      } catch {
        // 单个查询失败不应让整个列表不可用
        visualNames.value[String(row.visualSettingId)] = ''
      }
    })
  )
}

const visualSettingName = (row: AidProject) => {
  if (row.visualSettingName) return row.visualSettingName
  if (!row.visualSettingId) return '-'
  return visualNames.value[String(row.visualSettingId)] || `#${row.visualSettingId}`
}

/**
 * 加载项目级视觉设定候选。
 *
 * 失败时只提示一次，不阻塞项目列表本身——M1 阶段视觉设定可能还没建。
 */
const loadVisualOptions = async () => {
  try {
    const res = await getVisualList(1, 100, { scope: 'PROJECT' })
    visualOptions.value = res.records || []
  } catch (error) {
    console.error(t('aid.visualListFailed'), error)
  }
}

const handleQuery = () => {
  queryParams.current = 1
  getList()
}

const handleReset = () => {
  queryFormRef.value?.resetFields()
  queryParams.name = ''
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

const handleSelectionChange = (rows: AidProject[]) => {
  selectedIds.value = rows
    .map((row) => row.id)
    .filter((id): id is number => typeof id === 'number')
}

const resetForm = () => {
  Object.assign(formData, emptyProject())
  formRef.value?.clearValidate()
}

const handleAdd = () => {
  resetForm()
  dialogTitle.value = t('aid.addProject')
  dialogVisible.value = true
}

const handleEdit = (row: AidProject) => {
  Object.assign(formData, emptyProject(), row)
  dialogTitle.value = t('aid.editProject')
  dialogVisible.value = true
}

const handleDelete = async (rows: AidProject[]) => {
  const ids = rows.map((row) => row.id).filter((id): id is number => typeof id === 'number')
  if (ids.length === 0) {
    ElMessage.warning(t('common.empty'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('aid.deleteProjectConfirm', { count: ids.length }),
      t('common.tip'),
      { type: 'warning' }
    )
    await deleteProjects(ids)
    ElMessage.success(t('aid.projectDeleted'))
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
  const rows = tableData.value.filter((row) => selectedIds.value.includes(row.id as number))
  handleDelete(rows)
}

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
    submitting.value = true
    const payload: AidProject = { ...formData }
    if (payload.id) {
      await updateProject(payload)
      ElMessage.success(t('aid.projectUpdated'))
    } else {
      await addProject(payload)
      ElMessage.success(t('aid.projectCreated'))
    }
    dialogVisible.value = false
    getList()
  } catch (error) {
    if (error !== false) {
      // element-plus 校验失败会 reject false，这里只处理真正的请求异常
      console.error(t('aid.formInvalid'), error)
    }
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  getList()
  loadVisualOptions()
})
</script>

<template>
  <div class="aid-project">
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
          prop="name"
        >
          <el-input
            v-model="queryParams.name"
            :placeholder="t('aid.keywordPlaceholder')"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item
          :label="t('common.status')"
          prop="status"
        >
          <el-select
            v-model="queryParams.status"
            :placeholder="t('system.statusPlaceholder')"
            clearable
            style="width: 160px"
          >
            <el-option
              v-for="item in statusOptions"
              :key="item.code"
              :label="item.label"
              :value="item.code"
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
          <span class="title">{{ t('route.aidProject') }}</span>
          <div>
            <el-button
              v-auth="['aid:project:add']"
              type="primary"
              :icon="Plus"
              @click="handleAdd"
            >
              {{ t('aid.addProject') }}
            </el-button>
            <el-button
              v-auth="['aid:project:remove']"
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
          prop="name"
          :label="t('aid.projectName')"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          prop="description"
          :label="t('aid.description')"
          min-width="200"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.description || t('aid.noDescription') }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('aid.visualSetting')"
          width="140"
          align="center"
        >
          <template #default="{ row }">
            {{ visualSettingName(row) }}
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
          :label="t('aid.episodeCount')"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            {{ row.episodeCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('aid.assetCount')"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            {{ row.assetCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('aid.targetDuration')"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            {{ formatDuration(row.targetDuration) }}
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
              v-auth="['aid:project:edit']"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-auth="['aid:project:remove']"
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
      width="640px"
      draggable
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="100px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item
              :label="t('aid.projectName')"
              prop="name"
            >
              <el-input
                v-model="formData.name"
                :placeholder="t('aid.projectNameRequired')"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              :label="t('common.status')"
              prop="status"
            >
              <el-select
                v-model="formData.status"
                style="width: 100%"
              >
                <el-option
                  v-for="item in statusOptions"
                  :key="item.code"
                  :label="item.label"
                  :value="item.code"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item
              :label="t('aid.coverUrl')"
              prop="coverUrl"
            >
              <el-input
                v-model="formData.coverUrl"
                placeholder="/api/v1/aid/assets/files/xxx.png"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              :label="t('aid.targetDuration')"
              prop="targetDuration"
            >
              <el-input-number
                v-model="formData.targetDuration"
                :min="0"
                :step="30"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- AC-11：创建项目时即可绑定视觉设定（风格块 + 色盘） -->
        <el-form-item
          :label="t('aid.visualSetting')"
          prop="visualSettingId"
        >
          <el-select
            v-model="formData.visualSettingId"
            :placeholder="t('aid.selectVisualSetting')"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="item in visualOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id ?? 0"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          :label="t('aid.description')"
          prop="description"
        >
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            :placeholder="t('aid.noDescription')"
          />
        </el-form-item>
        <el-form-item
          :label="t('aid.remark')"
          prop="remark"
        >
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
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
.aid-project {
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
}
</style>
