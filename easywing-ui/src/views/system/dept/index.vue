<script setup lang="ts">
import { ref } from 'vue'

const loading = ref(false)
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
        <span>部门管理</span>
      </template>
      <el-table :data="tableData" stripe>
        <el-table-column prop="deptId" label="部门编号" width="100" align="center" />
        <el-table-column prop="deptName" label="部门名称" align="center" />
        <el-table-column prop="leader" label="负责人" width="120" align="center" />
        <el-table-column prop="phone" label="联系电话" width="150" align="center" />
        <el-table-column prop="email" label="邮箱" width="180" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
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
.dept-management {
  padding: 20px;
}
</style>
