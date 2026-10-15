<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { coverGradient, coverInitial } from '@/utils/cover'

/**
 * 菜谱封面。
 *
 * `coverUrl` 为空时（种子数据全是 null）用菜名的确定性哈希生成渐变占位图，
 * 有图时加载失败也会回退到渐变，所以任何情况下都不会出现破图。
 */
interface Props {
  title: string
  coverUrl?: string | null
  /** CSS 高度 */
  height?: string
  radius?: string
  /** 首屏图片用 eager，其余保持懒加载 */
  eager?: boolean
  /** 渐变占位上是否显示菜名 */
  showTitle?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  coverUrl: null,
  height: '176px',
  radius: '14px',
  eager: false,
  showTitle: true,
})

const failed = ref(false)

watch(
  () => props.coverUrl,
  () => {
    failed.value = false
  },
)

const imageSrc = computed(() => (failed.value ? '' : (props.coverUrl ?? '')))
const showImage = computed(() => imageSrc.value !== '')
const gradientStyle = computed(() => coverGradient(props.title))
const initial = computed(() => coverInitial(props.title))
</script>

<template>
  <div class="recipe-cover" :style="{ height: props.height, borderRadius: props.radius }">
    <img
      v-if="showImage"
      class="recipe-cover__img"
      :src="imageSrc"
      :alt="props.title"
      :loading="props.eager ? 'eager' : 'lazy'"
      decoding="async"
      @error="failed = true"
    />
    <div v-else class="recipe-cover__fallback" :style="gradientStyle">
      <span class="recipe-cover__initial">{{ initial }}</span>
      <span v-if="props.showTitle" class="recipe-cover__title">{{ props.title }}</span>
    </div>
  </div>
</template>

<style scoped>
.recipe-cover {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: #f7ece4;
}

.recipe-cover__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.recipe-cover__fallback {
  display: flex;
  height: 100%;
  width: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px;
  color: #fff;
  text-align: center;
}

.recipe-cover__initial {
  font-size: 40px;
  font-weight: 800;
  line-height: 1;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.18);
}

.recipe-cover__title {
  max-width: 100%;
  font-size: 13px;
  font-weight: 600;
  opacity: 0.95;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
