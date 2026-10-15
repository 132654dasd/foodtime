<script setup lang="ts">
import { ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'

import { uploadImage } from '@/api'
import type { UploadResult } from '@/api/types'

/** 最多 9 张，单张 5MB —— 与 `POST /api/files/image` 的约定保持一致 */
interface Props {
  modelValue: UploadResult[]
  max?: number
}

const props = withDefaults(defineProps<Props>(), { max: 9 })

const emit = defineEmits<{
  (event: 'update:modelValue', value: UploadResult[]): void
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

function pick(): void {
  inputRef.value?.click()
}

async function onFilesPicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  // 清空 value，否则同一张图第二次选不会触发 change
  input.value = ''
  if (files.length === 0) {
    return
  }

  const remaining = props.max - props.modelValue.length
  if (remaining <= 0) {
    ElMessage.warning(`最多上传 ${props.max} 张图片`)
    return
  }

  uploading.value = true
  const added: UploadResult[] = []
  try {
    for (const file of files.slice(0, remaining)) {
      if (file.size > 5 * 1024 * 1024) {
        ElMessage.error(`${file.name} 超过 5MB，已跳过`)
        continue
      }
      try {
        added.push(await uploadImage(file))
      } catch {
        /* 错误提示由 axios 拦截器统一给出 */
      }
    }
    if (added.length > 0) {
      emit('update:modelValue', [...props.modelValue, ...added])
    }
  } finally {
    uploading.value = false
  }
}

function removeAt(index: number): void {
  const next = [...props.modelValue]
  next.splice(index, 1)
  emit('update:modelValue', next)
}
</script>

<template>
  <div class="image-picker">
    <div v-for="(image, index) in props.modelValue" :key="image.url" class="image-picker__item">
      <img :src="image.thumbUrl || image.url" alt="已上传图片" loading="lazy" />
      <button class="image-picker__remove" type="button" title="移除" @click="removeAt(index)">
        <el-icon><Delete /></el-icon>
      </button>
    </div>

    <button
      v-if="props.modelValue.length < props.max"
      class="image-picker__add"
      type="button"
      :disabled="uploading"
      @click="pick"
    >
      <el-icon><Plus /></el-icon>
      <span>{{ uploading ? '上传中…' : '添加图片' }}</span>
    </button>

    <input
      ref="inputRef"
      class="image-picker__input"
      type="file"
      accept="image/png,image/jpeg,image/webp,image/gif"
      multiple
      @change="onFilesPicked"
    />
  </div>
</template>

<style scoped>
.image-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.image-picker__item {
  position: relative;
  width: 92px;
  height: 92px;
  overflow: hidden;
  border: 1px solid var(--foodie-border);
  border-radius: 10px;
}

.image-picker__item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-picker__remove {
  position: absolute;
  top: 2px;
  right: 2px;
  display: grid;
  width: 22px;
  height: 22px;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
}

.image-picker__add {
  display: flex;
  width: 92px;
  height: 92px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 1px dashed var(--foodie-border);
  border-radius: 10px;
  background: #fffaf6;
  color: var(--foodie-text-muted);
  cursor: pointer;
  font-size: 12px;
}

.image-picker__add:hover {
  border-color: var(--foodie-primary);
  color: var(--foodie-primary);
}

.image-picker__input {
  display: none;
}
</style>
