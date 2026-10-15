<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  Menu as MenuIcon,
  Search,
  Setting,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'

import { useUserStore } from '@/stores/user'
import { coverInitial } from '@/utils/cover'
import { debounce } from '@/utils/debounce'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const drawerOpen = ref(false)

const navItems = [
  { path: '/', label: '首页' },
  { path: '/recipes', label: '菜谱库' },
  { path: '/menu', label: '今天吃什么' },
  { path: '/community', label: '美食社区' },
]

const activeNav = computed(() => {
  const path = route.path
  if (path === '/') {
    return '/'
  }
  const hit = navItems.find((item) => item.path !== '/' && path.startsWith(item.path))
  return hit ? hit.path : ''
})

const avatarText = computed(() => coverInitial(userStore.displayName))

// 从首页/页脚带 keyword 跳到 /recipes 时，把搜索框同步过去
watch(
  () => route.query.keyword,
  (value) => {
    keyword.value = typeof value === 'string' ? value : ''
  },
)

function gotoSearch(value: string): void {
  const trimmed = value.trim()
  void router.push({ path: '/recipes', query: trimmed ? { keyword: trimmed } : {} })
  drawerOpen.value = false
}

// 搜索输入 300ms 防抖：边打字边出结果，但不会一个字一个请求
const debouncedSearch = debounce((value: string) => gotoSearch(value), 300)

function onKeywordInput(value: string): void {
  keyword.value = value
  debouncedSearch(value)
}

function onKeywordEnter(): void {
  debouncedSearch.cancel()
  gotoSearch(keyword.value)
}

function onKeywordClear(): void {
  debouncedSearch.cancel()
  keyword.value = ''
  gotoSearch('')
}

function goto(path: string): void {
  drawerOpen.value = false
  void router.push(path)
}

async function onCommand(command: string | number | object): Promise<void> {
  const action = String(command)
  drawerOpen.value = false
  if (action === 'profile') {
    void router.push('/profile')
    return
  }
  if (action === 'admin') {
    void router.push('/admin')
    return
  }
  if (action === 'logout') {
    await userStore.logout()
    ElMessage.success('已退出登录')
    if (route.meta.requiresAuth) {
      void router.push('/')
    }
  }
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner">
      <RouterLink class="brand" to="/">
        <span class="brand__mark">食</span>
        <span class="brand__text">
          <strong>食光</strong>
          <em>Foodie</em>
        </span>
      </RouterLink>

      <nav class="nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          class="nav__link"
          :class="{ 'is-active': activeNav === item.path }"
          :to="item.path"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="header-search">
        <el-input
          v-model="keyword"
          placeholder="搜索菜谱，如「番茄」"
          clearable
          :prefix-icon="Search"
          @input="onKeywordInput"
          @keyup.enter="onKeywordEnter"
          @clear="onKeywordClear"
        />
      </div>

      <div class="header-actions">
        <el-dropdown v-if="userStore.isLoggedIn" trigger="click" @command="onCommand">
          <span class="user-chip">
            <el-avatar :size="28" :src="userStore.avatarUrl ?? undefined">
              {{ avatarText }}
            </el-avatar>
            <span class="user-chip__name">{{ userStore.displayName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile" :icon="User">个人中心</el-dropdown-item>
              <el-dropdown-item v-if="userStore.isAdmin" command="admin" :icon="Setting">
                管理后台
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided :icon="SwitchButton">
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <template v-else>
          <el-button text @click="goto('/login')">登录</el-button>
          <el-button type="primary" @click="goto('/register')">注册</el-button>
        </template>

        <el-button class="nav-toggle" text circle :icon="MenuIcon" @click="drawerOpen = true" />
      </div>
    </div>

    <el-drawer v-model="drawerOpen" title="导航" direction="rtl" size="240px">
      <div class="drawer-nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          class="drawer-nav__link"
          :class="{ 'is-active': activeNav === item.path }"
          :to="item.path"
          @click="drawerOpen = false"
        >
          {{ item.label }}
        </RouterLink>
        <template v-if="!userStore.isLoggedIn">
          <el-button type="primary" style="width: 100%" @click="goto('/login')">登录</el-button>
          <el-button style="width: 100%; margin: 8px 0 0" @click="goto('/register')">
            注册
          </el-button>
        </template>
        <template v-else>
          <el-button style="width: 100%; margin: 8px 0 0" @click="goto('/profile')">
            个人中心
          </el-button>
          <el-button
            v-if="userStore.isAdmin"
            style="width: 100%; margin: 8px 0 0"
            @click="goto('/admin')"
          >
            管理后台
          </el-button>
        </template>
      </div>
    </el-drawer>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 20;
  backdrop-filter: blur(12px);
  background: rgba(255, 252, 249, 0.92);
  border-bottom: 1px solid var(--foodie-border);
}

.app-header__inner {
  display: flex;
  align-items: center;
  gap: 18px;
  max-width: 1200px;
  margin: 0 auto;
  padding: 10px 20px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}

.brand__mark {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 11px;
  background: linear-gradient(135deg, var(--foodie-accent), var(--foodie-primary));
  color: #fff;
  font-weight: 800;
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.brand__text strong {
  font-size: 16px;
}

.brand__text em {
  color: var(--foodie-text-muted);
  font-size: 11px;
  font-style: normal;
  letter-spacing: 1px;
}

.nav {
  display: flex;
  gap: 4px;
  flex: none;
}

.nav__link {
  padding: 6px 12px;
  border-radius: 9px;
  color: var(--foodie-text-muted);
  font-weight: 600;
  transition: all 0.16s ease;
}

.nav__link:hover {
  background: var(--foodie-primary-soft);
  color: var(--foodie-primary);
}

.nav__link.is-active {
  background: var(--foodie-primary-soft);
  color: var(--foodie-primary);
}

.header-search {
  flex: 1;
  min-width: 0;
  max-width: 320px;
  margin-left: auto;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px 3px 3px;
  border-radius: 999px;
  background: var(--foodie-primary-soft);
  color: var(--foodie-text);
  cursor: pointer;
  outline: none;
}

.user-chip__name {
  max-width: 96px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-toggle {
  display: none;
}

.drawer-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.drawer-nav__link {
  padding: 10px 12px;
  border-radius: 10px;
  font-weight: 600;
}

.drawer-nav__link.is-active {
  background: var(--foodie-primary-soft);
  color: var(--foodie-primary);
}

@media (max-width: 900px) {
  .header-search {
    max-width: none;
  }

  .nav {
    display: none;
  }

  .nav-toggle {
    display: inline-flex;
  }
}

@media (max-width: 620px) {
  .brand__text {
    display: none;
  }

  .user-chip__name {
    display: none;
  }
}
</style>
