<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const tableData = ref([
  { roleId: 1, roleName: '超级管理员', roleKey: 'admin', roleSort: 1, status: '0', createTime: '2024-01-01' },
  { roleId: 2, roleName: '普通用户', roleKey: 'user', roleSort: 2, status: '0', createTime: '2024-01-02' }
])
</script>

<template>
  <div class="role-management">
    <el-card shadow="never">
      <template #header>
        <span>{{ t('route.roleManagement') }}</span>
      </template>
      <el-table :data="tableData" stripe>
        <el-table-column prop="roleId" :label="t('system.roleId')" width="100" align="center" />
        <el-table-column prop="roleName" :label="t('system.roleName')" align="center" />
        <el-table-column prop="roleKey" :label="t('system.roleKey')" align="center" />
        <el-table-column prop="roleSort" :label="t('system.roleSort')" width="100" align="center" />
        <el-table-column prop="status" :label="t('common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? t('system.normal') : t('system.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('system.createTime')" width="180" align="center" />
        <el-table-column :label="t('common.actions')" width="200" align="center">
          <template #default>
            <el-button type="primary" link>{{ t('common.edit') }}</el-button>
            <el-button type="danger" link>{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.role-management {
  padding: 20px;
}
</style>
