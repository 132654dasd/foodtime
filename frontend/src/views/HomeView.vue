<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Bowl, MagicStick, Search } from '@element-plus/icons-vue'

import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import RecipeCard from '@/components/RecipeCard.vue'
import { hotRecipes, isCanceled, listRecipes } from '@/api'
import type { RecipeCardVO } from '@/api/types'
import { useMetaStore } from '@/stores/meta'
import { debounce } from '@/utils/debounce'

const router = useRouter()
const metaStore = useMetaStore()

const keyword = ref('')
const suggestions = ref<RecipeCardVO[]>([])
const suggesting = ref(false)
const showSuggestions = ref(false)

const hot = ref<RecipeCardVO[]>([])
const loading = ref(true)
const loadError = ref('')

const cuisines = computed(() => metaStore.cuisines.slice(0, 8))
const categories = computed(() => metaStore.categories.slice(0, 6))

async function loadHot(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    hot.value = await hotRecipes(8)
  } catch (error) {
    if (!isCanceled(error)) {
      loadError.value = error instanceof Error ? error.message : '加载失败'
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadHot()
  // 字典拉不到不影响首页主体，静默降级（chips 会自然变空）
  void metaStore.ensure().catch(() => undefined)
})

let suggestController: AbortController | null = null

/** 搜索输入 300ms 防抖后拉 4 条建议；输入太快时旧请求会被 abort */
const runSuggest = debounce(async (value: string) => {
  const trimmed = value.trim()
  if (!trimmed) {
    suggestController?.abort()
    suggestions.value = []
    suggesting.value = false
    return
  }
  suggestController?.abort()
  const controller = new AbortController()
  suggestController = controller
  suggesting.value = true
  try {
    const result = await listRecipes(
      { keyword: trimmed, page: 1, size: 4, sort: 'hot' },
      { signal: controller.signal },
    )
    if (suggestController === controller) {
      suggestions.value = result.list
    }
  } catch (error) {
    if (!isCanceled(error) && suggestController === controller) {
      suggestions.value = []
    }
  } finally {
    if (suggestController === controller) {
      suggesting.value = false
    }
  }
}, 300)

function onKeywordInput(value: string): void {
  keyword.value = value
  showSuggestions.value = true
  runSuggest(value)
}

function submitSearch(): void {
  runSuggest.cancel()
  showSuggestions.value = false
  const trimmed = keyword.value.trim()
  void router.push({ path: '/recipes', query: trimmed ? { keyword: trimmed } : {} })
}

function chooseSuggestion(recipe: RecipeCardVO): void {
  showSuggestions.value = false
  void router.push(`/recipes/${recipe.id}`)
}

function gotoRecipes(query: Record<string, string>): void {
  void router.push({ path: '/recipes', query })
}

function gotoMenu(): void {
  void router.push('/menu')
}

function gotoFridge(): void {
  // 未登录时由路由守卫拦到登录页，并把 redirect 带回来
  void router.push('/fridge')
}

onBeforeUnmount(() => {
  runSuggest.cancel()
  suggestController?.abort()
})
</script>

<template>
  <div class="home">
    <section class="hero">
      <div class="hero__inner">
        <div class="hero__text">
          <span class="hero__badge">今天吃什么，交给食光</span>
          <h1 class="hero__title">把冰箱里那点食材，<br />变成一顿像样的饭</h1>
          <p class="hero__sub">
            30 道家常菜谱、按忌口与规格随机配餐、把冰箱里有的食材填一填就能反查「现在能做什么」，还有真实的社区分享。挑不出来的时候，让「今天吃什么」替你决定。
          </p>

          <div class="hero__search">
            <el-input
              v-model="keyword"
              size="large"
              placeholder="搜菜名、食材或口味，比如「番茄」「快手」"
              clearable
              :prefix-icon="Search"
              @input="onKeywordInput"
              @keyup.enter="submitSearch"
              @blur="showSuggestions = false"
            />
            <el-button size="large" type="primary" @click="submitSearch">搜索</el-button>

            <div v-if="showSuggestions && keyword.trim()" class="hero__suggest">
              <div v-if="suggesting" class="hero__suggest-state muted">搜索中…</div>
              <template v-else-if="suggestions.length">
                <button
                  v-for="item in suggestions"
                  :key="item.id"
                  class="hero__suggest-item"
                  type="button"
                  @mousedown.prevent="chooseSuggestion(item)"
                >
                  <span class="hero__suggest-title">{{ item.title }}</span>
                  <span class="muted">{{ item.cuisine }} · {{ item.category }}</span>
                </button>
              </template>
              <div v-else class="hero__suggest-state muted">没有匹配的菜谱</div>
            </div>
          </div>

          <div class="hero__actions">
            <el-button type="primary" size="large" @click="gotoMenu">今天吃什么</el-button>
            <el-button size="large" @click="gotoFridge">看看我的冰箱</el-button>
            <el-button size="large" @click="gotoRecipes({})">浏览全部菜谱</el-button>
          </div>
        </div>

        <button class="hero__card" type="button" @click="gotoMenu">
          <span class="hero__card-icon">
            <el-icon :size="28"><MagicStick /></el-icon>
          </span>
          <strong>随机配餐</strong>
          <p>选菜系、填忌口，几秒钟配一桌菜，还附采购清单。</p>
          <span class="hero__card-link">
            去抽一桌 <el-icon><ArrowRight /></el-icon>
          </span>
        </button>
      </div>
    </section>

    <div class="page">
      <section v-if="cuisines.length || categories.length" class="section">
        <h2 class="section-title">按口味逛</h2>
        <div class="quick-nav">
          <div class="quick-nav__group">
            <span class="quick-nav__label">菜系</span>
            <div class="chip-row">
              <el-tag
                v-for="cuisine in cuisines"
                :key="cuisine"
                class="chip"
                effect="plain"
                round
                @click="gotoRecipes({ cuisine })"
              >
                {{ cuisine }}
              </el-tag>
            </div>
          </div>
          <div class="quick-nav__group">
            <span class="quick-nav__label">分类</span>
            <div class="chip-row">
              <el-tag
                v-for="category in categories"
                :key="category.value"
                class="chip"
                effect="plain"
                round
                @click="gotoRecipes({ category: category.value })"
              >
                {{ category.label }}（{{ category.count }}）
              </el-tag>
            </div>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="page-head">
          <h2 class="section-title" style="margin-bottom: 0">热门菜谱</h2>
          <el-button text @click="gotoRecipes({ sort: 'hot' })">
            更多热门 <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>

        <LoadingState v-if="loading" variant="cards" :cards="3" />

        <el-result
          v-else-if="loadError"
          icon="error"
          title="热门菜谱加载失败"
          :sub-title="loadError"
        >
          <template #extra>
            <el-button type="primary" @click="loadHot">重试</el-button>
          </template>
        </el-result>

        <EmptyState
          v-else-if="hot.length === 0"
          title="还没有菜谱"
          description="后端菜谱库是空的，先去后台加两道菜吧"
        >
          <el-button type="primary" @click="gotoRecipes({})">去菜谱库看看</el-button>
        </EmptyState>

        <div v-else class="card-grid">
          <RecipeCard
            v-for="(recipe, index) in hot"
            :key="recipe.id"
            :recipe="recipe"
            :eager="index < 3"
          />
        </div>
      </section>

      <section class="section">
        <div class="menu-banner">
          <div class="menu-banner__text">
            <span class="hero__badge">核心功能</span>
            <h2>不知道吃什么？让食光替你决定</h2>
            <p class="muted">
              菜 0-10 道、汤 0-3 道，勾上忌口（香菜、花生、海鲜…），选纯随机 / 荤素均衡 / 快手菜，
              一秒钟出一桌，并给出总热量、总耗时、难度分布与采购清单。
            </p>
          </div>
          <el-button type="primary" size="large" :icon="Bowl" @click="gotoMenu">
            帮我决定
          </el-button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.hero {
  padding: 34px 20px 30px;
}

.hero__inner {
  display: flex;
  gap: 28px;
  align-items: center;
  max-width: 1200px;
  margin: 0 auto;
}

.hero__text {
  flex: 1;
  min-width: 0;
}

.hero__badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--foodie-primary-soft);
  color: var(--foodie-primary);
  font-size: 12px;
  font-weight: 700;
}

.hero__title {
  margin: 12px 0 10px;
  font-size: 38px;
  line-height: 1.22;
  letter-spacing: -0.5px;
}

.hero__sub {
  max-width: 560px;
  margin: 0 0 20px;
  color: var(--foodie-text-muted);
  font-size: 15px;
}

.hero__search {
  position: relative;
  display: flex;
  gap: 10px;
  max-width: 520px;
}

.hero__suggest {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 10;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--foodie-border);
  border-radius: 12px;
  box-shadow: var(--foodie-shadow-hover);
}

.hero__suggest-item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  text-align: left;
}

.hero__suggest-item:hover {
  background: var(--foodie-primary-soft);
}

.hero__suggest-title {
  font-weight: 600;
}

.hero__suggest-state {
  padding: 12px 14px;
  font-size: 13px;
}

.hero__actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.hero__card {
  display: flex;
  width: 280px;
  flex: none;
  flex-direction: column;
  gap: 8px;
  padding: 22px;
  border: none;
  border-radius: 20px;
  background: linear-gradient(150deg, #ff8a4c, #ff5f6d 85%);
  box-shadow: 0 18px 40px rgba(255, 95, 109, 0.28);
  color: #fff;
  cursor: pointer;
  text-align: left;
  transition: transform 0.2s ease;
}

.hero__card:hover {
  transform: translateY(-4px);
}

.hero__card strong {
  font-size: 20px;
}

.hero__card p {
  margin: 0;
  font-size: 13px;
  opacity: 0.92;
}

.hero__card-icon {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.2);
}

.hero__card-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-weight: 700;
}

.quick-nav {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  background: var(--foodie-surface);
  border: 1px solid var(--foodie-border);
  border-radius: 16px;
}

.quick-nav__group {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.quick-nav__label {
  flex: none;
  padding-top: 2px;
  color: var(--foodie-text-muted);
  font-size: 13px;
  font-weight: 700;
}

.chip {
  cursor: pointer;
  transition: all 0.15s ease;
}

.chip:hover {
  transform: translateY(-1px);
}

.menu-banner {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
  padding: 24px;
  border: 1px solid #ffe0cd;
  border-radius: 18px;
  background: linear-gradient(120deg, #fff6ee, #fff1e6 60%, #ffe9d6);
}

.menu-banner h2 {
  margin: 10px 0 6px;
  font-size: 22px;
}

.menu-banner__text {
  max-width: 620px;
}

@media (max-width: 900px) {
  .hero__inner {
    flex-direction: column;
    align-items: stretch;
  }

  .hero__card {
    width: 100%;
  }

  .hero__title {
    font-size: 30px;
  }
}
</style>
