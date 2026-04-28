<script setup lang="ts">
import { ref } from 'vue'

const loading = ref(false)
const tableData = ref([
  { dictId: 1, dictName: '用户性别', dictType: 'sys_user_sex', status: '0', createTime: '2024-01-01' },
  { dictId: 2, dictName: '菜单状态', dictType: 'sys_show_hide', status: '0', createTime: '2024-01-02' }
])
</script>

<template>
  <div class="dict-management">
    <el-card shadow="never">
      <template #header>
        <span>字典管理</span>
      </template>
      <el-table :data="tableData" stripe>
        <el-table-column prop="dictId" label="字典编号" width="100" align="center" />
        <el-table-column prop="dictName" label="字典名称" align="center" />
        <el-table-column prop="dictType" label="字典类型" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center" />
        <el-table-column label="操作" width="200" align="center">
          <template #default>
            <el-button type="primary" link>编辑</el-button>
            <el-button type="primary" link>数据</el-button>
            <el-button type="danger" link>删除</el-button>
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
