<script setup lang="ts">
import { useUserStore } from '@/store/modules/user'
import { useI18n } from 'vue-i18n'
import { computed } from 'vue'

const { t } = useI18n()
const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
</script>

<template>
  <div class="profile-container">
    <el-card shadow="never">
      <template #header>
        <span>{{ t('profile.title') }}</span>
      </template>
      <div class="profile-content">
        <div class="profile-info">
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('profile.userName')">{{ userInfo?.userName }}</el-descriptions-item>
            <el-descriptions-item :label="t('profile.nickName')">{{ userInfo?.nickName }}</el-descriptions-item>
            <el-descriptions-item :label="t('profile.deptName')">{{ userInfo?.deptName }}</el-descriptions-item>
            <el-descriptions-item :label="t('profile.phonenumber')">{{ userInfo?.phonenumber }}</el-descriptions-item>
            <el-descriptions-item :label="t('profile.email')">{{ userInfo?.email }}</el-descriptions-item>
            <el-descriptions-item :label="t('profile.roles')">
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
