import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import { onUnauthorized } from './api/http'
import { useUserStore } from './stores/user'

import './style.css'

const app = createApp(App)
const pinia = createPinia()

// pinia 必须先于 router 安装：路由守卫里要用 store
app.use(pinia)
app.use(router)

/**
 * 令牌过期/未登录统一处理：清掉本地登录态并跳登录页。
 * 用回调而不是在 http.ts 里直接 import router，避免 axios ↔ router ↔ views ↔ api 的循环依赖。
 */
onUnauthorized((currentPath) => {
  const userStore = useUserStore(pinia)
  userStore.reset()
  const needRedirect =
    currentPath !== '' && !currentPath.startsWith('/login') && !currentPath.startsWith('/register')
  void router.replace({
    path: '/login',
    query: needRedirect ? { redirect: currentPath } : {},
  })
})

app.mount('#app')
