<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const tableData = ref([
  { menuId: 1, menuName: '系统管理', menuType: 'M', orderNum: 1, status: '0' },
  { menuId: 2, menuName: '用户管理', menuType: 'C', orderNum: 1, status: '0', perms: 'system:user:list' }
])
</script>

<template>
  <div class="menu-management">
    <el-card shadow="never">
      <template #header>
        <span>{{ t('route.menuManagement') }}</span>
      </template>
      <el-table :data="tableData" stripe>
        <el-table-column prop="menuId" :label="t('system.menuId')" width="100" align="center" />
        <el-table-column prop="menuName" :label="t('system.menuName')" align="center" />
        <el-table-column prop="icon" :label="t('system.icon')" width="100" align="center" />
        <el-table-column prop="orderNum" :label="t('system.orderNum')" width="100" align="center" />
        <el-table-column prop="perms" :label="t('system.perms')" align="center" />
        <el-table-column prop="component" :label="t('system.component')" align="center" />
        <el-table-column prop="status" :label="t('common.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? t('system.normal') : t('system.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200" align="center">
          <template #default>
            <el-button type="primary" link>{{ t('common.create') }}</el-button>
            <el-button type="primary" link>{{ t('common.edit') }}</el-button>
            <el-button type="danger" link>{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.menu-management {
  padding: 20px;
}
</style>
