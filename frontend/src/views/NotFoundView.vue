<script setup lang="ts">
import { Bowl } from '@element-plus/icons-vue'

import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
</script>

<template>
  <div class="page page--narrow">
    <div class="not-found">
      <div class="not-found__code">404</div>
      <h1>这道菜没找到</h1>
      <p class="muted">
        页面可能已经被删掉了，或者链接输错了。要不要先去菜谱库翻一翻？
      </p>
      <div class="not-found__actions">
        <el-button type="primary" :icon="Bowl" @click="$router.push('/recipes')">
          去菜谱库
        </el-button>
        <el-button @click="$router.push('/')">回到首页</el-button>
        <el-button v-if="!userStore.isLoggedIn" text @click="$router.push('/login')">
          去登录
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin-top: 40px;
  padding: 60px 24px;
  background: var(--foodie-surface);
  border: 1px solid var(--foodie-border);
  border-radius: 20px;
  box-shadow: var(--foodie-shadow);
  text-align: center;
}

.not-found__code {
  font-size: 64px;
  font-weight: 800;
  line-height: 1;
  background: linear-gradient(135deg, var(--foodie-accent), var(--foodie-primary));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.not-found__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-top: 12px;
}
</style>
