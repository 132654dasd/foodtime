<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'

import { listIngredientPrices, priceCategories } from '@/api'
import type { IngredientCategoryCode, IngredientCategoryOption, IngredientPriceVO } from '@/api/types'

const PAGE_SIZE = 20

const categories = ref<IngredientCategoryOption[]>([])
const activeCategory = ref<IngredientCategoryCode | ''>('')
const keyword = ref('')

const list = ref<IngredientPriceVO[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const errorText = ref('')

/** 加载价目列表；筛选条件全部下发给后端，前端只负责展示。 */
async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const result = await listIngredientPrices({
      keyword: keyword.value.trim() || undefined,
      category: activeCategory.value || undefined,
      page: page.value,
      size: PAGE_SIZE,
    })
    list.value = result.list
    total.value = result.total
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '价格加载失败'
  } finally {
    loading.value = false
  }
}

async function loadCategories(): Promise<void> {
  try {
    categories.value = await priceCategories()
  } catch {
    // 分类只是筛选辅助，拿不到也不该让整页不可用
    categories.value = []
  }
}

function selectCategory(code: IngredientCategoryCode | ''): void {
  activeCategory.value = code
  page.value = 1
  void load()
}

function onSearch(): void {
  page.value = 1
  void load()
}

function reset(): void {
  keyword.value = ''
  activeCategory.value = ''
  page.value = 1
  void load()
}

function onPageChange(next: number): void {
  page.value = next
  void load()
}

function priceOf(row: IngredientPriceVO): string {
  return Number(row.unitPrice).toFixed(2)
}

function gramRefOf(row: IngredientPriceVO): string {
  if (row.gramsPerUnit === null || row.gramsPerUnit === undefined) {
    return ''
  }
  return `1 ${row.priceUnit} ≈ ${Number(row.gramsPerUnit)} g`
}

onMounted(() => {
  void loadCategories()
  void load()
})
</script>

<template>
  <div class="price-board">
    <section class="hero">
      <h1>食材价格看板</h1>
      <p>
        这里是由管理员维护的实时价目。做菜成本 =
        每样食材的用量换算到计价单位后 × 单价，再逐项累加。
        换算不了的（比如「少许」）会按经验值估算并标注。
      </p>
    </section>

    <el-card shadow="never" class="panel">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          class="toolbar__search"
          placeholder="搜索食材，如「番茄」「排骨」"
          clearable
          :prefix-icon="Search"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <div class="chips">
        <span class="chip" :class="{ 'is-active': activeCategory === '' }" @click="selectCategory('')">
          全部
        </span>
        <span
          v-for="item in categories"
          :key="item.value"
          class="chip"
          :class="{ 'is-active': activeCategory === item.value }"
          @click="selectCategory(item.value)"
        >
          {{ item.label }}
        </span>
      </div>

      <el-alert
        v-if="errorText"
        class="alert"
        :title="errorText"
        type="error"
        :closable="false"
        show-icon
      />

      <el-table v-loading="loading" :data="list" stripe empty-text="没有匹配的食材">
        <el-table-column prop="name" label="食材" min-width="120" />
        <el-table-column label="分类" width="96">
          <template #default="scope">
            {{ (scope.row as IngredientPriceVO).category ?? '—' }}
          </template>
        </el-table-column>
        <el-table-column label="单价" width="150">
          <template #default="scope">
            <span class="price">¥{{ priceOf(scope.row as IngredientPriceVO) }}</span>
            <span class="unit">/ {{ (scope.row as IngredientPriceVO).priceUnit }}</span>
          </template>
        </el-table-column>
        <el-table-column label="换算参考" min-width="150">
          <template #default="scope">
            <span v-if="gramRefOf(scope.row as IngredientPriceVO)">
              {{ gramRefOf(scope.row as IngredientPriceVO) }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="160">
          <template #default="scope">
            {{ (scope.row as IngredientPriceVO).note ?? '—' }}
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > PAGE_SIZE"
        class="pager"
        layout="prev, pager, next, total"
        :current-page="page"
        :page-size="PAGE_SIZE"
        :total="total"
        @current-change="onPageChange"
      />
    </el-card>
  </div>
</template>

<style scoped>
.price-board {
  max-width: 1080px;
  margin: 0 auto;
  padding: 28px 20px 60px;
}

.hero h1 {
  margin: 0 0 10px;
  font-size: 26px;
}

.hero p {
  margin: 0 0 22px;
  max-width: 720px;
  color: var(--foodie-text-muted);
  line-height: 1.7;
}

.panel {
  border-radius: 16px;
}

.toolbar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.toolbar__search {
  flex: 1;
  min-width: 200px;
  max-width: 360px;
}

.chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.chip {
  padding: 5px 14px;
  border-radius: 999px;
  background: var(--foodie-primary-soft);
  color: var(--foodie-text-muted);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.16s ease;
  user-select: none;
}

.chip:hover {
  color: var(--foodie-primary);
}

.chip.is-active {
  background: var(--foodie-primary);
  color: #fff;
}

.alert {
  margin-bottom: 14px;
}

.price {
  font-weight: 700;
  color: var(--foodie-primary);
}

.unit {
  margin-left: 2px;
  color: var(--foodie-text-muted);
  font-size: 12px;
}

.muted {
  color: var(--foodie-text-muted);
}

.pager {
  margin-top: 18px;
  justify-content: flex-end;
}
</style>
