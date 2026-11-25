<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/store/modules/user'

const { t } = useI18n()
const userStore = useUserStore()

const userInfo = computed(() => userStore.userInfo)
const nickName = computed(() => userInfo.value?.nickName || userInfo.value?.userName || 'Admin')

// 统计数据：titleKey 指向语言包词条
const statisticsData = ref([
  {
    titleKey: 'dashboard.visits',
    value: '10,284',
    increase: '+12.5%',
    icon: 'View',
    color: '#409eff'
  },
  {
    titleKey: 'dashboard.users',
    value: '8,542',
    increase: '+8.2%',
    icon: 'User',
    color: '#67c23a'
  },
  {
    titleKey: 'dashboard.orders',
    value: '3,215',
    increase: '+5.3%',
    icon: 'ShoppingCart',
    color: '#e6a23c'
  },
  {
    titleKey: 'dashboard.sales',
    value: '¥125,680',
    increase: '+15.8%',
    icon: 'Money',
    color: '#f56c6c'
  }
])

const statTitle = (item: { titleKey: string }) => t(item.titleKey)

onMounted(() => {
  // 获取仪表盘数据
})
</script>

<template>
  <div class="dashboard">
    <!-- 欢迎信息 -->
    <el-row :gutter="20" class="welcome-row">
      <el-col :span="24">
        <div class="welcome-card">
          <div class="welcome-content">
            <h2>{{ t('dashboard.welcome', { name: nickName }) }}</h2>
            <p>{{ t('dashboard.welcomeSubtitle') }}</p>
          </div>
          <div class="welcome-illustration">
            <img src="@/assets/images/logo.svg" alt="welcome" />
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="statistics-row">
      <el-col v-for="(item, index) in statisticsData" :key="index" :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-title">{{ statTitle(item) }}</div>
              <div class="stat-value">{{ item.value }}</div>
              <div class="stat-increase">
                <span :style="{ color: item.color }">{{ item.increase }}</span>
                <span class="text-gray">{{ t('dashboard.sinceLastMonth') }}</span>
              </div>
            </div>
            <div class="stat-icon" :style="{ backgroundColor: item.color + '15' }">
              <el-icon :size="32" :style="{ color: item.color }">
                <component :is="item.icon" />
              </el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <el-row :gutter="20" class="quick-entry-row">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ t('dashboard.quickEntry') }}</span>
            </div>
          </template>
          <div class="quick-entry-grid">
            <div class="quick-entry-item">
              <el-icon :size="32" color="#409eff"><User /></el-icon>
              <span>{{ t('route.userManagement') }}</span>
            </div>
            <div class="quick-entry-item">
              <el-icon :size="32" color="#67c23a"><Role /></el-icon>
              <span>{{ t('route.roleManagement') }}</span>
            </div>
            <div class="quick-entry-item">
              <el-icon :size="32" color="#e6a23c"><Menu /></el-icon>
              <span>{{ t('route.menuManagement') }}</span>
            </div>
            <div class="quick-entry-item">
              <el-icon :size="32" color="#f56c6c"><Setting /></el-icon>
              <span>{{ t('header.settings') }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style lang="scss" scoped>
.dashboard {
  padding: 20px;

  .welcome-row {
    margin-bottom: 20px;

    .welcome-card {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-radius: 12px;
      padding: 30px;
      color: #fff;
      display: flex;
      justify-content: space-between;
      align-items: center;

      .welcome-content {
        h2 {
          font-size: 28px;
          margin: 0 0 10px 0;
        }

        p {
          font-size: 14px;
          margin: 0;
          opacity: 0.9;
        }
      }

      .welcome-illustration {
        img {
          width: 180px;
          height: auto;
        }
      }
    }
  }

  .statistics-row {
    margin-bottom: 20px;

    .stat-card {
      .stat-content {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .stat-info {
          .stat-title {
            font-size: 14px;
            color: #909399;
            margin-bottom: 8px;
          }

          .stat-value {
            font-size: 28px;
            font-weight: 600;
            color: #303133;
            margin-bottom: 8px;
          }

          .stat-increase {
            font-size: 12px;

            .text-gray {
              color: #909399;
              margin-left: 8px;
            }
          }
        }

        .stat-icon {
          width: 60px;
          height: 60px;
          border-radius: 12px;
          display: flex;
          align-items: center;
          justify-content: center;
        }
      }
    }
  }

  .quick-entry-row {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      span {
        font-size: 16px;
        font-weight: 600;
      }
    }

    .quick-entry-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 20px;

      .quick-entry-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        padding: 20px;
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.3s;

        &:hover {
          background: #f5f7fa;
        }

        span {
          margin-top: 10px;
          font-size: 14px;
          color: #606266;
        }
      }
    }
  }
}
</style>
