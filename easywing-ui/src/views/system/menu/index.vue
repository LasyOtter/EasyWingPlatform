<script setup lang="ts">
import { ref } from 'vue'

const loading = ref(false)
const tableData = ref([
  { menuId: 1, menuName: '系统管理', menuType: 'M', orderNum: 1, status: '0' },
  { menuId: 2, menuName: '用户管理', menuType: 'C', orderNum: 1, status: '0', perms: 'system:user:list' }
])
</script>

<template>
  <div class="menu-management">
    <el-card shadow="never">
      <template #header>
        <span>菜单管理</span>
      </template>
      <el-table :data="tableData" stripe>
        <el-table-column prop="menuId" label="菜单编号" width="100" align="center" />
        <el-table-column prop="menuName" label="菜单名称" align="center" />
        <el-table-column prop="icon" label="图标" width="100" align="center" />
        <el-table-column prop="orderNum" label="显示排序" width="100" align="center" />
        <el-table-column prop="perms" label="权限标识" align="center" />
        <el-table-column prop="component" label="组件路径" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default>
            <el-button type="primary" link>新增</el-button>
            <el-button type="primary" link>编辑</el-button>
            <el-button type="danger" link>删除</el-button>
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
