<script setup lang="ts">
import { useUserStore } from '@/store/modules/user'
import { computed } from 'vue'

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
</script>

<template>
  <div class="profile-container">
    <el-card shadow="never">
      <template #header>
        <span>个人中心</span>
      </template>
      <div class="profile-content">
        <div class="profile-info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="用户名称">{{ userInfo?.userName }}</el-descriptions-item>
            <el-descriptions-item label="用户昵称">{{ userInfo?.nickName }}</el-descriptions-item>
            <el-descriptions-item label="部门名称">{{ userInfo?.deptName }}</el-descriptions-item>
            <el-descriptions-item label="手机号码">{{ userInfo?.phonenumber }}</el-descriptions-item>
            <el-descriptions-item label="用户邮箱">{{ userInfo?.email }}</el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag v-for="role in userInfo?.roles" :key="role.roleId" size="small" style="margin-right: 4px">
                {{ role.roleName }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.profile-container {
  padding: 20px;
  .profile-content {
    .profile-info {
      max-width: 800px;
    }
  }
}
</style>
