<script setup lang="ts">
import { onMounted } from 'vue'

import AppFooter from '@/components/AppFooter.vue'
import AppHeader from '@/components/AppHeader.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

// 首次进入：带着 localStorage 里的 token 静默拉一次当前用户
onMounted(() => {
  void userStore.ensureUser()
})
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main">
      <RouterView />
    </main>
    <AppFooter />
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
}

.app-main {
  flex: 1;
  width: 100%;
  padding-bottom: 48px;
}
</style>
