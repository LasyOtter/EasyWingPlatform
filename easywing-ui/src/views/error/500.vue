<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const handleBack = () => {
  router.push('/')
}
</script>

<template>
  <div class="error-container">
    <div class="error-content">
      <div class="error-code">500</div>
      <div class="error-message">抱歉，服务器内部错误</div>
      <el-button type="primary" @click="handleBack">返回首页</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.error-container {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;

  .error-content {
    text-align: center;

    .error-code {
      font-size: 120px;
      font-weight: bold;
      color: #f56c6c;
      line-height: 1;
      margin-bottom: 20px;
    }

    .error-message {
      font-size: 20px;
      color: #606266;
      margin-bottom: 30px;
    }
  }
}
</style>
