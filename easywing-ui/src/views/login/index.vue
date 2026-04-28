<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { getCodeApi } from '@/api/system/user'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const captchaLoading = ref(false)
const codeUrl = ref('')
const uuid = ref('')

const loginForm = reactive({
  username: 'admin',
  password: 'admin123',
  code: '',
  uuid: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

// 获取验证码
const getCaptcha = async () => {
  try {
    captchaLoading.value = true
    const res: any = await getCodeApi()
    codeUrl.value = res.img
    uuid.value = res.uuid
    loginForm.uuid = res.uuid
  } catch (error) {
    console.error('获取验证码失败:', error)
  } finally {
    captchaLoading.value = false
  }
}

// 登录
const handleLogin = async (formEl: FormInstance | undefined) => {
  if (!formEl) return

  await formEl.validate(async (valid) => {
    if (valid) {
      try {
        loading.value = true
        await userStore.login(loginForm)
        ElMessage.success('登录成功')
        const redirect = (route.query.redirect as string) || '/'
        router.push(redirect)
      } catch (error) {
        ElMessage.error('登录失败')
        getCaptcha()
      } finally {
        loading.value = false
      }
    }
  })
}

// 重置表单
const handleReset = () => {
  loginFormRef.value?.resetFields()
}

onMounted(() => {
  getCaptcha()
})
</script>

<template>
  <div class="login-container">
    <div class="login-box">
      <!-- 左侧装饰 -->
      <div class="login-left">
        <div class="login-intro">
          <h1>EasyWing Platform</h1>
          <p>企业级后台管理系统</p>
          <ul>
            <li>微服务架构</li>
            <li>Spring Cloud Alibaba</li>
            <li>Spring Boot 3.x</li>
            <li>Vue 3.0</li>
          </ul>
        </div>
      </div>

      <!-- 右侧表单 -->
      <div class="login-right">
        <div class="login-form">
          <div class="title">欢迎登录</div>

          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="rules"
            class="form"
            size="large"
          >
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="用户名"
                prefix-icon="User"
                clearable
              />
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="密码"
                prefix-icon="Lock"
                show-password
                clearable
                @keyup.enter="handleLogin(loginFormRef)"
              />
            </el-form-item>

            <el-form-item prop="code">
              <el-input
                v-model="loginForm.code"
                placeholder="验证码"
                prefix-icon="CircleCheck"
                style="width: 60%"
                clearable
                @keyup.enter="handleLogin(loginFormRef)"
              />
              <div class="captcha">
                <el-image
                  v-if="codeUrl"
                  :src="codeUrl"
                  fit="contain"
                  @click="getCaptcha"
                >
                  <template #error>
                    <div class="image-slot">
                      <el-icon><RefreshRight /></el-icon>
                    </div>
                  </template>
                </el-image>
              </div>
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                :loading="loading"
                class="login-btn"
                @click="handleLogin(loginFormRef)"
              >
                {{ loading ? '登录中...' : '登 录' }}
              </el-button>
            </el-form-item>
          </el-form>

          <div class="login-tip">
            <span>默认账号：admin / admin123</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.login-container {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-box {
  width: 900px;
  height: 540px;
  background: #fff;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  display: flex;
}

.login-left {
  width: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;

  .login-intro {
    padding: 40px;

    h1 {
      font-size: 36px;
      margin-bottom: 20px;
    }

    p {
      font-size: 18px;
      margin-bottom: 30px;
      opacity: 0.9;
    }

    ul {
      list-style: none;
      padding: 0;
      margin: 0;

      li {
        padding: 8px 0;
        font-size: 14px;
        opacity: 0.8;

        &::before {
          content: '✓';
          margin-right: 10px;
          color: #67c23a;
        }
      }
    }
  }
}

.login-right {
  width: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;

  .login-form {
    width: 100%;
    max-width: 320px;

    .title {
      font-size: 28px;
      font-weight: 600;
      color: #333;
      margin-bottom: 30px;
      text-align: center;
    }

    .form {
      .captcha {
        width: 38%;
        height: 40px;
        margin-left: 10px;
        cursor: pointer;

        :deep(.el-image) {
          width: 100%;
          height: 100%;
        }

        .image-slot {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 100%;
          height: 100%;
          background: #f5f7fa;
          color: #909399;
          font-size: 20px;
        }
      }

      .login-btn {
        width: 100%;
        height: 45px;
        font-size: 16px;
      }
    }

    .login-tip {
      text-align: center;
      margin-top: 20px;
      font-size: 12px;
      color: #999;
    }
  }
}
</style>
