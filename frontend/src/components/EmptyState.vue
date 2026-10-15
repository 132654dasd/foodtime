<script setup lang="ts">
import { Bowl } from '@element-plus/icons-vue'

/** 空状态：列表没数据、搜索无结果、未登录都复用它 */
withDefaults(
  defineProps<{
    title?: string
    description?: string
  }>(),
  {
    title: '这里空空如也',
    description: '',
  },
)
</script>

<template>
  <div class="empty-state">
    <div class="empty-state__icon">
      <el-icon :size="34"><Bowl /></el-icon>
    </div>
    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__desc">{{ description }}</p>
    <div class="empty-state__action">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 46px 20px;
  background: var(--foodie-surface);
  border: 1px dashed var(--foodie-border);
  border-radius: 16px;
  text-align: center;
}

.empty-state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin-bottom: 4px;
  border-radius: 50%;
  background: var(--foodie-primary-soft);
  color: var(--foodie-primary);
}

.empty-state__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.empty-state__desc {
  margin: 0;
  color: var(--foodie-text-muted);
  font-size: 13px;
}

.empty-state__action:not(:empty) {
  margin-top: 10px;
}
</style>
