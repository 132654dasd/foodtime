import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { recipeMeta } from '@/api'
import type { RecipeMeta } from '@/api/types'

/**
 * 菜谱筛选字典（菜系/分类/难度/标签/忌口建议）。
 *
 * 后端这份字典是长缓存的，前端没必要每个页面都拉一次：
 * 进内存 + 并发去重（同一个 tick 里多个组件同时调用只发一个请求）。
 */
export const useMetaStore = defineStore('meta', () => {
  const meta = ref<RecipeMeta | null>(null)
  const loading = ref(false)

  const cuisines = computed<string[]>(() => meta.value?.cuisines ?? [])
  const categories = computed(() => meta.value?.categories ?? [])
  const difficulties = computed(() => meta.value?.difficulties ?? [])
  const tags = computed<string[]>(() => meta.value?.tags ?? [])
  const avoidSuggestions = computed(() => meta.value?.avoidSuggestions ?? [])

  let inflight: Promise<RecipeMeta> | null = null

  async function ensure(force = false): Promise<RecipeMeta> {
    if (meta.value && !force) {
      return meta.value
    }
    if (inflight && !force) {
      return inflight
    }
    loading.value = true
    inflight = recipeMeta()
      .then((data) => {
        meta.value = data
        return data
      })
      .finally(() => {
        loading.value = false
        inflight = null
      })
    return inflight
  }

  return { meta, loading, cuisines, categories, difficulties, tags, avoidSuggestions, ensure }
})
