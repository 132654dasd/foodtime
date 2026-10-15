<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, Message, User } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

import { register as registerApi } from '@/api'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  nickname: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_]{3,20}$/,
      message: '用户名需为 3-20 位字母、数字或下划线',
      trigger: 'blur',
    },
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 20, message: '昵称不超过 20 个字', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' },
  ],
  confirmPassword: [
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (!value) {
          callback(new Error('请再输入一次密码'))
        } else if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
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
    // 注册接口直接返回令牌，等于注册即登录
    const response = await registerApi({
      username: form.username.trim(),
      password: form.password,
      nickname: form.nickname.trim(),
      email: form.email.trim() || null,
    })
    userStore.setToken(response.token)
    await userStore.loadMe()
    ElMessage.success('注册成功，已自动登录')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch {
    /* 用户名重复等提示由 axios 拦截器统一给出 */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page auth-page">
    <div class="auth-card surface">
      <h1 class="auth-title">加入食光</h1>
      <p class="muted">注册后即可配餐、收藏和发帖</p>

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

        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" size="large" placeholder="别人看到的名字" />
        </el-form-item>

        <el-form-item label="邮箱（可选）" prop="email">
          <el-input
            v-model="form.email"
            size="large"
            placeholder="用于展示，暂不发送邮件"
            :prefix-icon="Message"
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
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            size="large"
            type="password"
            show-password
            placeholder="再输一次"
            :prefix-icon="Lock"
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-button
          class="auth-submit"
          type="primary"
          size="large"
          :loading="loading"
          @click="submit"
        >
          注册并登录
        </el-button>
      </el-form>

      <div class="auth-foot">
        <span class="muted">已经有账号了？</span>
        <RouterLink class="auth-link" to="/login">去登录</RouterLink>
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

.auth-submit {
  width: 100%;
}

.auth-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--foodie-border);
  font-size: 13px;
}

.auth-link {
  color: var(--foodie-primary);
}
</style>
