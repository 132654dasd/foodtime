<script setup lang="ts">
import { computed } from 'vue'
import { ChatDotRound, Pointer, View } from '@element-plus/icons-vue'

import RecipeCover from '@/components/RecipeCover.vue'
import type { PostCardVO } from '@/api/types'
import { coverInitial } from '@/utils/cover'
import { formatDateTime } from '@/utils/format'

interface Props {
  post: PostCardVO
  busy?: boolean
}

const props = withDefaults(defineProps<Props>(), { busy: false })

const emit = defineEmits<{
  (event: 'like', post: PostCardVO): void
}>()

const avatarText = computed(() => coverInitial(props.post.authorNickname || '匿'))

function onLike(): void {
  emit('like', props.post)
}
</script>

<template>
  <article class="post-item surface">
    <RouterLink class="post-item__cover" :to="`/community/${props.post.id}`">
      <RecipeCover
        :title="props.post.title"
        :cover-url="props.post.coverThumb"
        height="100%"
        radius="14px 0 0 14px"
        :show-title="false"
      />
      <span v-if="props.post.imageCount > 1" class="post-item__count">
        {{ props.post.imageCount }} 图
      </span>
    </RouterLink>

    <div class="post-item__body">
      <RouterLink class="post-item__title" :to="`/community/${props.post.id}`">
        {{ props.post.title }}
      </RouterLink>
      <p class="post-item__summary">{{ props.post.summary }}</p>

      <div class="post-item__meta">
        <span class="post-item__author">
          <el-avatar :size="22" :src="props.post.authorAvatar ?? undefined">
            {{ avatarText }}
          </el-avatar>
          {{ props.post.authorNickname || '匿名用户' }}
        </span>
        <span class="muted">{{ formatDateTime(props.post.createdAt) }}</span>
      </div>

      <div class="post-item__foot">
        <button
          class="post-item__action"
          :class="{ 'is-on': props.post.liked }"
          type="button"
          :disabled="props.busy"
          @click.stop.prevent="onLike"
        >
          <el-icon><Pointer /></el-icon>
          <span class="tnum">{{ props.post.likeCount }}</span>
        </button>
        <span class="post-item__action">
          <el-icon><ChatDotRound /></el-icon>
          <span class="tnum">{{ props.post.commentCount }}</span>
        </span>
        <span class="post-item__action">
          <el-icon><View /></el-icon>
          <span class="tnum">{{ props.post.viewCount }}</span>
        </span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.post-item {
  display: flex;
  overflow: hidden;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
}

.post-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--foodie-shadow-hover);
}

.post-item__cover {
  position: relative;
  display: block;
  width: 190px;
  flex: none;
}

.post-item__cover :deep(.recipe-cover) {
  height: 100%;
}

.post-item__count {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 11px;
}

.post-item__body {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
}

.post-item__title {
  font-size: 16px;
  font-weight: 700;
}

.post-item__title:hover {
  color: var(--foodie-primary);
}

.post-item__summary {
  margin: 0;
  color: var(--foodie-text-muted);
  font-size: 13px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}

.post-item__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
}

.post-item__author {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.post-item__foot {
  display: flex;
  gap: 18px;
  margin-top: auto;
  padding-top: 6px;
  border-top: 1px dashed var(--foodie-border);
}

.post-item__action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: none;
  background: none;
  color: var(--foodie-text-muted);
  font-size: 12px;
}

button.post-item__action {
  cursor: pointer;
}

button.post-item__action:hover,
.post-item__action.is-on {
  color: var(--foodie-primary);
}

@media (max-width: 620px) {
  .post-item {
    flex-direction: column;
  }

  .post-item__cover {
    width: 100%;
    height: 160px;
  }

  .post-item__cover :deep(.recipe-cover) {
    border-radius: 14px 14px 0 0;
  }
}
</style>
