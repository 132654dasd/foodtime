<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'

import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import RecipeCover from '@/components/RecipeCover.vue'
import {
  addFridgeItem,
  deleteFridgeItem,
  fridgeCatalog,
  fridgeSuggestions,
  isCanceled,
  listFridgeItems,
  updateFridgeItem,
} from '@/api'
import type {
  CatalogItemVO,
  FridgeItemVO,
  FridgeStock,
  FridgeSuggestionVO,
  RecipeMatchVO,
} from '@/api/types'
import { difficultyTagType, formatDate, formatMinutes, formatNumber } from '@/utils/format'

/**
 * 单位下拉。
 *
 * <p>必须与后端 `com.foodie.price.UnitConverter` 的白名单一致：
 * 只有这些单位能换算成克，才能参与「够不够」的判断。
 * 选了别的写法（或干脆不填）只会退化成「有就行」——不会报错，但也判断不了数量。
 */
const UNIT_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: '不限（有就行）' },
  { value: 'g', label: '克' },
  { value: 'kg', label: '千克' },
  { value: '斤', label: '斤' },
  { value: '两', label: '两' },
  { value: 'ml', label: '毫升' },
  { value: 'l', label: '升' },
  { value: '勺', label: '大勺' },
  { value: '小勺', label: '小勺' },
  { value: '个', label: '个' },
  { value: '根', label: '根' },
  { value: '片', label: '片' },
  { value: '段', label: '段' },
  { value: '瓣', label: '瓣' },
  { value: '只', label: '只' },
  { value: '粒', label: '粒' },
  { value: '块', label: '块' },
  { value: '棵', label: '棵' },
  { value: '条', label: '条' },
  { value: '朵', label: '朵' },
  { value: '颗', label: '颗' },
]

const STOCK_LABEL: Record<FridgeStock, string> = {
  ENOUGH: '够',
  PRESENT: '有',
  NOT_ENOUGH: '量不够',
}

// ------------------------------------------------------------ 冰箱

const items = ref<FridgeItemVO[]>([])
const catalog = ref<CatalogItemVO[]>([])
const loading = ref(true)
const loadError = ref('')

const addName = ref('')
const addQuantity = ref<number | undefined>(undefined)
const addUnit = ref('')
const addExpire = ref<string | null>(null)
const adding = ref(false)

const editOpen = ref(false)
const editTarget = ref<FridgeItemVO | null>(null)
const editQuantity = ref<number | undefined>(undefined)
const editUnit = ref('')
const editExpire = ref<string | null>(null)
const editSaving = ref(false)
const deletingId = ref<number | null>(null)

// ------------------------------------------------------------ 推荐

const suggestion = ref<FridgeSuggestionVO | null>(null)
const suggestLoading = ref(false)
const onlyCookable = ref(false)

const expiringItems = computed(() => items.value.filter((item) => item.expiringSoon))

onMounted(async () => {
  await loadFridge()
  void loadCatalog()
  void loadSuggestion()
})

// ------------------------------------------------------------ 数据加载

async function loadFridge(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    items.value = await listFridgeItems()
  } catch (error) {
    if (!isCanceled(error)) {
      loadError.value = error instanceof Error ? error.message : '加载失败'
    }
  } finally {
    loading.value = false
  }
}

async function loadCatalog(): Promise<void> {
  try {
    catalog.value = await fridgeCatalog()
  } catch {
    // 字典拉不到不影响主体，输入框仍然是自由文本
  }
}

async function loadSuggestion(): Promise<void> {
  suggestLoading.value = true
  try {
    suggestion.value = await fridgeSuggestions({
      limit: 12,
      onlyCookable: onlyCookable.value,
    })
  } catch {
    /* 拦截器已提示 */
  } finally {
    suggestLoading.value = false
  }
}

/** 冰箱变了，两边都要刷：列表要更新，推荐结果也要按新食材重算。 */
async function refreshAll(): Promise<void> {
  await loadFridge()
  await loadSuggestion()
}

// ------------------------------------------------------------ 添加

async function submitAdd(): Promise<void> {
  const name = addName.value.trim()
  if (!name) {
    ElMessage.warning('先写一个食材名')
    return
  }
  adding.value = true
  try {
    await addFridgeItem({
      name,
      quantity: addQuantity.value ?? null,
      unit: addUnit.value,
      expireAt: addExpire.value,
    })
    ElMessage.success(`已放进冰箱：${name}`)
    addName.value = ''
    addQuantity.value = undefined
    addUnit.value = ''
    addExpire.value = null
    await refreshAll()
  } catch {
    /* 409「已经有了」由拦截器提示，用户直接改数量即可 */
  } finally {
    adding.value = false
  }
}

// ------------------------------------------------------------ 编辑 / 删除

function openEdit(item: FridgeItemVO): void {
  editTarget.value = item
  editQuantity.value = item.quantity ?? undefined
  editUnit.value = item.unit
  editExpire.value = item.expireAt
  editOpen.value = true
}

async function saveEdit(): Promise<void> {
  const target = editTarget.value
  if (!target) {
    return
  }
  editSaving.value = true
  try {
    // PUT 是整体替换：null 就是清空，所以三个字段原样提交
    await updateFridgeItem(target.id, {
      quantity: editQuantity.value ?? null,
      unit: editUnit.value,
      expireAt: editExpire.value,
    })
    editOpen.value = false
    ElMessage.success('已更新')
    await refreshAll()
  } catch {
    /* 拦截器已提示 */
  } finally {
    editSaving.value = false
  }
}

async function removeItem(item: FridgeItemVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定把「${item.name}」从冰箱里移除吗？`, '移除食材', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  deletingId.value = item.id
  try {
    await deleteFridgeItem(item.id)
    ElMessage.success('已移除')
    await refreshAll()
  } catch {
    /* 拦截器已提示 */
  } finally {
    deletingId.value = null
  }
}

// ------------------------------------------------------------ 展示辅助

function stockLabel(stock: FridgeStock): string {
  return STOCK_LABEL[stock]
}

function stockTagType(stock: FridgeStock): 'success' | 'info' | 'warning' {
  switch (stock) {
    case 'ENOUGH':
      return 'success'
    case 'NOT_ENOUGH':
      return 'warning'
    default:
      return 'info'
  }
}

/** 名字太多时只显示前几个，剩下的用「等 N 样」概括。 */
function shortNames(names: string[], limit: number): string {
  if (names.length <= limit) {
    return names.join('、')
  }
  return `${names.slice(0, limit).join('、')} 等 ${names.length} 样`
}

function quantityText(item: FridgeItemVO): string {
  if (item.quantity === null || item.unit === '') {
    return '数量不限'
  }
  return `${item.quantity} ${item.unit}`
}

function matchCandidates(match: RecipeMatchVO): string {
  return `${match.mainMatched}/${match.mainTotal} 样主料`
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">我的冰箱</h1>
        <p class="page-subtitle">
          登记冰箱里有什么，食光会从 30 道菜谱里挑出用得上的，并告诉你还差哪几样、数量够不够
        </p>
      </div>
    </div>

    <div class="fridge-layout">
      <!-- 左：冰箱管理 -->
      <section class="fridge-panel surface">
        <h2 class="panel-title">1. 放进冰箱</h2>
        <div class="add-form">
          <el-select
            v-model="addName"
            class="add-form__name"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="选一个系统食材，或直接输入"
          >
            <el-option
              v-for="entry in catalog"
              :key="entry.name"
              :label="entry.name"
              :value="entry.name"
            >
              <span class="option-name">{{ entry.name }}</span>
              <span class="muted option-meta">{{ entry.recipeCount }} 道菜</span>
            </el-option>
          </el-select>

          <div class="add-form__row">
            <el-input-number
              v-model="addQuantity"
              class="add-form__qty"
              :min="0.01"
              :max="9999"
              :precision="2"
              :step="1"
              controls-position="right"
              placeholder="数量"
            />
            <el-select v-model="addUnit" class="add-form__unit">
              <el-option
                v-for="option in UNIT_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-date-picker
              v-model="addExpire"
              class="add-form__date"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="过期日（可选）"
            />
          </div>

          <el-button type="primary" :icon="Plus" :loading="adding" @click="submitAdd">
            放进去
          </el-button>
        </div>
        <p class="muted small">
          数量与单位都可以不填，表示「有就行」；只有两样都填了，才会去判断「够不够」。
        </p>

        <h2 class="panel-title">
          2. 冰箱里（{{ items.length }} 样）
          <el-button
            text
            size="small"
            :icon="Refresh"
            :loading="suggestLoading"
            @click="refreshAll"
          >
            刷新
          </el-button>
        </h2>

        <LoadingState v-if="loading" :rows="4" />

        <el-result v-else-if="loadError" icon="error" title="冰箱加载失败" :sub-title="loadError">
          <template #extra>
            <el-button type="primary" @click="loadFridge">重试</el-button>
          </template>
        </el-result>

        <EmptyState
          v-else-if="items.length === 0"
          title="冰箱还是空的"
          description="先在上面加几样食材。番茄、鸡蛋、土豆这类最好用，能解锁最多的菜"
        />

        <ul v-else class="fridge-list">
          <li v-for="item in items" :key="item.id" class="fridge-item">
            <div class="fridge-item__head">
              <strong class="fridge-item__name">{{ item.name }}</strong>
              <span class="muted tnum">{{ quantityText(item) }}</span>
            </div>
            <div class="fridge-item__tags">
              <el-tag v-if="item.expired" size="small" type="danger" effect="light">已过期</el-tag>
              <el-tag v-else-if="item.expiringSoon" size="small" type="warning" effect="light">
                快过期
              </el-tag>
              <el-tag v-if="item.expireAt" size="small" effect="plain">
                {{ formatDate(item.expireAt) }}
              </el-tag>
              <el-tag v-if="!item.known" size="small" type="info" effect="plain">
                暂无菜谱用到
              </el-tag>
            </div>
            <div class="fridge-item__actions">
              <el-button text size="small" :icon="Edit" @click="openEdit(item)">修改</el-button>
              <el-button
                text
                size="small"
                type="danger"
                :icon="Delete"
                :loading="deletingId === item.id"
                @click="removeItem(item)"
              >
                移除
              </el-button>
            </div>
          </li>
        </ul>
      </section>

      <!-- 右：推荐结果 -->
      <section class="fridge-result">
        <div v-if="suggestLoading && !suggestion" class="surface result-loading">
          <LoadingState :rows="5" />
        </div>

        <template v-else>
          <div class="match-summary surface">
            <div class="match-summary__stats">
              <div class="match-summary__stat">
                <em>冰箱食材</em>
                <strong class="tnum">{{ suggestion?.fridgeCount ?? items.length }}</strong>
                <span>样</span>
              </div>
              <div class="match-summary__stat">
                <em>现在就能做</em>
                <strong class="tnum">{{ suggestion?.cookableCount ?? 0 }}</strong>
                <span>道</span>
              </div>
              <div class="match-summary__stat">
                <em>能凑出候选</em>
                <strong class="tnum">{{ suggestion?.candidateCount ?? 0 }}</strong>
                <span>道</span>
              </div>
              <div class="match-summary__stat">
                <em>忌口排除</em>
                <strong class="tnum">{{ suggestion?.excludedByAvoidance ?? 0 }}</strong>
                <span>道</span>
              </div>
            </div>
            <div class="match-summary__actions">
              <el-switch
                v-model="onlyCookable"
                active-text="只看现在就能做的"
                @change="loadSuggestion"
              />
              <el-button text :icon="Refresh" :loading="suggestLoading" @click="loadSuggestion">
                重新匹配
              </el-button>
            </div>
          </div>

          <div v-if="expiringItems.length" class="warning-panel">
            <strong>先用掉这几样：</strong>
            {{ expiringItems.map((item) => item.name).join('、') }}
            —— 推荐结果里用到它们的菜会排在前面。
          </div>

          <el-alert
            v-if="suggestion && suggestion.warnings.length > 0"
            class="warnings"
            type="warning"
            :closable="false"
            show-icon
            title="有几件事要说明"
          >
            <ul class="warnings__list">
              <li v-for="(warning, index) in suggestion.warnings" :key="index">{{ warning }}</li>
            </ul>
          </el-alert>

          <EmptyState
            v-if="items.length === 0"
            title="还没有可推荐的菜"
            description="冰箱里一样东西都没有，先去左边加几样吧"
          />

          <EmptyState
            v-else-if="suggestion && suggestion.items.length === 0"
            title="暂时没有匹配的菜谱"
            description="试试再加一两样主料（鸡蛋、番茄、土豆都很好用），或者关掉「只看现在就能做的」"
          />

          <div v-else class="match-grid">
            <RouterLink
              v-for="match in suggestion?.items ?? []"
              :key="match.recipe.id"
              class="match-card surface"
              :to="`/recipes/${match.recipe.id}`"
            >
              <RecipeCover
                :title="match.recipe.title"
                :cover-url="match.recipe.coverUrl"
                height="132px"
                radius="15px 15px 0 0"
                :show-title="false"
              />
              <div class="match-card__body">
                <div class="match-card__head">
                  <h3 class="match-card__title">{{ match.recipe.title }}</h3>
                  <el-tag
                    size="small"
                    :type="match.cookable ? 'success' : 'warning'"
                    effect="light"
                  >
                    {{ match.cookable ? '现在就能做' : `还差 ${match.missingMain.length} 样主料` }}
                  </el-tag>
                </div>

                <div class="match-card__meta">
                  <el-tag size="small" :type="difficultyTagType(match.recipe.difficulty)" effect="light">
                    {{ match.recipe.difficultyLabel || '未知难度' }}
                  </el-tag>
                  <span class="muted">{{ formatMinutes(match.recipe.cookMinutes) }}</span>
                  <span class="muted tnum">{{ formatNumber(match.recipe.calories) }} 千卡</span>
                  <span class="muted tnum">{{ matchCandidates(match) }}</span>
                </div>

                <div v-if="match.matchedIngredients.length" class="match-card__row">
                  <span class="match-card__label">用得上</span>
                  <span class="chip-row">
                    <el-tag
                      v-for="ingredient in match.matchedIngredients"
                      :key="`${ingredient.ingredientName}-${ingredient.fridgeName}`"
                      size="small"
                      :type="stockTagType(ingredient.stock)"
                      effect="plain"
                      round
                    >
                      {{ ingredient.approximate ? `≈ ${ingredient.fridgeName}` : ingredient.fridgeName }}
                      <template v-if="ingredient.approximate">
                        （{{ ingredient.ingredientName }}）
                      </template>
                      <template v-if="ingredient.stock !== 'PRESENT'">
                        · {{ stockLabel(ingredient.stock) }}
                      </template>
                    </el-tag>
                  </span>
                </div>

                <div v-if="match.missingMain.length" class="match-card__row">
                  <span class="match-card__label">还缺主料</span>
                  <span class="missing">{{ shortNames(match.missingMain, 3) }}</span>
                </div>

                <div v-if="match.missingAux.length" class="match-card__row">
                  <span class="match-card__label">缺调料</span>
                  <span class="muted missing">{{ shortNames(match.missingAux, 3) }}</span>
                </div>

                <div class="match-card__foot">
                  <span class="muted small">
                    {{ match.recipe.cuisine || '—' }} · {{ match.recipe.category || '—' }}
                  </span>
                  <el-tag v-if="match.expiringUsed.length" size="small" type="warning" effect="plain">
                    含快过期食材
                  </el-tag>
                </div>
              </div>
            </RouterLink>
          </div>
        </template>
      </section>
    </div>

    <!-- 修改食材 -->
    <el-dialog v-model="editOpen" title="修改食材" width="420px">
      <p v-if="editTarget" class="muted small edit-hint">
        {{ editTarget.name }} · 三个字段会一起提交，清空即表示「不跟踪 / 数量不限」
      </p>
      <el-input-number
        v-model="editQuantity"
        class="edit-field"
        :min="0.01"
        :max="9999"
        :precision="2"
        controls-position="right"
        placeholder="数量（留空 = 有就行）"
      />
      <el-select v-model="editUnit" class="edit-field">
        <el-option
          v-for="option in UNIT_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-date-picker
        v-model="editExpire"
        class="edit-field"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="过期日（留空 = 不跟踪）"
      />
      <template #footer>
        <el-button @click="editOpen = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.fridge-layout {
  display: grid;
  gap: 20px;
  grid-template-columns: 1fr;
}

@media (min-width: 980px) {
  .fridge-layout {
    grid-template-columns: 380px minmax(0, 1fr);
    align-items: start;
  }
}

.fridge-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
}

@media (min-width: 980px) {
  .fridge-panel {
    position: sticky;
    top: 76px;
    max-height: calc(100vh - 100px);
    overflow-y: auto;
  }
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 15px;
}

.small {
  font-size: 12px;
}

.add-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.add-form__name {
  width: 100%;
}

.add-form__row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.add-form__qty {
  width: 120px;
}

.add-form__unit {
  width: 130px;
}

.add-form__date {
  flex: 1;
  min-width: 150px;
}

.option-name {
  float: left;
}

.option-meta {
  float: right;
  font-size: 12px;
}

.fridge-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.fridge-item {
  padding: 10px 12px;
  border: 1px solid var(--foodie-border);
  border-radius: 12px;
  background: #fffaf6;
}

.fridge-item__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.fridge-item__name {
  font-size: 14px;
}

.fridge-item__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.fridge-item__actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 4px;
}

.fridge-result {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.result-loading {
  padding: 20px;
}

.match-summary {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px;
}

.match-summary__stats {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (min-width: 640px) {
  .match-summary__stats {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

.match-summary__stat {
  display: flex;
  flex-direction: column;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fffaf6;
  border: 1px solid var(--foodie-border);
}

.match-summary__stat em {
  color: var(--foodie-text-muted);
  font-size: 12px;
  font-style: normal;
}

.match-summary__stat strong {
  font-size: 22px;
  line-height: 1.3;
}

.match-summary__stat span {
  color: var(--foodie-text-muted);
  font-size: 12px;
}

.match-summary__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.warnings {
  border-radius: 12px;
}

.warnings__list {
  margin: 4px 0 0;
  padding-left: 18px;
  line-height: 1.8;
}

.match-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
}

.match-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
}

/* 整卡可点，与首页 RecipeCard 一致 */
.match-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--foodie-shadow-hover);
}

.match-card:hover .match-card__title {
  color: var(--foodie-primary);
}

.match-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px 14px;
}

.match-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.match-card__title {
  font-size: 16px;
  line-height: 1.35;
}

.match-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.match-card__row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 12px;
}

.match-card__label {
  flex: none;
  color: var(--foodie-text-muted);
}

.missing {
  word-break: break-word;
}

.match-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
  padding-top: 6px;
  border-top: 1px dashed var(--foodie-border);
}

.edit-hint {
  margin: 0 0 12px;
}

.edit-field {
  width: 100%;
  margin-bottom: 10px;
}
</style>
