<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Edit, Delete, Search, Refresh, UploadFilled } from '@element-plus/icons-vue'
import {
  getAssetList,
  getProjectOptions,
  addAsset,
  updateAsset,
  deleteAssets,
  uploadAssetFile,
  checkAssetCode
} from '@/api/aid'
import type { AidAsset, AidAssetQuery, AidProject } from '@/types'
import { ASSET_TYPES, statusTagType, validateImage } from '@/utils/aid'

const props = defineProps<{
  /** 从项目页跳转过来时锁定项目，不允许切换 */
  projectId?: number
}>()

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<AidAsset[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const projectOptions = ref<AidProject[]>([])
const queryFormRef = ref<FormInstance>()

const queryParams = reactive<AidAssetQuery & { current: number; size: number }>({
  current: 1,
  size: 10,
  projectId: undefined,
  assetType: '',
  keyword: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const submitting = ref(false)

const emptyAsset = (): AidAsset => ({
  projectId: queryParams.projectId,
  assetCode: '',
  assetType: 'CHARACTER',
  name: '',
  description: '',
  referenceUrl: '',
  promptTemplate: ''
})

const formData = reactive<AidAsset>(emptyAsset())

const rules: FormRules = {
  projectId: [{ required: true, message: t('aid.selectProject'), trigger: 'change' }],
  assetCode: [
    { required: true, message: t('aid.assetCodeRequired'), trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]+$/,
      message: t('aid.assetCodePlaceholder'),
      trigger: 'blur'
    }
  ],
  name: [{ required: true, message: t('aid.assetNameRequired'), trigger: 'blur' }]
}

// ---------------- 参考图上传（未用 el-upload，自实现以贴合既有请求层） ----------------
const fileInput = ref<HTMLInputElement>()
const uploading = ref(false)
const uploadPercent = ref(0)
let pendingFile: File | null = null

const assetTypeLabel = (code?: string) => {
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
    const res = await getAssetList(queryParams.current, queryParams.size, {
      projectId: queryParams.projectId,
      assetType: queryParams.assetType || undefined,
      keyword: queryParams.keyword || undefined
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error(t('aid.assetListFailed'), error)
    ElMessage.error(t('aid.assetListFailed'))
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
  queryParams.assetType = ''
  queryParams.keyword = ''
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

const handleSelectionChange = (rows: AidAsset[]) => {
  selectedIds.value = rows
    .map((row) => row.id)
    .filter((id): id is number => typeof id === 'number')
}

watch(
  () => queryParams.projectId,
  (value) => {
    if (value) {
      formData.projectId = value
      handleQuery()
    }
  }
)

watch(
  () => props.projectId,
  (value) => {
    if (value) {
      queryParams.projectId = value
      formData.projectId = value
      handleQuery()
    }
  },
  { immediate: true }
)

const resetForm = () => {
  Object.assign(formData, emptyAsset())
  cancelPendingFile()
  formRef.value?.clearValidate()
}

const handleAdd = () => {
  resetForm()
  dialogTitle.value = t('aid.addAsset')
  dialogVisible.value = true
}

const handleEdit = (row: AidAsset) => {
  Object.assign(formData, emptyAsset(), row)
  dialogTitle.value = t('aid.editAsset')
  dialogVisible.value = true
}

const handleDelete = async (rows: AidAsset[]) => {
  const ids = rows.map((row) => row.id).filter((id): id is number => typeof id === 'number')
  if (ids.length === 0) {
    ElMessage.warning(t('common.empty'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('aid.deleteAssetConfirm', { count: ids.length }),
      t('common.tip'),
      { type: 'warning' }
    )
    await deleteAssets(ids)
    ElMessage.success(t('aid.assetDeleted'))
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

/** 选择文件后先做前端校验，通过则暂存，随表单一起提交 */
const handleFileChange = () => {
  const file = fileInput.value?.files?.[0]
  if (!file) return
  const message = validateImage(file)
  if (message) {
    ElMessage.error(message)
    clearFileInput()
    return
  }
  pendingFile = file
}

const clearFileInput = () => {
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

const cancelPendingFile = () => {
  pendingFile = null
  uploadPercent.value = 0
  clearFileInput()
}

/**
 * 提交前上传参考图换取 URL。
 *
 * 已有 referenceUrl 且未换文件时直接沿用，避免编辑一次就重复落盘。
 */
const uploadIfNeeded = async () => {
  if (!pendingFile) return
  uploading.value = true
  uploadPercent.value = 0
  try {
    formData.referenceUrl = await uploadAssetFile(pendingFile, (percent) => {
      uploadPercent.value = percent
    })
    ElMessage.success(t('aid.uploadImageSuccess'))
  } catch (error) {
    console.error(t('aid.uploadImageFailed'), error)
    ElMessage.error(t('aid.uploadImageFailed'))
    throw error
  } finally {
    uploading.value = false
  }
}

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
    submitting.value = true
    await uploadIfNeeded()
    // 编号在项目内唯一：提交前再确认一次，避免依赖后端报错来提示
    const reusable = await checkAssetCode(
      formData.projectId as number,
      formData.assetCode as string,
      formData.id
    )
    if (reusable === false) {
      ElMessage.error(t('aid.codeOccupied'))
      return
    }
    if (formData.id) {
      await updateAsset({ ...formData })
      ElMessage.success(t('aid.assetUpdated'))
    } else {
      await addAsset({ ...formData })
      ElMessage.success(t('aid.assetCreated'))
    }
    dialogVisible.value = false
    getList()
  } catch (error) {
    if (error !== false && error !== undefined) {
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
  <div class="aid-asset">
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
            style="width: 220px"
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
          :label="t('aid.assetType')"
          prop="assetType"
        >
          <el-select
            v-model="queryParams.assetType"
            :placeholder="t('aid.selectAssetType')"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="type in ASSET_TYPES"
              :key="type"
              :label="assetTypeLabel(type)"
              :value="type"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          :label="t('aid.keyword')"
          prop="keyword"
        >
          <el-input
            v-model="queryParams.keyword"
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
          <span class="title">{{ t('route.aidAsset') }}</span>
          <div>
            <el-button
              v-auth="['aid:asset:add']"
              type="primary"
              :icon="Plus"
              :disabled="!queryParams.projectId"
              @click="handleAdd"
            >
              {{ t('aid.addAsset') }}
            </el-button>
            <el-button
              v-auth="['aid:asset:remove']"
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
        :disabled="!queryParams.projectId"
        @selection-change="handleSelectionChange"
      >
        <el-table-column
          type="selection"
          width="50"
          align="center"
        />
        <el-table-column
          prop="assetCode"
          :label="t('aid.assetCode')"
          width="110"
          align="center"
        />
        <el-table-column
          :label="t('aid.referenceImage')"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <el-image
              v-if="row.referenceUrl"
              :src="row.referenceUrl"
              :preview-src-list="[row.referenceUrl]"
              preview-teleported
              fit="cover"
              class="reference-thumb"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="name"
          :label="t('aid.name')"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          :label="t('aid.assetType')"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.assetType)">
              {{ assetTypeLabel(row.assetType) }}
            </el-tag>
          </template>
        </el-table-column>
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
          width="180"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-auth="['aid:asset:edit']"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-auth="['aid:asset:remove']"
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
          <el-col :span="12">
            <el-form-item
              :label="t('aid.assetCode')"
              prop="assetCode"
            >
              <el-input
                v-model="formData.assetCode"
                :placeholder="t('aid.assetCodePlaceholder')"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item
              :label="t('aid.name')"
              prop="name"
            >
              <el-input v-model="formData.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              :label="t('aid.assetType')"
              prop="assetType"
            >
              <el-select
                v-model="formData.assetType"
                style="width: 100%"
              >
                <el-option
                  v-for="type in ASSET_TYPES"
                  :key="type"
                  :label="assetTypeLabel(type)"
                  :value="type"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item
          :label="t('aid.referenceImage')"
          prop="referenceUrl"
        >
          <div class="upload-row">
            <el-input
              v-model="formData.referenceUrl"
              :placeholder="t('aid.coverUrl')"
              :disabled="uploading"
            />
            <el-button
              :icon="UploadFilled"
              :disabled="uploading"
              @click="fileInput?.click()"
            >
              {{ uploading ? t('aid.uploading') : t('aid.uploadImage') }}
            </el-button>
            <input
              ref="fileInput"
              type="file"
              accept=".jpg,.jpeg,.png,.webp,.gif"
              class="hidden-file-input"
              @change="handleFileChange"
            />
          </div>
          <div
            v-if="pendingFile"
            class="upload-hint"
          >
            {{ pendingFile.name }}（{{ (pendingFile.size / 1024).toFixed(1) }} KB）
            <el-link
              type="danger"
              :underline="false"
              @click="cancelPendingFile"
            >
              {{ t('common.delete') }}
            </el-link>
          </div>
          <el-progress
            v-if="uploading && uploadPercent > 0"
            :percentage="uploadPercent"
            :stroke-width="4"
            class="upload-progress"
          />
        </el-form-item>
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
          :label="t('aid.promptTemplate')"
          prop="promptTemplate"
        >
          <el-input
            v-model="formData.promptTemplate"
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
          :loading="submitting || uploading"
          @click="handleSubmit"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.aid-asset {
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

    .reference-thumb {
      width: 48px;
      height: 48px;
      border-radius: 4px;
      background: var(--el-fill-color-light);
    }

    .pagination {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }

  .upload-row {
    display: flex;
    gap: 8px;
    width: 100%;
  }

  .hidden-file-input {
    display: none;
  }

  .upload-hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 4px;
  }

  .upload-progress {
    width: 100%;
    margin-top: 4px;
  }
}
</style>
