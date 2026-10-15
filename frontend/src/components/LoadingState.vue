<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'

/** 加载态：`cards` 用骨架屏，`block` 用转圈，避免页面来回跳动 */
withDefaults(
  defineProps<{
    text?: string
    variant?: 'block' | 'cards'
    cards?: number
    rows?: number
  }>(),
  {
    text: '加载中…',
    variant: 'block',
    cards: 3,
    rows: 4,
  },
)
</script>

<template>
  <div v-if="variant === 'cards'" class="loading-cards">
    <div v-for="index in cards" :key="index" class="loading-card">
      <el-skeleton animated>
        <template #template>
          <el-skeleton-item variant="image" style="width: 100%; height: 150px" />
          <div style="padding: 12px">
            <el-skeleton-item variant="h3" style="width: 60%" />
            <el-skeleton-item variant="text" style="margin-top: 10px" />
            <el-skeleton-item variant="text" style="width: 80%; margin-top: 6px" />
          </div>
        </template>
      </el-skeleton>
    </div>
  </div>

  <div v-else class="loading-block">
    <el-icon class="loading-block__spin" :size="22"><Loading /></el-icon>
    <el-skeleton v-if="rows > 0" :rows="rows" animated style="width: 100%" />
    <span v-else class="muted">{{ text }}</span>
  </div>
</template>

<style scoped>
.loading-cards {
  display: grid;
  gap: 18px;
  grid-template-columns: 1fr;
}

@media (min-width: 560px) {
  .loading-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 768px) {
  .loading-cards {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.loading-card {
  overflow: hidden;
  background: var(--foodie-surface);
  border: 1px solid var(--foodie-border);
  border-radius: 15px;
}

.loading-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 16px;
}

.loading-block__spin {
  color: var(--foodie-primary);
  animation: loading-rotate 1.4s linear infinite;
}

@keyframes loading-rotate {
  to {
    transform: rotate(360deg);
  }
}
</style>
