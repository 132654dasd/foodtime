import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  getMe,
  login as loginApi,
  logout as logoutApi,
  saveAvoidances as saveAvoidancesApi,
} from '@/api'
import { tokenStorage } from '@/api/http'
import type { LoginRequest, LoginResponse, UserVO } from '@/api/types'

/**
 * 当前登录用户。
 *
 * token 持久化在 localStorage（key 与 http.ts 共用 tokenStorage），
 * 刷新页面后先乐观认为「已登录」，再用 /auth/me 静默校正。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(tokenStorage.get())
  const user = ref<UserVO | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const displayName = computed(() => user.value?.nickname || user.value?.username || '未登录')
  const avatarUrl = computed(() => user.value?.avatarUrl ?? null)
  const avoidances = computed<string[]>(() => user.value?.avoidances ?? [])
  const userId = computed(() => user.value?.id ?? null)

  // 同一时刻只允许一个 /auth/me 在飞
  let meInflight: Promise<UserVO | null> | null = null

  function setToken(value: string | null): void {
    token.value = value
    if (value) {
      tokenStorage.set(value)
    } else {
      tokenStorage.clear()
    }
  }

  /** 清掉本地登录态（不发请求） */
  function reset(): void {
    setToken(null)
    user.value = null
    meInflight = null
  }

  async function login(payload: LoginRequest): Promise<LoginResponse> {
    const response = await loginApi(payload)
    setToken(response.token)
    user.value = response.user
    return response
  }

  /** 拉取当前用户；失败（令牌失效）时清态并返回 null，不往上抛 */
  async function loadMe(): Promise<UserVO | null> {
    if (!token.value) {
      return null
    }
    if (meInflight) {
      return meInflight
    }
    meInflight = getMe()
      .then((me) => {
        user.value = me
        return me
      })
      .catch(() => {
        reset()
        return null
      })
      .finally(() => {
        meInflight = null
      })
    return meInflight
  }

  /** 已经有用户信息就直接用，否则拉一次 */
  async function ensureUser(): Promise<UserVO | null> {
    if (user.value) {
      return user.value
    }
    return loadMe()
  }

  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } catch {
      /* 服务端无状态，登出失败也要清本地 */
    } finally {
      reset()
    }
  }

  async function saveAvoidances(keywords: string[]): Promise<string[]> {
    const saved = await saveAvoidancesApi(keywords)
    if (user.value) {
      user.value = { ...user.value, avoidances: saved }
    }
    return saved
  }

  return {
    token,
    user,
    isLoggedIn,
    isAdmin,
    displayName,
    avatarUrl,
    avoidances,
    userId,
    setToken,
    reset,
    login,
    loadMe,
    ensureUser,
    logout,
    saveAvoidances,
  }
})
