<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([
  { roleId: 1, roleName: '超级管理员', roleKey: 'admin', roleSort: 1, status: '0', createTime: '2024-01-01' },
  { roleId: 2, roleName: '普通用户', roleKey: 'user', roleSort: 2, status: '0', createTime: '2024-01-02' }
])
</script>

<template>
  <div class="role-management">
    <el-card shadow="never">
      <template #header>
        <span>角色管理</span>
      </template>
      <el-table :data="tableData" stripe>
        <el-table-column prop="roleId" label="角色编号" width="100" align="center" />
        <el-table-column prop="roleName" label="角色名称" align="center" />
        <el-table-column prop="roleKey" label="权限字符" align="center" />
        <el-table-column prop="roleSort" label="显示顺序" width="100" align="center" />
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
            <el-button type="danger" link>删除</el-button>
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
