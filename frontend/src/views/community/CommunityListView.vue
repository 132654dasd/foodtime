<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { EditPen, Loading } from '@element-plus/icons-vue'

import EmptyState from '@/components/EmptyState.vue'
import ImagePicker from '@/components/ImagePicker.vue'
import LoadingState from '@/components/LoadingState.vue'
import PostListItem from '@/components/PostListItem.vue'
import { createPost, isCanceled, likePost, listPosts, unlikePost } from '@/api'
import type { PostCardVO, PostSort, UploadResult } from '@/api/types'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const PAGE_SIZE = 10

const sort = ref<PostSort>('latest')
const posts = ref<PostCardVO[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const errorText = ref('')
const hasMore = ref(false)
const page = ref(1)
const cursor = ref<number | null>(null)
const likeBusyId = ref<number | null>(null)

const composerOpen = ref(false)
const composer = reactive({ title: '', content: '' })
const composerImages = ref<UploadResult[]>([])
const submitting = ref(false)

const sentinel = ref<HTMLElement | null>(null)
let controller: AbortController | null = null
let observer: IntersectionObserver | null = null

function readSort(): PostSort {
  return route.query.sort === 'hot' ? 'hot' : 'latest'
}

async function reload(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current

  loading.value = true
  errorText.value = ''
  posts.value = []
  hasMore.value = false
  page.value = 1
  cursor.value = null

  try {
    const result = await listPosts(
      {
        sort: sort.value,
        size: PAGE_SIZE,
        ...(sort.value === 'hot' ? { page: 1 } : {}),
      },
      { signal: current.signal },
    )
    if (controller !== current) {
      return
    }
    posts.value = result.list
    if (sort.value === 'latest') {
      // 游标分页：上一页最后一条的 id 就是下一页的 cursor；pages === -1 表示还有下一页
      const last = result.list[result.list.length - 1]
      cursor.value = last ? last.id : null
      hasMore.value = result.pages === -1 && result.list.length > 0
    } else {
      hasMore.value = result.page < result.pages
    }
  } catch (error) {
    if (controller !== current || isCanceled(error)) {
      return
    }
    errorText.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    if (controller === current) {
      loading.value = false
    }
  }
}

async function loadMore(): Promise<void> {
  if (loading.value || loadingMore.value || !hasMore.value) {
    return
  }
  loadingMore.value = true
  try {
    if (sort.value === 'latest') {
      const result = await listPosts({ sort: 'latest', size: PAGE_SIZE, cursor: cursor.value })
      appendNew(result.list)
      const last = result.list[result.list.length - 1]
      if (last) {
        cursor.value = last.id
      }
      hasMore.value = result.pages === -1 && result.list.length > 0
    } else {
      const nextPage = page.value + 1
      const result = await listPosts({ sort: 'hot', size: PAGE_SIZE, page: nextPage })
      page.value = nextPage
      appendNew(result.list)
      hasMore.value = result.page < result.pages
    }
  } catch (error) {
    if (!isCanceled(error)) {
      ElMessage.error(error instanceof Error ? error.message : '加载更多失败')
    }
  } finally {
    loadingMore.value = false
  }
}

function appendNew(incoming: PostCardVO[]): void {
  const existing = new Set(posts.value.map((item) => item.id))
  posts.value = [...posts.value, ...incoming.filter((item) => !existing.has(item.id))]
}

function switchSort(next: PostSort): void {
  if (sort.value === next) {
    return
  }
  void router.push({ path: '/community', query: next === 'hot' ? { sort: 'hot' } : {} })
}

watch(
  () => route.fullPath,
  () => {
    sort.value = readSort()
    void reload()
  },
  { immediate: true },
)

// 无限滚动：用主内容区下方的哨兵元素 + IntersectionObserver
observer = new IntersectionObserver(
  (entries) => {
    if (entries.some((entry) => entry.isIntersecting)) {
      void loadMore()
    }
  },
  { rootMargin: '240px 0px' },
)

watch(sentinel, (element) => {
  observer?.disconnect()
  if (element) {
    observer?.observe(element)
  }
})

onBeforeUnmount(() => {
  controller?.abort()
  observer?.disconnect()
  observer = null
})

async function toggleLike(post: PostCardVO): Promise<void> {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('登录后才能点赞')
    void router.push({ path: '/login', query: { redirect: '/community' } })
    return
  }
  likeBusyId.value = post.id
  try {
    const result = post.liked ? await unlikePost(post.id) : await likePost(post.id)
    post.liked = result.liked
    post.likeCount = result.likeCount
  } catch {
    /* 拦截器已提示 */
  } finally {
    likeBusyId.value = null
  }
}

function openComposer(): void {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('登录后才能发帖')
    void router.push({ path: '/login', query: { redirect: '/community' } })
    return
  }
  composerOpen.value = true
}

async function submitPost(): Promise<void> {
  if (!composer.title.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  if (!composer.content.trim()) {
    ElMessage.warning('正文不能为空')
    return
  }

  submitting.value = true
  try {
    const created = await createPost({
      title: composer.title.trim(),
      content: composer.content.trim(),
      imageUrls: composerImages.value.map((image) => image.url),
    })
    ElMessage.success('发布成功')
    composerOpen.value = false
    composer.title = ''
    composer.content = ''
    composerImages.value = []
    void router.push(`/community/${created.id}`)
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">美食社区</h1>
        <p class="page-subtitle">看看别人做了什么，也把你的作品晒出来</p>
      </div>
      <el-button type="primary" :icon="EditPen" @click="openComposer">发帖</el-button>
    </div>

    <div class="feed-tabs">
      <el-radio-group :model-value="sort" @change="(value: string | number | boolean | undefined) => switchSort(value === 'hot' ? 'hot' : 'latest')">
        <el-radio-button value="latest">最新</el-radio-button>
        <el-radio-button value="hot">最热</el-radio-button>
      </el-radio-group>
      <span class="muted small">
        {{ sort === 'latest' ? '按时间倒序，滚动自动加载（游标分页）' : '按点赞数排序，滚动自动加载' }}
      </span>
    </div>

    <LoadingState v-if="loading" variant="cards" :cards="3" />

    <el-result v-else-if="errorText" icon="error" title="帖子加载失败" :sub-title="errorText">
      <template #extra>
        <el-button type="primary" @click="reload">重试</el-button>
      </template>
    </el-result>

    <EmptyState
      v-else-if="posts.length === 0"
      title="还没有人发帖"
      description="来当第一个晒作品的人吧"
    >
      <el-button type="primary" @click="openComposer">发第一帖</el-button>
    </EmptyState>

    <template v-else>
      <div class="post-list">
        <PostListItem
          v-for="post in posts"
          :key="post.id"
          :post="post"
          :busy="likeBusyId === post.id"
          @like="toggleLike"
        />
      </div>

      <div ref="sentinel" class="feed-sentinel">
        <span v-if="loadingMore" class="muted">
          <el-icon class="spin"><Loading /></el-icon> 正在加载更多…
        </span>
        <span v-else-if="hasMore" class="muted">向下滚动加载更多</span>
        <span v-else class="muted">— 已经到底啦 —</span>
      </div>
    </template>

    <el-dialog v-model="composerOpen" title="发一篇帖子" width="640px">
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="composer.title" maxlength="100" show-word-limit placeholder="比如：第一次做番茄炒蛋" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input
            v-model="composer.content"
            type="textarea"
            :rows="6"
            maxlength="5000"
            show-word-limit
            placeholder="用回车分段，讲讲你的做法和心得"
          />
        </el-form-item>
        <el-form-item label="图片（最多 9 张，单张 5MB）">
          <ImagePicker v-model="composerImages" :max="9" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="composerOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPost">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.feed-tabs {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.small {
  font-size: 12px;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.feed-sentinel {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 26px 0 8px;
  font-size: 13px;
}

.spin {
  animation: feed-spin 1.2s linear infinite;
}

@keyframes feed-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
