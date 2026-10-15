<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type HealthState = 'checking' | 'up' | 'down'

const health = ref<HealthState>('checking')

const healthText = computed(() => {
  if (health.value === 'checking') {
    return '检测中…'
  }
  return health.value === 'up' ? '后端服务正常' : '后端服务不可用'
})

/** GET /actuator/health —— 公开接口，用来给用户一个「后端是否在线」的直观提示 */
async function checkHealth(): Promise<void> {
  health.value = 'checking'
  try {
    const response = await fetch('/actuator/health')
    const body = (await response.json()) as { status?: string }
    health.value = response.ok && body.status === 'UP' ? 'up' : 'down'
  } catch {
    health.value = 'down'
  }
}

onMounted(() => {
  void checkHealth()
})
</script>

<template>
  <footer class="app-footer">
    <div class="app-footer__inner">
      <div class="app-footer__brand">
        <strong>食光 Foodie</strong>
        <p class="muted">菜谱库 · 随机配餐 · 美食社区</p>
      </div>

      <div class="app-footer__links">
        <RouterLink to="/recipes">菜谱库</RouterLink>
        <RouterLink to="/menu">今天吃什么</RouterLink>
        <RouterLink to="/community">美食社区</RouterLink>
        <RouterLink to="/profile">个人中心</RouterLink>
      </div>

      <div class="app-footer__status">
        <span class="dot" :class="`dot--${health}`" />
        <span class="muted">{{ healthText }}</span>
        <el-button text size="small" @click="checkHealth">重新检测</el-button>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  margin-top: 40px;
  border-top: 1px solid var(--foodie-border);
  background: rgba(255, 255, 255, 0.72);
}

.app-footer__inner {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 32px;
  align-items: center;
  justify-content: space-between;
  max-width: 1200px;
  margin: 0 auto;
  padding: 22px 20px;
}

.app-footer__brand p {
  margin: 2px 0 0;
  font-size: 12px;
}

.app-footer__links {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: var(--foodie-text-muted);
}

.app-footer__links a:hover {
  color: var(--foodie-primary);
}

.app-footer__status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ccc;
}

.dot--up {
  background: #4caf7d;
}

.dot--down {
  background: #e2503c;
}

.dot--checking {
  background: var(--foodie-accent);
}
</style>
