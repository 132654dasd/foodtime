<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'

import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import RecipeCard from '@/components/RecipeCard.vue'
import { favoriteRecipe, isCanceled, listRecipes, unfavoriteRecipe } from '@/api'
import type { Difficulty, RecipeCardVO, RecipeSort } from '@/api/types'
import { useMetaStore } from '@/stores/meta'
import { useUserStore } from '@/stores/user'
import { debounce } from '@/utils/debounce'

const route = useRoute()
const router = useRouter()
const metaStore = useMetaStore()
const userStore = useUserStore()

interface Filters {
  keyword: string
  cuisine: string
  category: string
  difficulty: '' | Difficulty
  maxMinutes: number | undefined
  sort: RecipeSort
  page: number
  size: number
}

const SORT_OPTIONS: Array<{ value: RecipeSort; label: string }> = [
  { value: 'latest', label: '最新收录' },
  { value: 'hot', label: '最热门' },
  { value: 'rating', label: '评分最高' },
  { value: 'cook_asc', label: '耗时最短' },
  { value: 'difficulty_asc', label: '难度由易到难' },
  { value: 'difficulty_desc', label: '难度由难到易' },
]

const DEFAULT_SIZE = 12

const filters = reactive<Filters>({
  keyword: '',
  cuisine: '',
  category: '',
  difficulty: '',
  maxMinutes: undefined,
  sort: 'latest',
  page: 1,
  size: DEFAULT_SIZE,
})

const keywordInput = ref('')
const list = ref<RecipeCardVO[]>([])
const total = ref(0)
const loading = ref(false)
const errorText = ref('')
const favoriteBusyId = ref<number | null>(null)

let controller: AbortController | null = null

const hasFilter = computed(
  () =>
    !!filters.keyword ||
    !!filters.cuisine ||
    !!filters.category ||
    !!filters.difficulty ||
    !!filters.maxMinutes ||
    filters.sort !== 'latest',
)

// ------------------------------------------------------------ URL ↔ 状态

function readQuery(): void {
  const query = route.query
  const readString = (key: string): string =>
    typeof query[key] === 'string' ? (query[key] as string) : ''
  const readNumber = (key: string, fallback: number): number => {
    const parsed = Number(readString(key))
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
  }

  filters.keyword = readString('keyword')
  filters.cuisine = readString('cuisine')
  filters.category = readString('category')

  const difficulty = readString('difficulty').toUpperCase()
  filters.difficulty =
    difficulty === 'EASY' || difficulty === 'MEDIUM' || difficulty === 'HARD'
      ? (difficulty as Difficulty)
      : ''

  const maxMinutes = readNumber('maxMinutes', 0)
  filters.maxMinutes = maxMinutes > 0 ? maxMinutes : undefined

  const sort = readString('sort')
  const matched = SORT_OPTIONS.find((option) => option.value === sort)
  filters.sort = matched ? matched.value : 'latest'

  filters.page = readNumber('page', 1)
  filters.size = readNumber('size', DEFAULT_SIZE)
  keywordInput.value = filters.keyword
}

function buildQueryFrom(state: Filters): Record<string, string> {
  const query: Record<string, string> = {}
  if (state.keyword.trim()) {
    query.keyword = state.keyword.trim()
  }
  if (state.cuisine) {
    query.cuisine = state.cuisine
  }
  if (state.category) {
    query.category = state.category
  }
  if (state.difficulty) {
    query.difficulty = state.difficulty
  }
  if (state.maxMinutes) {
    query.maxMinutes = String(state.maxMinutes)
  }
  if (state.sort !== 'latest') {
    query.sort = state.sort
  }
  if (state.page > 1) {
    query.page = String(state.page)
  }
  if (state.size !== DEFAULT_SIZE) {
    query.size = String(state.size)
  }
  return query
}

/**
 * 所有筛选变更都走这里：先改 URL，再由 `watch(route.fullPath)` 统一触发请求。
 * URL 是筛选状态的唯一事实来源，刷新/分享链接都能还原同样的结果。
 */
function applyPatch(patch: Partial<Filters>): void {
  const next: Filters = { ...filters, ...patch }
  if (patch.page === undefined) {
    next.page = 1
  }
  void router.push({ path: '/recipes', query: buildQueryFrom(next) })
}

const keywordDebounced = debounce((value: string) => {
  applyPatch({ keyword: value })
}, 300)

function onKeywordInput(value: string): void {
  keywordInput.value = value
  keywordDebounced(value)
}

function onKeywordEnter(): void {
  keywordDebounced.cancel()
  applyPatch({ keyword: keywordInput.value })
}

function resetFilters(): void {
  keywordDebounced.cancel()
  keywordInput.value = ''
  void router.push({ path: '/recipes', query: {} })
}

// ------------------------------------------------------------ 请求

async function load(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current

  loading.value = true
  errorText.value = ''

  try {
    const result = await listRecipes(
      {
        keyword: filters.keyword.trim() || undefined,
        cuisine: filters.cuisine || undefined,
        category: filters.category || undefined,
        difficulty: filters.difficulty || undefined,
        maxMinutes: filters.maxMinutes,
        sort: filters.sort,
        page: filters.page,
        size: filters.size,
      },
      { signal: current.signal },
    )
    if (controller !== current) {
      return
    }
    list.value = result.list
    total.value = result.total
  } catch (error) {
    if (controller !== current || isCanceled(error)) {
      return
    }
    errorText.value = error instanceof Error ? error.message : '加载失败'
    list.value = []
    total.value = 0
  } finally {
    if (controller === current) {
      loading.value = false
    }
  }
}

watch(
  () => route.fullPath,
  () => {
    readQuery()
    void load()
  },
  { immediate: true },
)

onMounted(() => {
  void metaStore.ensure().catch(() => undefined)
})

onBeforeUnmount(() => {
  controller?.abort()
  keywordDebounced.cancel()
})

// ------------------------------------------------------------ 收藏

async function toggleFavorite(recipe: RecipeCardVO): Promise<void> {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('登录后才能收藏菜谱')
    void router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  favoriteBusyId.value = recipe.id
  try {
    if (recipe.favorited) {
      await unfavoriteRecipe(recipe.id)
      recipe.favorited = false
      recipe.favoriteCount = Math.max(0, (recipe.favoriteCount ?? 1) - 1)
      ElMessage.success('已取消收藏')
    } else {
      await favoriteRecipe(recipe.id)
      recipe.favorited = true
      recipe.favoriteCount = (recipe.favoriteCount ?? 0) + 1
      ElMessage.success('已加入收藏')
    }
  } catch {
    /* 错误提示由 axios 拦截器统一给出 */
  } finally {
    favoriteBusyId.value = null
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">菜谱库</h1>
        <p class="page-subtitle">
          共 {{ total }} 道菜
          <template v-if="filters.keyword">· 关键词「{{ filters.keyword }}」</template>
        </p>
      </div>
      <div class="view-actions">
        <el-button v-if="hasFilter" text :icon="Refresh" @click="resetFilters">清空筛选</el-button>
      </div>
    </div>

    <div class="filter-bar surface">
      <el-input
        v-model="keywordInput"
        class="filter-search"
        placeholder="搜索菜名 / 食材 / 口味（300ms 防抖）"
        clearable
        :prefix-icon="Search"
        @input="onKeywordInput"
        @keyup.enter="onKeywordEnter"
        @clear="onKeywordEnter"
      />

      <div class="filter-item">
        <span>菜系</span>
        <el-select
          :model-value="filters.cuisine"
          placeholder="全部"
          clearable
          style="width: 120px"
          @change="(value: string) => applyPatch({ cuisine: value ?? '' })"
        >
          <el-option v-for="item in metaStore.cuisines" :key="item" :label="item" :value="item" />
        </el-select>
      </div>

      <div class="filter-item">
        <span>分类</span>
        <el-select
          :model-value="filters.category"
          placeholder="全部"
          clearable
          style="width: 120px"
          @change="(value: string) => applyPatch({ category: value ?? '' })"
        >
          <el-option
            v-for="item in metaStore.categories"
            :key="item.value"
            :label="`${item.label}（${item.count}）`"
            :value="item.value"
          />
        </el-select>
      </div>

      <div class="filter-item">
        <span>难度</span>
        <el-select
          :model-value="filters.difficulty"
          placeholder="全部"
          clearable
          style="width: 120px"
          @change="(value: string) => applyPatch({ difficulty: (value ?? '') as Difficulty | '' })"
        >
          <el-option
            v-for="item in metaStore.difficulties"
            :key="item.value"
            :label="`${item.label}（${item.count}）`"
            :value="item.value"
          />
        </el-select>
      </div>

      <div class="filter-item">
        <span>最长耗时</span>
        <el-select
          :model-value="filters.maxMinutes"
          placeholder="不限"
          clearable
          style="width: 130px"
          @change="
            (value: number | undefined) => applyPatch({ maxMinutes: value ?? undefined })
          "
        >
          <el-option :value="15" label="15 分钟内" />
          <el-option :value="30" label="30 分钟内" />
          <el-option :value="60" label="1 小时内" />
          <el-option :value="120" label="2 小时内" />
        </el-select>
      </div>

      <div class="filter-item">
        <span>排序</span>
        <el-select
          :model-value="filters.sort"
          style="width: 150px"
          @change="(value: string) => applyPatch({ sort: value as RecipeSort })"
        >
          <el-option
            v-for="option in SORT_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
    </div>

    <LoadingState v-if="loading" variant="cards" :cards="6" />

    <el-result v-else-if="errorText" icon="error" title="菜谱加载失败" :sub-title="errorText">
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
        <el-button @click="resetFilters">清空筛选</el-button>
      </template>
    </el-result>

    <EmptyState
      v-else-if="list.length === 0"
      title="没有符合条件的菜谱"
      description="换个关键词，或者把筛选条件放宽一点"
    >
      <el-button type="primary" @click="resetFilters">清空筛选</el-button>
    </EmptyState>

    <template v-else>
      <div class="card-grid">
        <RecipeCard
          v-for="(recipe, index) in list"
          :key="recipe.id"
          :recipe="recipe"
          :eager="index < 3"
          favoritable
          :busy="favoriteBusyId === recipe.id"
          @toggle-favorite="toggleFavorite"
        />
      </div>

      <div class="pager">
        <el-pagination
          background
          layout="prev, pager, next, jumper, total"
          :current-page="filters.page"
          :page-size="filters.size"
          :total="total"
          @current-change="(page: number) => applyPatch({ page })"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.filter-search {
  width: 280px;
  max-width: 100%;
}

.view-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 640px) {
  .filter-search {
    width: 100%;
  }
}
</style>
