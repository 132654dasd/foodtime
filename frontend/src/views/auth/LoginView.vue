<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  rememberMe: false,
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function fillDemo(username: string, password: string): void {
  form.username = username
  form.password = password
}

async function submit(): Promise<void> {
  const instance = formRef.value
  if (!instance) {
    return
  }
  const valid = await instance.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    await userStore.login({
      username: form.username.trim(),
      password: form.password,
      rememberMe: form.rememberMe,
    })
    ElMessage.success(`欢迎回来，${userStore.displayName}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch {
    /* 用户名/密码错误等提示由 axios 拦截器统一给出 */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page auth-page">
    <div class="auth-card surface">
      <h1 class="auth-title">登录食光</h1>
      <p class="muted">配餐、收藏、发帖都需要先登录</p>

      <el-form
        ref="formRef"
        class="auth-form"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            size="large"
            placeholder="3-20 位字母 / 数字 / 下划线"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            placeholder="6-32 位"
            :prefix-icon="Lock"
            @keyup.enter="submit"
          />
        </el-form-item>

        <div class="auth-row">
          <el-checkbox v-model="form.rememberMe">记住我（7 天）</el-checkbox>
          <RouterLink class="auth-link" to="/register">还没有账号？去注册</RouterLink>
        </div>

        <el-button
          class="auth-submit"
          type="primary"
          size="large"
          :loading="loading"
          @click="submit"
        >
          登录
        </el-button>
      </el-form>

      <div class="auth-demo">
        <span class="muted">演示账号：</span>
        <el-button text size="small" @click="fillDemo('admin', '123456')">admin / 123456</el-button>
        <el-button text size="small" @click="fillDemo('demo', 'demo1234')">demo / demo1234</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
}

.auth-card {
  width: 100%;
  max-width: 420px;
  margin-top: 34px;
  padding: 28px 26px;
}

.auth-title {
  font-size: 24px;
}

.auth-form {
  margin-top: 18px;
}

.auth-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 16px;
}

.auth-link {
  color: var(--foodie-primary);
  font-size: 13px;
}

.auth-submit {
  width: 100%;
}

.auth-demo {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--foodie-border);
  font-size: 12px;
}
</style>
