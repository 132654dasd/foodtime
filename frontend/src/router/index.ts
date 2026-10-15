import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { useUserStore } from '@/stores/user'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** 需要登录 */
    requiresAuth?: boolean
    /** 需要 ADMIN 角色 */
    requiresAdmin?: boolean
    /** 已登录用户不应再看到（登录/注册页） */
    guestOnly?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '首页' },
  },
  {
    path: '/recipes',
    name: 'recipe-list',
    component: () => import('@/views/recipe/RecipeListView.vue'),
    meta: { title: '菜谱库' },
  },
  {
    path: '/recipes/:id',
    name: 'recipe-detail',
    component: () => import('@/views/recipe/RecipeDetailView.vue'),
    meta: { title: '菜谱详情' },
  },
  {
    path: '/menu',
    name: 'menu',
    component: () => import('@/views/menu/MenuPlannerView.vue'),
    meta: { title: '今天吃什么', requiresAuth: true },
  },
  {
    path: '/community',
    name: 'community',
    component: () => import('@/views/community/CommunityListView.vue'),
    meta: { title: '美食社区' },
  },
  {
    path: '/community/:id',
    name: 'post-detail',
    component: () => import('@/views/community/PostDetailView.vue'),
    meta: { title: '帖子详情' },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: '登录', guestOnly: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { title: '注册', guestOnly: true },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/profile/ProfileView.vue'),
    meta: { title: '个人中心', requiresAuth: true },
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('@/views/admin/AdminView.vue'),
    meta: { title: '管理后台', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition ?? { top: 0 }
  },
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin) {
    // 直接敲 /admin 进来的：先确认角色，别让页面自己发一堆 403
    await userStore.ensureUser()
    if (!userStore.isAdmin) {
      return { path: '/' }
    }
  }

  if (to.meta.guestOnly && userStore.isLoggedIn) {
    return { path: '/' }
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} · 食光 Foodie` : '食光 Foodie'
})

export default router
