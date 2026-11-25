<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Edit, Delete, Search, Refresh } from '@element-plus/icons-vue'
import { getUserList, deleteUser, changeUserStatus } from '@/api/system/user'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const queryFormRef = ref<FormInstance>()

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  userName: '',
  phonenumber: '',
  status: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const formData = reactive({
  userId: '',
  userName: '',
  nickName: '',
  email: '',
  phonenumber: '',
  sex: '0',
  status: '0',
  deptId: '',
  remark: ''
})

const rules: FormRules = {
  userName: [{ required: true, message: t('system.userNameRequired'), trigger: 'blur' }],
  nickName: [{ required: true, message: t('system.nickNameRequired'), trigger: 'blur' }]
}

const getList = async () => {
  try {
    loading.value = true
    const res: any = await getUserList(queryParams)
    tableData.value = res.rows || []
    total.value = res.total || 0
  } catch (error) {
    console.error(t('system.userListFailed'), error)
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const handlePageChange = (page: number) => {
  queryParams.pageNum = page
  getList()
}

const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  getList()
}

const handleAdd = () => {
  dialogTitle.value = t('system.addUser')
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  dialogTitle.value = t('common.edit')
  Object.assign(formData, row)
  dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(t('system.deleteUserConfirm'), t('common.tip'), { type: 'warning' })
    await deleteUser(row.userId)
    ElMessage.success(t('common.success'))
    getList()
  } catch (error) {
    console.error(t('common.delete'), error)
  }
}

const handleStatusChange = async (row: any) => {
  try {
    await changeUserStatus({ userId: row.userId, status: row.status })
    ElMessage.success(t('common.success'))
  } catch (error) {
    row.status = row.status === '0' ? '1' : '0'
    console.error(t('system.statusUpdateFailed'), error)
  }
}

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
    ElMessage.success(t('common.success'))
    dialogVisible.value = false
    getList()
  } catch (error) {
    console.error(t('system.formInvalid'), error)
  }
}

onMounted(() => {
  getList()
})
</script>

<template>
  <div class="user-management">
    <el-card shadow="never" class="search-card">
      <el-form ref="queryFormRef" :model="queryParams" inline>
        <el-form-item :label="t('system.userName')" prop="userName">
          <el-input v-model="queryParams.userName" :placeholder="t('system.userNamePlaceholder')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="t('system.phonenumber')" prop="phonenumber">
          <el-input v-model="queryParams.phonenumber" :placeholder="t('system.phonePlaceholder')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="t('common.status')" prop="status">
          <el-select v-model="queryParams.status" :placeholder="t('system.statusPlaceholder')" clearable style="width: 150px">
            <el-option :label="t('system.normal')" value="0" />
            <el-option :label="t('system.disabled')" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span class="title">{{ t('route.userManagement') }}</span>
          <div>
            <el-button type="primary" :icon="Plus" @click="handleAdd">{{ t('common.create') }}</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column :label="t('system.userId')" prop="userId" width="100" align="center" />
        <el-table-column :label="t('system.userName')" prop="userName" width="150" align="center" />
        <el-table-column :label="t('system.nickName')" prop="nickName" width="150" align="center" />
        <el-table-column :label="t('system.dept')" prop="deptName" width="150" align="center" />
        <el-table-column :label="t('system.phonenumber')" prop="phonenumber" width="150" align="center" />
        <el-table-column :label="t('common.status')" prop="status" width="100" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.status" active-value="0" inactive-value="1" @change="handleStatusChange(row)" />
          </template>
        </el-table-column>
        <el-table-column :label="t('system.createTime')" prop="createTime" width="180" align="center" />
        <el-table-column :label="t('common.actions')" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" draggable>
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="80px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('system.userName')" prop="userName">
              <el-input v-model="formData.userName" :placeholder="t('system.userNamePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('system.nickName')" prop="nickName">
              <el-input v-model="formData.nickName" :placeholder="t('system.nickNamePlaceholder')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('system.phonenumber')" prop="phonenumber">
              <el-input v-model="formData.phonenumber" :placeholder="t('system.phonePlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('system.email')" prop="email">
              <el-input v-model="formData.email" :placeholder="t('system.emailPlaceholder')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('system.remark')" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="3" :placeholder="t('system.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.user-management {
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
