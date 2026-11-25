<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const tableData = ref([
  { dictId: 1, dictName: '用户性别', dictType: 'sys_user_sex', status: '0', createTime: '2024-01-01' },
  { dictId: 2, dictName: '菜单状态', dictType: 'sys_show_hide', status: '0', createTime: '2024-01-02' }
])
</script>

<template>
  <div class="dict-management">
    <el-card shadow="never">
      <template #header>
        <span>{{ t('route.dictManagement') }}</span>
      </template>
      <el-table :data="tableData" stripe>
        <el-table-column prop="dictId" :label="t('system.dictId')" width="100" align="center" />
        <el-table-column prop="dictName" :label="t('system.dictName')" align="center" />
        <el-table-column prop="dictType" :label="t('system.dictType')" align="center" />
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
            <el-button type="primary" link>{{ t('system.data') }}</el-button>
            <el-button type="danger" link>{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.dict-management {
  padding: 20px;
}
</style>
