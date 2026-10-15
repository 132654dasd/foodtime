<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, EditPen, Plus, Refresh, Search } from '@element-plus/icons-vue'
import type { EChartsCoreOption } from 'echarts/core'

import EChart from '@/components/EChart.vue'
import EmptyState from '@/components/EmptyState.vue'
import ImagePicker from '@/components/ImagePicker.vue'
import LoadingState from '@/components/LoadingState.vue'
import {
  adminStats,
  adminUpdateUserStatus,
  adminUsers,
  createRecipe,
  deleteRecipe,
  isCanceled,
  listRecipes,
  recipeDetail,
  updateRecipe,
} from '@/api'
import type {
  AdminUserVO,
  Difficulty,
  RecipeCardVO,
  RecipeSaveRequest,
  StatsVO,
  UploadResult,
} from '@/api/types'
import { useMetaStore } from '@/stores/meta'
import { useUserStore } from '@/stores/user'
import { debounce } from '@/utils/debounce'
import { difficultyTagType, formatDateTime, formatMinutes } from '@/utils/format'

const metaStore = useMetaStore()
const userStore = useUserStore()

const activeTab = ref('dashboard')
const RECIPE_SIZE = 10
const USER_SIZE = 10

// ---------------------------------------------------------------- 看板

const stats = ref<StatsVO | null>(null)
const statsLoading = ref(false)
const statsError = ref('')

const statCards = computed(() => [
  { label: '菜谱总数', value: stats.value?.recipeCount ?? 0, hint: '含汤 / 凉菜 / 甜品' },
  { label: '用户总数', value: stats.value?.userCount ?? 0, hint: '含管理员' },
  { label: '社区帖子', value: stats.value?.postCount ?? 0, hint: '不含已删除' },
  { label: '快手菜', value: stats.value?.quickRecipeCount ?? 0, hint: '30 分钟内可完成' },
])

const categoryOption = computed<EChartsCoreOption>(() => {
  const entries = Object.entries(stats.value?.recipesByCategory ?? {})
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, icon: 'circle' },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '44%'],
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b} {c}' },
        data: entries.map(([name, value]) => ({ name, value })),
      },
    ],
  }
})

const difficultyOption = computed<EChartsCoreOption>(() => {
  const entries = Object.entries(stats.value?.recipesByDifficulty ?? {})
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 44, right: 20, top: 24, bottom: 28 },
    xAxis: { type: 'category', data: entries.map(([name]) => name) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'bar',
        barWidth: '46%',
        itemStyle: { color: '#ff8c42', borderRadius: [6, 6, 0, 0] },
        label: { show: true, position: 'top' },
        data: entries.map(([, value]) => value),
      },
    ],
  }
})

async function loadStats(): Promise<void> {
  statsLoading.value = true
  statsError.value = ''
  try {
    stats.value = await adminStats()
  } catch (error) {
    if (!isCanceled(error)) {
      statsError.value = error instanceof Error ? error.message : '加载失败'
    }
  } finally {
    statsLoading.value = false
  }
}

// ---------------------------------------------------------------- 菜谱管理

const recipes = ref<RecipeCardVO[]>([])
const recipeTotal = ref(0)
const recipePage = ref(1)
const recipeKeyword = ref('')
const recipesLoading = ref(false)
const recipesError = ref('')
const deletingId = ref<number | null>(null)

async function loadRecipes(page = recipePage.value): Promise<void> {
  recipesLoading.value = true
  recipesError.value = ''
  try {
    const result = await listRecipes({
      keyword: recipeKeyword.value.trim() || undefined,
      page,
      size: RECIPE_SIZE,
      sort: 'latest',
    })
    recipes.value = result.list
    recipeTotal.value = result.total
    recipePage.value = result.page
  } catch (error) {
    if (!isCanceled(error)) {
      recipesError.value = error instanceof Error ? error.message : '加载失败'
      recipes.value = []
    }
  } finally {
    recipesLoading.value = false
  }
}

const recipeSearchDebounced = debounce(() => {
  void loadRecipes(1)
}, 300)

function onRecipeKeywordInput(): void {
  recipeSearchDebounced()
}

// ---------------------------------------------------------------- 菜谱表单

interface IngredientRow {
  name: string
  amount: string
  unit: string
  main: boolean
}

interface StepRow {
  content: string
  imageUrl: string | null
}

interface RecipeForm {
  title: string
  coverUrl: string | null
  description: string
  cuisine: string
  category: string
  difficulty: Difficulty
  cookMinutes: number
  servings: number
  calories: number
  tags: string[]
  ingredients: IngredientRow[]
  steps: StepRow[]
}

const recipeDialogOpen = ref(false)
const recipeSaving = ref(false)
const editingId = ref<number | null>(null)
const coverImages = ref<UploadResult[]>([])

const recipeForm = reactive<RecipeForm>({
  title: '',
  coverUrl: null,
  description: '',
  cuisine: '家常',
  category: '荤菜',
  difficulty: 'EASY',
  cookMinutes: 20,
  servings: 2,
  calories: 200,
  tags: [],
  ingredients: [{ name: '', amount: '', unit: '', main: true }],
  steps: [{ content: '', imageUrl: null }],
})

const dialogTitle = computed(() => (editingId.value === null ? '新增菜谱' : `编辑菜谱 #${editingId.value}`))

function resetForm(): void {
  editingId.value = null
  coverImages.value = []
  recipeForm.title = ''
  recipeForm.coverUrl = null
  recipeForm.description = ''
  recipeForm.cuisine = metaStore.cuisines[0] ?? '家常'
  recipeForm.category = metaStore.categories[0]?.value ?? '荤菜'
  recipeForm.difficulty = 'EASY'
  recipeForm.cookMinutes = 20
  recipeForm.servings = 2
  recipeForm.calories = 200
  recipeForm.tags = []
  recipeForm.ingredients = [{ name: '', amount: '', unit: '', main: true }]
  recipeForm.steps = [{ content: '', imageUrl: null }]
}

function openCreate(): void {
  resetForm()
  recipeDialogOpen.value = true
}

async function openEdit(recipe: RecipeCardVO): Promise<void> {
  resetForm()
  editingId.value = recipe.id
  recipeDialogOpen.value = true
  try {
    // 列表卡片不含食材与步骤，必须回查详情
    const detail = await recipeDetail(recipe.id)
    recipeForm.title = detail.title
    recipeForm.coverUrl = detail.coverUrl
    recipeForm.description = detail.description ?? ''
    recipeForm.cuisine = detail.cuisine ?? recipeForm.cuisine
    recipeForm.category = detail.category ?? recipeForm.category
    recipeForm.difficulty = detail.difficulty ?? 'EASY'
    recipeForm.cookMinutes = detail.cookMinutes ?? 20
    recipeForm.servings = detail.servings ?? 2
    recipeForm.calories = detail.calories ?? 200
    recipeForm.tags = [...detail.tags]
    recipeForm.ingredients = detail.ingredients.length
      ? detail.ingredients.map((item) => ({
          name: item.name,
          amount: item.amount ?? '',
          unit: item.unit ?? '',
          main: item.main,
        }))
      : [{ name: '', amount: '', unit: '', main: true }]
    recipeForm.steps = detail.steps.length
      ? detail.steps.map((step) => ({ content: step.content, imageUrl: step.imageUrl }))
      : [{ content: '', imageUrl: null }]
    coverImages.value = detail.coverUrl
      ? [{ url: detail.coverUrl, thumbUrl: detail.coverUrl, width: null, height: null }]
      : []
  } catch {
    /* 拦截器已提示 */
  }
}

function onCoverChange(images: UploadResult[]): void {
  coverImages.value = images
  recipeForm.coverUrl = images[0]?.url ?? null
}

function addIngredient(): void {
  recipeForm.ingredients.push({ name: '', amount: '', unit: '', main: false })
}

function removeIngredient(index: number): void {
  recipeForm.ingredients.splice(index, 1)
  if (recipeForm.ingredients.length === 0) {
    addIngredient()
  }
}

function addStep(): void {
  recipeForm.steps.push({ content: '', imageUrl: null })
}

function removeStep(index: number): void {
  recipeForm.steps.splice(index, 1)
  if (recipeForm.steps.length === 0) {
    addStep()
  }
}

async function submitRecipe(): Promise<void> {
  const title = recipeForm.title.trim()
  if (!title) {
    ElMessage.warning('菜名不能为空')
    return
  }
  const ingredients = recipeForm.ingredients
    .filter((item) => item.name.trim())
    .map((item) => ({
      name: item.name.trim(),
      amount: item.amount.trim(),
      unit: item.unit.trim(),
      main: item.main,
    }))
  const steps = recipeForm.steps
    .filter((step) => step.content.trim())
    .map((step) => ({ content: step.content.trim(), imageUrl: step.imageUrl }))

  if (ingredients.length === 0) {
    ElMessage.warning('至少填一条食材')
    return
  }
  if (steps.length === 0) {
    ElMessage.warning('至少填一个步骤')
    return
  }
  if (!recipeForm.cuisine) {
    ElMessage.warning('请选择菜系')
    return
  }
  if (!recipeForm.category) {
    ElMessage.warning('请选择分类')
    return
  }

  const payload: RecipeSaveRequest = {
    title,
    coverUrl: recipeForm.coverUrl,
    description: recipeForm.description.trim(),
    cuisine: recipeForm.cuisine,
    category: recipeForm.category,
    difficulty: recipeForm.difficulty,
    cookMinutes: recipeForm.cookMinutes,
    servings: recipeForm.servings,
    calories: recipeForm.calories,
    tags: recipeForm.tags,
    ingredients,
    steps,
  }

  recipeSaving.value = true
  try {
    if (editingId.value === null) {
      await createRecipe(payload)
      ElMessage.success('菜谱已新增')
    } else {
      await updateRecipe(editingId.value, payload)
      ElMessage.success('菜谱已更新')
    }
    recipeDialogOpen.value = false
    await Promise.all([loadRecipes(editingId.value === null ? 1 : recipePage.value), loadStats()])
  } catch {
    /* 拦截器已提示 */
  } finally {
    recipeSaving.value = false
  }
}

async function removeRecipe(recipe: RecipeCardVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除「${recipe.title}」吗？删除后不可恢复。`, '删除菜谱', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '再想想',
    })
  } catch {
    return
  }

  deletingId.value = recipe.id
  try {
    await deleteRecipe(recipe.id)
    ElMessage.success('已删除')
    await loadRecipes(recipePage.value)
    await loadStats()
  } catch {
    /* 拦截器已提示 */
  } finally {
    deletingId.value = null
  }
}

// ---------------------------------------------------------------- 用户管理

const users = ref<AdminUserVO[]>([])
const userTotal = ref(0)
const userPage = ref(1)
const usersLoading = ref(false)
const usersError = ref('')
const statusBusyId = ref<number | null>(null)

async function loadUsers(page = userPage.value): Promise<void> {
  usersLoading.value = true
  usersError.value = ''
  try {
    const result = await adminUsers({ page, size: USER_SIZE })
    users.value = result.list
    userTotal.value = result.total
    userPage.value = result.page
  } catch (error) {
    if (!isCanceled(error)) {
      usersError.value = error instanceof Error ? error.message : '加载失败'
      users.value = []
    }
  } finally {
    usersLoading.value = false
  }
}

async function changeStatus(user: AdminUserVO, enabled: boolean): Promise<void> {
  const next = enabled ? 1 : 0
  try {
    await ElMessageBox.confirm(
      enabled ? `确定启用「${user.nickname}」吗？` : `确定禁用「${user.nickname}」吗？禁用后其令牌很快失效。`,
      enabled ? '启用账号' : '禁用账号',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  statusBusyId.value = user.id
  try {
    await adminUpdateUserStatus(user.id, next)
    user.status = next
    ElMessage.success(enabled ? '已启用' : '已禁用')
  } catch {
    /* 拦截器已提示；失败时保持原状态 */
  } finally {
    statusBusyId.value = null
  }
}

function onTabChange(name: string | number): void {
  if (name === 'recipes' && recipes.value.length === 0 && !recipesLoading.value) {
    void loadRecipes(1)
  }
  if (name === 'users' && users.value.length === 0 && !usersLoading.value) {
    void loadUsers(1)
  }
}

onMounted(async () => {
  await metaStore.ensure().catch(() => undefined)
  void loadStats()
  void loadRecipes(1)
  void loadUsers(1)
  resetForm()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">管理后台</h1>
        <p class="page-subtitle">
          仅管理员可见
          <template v-if="stats">· 数据生成于 {{ formatDateTime(stats.generatedAt) }}</template>
        </p>
      </div>
      <el-button :icon="Refresh" text @click="loadStats">刷新看板</el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- 看板 -->
      <el-tab-pane label="数据看板" name="dashboard">
        <LoadingState v-if="statsLoading && !stats" :rows="4" />

        <el-result v-else-if="statsError" icon="error" title="看板加载失败" :sub-title="statsError">
          <template #extra>
            <el-button type="primary" @click="loadStats">重试</el-button>
          </template>
        </el-result>

        <template v-else-if="stats">
          <div class="stat-grid">
            <div v-for="card in statCards" :key="card.label" class="stat-card surface">
              <span class="muted">{{ card.label }}</span>
              <strong class="tnum">{{ card.value }}</strong>
              <span class="muted small">{{ card.hint }}</span>
            </div>
          </div>

          <div class="chart-grid">
            <div class="surface chart-card">
              <h2 class="card-title">菜谱分类分布</h2>
              <EChart :option="categoryOption" height="300px" />
            </div>
            <div class="surface chart-card">
              <h2 class="card-title">难度分布</h2>
              <EChart :option="difficultyOption" height="300px" />
            </div>
          </div>

          <div class="surface chart-card">
            <h2 class="card-title">浏览最多</h2>
            <ul v-if="stats.topRecipes.length" class="top-list">
              <li v-for="(recipe, index) in stats.topRecipes" :key="recipe.id">
                <span class="top-list__rank">{{ index + 1 }}</span>
                <RouterLink class="top-list__title" :to="`/recipes/${recipe.id}`">
                  {{ recipe.title }}
                </RouterLink>
                <span class="muted small">
                  {{ recipe.category }} · {{ formatMinutes(recipe.cookMinutes) }} ·
                  {{ recipe.viewCount ?? 0 }} 次浏览
                </span>
              </li>
            </ul>
            <EmptyState v-else title="暂无数据" />
          </div>
        </template>
      </el-tab-pane>

      <!-- 菜谱管理 -->
      <el-tab-pane label="菜谱管理" name="recipes">
        <div class="toolbar">
          <el-input
            v-model="recipeKeyword"
            placeholder="按菜名搜索（300ms 防抖）"
            clearable
            :prefix-icon="Search"
            style="max-width: 280px"
            @input="onRecipeKeywordInput"
            @keyup.enter="loadRecipes(1)"
          />
          <el-button type="primary" :icon="Plus" @click="openCreate">新增菜谱</el-button>
          <el-button text :icon="Refresh" @click="loadRecipes()">刷新</el-button>
        </div>

        <LoadingState v-if="recipesLoading" variant="cards" :cards="3" />

        <el-result v-else-if="recipesError" icon="error" title="加载失败" :sub-title="recipesError">
          <template #extra>
            <el-button type="primary" @click="loadRecipes(recipePage)">重试</el-button>
          </template>
        </el-result>

        <EmptyState v-else-if="recipes.length === 0" title="没有找到菜谱" />

        <template v-else>
          <el-table :data="recipes" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="title" label="菜名" min-width="150" show-overflow-tooltip />
            <el-table-column prop="cuisine" label="菜系" width="90" />
            <el-table-column prop="category" label="分类" width="90" />
            <el-table-column label="难度" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="difficultyTagType(row.difficulty)" effect="light">
                  {{ row.difficultyLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="耗时" width="100">
              <template #default="{ row }">{{ formatMinutes(row.cookMinutes) }}</template>
            </el-table-column>
            <el-table-column label="收藏" width="80">
              <template #default="{ row }">
                <span class="tnum">{{ row.favoriteCount ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button text size="small" :icon="EditPen" @click="openEdit(row as RecipeCardVO)">编辑</el-button>
                <el-button
                  text
                  size="small"
                  type="danger"
                  :icon="Delete"
                  :loading="deletingId === row.id"
                  @click="removeRecipe(row as RecipeCardVO)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              background
              layout="prev, pager, next, total"
              :current-page="recipePage"
              :page-size="RECIPE_SIZE"
              :total="recipeTotal"
              @current-change="(page: number) => loadRecipes(page)"
            />
          </div>
        </template>
      </el-tab-pane>

      <!-- 用户管理 -->
      <el-tab-pane label="用户管理" name="users">
        <div class="toolbar">
          <el-button text :icon="Refresh" @click="loadUsers()">刷新</el-button>
          <span class="muted small">不能修改自己的状态</span>
        </div>

        <LoadingState v-if="usersLoading" variant="cards" :cards="3" />

        <el-result v-else-if="usersError" icon="error" title="加载失败" :sub-title="usersError">
          <template #extra>
            <el-button type="primary" @click="loadUsers(userPage)">重试</el-button>
          </template>
        </el-result>

        <EmptyState v-else-if="users.length === 0" title="没有用户" />

        <template v-else>
          <el-table :data="users" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="username" label="用户名" min-width="120" />
            <el-table-column prop="nickname" label="昵称" min-width="120" />
            <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
            <el-table-column label="角色" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.role === 'ADMIN' ? 'danger' : 'info'" effect="light">
                  {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最近登录" width="160">
              <template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110" fixed="right">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.status === 1"
                  :loading="statusBusyId === row.id"
                  :disabled="row.id === userStore.userId"
                  active-text="正常"
                  inactive-text="禁用"
                  inline-prompt
                  @change="(value: string | number | boolean) => changeStatus(row as AdminUserVO, value === true)"
                />
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              background
              layout="prev, pager, next, total"
              :current-page="userPage"
              :page-size="USER_SIZE"
              :total="userTotal"
              @current-change="(page: number) => loadUsers(page)"
            />
          </div>
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 菜谱增删改 -->
    <el-dialog v-model="recipeDialogOpen" :title="dialogTitle" width="760px" top="6vh">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="菜名">
            <el-input v-model="recipeForm.title" maxlength="60" show-word-limit />
          </el-form-item>
          <el-form-item label="菜系">
            <el-select v-model="recipeForm.cuisine" filterable allow-create style="width: 100%">
              <el-option v-for="item in metaStore.cuisines" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="recipeForm.category" style="width: 100%">
              <el-option
                v-for="item in metaStore.categories"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="recipeForm.difficulty" style="width: 100%">
              <el-option
                v-for="item in metaStore.difficulties"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="耗时（分钟）">
            <el-input-number v-model="recipeForm.cookMinutes" :min="1" :max="2880" style="width: 100%" />
          </el-form-item>
          <el-form-item label="份量（人份）">
            <el-input-number v-model="recipeForm.servings" :min="1" :max="20" style="width: 100%" />
          </el-form-item>
          <el-form-item label="热量（千卡）">
            <el-input-number v-model="recipeForm.calories" :min="0" :max="5000" style="width: 100%" />
          </el-form-item>
        </div>

        <el-form-item label="简介">
          <el-input v-model="recipeForm.description" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>

        <el-form-item label="标签（只能选字典里的值）">
          <el-select v-model="recipeForm.tags" multiple filterable style="width: 100%">
            <el-option v-for="tag in metaStore.tags" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>

        <el-form-item label="封面（可选，不传则前端按菜名生成渐变封面）">
          <ImagePicker :model-value="coverImages" :max="1" @update:model-value="onCoverChange" />
        </el-form-item>

        <el-divider content-position="left">食材（至少一条）</el-divider>
        <div v-for="(item, index) in recipeForm.ingredients" :key="index" class="row-line">
          <el-input v-model="item.name" placeholder="食材名" style="flex: 2" />
          <el-input v-model="item.amount" placeholder="用量" style="flex: 1" />
          <el-input v-model="item.unit" placeholder="单位" style="flex: 1" />
          <el-checkbox v-model="item.main">主料</el-checkbox>
          <el-button text type="danger" :icon="Delete" @click="removeIngredient(index)" />
        </div>
        <el-button text :icon="Plus" @click="addIngredient">添加食材</el-button>

        <el-divider content-position="left">步骤（至少一条）</el-divider>
        <div v-for="(step, index) in recipeForm.steps" :key="index" class="row-line">
          <span class="row-line__no">{{ index + 1 }}</span>
          <el-input
            v-model="step.content"
            type="textarea"
            :rows="2"
            placeholder="这一步做什么"
            style="flex: 1"
          />
          <el-button text type="danger" :icon="Delete" @click="removeStep(index)" />
        </div>
        <el-button text :icon="Plus" @click="addStep">添加步骤</el-button>
      </el-form>

      <template #footer>
        <el-button @click="recipeDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="recipeSaving" @click="submitRecipe">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (min-width: 860px) {
  .stat-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 16px 18px;
}

.stat-card strong {
  font-size: 26px;
  line-height: 1.25;
}

.small {
  font-size: 12px;
}

.chart-grid {
  display: grid;
  gap: 16px;
  margin-top: 16px;
  grid-template-columns: 1fr;
}

@media (min-width: 980px) {
  .chart-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.chart-card {
  margin-top: 16px;
  padding: 16px 18px;
}

.chart-grid .chart-card {
  margin-top: 0;
}

.card-title {
  margin-bottom: 10px;
  font-size: 16px;
}

.top-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.top-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--foodie-border);
}

.top-list li:last-child {
  border-bottom: none;
}

.top-list__rank {
  display: grid;
  width: 24px;
  height: 24px;
  flex: none;
  place-items: center;
  border-radius: 50%;
  background: var(--foodie-primary-soft);
  color: var(--foodie-primary);
  font-size: 12px;
  font-weight: 700;
}

.top-list__title {
  font-weight: 600;
}

.top-list__title:hover {
  color: var(--foodie-primary);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.form-grid {
  display: grid;
  gap: 0 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (min-width: 760px) {
  .form-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.row-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.row-line__no {
  display: grid;
  width: 24px;
  height: 24px;
  flex: none;
  place-items: center;
  border-radius: 50%;
  background: var(--foodie-primary-soft);
  color: var(--foodie-primary);
  font-size: 12px;
  font-weight: 700;
}
</style>
