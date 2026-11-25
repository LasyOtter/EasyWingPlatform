<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Plus,
  Edit,
  Delete,
  Search,
  Refresh,
  Download,
  UploadFilled,
  Close
} from '@element-plus/icons-vue'
import {
  getVisualList,
  addVisual,
  updateVisual,
  deleteVisuals,
  exportVisual,
  importVisual
} from '@/api/aid'
import type { AidVisualSetting, AidVisualTemplate } from '@/types'
import { SETTING_SCOPES } from '@/utils/aid'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<AidVisualSetting[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const queryFormRef = ref<FormInstance>()

const queryParams = reactive({
  current: 1,
  size: 10,
  scope: 'PROJECT',
  name: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const submitting = ref(false)

// ---------------- 导入模板（AC-11） ----------------
const importVisible = ref(false)
const importFormRef = ref<FormInstance>()
const importFileInput = ref<HTMLInputElement>()
const importSubmitting = ref(false)
const parsedTemplate = ref<AidVisualTemplate | null>(null)
const parsedFileName = ref('')

const importForm = reactive({
  name: '',
  overwrite: false
})

const importRules: FormRules = {
  name: [{ required: true, message: t('aid.visualNameRequired'), trigger: 'blur' }]
}

const emptySetting = (): AidVisualSetting => ({
  name: '',
  scope: 'PROJECT',
  styleDescription: '',
  colorPalette: [],
  subStyles: [],
  constraintsList: [],
  negativeWords: [],
  aspectRatio: '9:16',
  tailFrameSubject: '',
  tailFrameBackground: '',
  tailFrameLight: '',
  tailFrameComposition: '',
  tailFrameMood: '',
  tailFrameColors: []
})

const formData = reactive<AidVisualSetting>(emptySetting())

const rules: FormRules = {
  name: [{ required: true, message: t('aid.visualNameRequired'), trigger: 'blur' }],
  scope: [{ required: true, message: t('aid.scope'), trigger: 'change' }],
  aspectRatio: [
    {
      pattern: /^(\d{1,2}:\d{1,2})?$/,
      message: t('aid.aspectRatioPlaceholder'),
      trigger: 'blur'
    }
  ]
}

/** 从「色名 #HEX」里取 HEX，用于列表色块展示 */
const extractHex = (color: string) => {
  const matched = String(color).match(/#[0-9a-fA-F]{3,8}/)
  return matched ? matched[0] : ''
}

const scopeLabel = (code?: string) => {
  if (!code) return '-'
  const key = `aid.statusCodes.${code}`
  return t(key) === key ? code : t(key)
}

/**
 * 色盘条目：中文色名 + 十六进制，逐字引用到提示词。
 *
 * 用对象数组而非纯字符串，才能在失焦时校验格式。
 */
const paletteDraft = ref<{ name: string; hex: string }[]>([])

/** 标签族编辑草稿（子风格 / 约束 / 禁用词 / 尾帧颜色） */
const listDrafts = reactive<Record<string, string[]>>({
  subStyles: [],
  constraintsList: [],
  negativeWords: [],
  tailFrameColors: []
})

const LIST_KEYS = ['subStyles', 'constraintsList', 'negativeWords', 'tailFrameColors'] as const

type ListKey = (typeof LIST_KEYS)[number]

const listFields = computed(() =>
  LIST_KEYS.map((key) => ({ key, label: t(LIST_LABEL_KEYS[key]) }))
)

const listInputs = reactive<Record<ListKey, string>>({
  subStyles: '',
  constraintsList: '',
  negativeWords: '',
  tailFrameColors: ''
})

const listInputVisible = reactive<Record<ListKey, boolean>>({
  subStyles: false,
  constraintsList: false,
  negativeWords: false,
  tailFrameColors: false
})

/** 标签族在语言包里的键名（随 t() 结果变化，仅用于渲染） */
const LIST_LABEL_KEYS: Record<ListKey, string> = {
  subStyles: 'aid.subStyles',
  constraintsList: 'aid.constraintsList',
  negativeWords: 'aid.negativeWords',
  tailFrameColors: 'aid.tailFrameColors'
}

const showListInput = (key: ListKey) => {
  listInputs[key] = ''
  listInputVisible[key] = true
}

/** 回车或失焦时把输入落成一个标签；空内容直接收起输入框 */
const confirmListItem = (key: ListKey) => {
  const value = listInputs[key].trim()
  if (value) {
    if (!listDrafts[key].includes(value)) {
      listDrafts[key].push(value)
    }
  }
  listInputs[key] = ''
  listInputVisible[key] = false
}

const getList = async () => {
  try {
    loading.value = true
    const res = await getVisualList(queryParams.current, queryParams.size, {
      scope: queryParams.scope || undefined,
      name: queryParams.name || undefined
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error(t('aid.visualListFailed'), error)
    ElMessage.error(t('aid.visualListFailed'))
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
  queryParams.name = ''
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

const handleSelectionChange = (rows: AidVisualSetting[]) => {
  selectedIds.value = rows
    .map((row) => row.id)
    .filter((id): id is number => typeof id === 'number')
}

/** 实体 → 表单草稿 */
const applyToDrafts = (setting: AidVisualSetting) => {
  paletteDraft.value = (setting.colorPalette || []).map((item) => {
    const matched = String(item).match(/^([^#]+?)\s*(#[0-9a-fA-F]{3,8})?$/)
    return {
      name: (matched?.[1] ?? String(item)).trim(),
      hex: (matched?.[2] ?? '').trim()
    }
  })
  LIST_KEYS.forEach((key) => {
    listDrafts[key] = [
      ...((setting[key as keyof AidVisualSetting] as string[] | undefined) || [])
    ]
  })
}

/** 表单草稿 → 实体；色盘拼回「色名 #HEX」形式，空行丢弃 */
const collectFromDrafts = () => {
  formData.colorPalette = paletteDraft.value
    .map((item) => {
      const name = item.name.trim()
      const hex = item.hex.trim()
      return hex ? `${name} ${hex}` : name
    })
    .filter((item) => item.trim().length > 0)
  LIST_KEYS.forEach((key) => {
    (formData as Record<string, unknown>)[key] = listDrafts[key].filter(
      (item) => item.trim().length > 0
    )
  })
}

const resetForm = () => {
  Object.assign(formData, emptySetting())
  paletteDraft.value = []
  LIST_KEYS.forEach((key) => {
    listDrafts[key] = []
    listInputs[key] = ''
    listInputVisible[key] = false
  })
  formRef.value?.clearValidate()
}

const handleAdd = () => {
  resetForm()
  dialogTitle.value = t('aid.addVisualSetting')
  dialogVisible.value = true
}

const handleEdit = (row: AidVisualSetting) => {
  Object.assign(formData, emptySetting(), row)
  applyToDrafts(row)
  dialogTitle.value = t('aid.editVisualSetting')
  dialogVisible.value = true
}

const handleDelete = async (rows: AidVisualSetting[]) => {
  const ids = rows.map((row) => row.id).filter((id): id is number => typeof id === 'number')
  if (ids.length === 0) {
    ElMessage.warning(t('common.empty'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('aid.deleteVisualConfirm', { count: ids.length }),
      t('common.tip'),
      { type: 'warning' }
    )
    await deleteVisuals(ids)
    ElMessage.success(t('aid.visualDeleted'))
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
    collectFromDrafts()
    submitting.value = true
    if (formData.id) {
      await updateVisual({ ...formData })
      ElMessage.success(t('aid.visualUpdated'))
    } else {
      await addVisual({ ...formData })
      ElMessage.success(t('aid.visualCreated'))
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

// ---------------- 导出（AC-11） ----------------
const exportingId = ref<number | null>(null)

const handleExport = async (row: AidVisualSetting) => {
  if (!row.id) return
  exportingId.value = row.id
  try {
    const template = await exportVisual(row.id)
    // 后端返回结构化模板对象，直接序列化下载，避免手动拼字段漏项
    const blob = new Blob([JSON.stringify(template, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${row.name || 'visual-setting'}.json`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success(t('aid.exportSuccess'))
  } catch (error) {
    console.error(t('aid.exportFailed'), error)
    ElMessage.error(t('aid.exportFailed'))
  } finally {
    exportingId.value = null
  }
}

// ---------------- 导入（AC-11） ----------------
const openImport = () => {
  parsedTemplate.value = null
  parsedFileName.value = ''
  importForm.name = ''
  importForm.overwrite = false
  if (importFileInput.value) {
    importFileInput.value.value = ''
  }
  importVisible.value = true
}

const handleImportFileChange = async () => {
  const file = importFileInput.value?.files?.[0]
  if (!file) return
  try {
    const text = await file.text()
    const json = JSON.parse(text) as AidVisualTemplate
    if (!json || typeof json !== 'object' || Array.isArray(json)) {
      throw new Error('not an object')
    }
    if (!json.name) {
      ElMessage.warning(t('aid.importTemplate'))
    }
    parsedTemplate.value = json
    parsedFileName.value = file.name
    // 文件名作为默认名称的兜底，用户可改
    if (!importForm.name) {
      importForm.name = String(json.name ?? '')
    }
  } catch {
    ElMessage.error(t('aid.importFailed'))
    parsedTemplate.value = null
    if (importFileInput.value) {
      importFileInput.value.value = ''
    }
  }
}

const handleImport = async () => {
  if (!parsedTemplate.value) {
    ElMessage.warning(t('aid.importJsonFile'))
    return
  }
  try {
    await importFormRef.value?.validate()
    importSubmitting.value = true
    // 未改名且后端也不接受空名时会自行回落到模板内 name
    await importVisual({
      content: parsedTemplate.value,
      name: importForm.name || undefined,
      overwrite: importForm.overwrite
    })
    ElMessage.success(t('aid.importSuccess'))
    importVisible.value = false
    getList()
  } catch (error) {
    if (error !== false) {
      console.error(t('aid.importFailed'), error)
    }
  } finally {
    importSubmitting.value = false
  }
}
</script>

<template>
  <div class="aid-visual">
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
          :label="t('aid.scope')"
          prop="scope"
        >
          <el-select
            v-model="queryParams.scope"
            style="width: 140px"
            @change="handleQuery"
          >
            <el-option
              :label="t('system.allProviders')"
              value=""
            />
            <el-option
              v-for="scope in SETTING_SCOPES"
              :key="scope"
              :label="scopeLabel(scope)"
              :value="scope"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          :label="t('aid.name')"
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
          <span class="title">{{ t('route.aidVisualSetting') }}</span>
          <div>
            <el-button
              v-auth="['aid:visual:add']"
              :icon="UploadFilled"
              @click="openImport"
            >
              {{ t('aid.importTemplate') }}
            </el-button>
            <el-button
              v-auth="['aid:visual:add']"
              type="primary"
              :icon="Plus"
              @click="handleAdd"
            >
              {{ t('aid.addVisualSetting') }}
            </el-button>
            <el-button
              v-auth="['aid:visual:remove']"
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
          :label="t('aid.name')"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column
          :label="t('aid.scope')"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-tag :type="row.scope === 'TEMPLATE' ? 'success' : 'primary'">
              {{ scopeLabel(row.scope) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="styleDescription"
          :label="t('aid.styleDescription')"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.styleDescription || t('aid.noDescription') }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('aid.colorPalette')"
          min-width="200"
        >
          <template #default="{ row }">
            <div
              v-if="row.colorPalette?.length"
              class="palette-cell"
            >
              <span
                v-for="(color, index) in row.colorPalette.slice(0, 4)"
                :key="index"
                class="palette-swatch"
              >
                <span
                  class="swatch-chip"
                  :style="{ background: extractHex(color) || '#eee' }"
                ></span>
                {{ color }}
              </span>
              <span
                v-if="row.colorPalette.length > 4"
                class="palette-more"
              >
                +{{ row.colorPalette.length - 4 }}
              </span>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="aspectRatio"
          :label="t('aid.aspectRatio')"
          width="90"
          align="center"
        />
        <el-table-column
          prop="version"
          :label="t('aid.version')"
          width="80"
          align="center"
        />
        <el-table-column
          prop="updateTime"
          :label="t('aid.updateTime')"
          width="170"
          align="center"
        />
        <el-table-column
          :label="t('common.actions')"
          width="260"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-auth="['aid:visual:export']"
              type="primary"
              link
              :icon="Download"
              :loading="exportingId === row.id"
              @click="handleExport(row)"
            >
              {{ t('aid.exportTemplate') }}
            </el-button>
            <el-button
              v-auth="['aid:visual:edit']"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-auth="['aid:visual:remove']"
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
      width="720px"
      draggable
      top="6vh"
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
              :label="t('aid.name')"
              prop="name"
            >
              <el-input
                v-model="formData.name"
                :placeholder="t('aid.visualNameRequired')"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              :label="t('aid.scope')"
              prop="scope"
            >
              <el-select
                v-model="formData.scope"
                style="width: 100%"
              >
                <el-option
                  v-for="scope in SETTING_SCOPES"
                  :key="scope"
                  :label="scopeLabel(scope)"
                  :value="scope"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item
          :label="t('aid.styleDescription')"
          prop="styleDescription"
        >
          <el-input
            v-model="formData.styleDescription"
            type="textarea"
            :rows="3"
            :placeholder="t('aid.styleDescription')"
          />
        </el-form-item>

        <!-- 色盘编辑器：每个条目一个色名 + 取色器 -->
        <el-form-item :label="t('aid.colorPalette')">
          <div class="palette-editor">
            <div
              v-for="(item, index) in paletteDraft"
              :key="index"
              class="palette-row"
            >
              <el-color-picker v-model="item.hex" />
              <el-input
                v-model="item.name"
                :placeholder="t('aid.colorPalettePlaceholder')"
              />
              <el-button
                :icon="Close"
                circle
                @click="paletteDraft.splice(index, 1)"
              />
            </div>
            <el-button
              :icon="Plus"
              @click="paletteDraft.push({ name: '', hex: '' })"
            >
              {{ t('common.create') }}
            </el-button>
          </div>
        </el-form-item>

        <el-row :gutter="20">
          <el-col
            v-for="field in listFields.slice(0, 2)"
            :key="field.key"
            :span="12"
          >
            <el-form-item :label="field.label">
              <div class="tag-editor">
                <el-tag
                  v-for="(item, index) in listDrafts[field.key]"
                  :key="`${index}-${item}`"
                  closable
                  class="tag-item"
                  @close="listDrafts[field.key].splice(index, 1)"
                >
                  {{ item }}
                </el-tag>
                <el-input
                  v-if="listInputVisible[field.key]"
                  v-model="listInputs[field.key]"
                  size="small"
                  class="tag-input"
                  @keyup.enter="confirmListItem(field.key)"
                  @blur="confirmListItem(field.key)"
                />
                <el-button
                  v-else
                  size="small"
                  class="tag-add"
                  @click="showListInput(field.key)"
                >
                  + {{ field.label }}
                </el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">
          {{ t('aid.tailFrame') }}
        </el-divider>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item
              :label="t('aid.aspectRatio')"
              prop="aspectRatio"
            >
              <el-input
                v-model="formData.aspectRatio"
                :placeholder="t('aid.aspectRatioPlaceholder')"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item
              :label="t('aid.tailFrameSubject')"
              prop="tailFrameSubject"
            >
              <el-input v-model="formData.tailFrameSubject" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item
              :label="t('aid.tailFrameBackground')"
              prop="tailFrameBackground"
            >
              <el-input v-model="formData.tailFrameBackground" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item
              :label="t('aid.tailFrameLight')"
              prop="tailFrameLight"
            >
              <el-input v-model="formData.tailFrameLight" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item
              :label="t('aid.tailFrameComposition')"
              prop="tailFrameComposition"
            >
              <el-input v-model="formData.tailFrameComposition" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item
              :label="t('aid.tailFrameMood')"
              prop="tailFrameMood"
            >
              <el-input v-model="formData.tailFrameMood" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('aid.tailFrameColors')">
          <div class="tag-editor">
            <el-tag
              v-for="(item, index) in listDrafts.tailFrameColors"
              :key="`color-${index}-${item}`"
              closable
              class="tag-item"
              @close="listDrafts.tailFrameColors.splice(index, 1)"
            >
              {{ item }}
            </el-tag>
            <el-input
              v-if="listInputVisible.tailFrameColors"
              v-model="listInputs.tailFrameColors"
              size="small"
              class="tag-input"
              @keyup.enter="confirmListItem('tailFrameColors')"
              @blur="confirmListItem('tailFrameColors')"
            />
            <el-button
              v-else
              size="small"
              class="tag-add"
              @click="showListInput('tailFrameColors')"
            >
              + {{ t('common.create') }}
            </el-button>
          </div>
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

    <el-dialog
      v-model="importVisible"
      :title="t('aid.importTemplateTitle')"
      width="520px"
      draggable
    >
      <el-form
        ref="importFormRef"
        :model="importForm"
        :rules="importRules"
        label-width="110px"
      >
        <el-form-item :label="t('aid.importJsonFile')">
          <div class="import-row">
            <el-input
              :value="parsedFileName"
              :placeholder="t('aid.importJsonFile')"
              readonly
            />
            <el-button
              :icon="UploadFilled"
              @click="importFileInput?.click()"
            >
              {{ t('aid.importJsonFile') }}
            </el-button>
            <input
              ref="importFileInput"
              type="file"
              accept=".json,application/json"
              class="hidden-file-input"
              @change="handleImportFileChange"
            />
          </div>
          <div
            v-if="parsedTemplate"
            class="import-preview"
          >
            <pre>{{ JSON.stringify(parsedTemplate, null, 2) }}</pre>
          </div>
        </el-form-item>
        <el-form-item
          :label="t('aid.importName')"
          prop="name"
        >
          <el-input
            v-model="importForm.name"
            :placeholder="t('aid.importNamePlaceholder')"
          />
        </el-form-item>
        <el-form-item
          label=" "
          prop="overwrite"
        >
          <el-checkbox v-model="importForm.overwrite">
            {{ t('aid.overwriteSameName') }}
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="importSubmitting"
          @click="handleImport"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.aid-visual {
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

    .palette-cell {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      align-items: center;
    }

    .palette-swatch {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
    }

    .swatch-chip {
      width: 12px;
      height: 12px;
      border-radius: 2px;
      border: 1px solid var(--el-border-color-lighter);
    }

    .palette-more {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }

    .pagination {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }

  .palette-editor {
    width: 100%;

    .palette-row {
      display: flex;
      gap: 8px;
      align-items: center;
      margin-bottom: 8px;
    }
  }

  .tag-editor {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-items: center;
    width: 100%;

    .tag-item {
      max-width: 100%;
    }

    .tag-input {
      width: 140px;
    }

    .tag-add {
      border-style: dashed;
    }
  }

  .hidden-file-input {
    display: none;
  }

  .import-row {
    display: flex;
    gap: 8px;
    width: 100%;
  }

  .import-preview {
    max-height: 240px;
    margin-top: 8px;
    overflow: auto;
    background: var(--el-fill-color-light);
    border-radius: 4px;

    pre {
      margin: 0;
      padding: 8px;
      font-size: 12px;
    }
  }
}
</style>
