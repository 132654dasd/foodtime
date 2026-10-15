<script setup lang="ts">
import { computed } from 'vue'
import { ChatDotRound, Delete } from '@element-plus/icons-vue'

import type { CommentVO } from '@/api/types'
import { coverInitial } from '@/utils/cover'
import { formatDateTime } from '@/utils/format'

interface Props {
  comment: CommentVO
  /** 二级回复：缩进 + 更小的字号 */
  nested?: boolean
}

const props = withDefaults(defineProps<Props>(), { nested: false })

const emit = defineEmits<{
  (event: 'reply', comment: CommentVO): void
  (event: 'delete', comment: CommentVO): void
}>()

const avatarText = computed(() => coverInitial(props.comment.authorNickname || '匿名'))
</script>

<template>
  <div class="comment" :class="{ 'comment--nested': props.nested }">
    <el-avatar :size="props.nested ? 28 : 34" :src="props.comment.authorAvatar ?? undefined">
      {{ avatarText }}
    </el-avatar>

    <div class="comment__main">
      <div class="comment__head">
        <span class="comment__author">{{ props.comment.authorNickname || '匿名用户' }}</span>
        <span class="comment__time muted">{{ formatDateTime(props.comment.createdAt) }}</span>
      </div>

      <!-- 用户内容一律用文本插值（自动转义），不要用 v-html -->
      <p class="comment__content prewrap">{{ props.comment.content }}</p>

      <div class="comment__actions">
        <el-button text size="small" :icon="ChatDotRound" @click="emit('reply', props.comment)">
          回复
        </el-button>
        <el-button
          v-if="props.comment.editable"
          text
          size="small"
          type="danger"
          :icon="Delete"
          @click="emit('delete', props.comment)"
        >
          删除
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.comment {
  display: flex;
  gap: 10px;
  padding: 12px 0;
}

.comment--nested {
  padding: 8px 0 8px 0;
}

.comment__main {
  flex: 1;
  min-width: 0;
}

.comment__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.comment__author {
  font-size: 13px;
  font-weight: 700;
}

.comment__time {
  font-size: 12px;
}

.comment__content {
  margin: 4px 0 0;
  font-size: 14px;
  line-height: 1.7;
}

.comment__actions {
  display: flex;
  gap: 2px;
  margin-top: 2px;
  margin-left: -8px;
}
</style>
