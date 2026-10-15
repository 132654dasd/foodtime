<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CopyDocument, MagicStick, Refresh, ShoppingCart } from '@element-plus/icons-vue'

import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import RecipeCover from '@/components/RecipeCover.vue'
import { isCanceled, randomMenu, rerollMenu, shoppingList } from '@/api'
import type { MenuRequest, MenuResponse, MenuStrategy, ShoppingItem } from '@/api/types'
import { useMetaStore } from '@/stores/meta'
import { useUserStore } from '@/stores/user'
import { copyText } from '@/utils/clipboard'
import { difficultyTagType, formatMinutes, formatNumber } from '@/utils/format'

const router = useRouter()
const metaStore = useMetaStore()
const userStore = useUserStore()

const STRATEGIES: Array<{ value: MenuStrategy; label: string; hint: string }> = [
  { value: 'RANDOM', label: '纯随机', hint: '完全随机抽，可能全是荤菜' },
  { value: 'BALANCED', label: '荤素均衡', hint: '按荤素比例搭配，最像一桌正经饭' },
  { value: 'QUICK', label: '快手菜', hint: '优先挑 30 分钟内能做完的' },
]

const DRAW_HINTS = [
  '正在翻开冰箱…',
  '按忌口过筛…',
  '搭配荤素比例…',
  '算一算这桌菜的热量…',
  '快好了，摆盘中…',
]

// ------------------------------------------------------------ 表单状态

const avoidSelected = ref<string[]>([])
const customAvoid = ref('')
const dishCount = ref(3)
const soupCount = ref(1)
const strategy = ref<MenuStrategy>('BALANCED')
const excludeRecent = ref(true)
const savingAvoid = ref(false)

const drawing = ref(false)
const drawingHint = ref(DRAW_HINTS[0] ?? '')
const result = ref<MenuResponse | null>(null)
const errorText = ref('')
const rerollKey = ref('')
const summaryStale = ref(false)

const shoppingOpen = ref(false)
const shoppingLoading = ref(false)
const shoppingItems = ref<ShoppingItem[]>([])

let controller: AbortController | null = null
let hintTimer: number | undefined

const presetAvoids = computed(() => metaStore.avoidSuggestions)
const customAvoids = computed(() =>
  avoidSelected.value.filter(
    (item) => !presetAvoids.value.some((preset) => preset.value === item),
  ),
)
const totalCount = computed(() => dishCount.value + soupCount.value)

const resultSections = computed(() => {
  const current = result.value
  if (!current) {
    return []
  }
  return [
    { key: 'dish' as const, title: '菜', items: current.dishes },
    { key: 'soup' as const, title: '汤', items: current.soups },
  ].filter((section) => section.items.length > 0)
})

const allPickedIds = computed(() => {
  const current = result.value
  if (!current) {
    return []
  }
  return [...current.dishes, ...current.soups].map((item) => item.id)
})

const selectedStrategyHint = computed(
  () => STRATEGIES.find((item) => item.value === strategy.value)?.hint ?? '',
)

// ------------------------------------------------------------ 初始化

onMounted(async () => {
  // 忌口建议 + 账号里保存的忌口
  void metaStore.ensure().catch(() => undefined)
  await userStore.ensureUser()
  avoidSelected.value = [...userStore.avoidances]
})

onBeforeUnmount(() => {
  controller?.abort()
  stopHintTimer()
})

// ------------------------------------------------------------ 忌口

function toggleAvoid(value: string): void {
  if (avoidSelected.value.includes(value)) {
    avoidSelected.value = avoidSelected.value.filter((item) => item !== value)
  } else {
    avoidSelected.value = [...avoidSelected.value, value]
  }
}

function addCustomAvoid(): void {
  const value = customAvoid.value.trim()
  if (!value) {
    return
  }
  if (!avoidSelected.value.includes(value)) {
    avoidSelected.value = [...avoidSelected.value, value]
  }
  customAvoid.value = ''
}

function removeAvoid(value: string): void {
  avoidSelected.value = avoidSelected.value.filter((item) => item !== value)
}

async function saveMyAvoidances(): Promise<void> {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('登录后才能保存忌口偏好')
    return
  }
  savingAvoid.value = true
  try {
    await userStore.saveAvoidances(normalizedAvoid())
    ElMessage.success('忌口偏好已保存，以后配餐会自动带上')
  } catch {
    /* 拦截器已提示 */
  } finally {
    savingAvoid.value = false
  }
}

function normalizedAvoid(): string[] {
  return Array.from(new Set(avoidSelected.value.map((item) => item.trim()).filter(Boolean)))
}

// ------------------------------------------------------------ 抽卡动画

function startHintTimer(): void {
  stopHintTimer()
  let index = 0
  drawingHint.value = DRAW_HINTS[0] ?? ''
  hintTimer = window.setInterval(() => {
    index = (index + 1) % DRAW_HINTS.length
    drawingHint.value = DRAW_HINTS[index] ?? ''
  }, 700)
}

function stopHintTimer(): void {
  if (hintTimer !== undefined) {
    window.clearInterval(hintTimer)
    hintTimer = undefined
  }
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

// ------------------------------------------------------------ 配餐

function buildRequest(dish: number, soup: number): MenuRequest {
  return {
    dishCount: dish,
    soupCount: soup,
    avoid: normalizedAvoid(),
    strategy: strategy.value,
    excludeRecent: excludeRecent.value,
  }
}

async function draw(): Promise<void> {
  if (totalCount.value === 0) {
    ElMessage.warning('菜和汤至少选一样，不然没法配餐')
    return
  }

  controller?.abort()
  const current = new AbortController()
  controller = current

  drawing.value = true
  errorText.value = ''
  startHintTimer()

  try {
    // 动画至少放 1.6s，避免接口太快导致画面一闪而过
    const [response] = await Promise.all([
      randomMenu(buildRequest(dishCount.value, soupCount.value), { signal: current.signal }),
      wait(1600),
    ])
    if (controller !== current) {
      return
    }
    result.value = response
    summaryStale.value = false
  } catch (error) {
    if (controller !== current || isCanceled(error)) {
      return
    }
    errorText.value = error instanceof Error ? error.message : '配餐失败，请稍后再试'
    result.value = null
  } finally {
    if (controller === current) {
      drawing.value = false
      stopHintTimer()
    }
  }
}

/** 换一道：`dishCount=1 / soupCount=0` 表示换菜，反过来表示换汤 */
async function reroll(kind: 'dish' | 'soup', index: number): Promise<void> {
  const current = result.value
  if (!current) {
    return
  }
  const key = `${kind}-${index}`
  if (rerollKey.value) {
    return
  }
  rerollKey.value = key

  try {
    const candidates = await rerollMenu(
      buildRequest(kind === 'dish' ? 1 : 0, kind === 'soup' ? 1 : 0),
      allPickedIds.value,
    )
    const picked = candidates[0]
    if (!picked) {
      ElMessage.warning('池子里没有别的菜可以换了，放宽一下忌口试试')
      return
    }
    if (kind === 'dish') {
      current.dishes.splice(index, 1, picked)
    } else {
      current.soups.splice(index, 1, picked)
    }
    recomputeSummary()
    ElMessage.success(`换成「${picked.title}」`)
  } catch {
    /* 拦截器已提示 */
  } finally {
    rerollKey.value = ''
  }
}

/**
 * 换过菜之后后端那份 summary 就过期了，这里按当前卡片重算数值部分。
 * `appliedAvoid` / `warnings` 仍沿用服务端返回的（那是后端才知道的信息）。
 */
function recomputeSummary(): void {
  const current = result.value
  if (!current) {
    return
  }
  const items = [...current.dishes, ...current.soups]
  const difficultyMix: Record<string, number> = {}
  const categoryMix: Record<string, number> = {}
  let totalCalories = 0
  let totalMinutes = 0
  let totalServings = 0

  for (const item of items) {
    totalCalories += item.calories ?? 0
    totalMinutes += item.cookMinutes ?? 0
    totalServings += item.servings ?? 0
    const difficulty = item.difficultyLabel ?? '未知'
    difficultyMix[difficulty] = (difficultyMix[difficulty] ?? 0) + 1
    const category = item.category ?? '其他'
    categoryMix[category] = (categoryMix[category] ?? 0) + 1
  }

  current.summary = {
    ...current.summary,
    dishCount: current.dishes.length,
    soupCount: current.soups.length,
    totalCalories,
    totalMinutes,
    totalServings,
    difficultyMix,
    categoryMix,
  }
  summaryStale.value = true
}

// ------------------------------------------------------------ 采购清单

async function openShoppingList(): Promise<void> {
  if (allPickedIds.value.length === 0) {
    ElMessage.warning('先生成一桌菜吧')
    return
  }
  shoppingOpen.value = true
  if (shoppingItems.value.length > 0) {
    return
  }
  shoppingLoading.value = true
  try {
    shoppingItems.value = await shoppingList(allPickedIds.value)
  } catch {
    /* 拦截器已提示 */
  } finally {
    shoppingLoading.value = false
  }
}

const shoppingText = computed(() => {
  const current = result.value
  const lines: string[] = ['【食光 Foodie】采购清单', '']
  for (const item of shoppingItems.value) {
    const amount = `${item.amount ?? '适量'} ${item.unit ?? ''}`.trim()
    lines.push(`- ${item.name} ${amount}（用于：${item.usedIn.join('、')}）`)
  }
  if (current) {
    lines.push('', `共 ${allPickedIds.value.length} 道菜`)
  }
  return lines.join('\n')
})

async function copyShoppingList(): Promise<void> {
  const ok = await copyText(shoppingText.value)
  if (ok) {
    ElMessage.success('采购清单已复制到剪贴板')
  } else {
    ElMessage.warning('当前浏览器不允许自动复制，请手动选中复制')
  }
}

function gotoRecipe(id: number): void {
  void router.push(`/recipes/${id}`)
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">今天吃什么</h1>
        <p class="page-subtitle">
          告诉食光你的忌口和规格，剩下的交给它（最多十菜三汤）
        </p>
      </div>
    </div>

    <div class="planner">
      <!-- 左：配置 -->
      <section class="planner__panel surface">
        <h2 class="panel-title">1. 忌口（可多选 / 可自定义）</h2>
        <div class="chip-row">
          <el-check-tag
            v-for="item in presetAvoids"
            :key="item.value"
            :checked="avoidSelected.includes(item.value)"
            @change="toggleAvoid(item.value)"
          >
            {{ item.label }}
          </el-check-tag>
          <span v-if="presetAvoids.length === 0" class="muted">字典加载中…</span>
        </div>

        <div class="avoid-custom">
          <el-input
            v-model="customAvoid"
            placeholder="自定义忌口，如「芹菜」"
            maxlength="20"
            @keyup.enter="addCustomAvoid"
          />
          <el-button @click="addCustomAvoid">添加</el-button>
        </div>

        <div v-if="customAvoids.length" class="chip-row">
          <el-tag
            v-for="item in customAvoids"
            :key="item"
            closable
            type="warning"
            effect="light"
            @close="removeAvoid(item)"
          >
            {{ item }}
          </el-tag>
        </div>

        <div class="panel-actions">
          <el-button text size="small" :loading="savingAvoid" @click="saveMyAvoidances">
            保存为我的忌口偏好
          </el-button>
          <span v-if="userStore.avoidances.length" class="muted">
            账号已保存：{{ userStore.avoidances.join('、') }}
          </span>
        </div>

        <h2 class="panel-title">2. 规格</h2>
        <div class="count-row">
          <span class="count-label">菜</span>
          <el-slider v-model="dishCount" :min="0" :max="10" :step="1" show-stops />
          <el-input-number v-model="dishCount" :min="0" :max="10" size="small" />
        </div>
        <div class="count-row">
          <span class="count-label">汤</span>
          <el-slider v-model="soupCount" :min="0" :max="3" :step="1" show-stops />
          <el-input-number v-model="soupCount" :min="0" :max="3" size="small" />
        </div>
        <p class="muted small">
          当前：{{ dishCount }} 菜 {{ soupCount }} 汤（菜 0-10，汤 0-3，两者不能同时为 0）
        </p>

        <h2 class="panel-title">3. 策略</h2>
        <el-radio-group v-model="strategy">
          <el-radio-button
            v-for="item in STRATEGIES"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
        <p class="muted small">{{ selectedStrategyHint }}</p>

        <div class="switch-row">
          <el-switch v-model="excludeRecent" />
          <span>尽量不重复上次（避开最近 3 次配过的菜）</span>
        </div>

        <div class="draw-actions">
          <el-button
            type="primary"
            size="large"
            :icon="MagicStick"
            :loading="drawing"
            @click="draw"
          >
            帮我决定
          </el-button>
          <el-button
            size="large"
            :icon="ShoppingCart"
            :disabled="allPickedIds.length === 0"
            @click="openShoppingList"
          >
            生成采购清单
          </el-button>
        </div>
      </section>

      <!-- 右：结果 -->
      <section class="planner__result">
        <div v-if="drawing" class="drawing surface">
          <div class="drawing__ring">
            <span class="drawing__core">
              <el-icon :size="26"><MagicStick /></el-icon>
            </span>
          </div>
          <div class="drawing__cards">
            <span v-for="index in 3" :key="index" class="drawing__card" :style="{ animationDelay: `${index * 0.12}s` }" />
          </div>
          <p class="drawing__hint">{{ drawingHint }}</p>
        </div>

        <el-result
          v-else-if="errorText"
          icon="error"
          title="配餐失败"
          :sub-title="errorText"
        >
          <template #extra>
            <el-button type="primary" @click="draw">再试一次</el-button>
          </template>
        </el-result>

        <EmptyState
          v-else-if="!result"
          title="还没有配餐结果"
          description="左边勾好忌口和规格，点「帮我决定」试试手气"
        />

        <template v-else>
          <!-- warnings 必须显著展示：后端池子不足时会降级并说明原因 -->
          <el-alert
            v-if="result.warnings.length > 0"
            class="warnings"
            type="warning"
            :closable="false"
            show-icon
            title="这桌菜有降级，请注意"
          >
            <ul class="warnings__list">
              <li v-for="(warning, index) in result.warnings" :key="index">{{ warning }}</li>
            </ul>
          </el-alert>

          <div class="summary surface">
            <div class="summary__head">
              <h2 class="panel-title" style="margin: 0">这桌菜的汇总</h2>
              <span v-if="summaryStale" class="muted small">换过菜，汇总已按当前结果重算</span>
            </div>
            <div class="summary__stats">
              <div class="summary__stat">
                <em>总热量</em>
                <strong class="tnum">{{ result.summary.totalCalories }}</strong>
                <span>千卡</span>
              </div>
              <div class="summary__stat">
                <em>总耗时</em>
                <strong class="tnum">{{ result.summary.totalMinutes }}</strong>
                <span>分钟</span>
              </div>
              <div class="summary__stat">
                <em>总份量</em>
                <strong class="tnum">{{ result.summary.totalServings }}</strong>
                <span>人份</span>
              </div>
              <div class="summary__stat">
                <em>忌口排除</em>
                <strong class="tnum">{{ result.excludedCount }}</strong>
                <span>道菜</span>
              </div>
            </div>

            <div class="summary__row">
              <span class="summary__label">难度分布</span>
              <span class="chip-row">
                <el-tag
                  v-for="(count, label) in result.summary.difficultyMix"
                  :key="label"
                  effect="light"
                  round
                >
                  {{ label }} × {{ count }}
                </el-tag>
                <span v-if="Object.keys(result.summary.difficultyMix).length === 0" class="muted">
                  —
                </span>
              </span>
            </div>

            <div class="summary__row">
              <span class="summary__label">分类分布</span>
              <span class="chip-row">
                <el-tag
                  v-for="(count, label) in result.summary.categoryMix"
                  :key="label"
                  effect="plain"
                  round
                >
                  {{ label }} × {{ count }}
                </el-tag>
                <span v-if="Object.keys(result.summary.categoryMix).length === 0" class="muted">
                  —
                </span>
              </span>
            </div>

            <div class="summary__row">
              <span class="summary__label">生效忌口</span>
              <span class="chip-row">
                <el-tag
                  v-for="item in result.summary.appliedAvoid"
                  :key="item"
                  type="danger"
                  effect="light"
                  round
                >
                  {{ item }}
                </el-tag>
                <span v-if="result.summary.appliedAvoid.length === 0" class="muted">
                  未启用忌口
                </span>
              </span>
            </div>
          </div>

          <div v-for="section in resultSections" :key="section.key" class="menu-section">
            <h2 class="section-title">
              {{ section.title }}
              <span class="muted small">共 {{ section.items.length }} 道</span>
            </h2>
            <div class="menu-items">
              <div
                v-for="(item, index) in section.items"
                :key="item.id"
                class="menu-item surface"
              >
                <RecipeCover
                  :title="item.title"
                  :cover-url="item.coverUrl"
                  height="128px"
                  radius="14px 14px 0 0"
                  :show-title="false"
                />
                <div class="menu-item__body">
                  <button class="menu-item__title" type="button" @click="gotoRecipe(item.id)">
                    {{ item.title }}
                  </button>
                  <div class="menu-item__meta">
                    <el-tag size="small" :type="difficultyTagType(item.difficulty)" effect="light">
                      {{ item.difficultyLabel || '—' }}
                    </el-tag>
                    <span class="muted">{{ formatMinutes(item.cookMinutes) }}</span>
                    <span class="muted tnum">{{ formatNumber(item.calories) }} 千卡</span>
                  </div>
                  <div class="menu-item__foot">
                    <span class="muted small">{{ item.category }} · {{ item.cuisine }}</span>
                    <el-button
                      text
                      size="small"
                      :icon="Refresh"
                      :loading="rerollKey === `${section.key}-${index}`"
                      @click="reroll(section.key, index)"
                    >
                      换一道
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="result-actions">
            <el-button :icon="ShoppingCart" @click="openShoppingList">生成采购清单</el-button>
            <el-button text @click="draw">重新配一桌</el-button>
          </div>
        </template>
      </section>
    </div>

    <el-dialog v-model="shoppingOpen" title="采购清单" width="640px">
      <LoadingState v-if="shoppingLoading" :rows="4" />
      <template v-else>
        <p class="muted small">
          共 {{ shoppingItems.length }} 项，来自 {{ allPickedIds.length }} 道菜；同名同单位的用量已相加。
        </p>
        <ul v-if="shoppingItems.length" class="shopping-list">
          <li v-for="(item, index) in shoppingItems" :key="`${item.name}-${index}`">
            <div class="shopping-list__head">
              <strong>{{ item.name }}</strong>
              <span class="tnum">{{ item.amount || '适量' }} {{ item.unit ?? '' }}</span>
            </div>
            <div class="shopping-list__meta">
              <span class="muted">用于：{{ item.usedIn.join('、') }}</span>
              <el-tag v-if="item.mergeable" size="small" type="success" effect="plain">
                已合并
              </el-tag>
            </div>
          </li>
        </ul>
        <EmptyState v-else title="清单是空的" description="这桌菜好像还没配" />
      </template>
      <template #footer>
        <el-button @click="shoppingOpen = false">关闭</el-button>
        <el-button
          type="primary"
          :icon="CopyDocument"
          :disabled="shoppingItems.length === 0"
          @click="copyShoppingList"
        >
          复制清单
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.planner {
  display: grid;
  gap: 20px;
  grid-template-columns: 1fr;
}

@media (min-width: 980px) {
  .planner {
    grid-template-columns: 380px minmax(0, 1fr);
    align-items: start;
  }
}

.planner__panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  position: sticky;
  top: 76px;
}

.planner__result {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.panel-title {
  font-size: 15px;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.small {
  font-size: 12px;
}

.avoid-custom {
  display: flex;
  gap: 8px;
}

.count-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.count-label {
  flex: none;
  width: 20px;
  font-weight: 700;
  color: var(--foodie-primary);
}

.count-row :deep(.el-slider) {
  flex: 1;
}

.switch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--foodie-text-muted);
}

.draw-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

/* ---------------- 抽卡动画 ---------------- */

.drawing {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  padding: 48px 20px;
}

.drawing__ring {
  display: grid;
  width: 96px;
  height: 96px;
  place-items: center;
  border-radius: 50%;
  background: conic-gradient(var(--foodie-primary), var(--foodie-accent), var(--foodie-primary));
  animation: spin 1.4s linear infinite;
}

.drawing__core {
  display: grid;
  width: 76px;
  height: 76px;
  place-items: center;
  border-radius: 50%;
  background: #fff;
  color: var(--foodie-primary);
}

.drawing__cards {
  display: flex;
  gap: 10px;
}

.drawing__card {
  width: 54px;
  height: 74px;
  border-radius: 10px;
  background: linear-gradient(160deg, #ffb347, #ff6b35);
  animation: flip 1.1s ease-in-out infinite;
}

.drawing__hint {
  margin: 0;
  color: var(--foodie-text-muted);
  font-size: 13px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes flip {
  0%,
  100% {
    transform: rotateY(0deg);
    opacity: 0.75;
  }
  50% {
    transform: rotateY(180deg);
    opacity: 1;
  }
}

/* ---------------- 结果 ---------------- */

.warnings {
  border-radius: 12px;
}

.warnings__list {
  margin: 4px 0 0;
  padding-left: 18px;
  line-height: 1.8;
}

.summary {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
}

.summary__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.summary__stats {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (min-width: 640px) {
  .summary__stats {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

.summary__stat {
  display: flex;
  flex-direction: column;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fffaf6;
  border: 1px solid var(--foodie-border);
}

.summary__stat em {
  color: var(--foodie-text-muted);
  font-size: 12px;
  font-style: normal;
}

.summary__stat strong {
  font-size: 22px;
  line-height: 1.3;
}

.summary__stat span {
  color: var(--foodie-text-muted);
  font-size: 12px;
}

.summary__row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.summary__label {
  flex: none;
  width: 64px;
  color: var(--foodie-text-muted);
  font-size: 13px;
}

.menu-items {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
}

.menu-item {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.menu-item__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px 12px;
}

.menu-item__title {
  padding: 0;
  border: none;
  background: none;
  color: var(--foodie-text);
  cursor: pointer;
  font-size: 15px;
  font-weight: 700;
  text-align: left;
}

.menu-item__title:hover {
  color: var(--foodie-primary);
}

.menu-item__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.menu-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-top: auto;
  padding-top: 6px;
  border-top: 1px dashed var(--foodie-border);
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

/* ---------------- 采购清单 ---------------- */

.shopping-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 46vh;
  margin: 10px 0 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.shopping-list li {
  padding: 10px 12px;
  border: 1px solid var(--foodie-border);
  border-radius: 10px;
  background: #fffaf6;
}

.shopping-list__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.shopping-list__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
  font-size: 12px;
}
</style>
