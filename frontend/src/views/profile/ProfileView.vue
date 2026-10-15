<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, Star } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

import EmptyState from '@/components/EmptyState.vue'
import LoadingState from '@/components/LoadingState.vue'
import PostListItem from '@/components/PostListItem.vue'
import RecipeCard from '@/components/RecipeCard.vue'
import {
  changePassword,
  favoriteRecipe,
  isCanceled,
  likePost,
  listMyFavorites,
  listPosts,
  unfavoriteRecipe,
  unlikePost,
} from '@/api'
import type { PostCardVO, RecipeCardVO } from '@/api/types'
import { useMetaStore } from '@/stores/meta'
import { useUserStore } from '@/stores/user'
import { coverInitial } from '@/utils/cover'

const router = useRouter()
const userStore = useUserStore()
const metaStore = useMetaStore()

const activeTab = ref('profile')

// ---------------------------------------------------------------- 资料

const avatarText = computed(() => coverInitial(userStore.displayName))
const roleLabel = computed(() => (userStore.isAdmin ? '管理员' : '普通用户'))

// ---------------------------------------------------------------- 忌口

const avoidSelected = ref<string[]>([])
const customAvoid = ref('')
const savingAvoid = ref(false)

const presetAvoids = computed(() => metaStore.avoidSuggestions)
const customAvoids = computed(() =>
  avoidSelected.value.filter(
    (item) => !presetAvoids.value.some((preset) => preset.value === item),
  ),
)

function toggleAvoid(value: string): void {
  avoidSelected.value = avoidSelected.value.includes(value)
    ? avoidSelected.value.filter((item) => item !== value)
    : [...avoidSelected.value, value]
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

async function saveAvoidances(): Promise<void> {
  savingAvoid.value = true
  try {
    await userStore.saveAvoidances(
      Array.from(new Set(avoidSelected.value.map((item) => item.trim()).filter(Boolean))),
    )
    ElMessage.success('忌口偏好已保存')
  } catch {
    /* 拦截器已提示 */
  } finally {
    savingAvoid.value = false
  }
}

// ---------------------------------------------------------------- 改密码

const passwordFormRef = ref<FormInstance>()
const passwordSaving = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '新密码长度 6-32 位', trigger: 'blur' },
  ],
  confirmPassword: [
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (!value) {
          callback(new Error('请再输入一次新密码'))
        } else if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function submitPassword(): Promise<void> {
  const instance = passwordFormRef.value
  if (!instance) {
    return
  }
  const valid = await instance.validate().catch(() => false)
  if (!valid) {
    return
  }

  passwordSaving.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    userStore.reset()
    ElMessage.success('密码已修改，请用新密码重新登录')
    void router.push('/login')
  } catch {
    /* 旧密码错误等提示由拦截器给出 */
  } finally {
    passwordSaving.value = false
  }
}

// ---------------------------------------------------------------- 我的收藏

const favorites = ref<RecipeCardVO[]>([])
const favoriteTotal = ref(0)
const favoritePage = ref(1)
const favoritesLoading = ref(false)
const favoritesError = ref('')
const favoriteBusyId = ref<number | null>(null)
const FAVORITE_SIZE = 12

async function loadFavorites(page = 1): Promise<void> {
  favoritesLoading.value = true
  favoritesError.value = ''
  try {
    const result = await listMyFavorites({ page, size: FAVORITE_SIZE })
    favorites.value = result.list
    favoriteTotal.value = result.total
    favoritePage.value = result.page
  } catch (error) {
    if (!isCanceled(error)) {
      favoritesError.value = error instanceof Error ? error.message : '加载失败'
      favorites.value = []
    }
  } finally {
    favoritesLoading.value = false
  }
}

async function removeFavorite(recipe: RecipeCardVO): Promise<void> {
  favoriteBusyId.value = recipe.id
  try {
    if (recipe.favorited) {
      await unfavoriteRecipe(recipe.id)
      ElMessage.success('已取消收藏')
      // 列表里删掉这条，保持和「我的收藏」语义一致
      favorites.value = favorites.value.filter((item) => item.id !== recipe.id)
      favoriteTotal.value = Math.max(0, favoriteTotal.value - 1)
    } else {
      await favoriteRecipe(recipe.id)
      recipe.favorited = true
      ElMessage.success('已加入收藏')
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    favoriteBusyId.value = null
  }
}

// ---------------------------------------------------------------- 我的帖子

const myPosts = ref<PostCardVO[]>([])
const myPostsLoading = ref(false)
const myPostsError = ref('')
const myPostsScanned = ref(0)
const likeBusyId = ref<number | null>(null)

/**
 * 契约里没有「我的帖子」接口，这里用公开信息流的游标分页往前翻几页，
 * 再按 authorId 过滤（只统计已经加载的部分，不做全量承诺）。
 */
async function loadMyPosts(): Promise<void> {
  myPostsLoading.value = true
  myPostsError.value = ''
  const collected: PostCardVO[] = []
  let cursor: number | null = null

  try {
    for (let round = 0; round < 5; round += 1) {
      const result = await listPosts(
        cursor === null
          ? { sort: 'latest', size: 20 }
          : { sort: 'latest', size: 20, cursor },
      )
      collected.push(...result.list)
      const last = result.list[result.list.length - 1]
      if (!last || result.pages !== -1) {
        break
      }
      cursor = last.id
    }
    myPostsScanned.value = collected.length
    myPosts.value = collected.filter((post) => post.authorId === userStore.userId)
  } catch (error) {
    if (!isCanceled(error)) {
      myPostsError.value = error instanceof Error ? error.message : '加载失败'
      myPosts.value = []
    }
  } finally {
    myPostsLoading.value = false
  }
}

async function toggleLike(post: PostCardVO): Promise<void> {
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

// ---------------------------------------------------------------- 初始化

onMounted(async () => {
  avoidSelected.value = [...userStore.avoidances]
  void loadFavorites(1)
  void loadMyPosts()
  void metaStore.ensure().catch(() => undefined)
  const me = await userStore.ensureUser()
  if (me) {
    avoidSelected.value = [...me.avoidances]
  }
})

function onTabChange(name: string | number): void {
  if (name === 'favorites' && favorites.value.length === 0 && !favoritesLoading.value) {
    void loadFavorites(1)
  }
  if (name === 'posts' && myPosts.value.length === 0 && !myPostsLoading.value) {
    void loadMyPosts()
  }
}
</script>

<template>
  <div class="page page--narrow">
    <div class="page-head">
      <div>
        <h1 class="page-title">个人中心</h1>
        <p class="page-subtitle">{{ userStore.displayName }} · {{ roleLabel }}</p>
      </div>
    </div>

    <div class="profile-head surface">
      <el-avatar :size="56" :src="userStore.avatarUrl ?? undefined">{{ avatarText }}</el-avatar>
      <div class="profile-head__text">
        <strong>{{ userStore.displayName }}</strong>
        <span class="muted">@{{ userStore.user?.username }}</span>
        <span class="muted">{{ userStore.user?.email || '未填写邮箱' }}</span>
      </div>
      <el-tag :type="userStore.isAdmin ? 'danger' : 'info'" effect="light">{{ roleLabel }}</el-tag>
    </div>

    <el-tabs v-model="activeTab" class="profile-tabs" @tab-change="onTabChange">
      <!-- 资料 -->
      <el-tab-pane label="资料" name="profile">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户 ID">{{ userStore.user?.id ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="用户名">
            {{ userStore.user?.username ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="昵称">
            {{ userStore.user?.nickname ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">
            {{ userStore.user?.email || '未填写' }}
          </el-descriptions-item>
          <el-descriptions-item label="已保存忌口">
            <template v-if="userStore.avoidances.length">
              <el-tag
                v-for="item in userStore.avoidances"
                :key="item"
                type="warning"
                effect="light"
                style="margin-right: 6px"
              >
                {{ item }}
              </el-tag>
            </template>
            <span v-else class="muted">还没有保存忌口</span>
          </el-descriptions-item>
        </el-descriptions>
        <p class="muted hint">
          说明：`docs/API.md` 里没有提供「修改昵称/邮箱/头像」的接口，所以这里只做展示，
          改密码与忌口在旁边的标签页里。
        </p>
      </el-tab-pane>

      <!-- 修改密码 -->
      <el-tab-pane label="修改密码" name="password">
        <el-form
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-width="96px"
          class="password-form"
        >
          <el-form-item label="当前密码" prop="oldPassword">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password :prefix-icon="Lock" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="passwordForm.newPassword" type="password" show-password :prefix-icon="Lock" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password :prefix-icon="Lock" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="passwordSaving" @click="submitPassword">
              修改密码
            </el-button>
            <span class="muted hint-inline">改完会清掉本地令牌，需要重新登录</span>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 忌口偏好 -->
      <el-tab-pane label="忌口偏好" name="avoidances">
        <p class="muted">保存后，每次随机配餐都会自动带上这些关键词。</p>
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
            @close="toggleAvoid(item)"
          >
            {{ item }}
          </el-tag>
        </div>

        <el-button type="primary" :loading="savingAvoid" @click="saveAvoidances">
          保存忌口
        </el-button>
      </el-tab-pane>

      <!-- 我的收藏 -->
      <el-tab-pane label="我的收藏" name="favorites">
        <LoadingState v-if="favoritesLoading" variant="cards" :cards="3" />

        <el-result
          v-else-if="favoritesError"
          icon="error"
          title="收藏加载失败"
          :sub-title="favoritesError"
        >
          <template #extra>
            <el-button type="primary" @click="loadFavorites(favoritePage)">重试</el-button>
          </template>
        </el-result>

        <EmptyState
          v-else-if="favorites.length === 0"
          title="还没有收藏任何菜谱"
          description="在菜谱卡片或详情页点收藏，就会出现在这里"
        >
          <el-button type="primary" :icon="Star" @click="router.push('/recipes')">
            去挑几道菜
          </el-button>
        </EmptyState>

        <template v-else>
          <div class="card-grid">
            <RecipeCard
              v-for="recipe in favorites"
              :key="recipe.id"
              :recipe="recipe"
              favoritable
              :busy="favoriteBusyId === recipe.id"
              @toggle-favorite="removeFavorite"
            />
          </div>
          <div v-if="favoriteTotal > FAVORITE_SIZE" class="pager">
            <el-pagination
              background
              layout="prev, pager, next, total"
              :current-page="favoritePage"
              :page-size="FAVORITE_SIZE"
              :total="favoriteTotal"
              @current-change="(page: number) => loadFavorites(page)"
            />
          </div>
        </template>
      </el-tab-pane>

      <!-- 我的帖子 -->
      <el-tab-pane label="我的帖子" name="posts">
        <p class="muted hint">
          契约里没有「我的帖子」接口，这里按最新信息流向前翻最多 5 页（已扫描
          {{ myPostsScanned }} 条）再按作者过滤，所以可能不是你的全部历史帖子。
        </p>

        <LoadingState v-if="myPostsLoading" variant="cards" :cards="2" />

        <el-result v-else-if="myPostsError" icon="error" title="加载失败" :sub-title="myPostsError">
          <template #extra>
            <el-button type="primary" @click="loadMyPosts">重试</el-button>
          </template>
        </el-result>

        <EmptyState
          v-else-if="myPosts.length === 0"
          title="还没有发过帖子"
          description="去社区晒一晒你的拿手菜"
        >
          <el-button type="primary" @click="router.push('/community')">去发帖</el-button>
        </EmptyState>

        <div v-else class="post-list">
          <PostListItem
            v-for="post in myPosts"
            :key="post.id"
            :post="post"
            :busy="likeBusyId === post.id"
            @like="toggleLike"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.profile-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
}

.profile-head__text {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.profile-head__text strong {
  font-size: 17px;
}

.profile-head__text span {
  font-size: 12px;
}

.profile-head :deep(.el-tag) {
  margin-left: auto;
}

.profile-tabs {
  margin-top: 18px;
}

.hint {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.7;
}

.hint-inline {
  margin-left: 12px;
  font-size: 12px;
}

.password-form {
  max-width: 460px;
}

.avoid-custom {
  display: flex;
  gap: 8px;
  max-width: 420px;
  margin: 14px 0;
}

.chip-row {
  margin-bottom: 12px;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
