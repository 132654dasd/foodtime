<script setup lang="ts">
import { Clock, Star } from '@element-plus/icons-vue'

import RecipeCover from '@/components/RecipeCover.vue'
import type { RecipeCardVO } from '@/api/types'
import { difficultyTagType, formatMinutes, formatNumber, formatRating } from '@/utils/format'

interface Props {
  recipe: RecipeCardVO
  /** 首屏卡片用 eager 加载封面 */
  eager?: boolean
  /** 是否显示收藏按钮（需要登录，未登录时由父组件提示） */
  favoritable?: boolean
  /** 收藏请求在飞时禁用按钮 */
  busy?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  eager: false,
  favoritable: false,
  busy: false,
})

const emit = defineEmits<{
  (event: 'toggle-favorite', recipe: RecipeCardVO): void
}>()

function onFavorite(): void {
  emit('toggle-favorite', props.recipe)
}
</script>

<template>
  <RouterLink class="recipe-card" :to="`/recipes/${props.recipe.id}`">
    <RecipeCover
      :title="props.recipe.title"
      :cover-url="props.recipe.coverUrl"
      :eager="props.eager"
      height="168px"
      radius="14px 14px 0 0"
      :show-title="false"
    />

    <div class="recipe-card__body">
      <div class="recipe-card__head">
        <h3 class="recipe-card__title">{{ props.recipe.title }}</h3>
        <el-button
          v-if="props.favoritable"
          class="recipe-card__fav"
          text
          circle
          :loading="props.busy"
          :title="props.recipe.favorited ? '取消收藏' : '收藏'"
          @click.stop.prevent="onFavorite"
        >
          <el-icon :class="{ 'is-on': props.recipe.favorited }">
            <Star />
          </el-icon>
        </el-button>
      </div>

      <p class="recipe-card__desc">{{ props.recipe.description || '暂无简介' }}</p>

      <div class="recipe-card__meta">
        <el-tag size="small" :type="difficultyTagType(props.recipe.difficulty)" effect="light">
          {{ props.recipe.difficultyLabel || '未知难度' }}
        </el-tag>
        <span class="recipe-card__meta-item">
          <el-icon><Clock /></el-icon>
          {{ formatMinutes(props.recipe.cookMinutes) }}
        </span>
        <span class="recipe-card__meta-item tnum">
          {{ formatNumber(props.recipe.calories) }} 千卡
        </span>
      </div>

      <div class="recipe-card__foot">
        <span class="muted">{{ props.recipe.cuisine || '—' }} · {{ props.recipe.category || '—' }}</span>
        <span class="recipe-card__rating tnum">
          <el-icon><Star /></el-icon>
          {{ formatRating(props.recipe.ratingAvg) }}
          <template v-if="props.recipe.ratingCount">（{{ props.recipe.ratingCount }}）</template>
        </span>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.recipe-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--foodie-surface);
  border: 1px solid var(--foodie-border);
  border-radius: 15px;
  box-shadow: var(--foodie-shadow);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
}

.recipe-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--foodie-shadow-hover);
}

.recipe-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px 14px;
}

.recipe-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.recipe-card__title {
  font-size: 16px;
  line-height: 1.35;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
}

.recipe-card__fav {
  flex: none;
  margin-top: -4px;
  margin-right: -6px;
}

.recipe-card__fav .is-on {
  color: var(--foodie-accent);
}

.recipe-card__desc {
  margin: 0;
  color: var(--foodie-text-muted);
  font-size: 13px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  min-height: 40px;
}

.recipe-card__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--foodie-text-muted);
}

.recipe-card__meta-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.recipe-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
  padding-top: 6px;
  border-top: 1px dashed var(--foodie-border);
  font-size: 12px;
}

.recipe-card__rating {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--foodie-accent);
  font-weight: 600;
}
</style>
