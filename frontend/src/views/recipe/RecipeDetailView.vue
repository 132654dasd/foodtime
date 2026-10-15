<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Bowl, ShoppingCart, Star, StarFilled } from '@element-plus/icons-vue'

import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import RecipeCover from '@/components/RecipeCover.vue'
import { favoriteRecipe, isCanceled, rateRecipe, recipeDetail, unfavoriteRecipe } from '@/api'
import type { RecipeDetailVO } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { difficultyTagType, formatMinutes, formatNumber, formatRating } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const detail = ref<RecipeDetailVO | null>(null)
const loading = ref(true)
const errorText = ref('')
const notFound = ref(false)
const favoriteBusy = ref(false)
const ratingBusy = ref(false)
const myScore = ref(0)

let controller: AbortController | null = null

const recipeId = computed(() => Number(route.params.id))
const validId = computed(() => Number.isInteger(recipeId.value) && recipeId.value > 0)

const ingredientRows = computed(() => detail.value?.ingredients ?? [])
const stepRows = computed(() => detail.value?.steps ?? [])

async function load(): Promise<void> {
  if (!validId.value) {
    detail.value = null
    loading.value = false
    notFound.value = true
    return
  }

  controller?.abort()
  const current = new AbortController()
  controller = current

  loading.value = true
  errorText.value = ''
  notFound.value = false

  try {
    const result = await recipeDetail(recipeId.value, { signal: current.signal })
    if (controller !== current) {
      return
    }
    detail.value = result
    myScore.value = 0
  } catch (error) {
    if (controller !== current || isCanceled(error)) {
      return
    }
    const failure = error as { code?: number; message?: string }
    if (failure.code === 404) {
      notFound.value = true
    } else {
      errorText.value = failure.message ?? '加载失败'
    }
    detail.value = null
  } finally {
    if (controller === current) {
      loading.value = false
    }
  }
}

watch(
  () => route.params.id,
  () => {
    void load()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  controller?.abort()
})

async function toggleFavorite(): Promise<void> {
  const current = detail.value
  if (!current) {
    return
  }
  if (!userStore.isLoggedIn) {
    ElMessage.warning('登录后才能收藏菜谱')
    void router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  favoriteBusy.value = true
  try {
    if (current.favorited) {
      await unfavoriteRecipe(current.id)
      current.favorited = false
      current.favoriteCount = Math.max(0, (current.favoriteCount ?? 1) - 1)
      ElMessage.success('已取消收藏')
    } else {
      await favoriteRecipe(current.id)
      current.favorited = true
      current.favoriteCount = (current.favoriteCount ?? 0) + 1
      ElMessage.success('已加入收藏')
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    favoriteBusy.value = false
  }
}

async function submitRating(value: number): Promise<void> {
  const current = detail.value
  if (!current) {
    return
  }
  if (!userStore.isLoggedIn) {
    ElMessage.warning('登录后才能评分')
    myScore.value = 0
    void router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  ratingBusy.value = true
  try {
    // 后端返回更新后的详情，直接用返回值刷新，避免自己算平均分算错
    detail.value = await rateRecipe(current.id, value)
    myScore.value = value
    ElMessage.success(`已评 ${value} 星`)
  } catch {
    myScore.value = 0
  } finally {
    ratingBusy.value = false
  }
}
</script>

<template>
  <div class="page">
    <el-button text :icon="ArrowLeft" style="margin-bottom: 12px" @click="router.back()">
      返回
    </el-button>

    <LoadingState v-if="loading" :rows="6" />

    <EmptyState
      v-else-if="notFound"
      title="菜谱不存在或已被删除"
      description="换一道菜看看吧"
    >
      <el-button type="primary" @click="router.push('/recipes')">回到菜谱库</el-button>
    </EmptyState>

    <el-result v-else-if="errorText" icon="error" title="菜谱加载失败" :sub-title="errorText">
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>

    <article v-else-if="detail" class="detail">
      <div class="detail__hero surface">
        <RecipeCover
          :title="detail.title"
          :cover-url="detail.coverUrl"
          height="300px"
          radius="16px 16px 0 0"
          eager
          :show-title="false"
        />

        <div class="detail__intro">
          <h1 class="detail__title">{{ detail.title }}</h1>
          <p v-if="detail.description" class="detail__desc">{{ detail.description }}</p>

          <div class="detail__stats">
            <span class="stat">
              <em>难度</em>
              <el-tag size="small" :type="difficultyTagType(detail.difficulty)" effect="light">
                {{ detail.difficultyLabel || '—' }}
              </el-tag>
            </span>
            <span class="stat">
              <em>耗时</em>
              <strong class="tnum">{{ formatMinutes(detail.cookMinutes) }}</strong>
            </span>
            <span class="stat">
              <em>份量</em>
              <strong class="tnum">{{ formatNumber(detail.servings) }} 人份</strong>
            </span>
            <span class="stat">
              <em>热量</em>
              <strong class="tnum">{{ formatNumber(detail.calories) }} 千卡</strong>
            </span>
            <span class="stat">
              <em>评分</em>
              <strong class="tnum">
                {{ formatRating(detail.ratingAvg) }}
                <span class="muted">（{{ detail.ratingCount ?? 0 }} 人）</span>
              </strong>
            </span>
            <span class="stat">
              <em>浏览</em>
              <strong class="tnum">{{ detail.viewCount ?? 0 }}</strong>
            </span>
          </div>

          <div v-if="detail.tags.length" class="chip-row">
            <RouterLink
              v-for="tag in detail.tags"
              :key="tag"
              :to="{ path: '/recipes', query: { keyword: tag } }"
            >
              <el-tag effect="plain" round>{{ tag }}</el-tag>
            </RouterLink>
          </div>

          <div class="detail__actions">
            <el-button
              :type="detail.favorited ? 'warning' : 'primary'"
              :loading="favoriteBusy"
              :icon="detail.favorited ? StarFilled : Star"
              @click="toggleFavorite"
            >
              {{ detail.favorited ? '已收藏' : '收藏这道菜' }}
            </el-button>
            <el-button :icon="Bowl" @click="router.push('/menu')">用它配一桌</el-button>
          </div>

          <div class="rating">
            <span class="rating__label">给它打个分：</span>
            <el-rate
              v-model="myScore"
              :disabled="ratingBusy"
              :colors="['#ffa62b', '#ff8c42', '#ff6b35']"
              @change="submitRating"
            />
            <span v-if="detail.favoriteCount" class="muted">
              · {{ detail.favoriteCount }} 人收藏
            </span>
          </div>
        </div>
      </div>

      <section class="section">
        <h2 class="section-title">食材清单</h2>
        <ul v-if="ingredientRows.length" class="ingredients surface">
          <li v-for="(item, index) in ingredientRows" :key="`${item.name}-${index}`">
            <span class="ingredients__name">
              {{ item.name }}
              <el-tag v-if="item.main" size="small" effect="plain" type="warning">主料</el-tag>
            </span>
            <span class="ingredients__amount tnum">
              {{ item.amount || '适量' }} {{ item.unit ?? '' }}
            </span>
          </li>
        </ul>
        <EmptyState v-else title="这道菜还没填食材" description="去后台补一下吧" />
      </section>

      <section class="section">
        <h2 class="section-title">做法步骤</h2>
        <ol v-if="stepRows.length" class="steps">
          <li v-for="(step, index) in stepRows" :key="index" class="steps__item surface">
            <span class="steps__no tnum">{{ step.stepNo ?? index + 1 }}</span>
            <div class="steps__body">
              <p class="prewrap">{{ step.content }}</p>
              <img
                v-if="step.imageUrl"
                class="steps__img"
                :src="step.imageUrl"
                :alt="`步骤 ${step.stepNo ?? index + 1}`"
                loading="lazy"
              />
            </div>
          </li>
        </ol>
        <EmptyState v-else title="这道菜还没写步骤" description="去后台补一下吧" />
      </section>

      <section class="section">
        <div class="menu-banner">
          <div>
            <h2>还想再来两道？</h2>
            <p class="muted">
              把这桌菜交给「今天吃什么」：填上忌口和规格，自动配齐并生成采购清单。
            </p>
          </div>
          <el-button type="primary" :icon="ShoppingCart" @click="router.push('/menu')">
            去随机配餐
          </el-button>
        </div>
      </section>
    </article>
  </div>
</template>

<style scoped>
.detail__hero {
  overflow: hidden;
}

.detail__intro {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
}

.detail__title {
  font-size: 28px;
}

.detail__desc {
  margin: 0;
  color: var(--foodie-text-muted);
  font-size: 14px;
  line-height: 1.75;
}

.detail__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 26px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #fffaf6;
  border: 1px dashed var(--foodie-border);
}

.stat {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.stat em {
  color: var(--foodie-text-muted);
  font-style: normal;
}

.detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.rating {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.rating__label {
  color: var(--foodie-text-muted);
}

.ingredients {
  display: grid;
  gap: 0;
  grid-template-columns: 1fr;
  margin: 0;
  padding: 6px 18px;
  list-style: none;
}

.ingredients li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--foodie-border);
}

.ingredients li:last-child {
  border-bottom: none;
}

.ingredients__name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.ingredients__amount {
  color: var(--foodie-text-muted);
}

.steps {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.steps__item {
  display: flex;
  gap: 14px;
  padding: 16px 18px;
}

.steps__no {
  display: grid;
  width: 30px;
  height: 30px;
  flex: none;
  place-items: center;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--foodie-accent), var(--foodie-primary));
  color: #fff;
  font-weight: 700;
}

.steps__body {
  flex: 1;
  min-width: 0;
}

.steps__body p {
  margin: 4px 0 0;
  line-height: 1.8;
}

.steps__img {
  width: 100%;
  max-width: 420px;
  margin-top: 10px;
  border-radius: 10px;
}

.menu-banner {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 22px;
  border: 1px solid #ffe0cd;
  border-radius: 18px;
  background: linear-gradient(120deg, #fff6ee, #ffe9d6);
}

.menu-banner h2 {
  font-size: 20px;
}

.menu-banner p {
  margin: 6px 0 0;
  max-width: 560px;
}

@media (min-width: 700px) {
  .ingredients {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    column-gap: 28px;
  }
}
</style>
