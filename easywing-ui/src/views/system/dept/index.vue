<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const tableData = ref([
  { deptId: 1, deptName: '总公司', parentId: 0, leader: '管理员', status: '0' },
  { deptId: 2, deptName: '研发部', parentId: 1, leader: '张三', status: '0' },
  { deptId: 3, deptName: '市场部', parentId: 1, leader: '李四', status: '0' }
])
</script>

<template>
  <div class="dept-management">
    <el-card shadow="never">
      <template #header>
        <span>{{ t('route.deptManagement') }}</span>
      </template>
      <el-table :data="tableData" stripe>
        <el-table-column prop="deptId" :label="t('system.deptId')" width="100" align="center" />
        <el-table-column prop="deptName" :label="t('system.deptName')" align="center" />
        <el-table-column prop="leader" :label="t('system.leader')" width="120" align="center" />
        <el-table-column prop="phone" :label="t('system.phone')" width="150" align="center" />
        <el-table-column prop="email" :label="t('system.email')" width="180" align="center" />
        <el-table-column prop="status" :label="t('common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? t('system.normal') : t('system.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
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
.dept-management {
  padding: 20px;
}
</style>
