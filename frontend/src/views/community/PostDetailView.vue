<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Delete, EditPen, Pointer, View } from '@element-plus/icons-vue'

import CommentItem from '@/components/CommentItem.vue'
import EmptyState from '@/components/EmptyState.vue'
import ImagePicker from '@/components/ImagePicker.vue'
import LoadingState from '@/components/LoadingState.vue'
import {
  addComment,
  deleteComment,
  deletePost,
  isCanceled,
  likePost,
  listComments,
  postDetail,
  unlikePost,
  updatePost,
} from '@/api'
import type { CommentVO, PostDetailVO, UploadResult } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { coverInitial } from '@/utils/cover'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const COMMENT_SIZE = 10

const detail = ref<PostDetailVO | null>(null)
const comments = ref<CommentVO[]>([])
const commentTotal = ref(0)
const commentPage = ref(1)

const loading = ref(true)
const commentsLoading = ref(false)
const errorText = ref('')
const notFound = ref(false)
const likeBusy = ref(false)
const submitting = ref(false)

const commentContent = ref('')
const replyTarget = ref<CommentVO | null>(null)
const replyContent = ref('')

const editOpen = ref(false)
const editSaving = ref(false)
const editForm = reactive({ title: '', content: '' })
const editImages = ref<UploadResult[]>([])

let controller: AbortController | null = null
let commentsController: AbortController | null = null

const postId = computed(() => Number(route.params.id))
const validId = computed(() => Number.isInteger(postId.value) && postId.value > 0)
const avatarText = computed(() => coverInitial(detail.value?.authorNickname || '匿'))
const previewUrls = computed(() => (detail.value?.images ?? []).map((image) => image.url))

async function loadDetail(): Promise<void> {
  if (!validId.value) {
    detail.value = null
    loading.value = false
    notFound.value = true
    return
  }

  controller?.abort()
  const current = new AbortController()
  controller = current

  loading.value = true
  errorText.value = ''
  notFound.value = false

  try {
    const result = await postDetail(postId.value, { signal: current.signal })
    if (controller !== current) {
      return
    }
    detail.value = result
    editForm.title = result.title
    editForm.content = result.content
    editImages.value = result.images.map((image) => ({
      url: image.url,
      thumbUrl: image.thumbUrl,
      width: image.width,
      height: image.height,
    }))
  } catch (error) {
    if (controller !== current || isCanceled(error)) {
      return
    }
    const failure = error as { code?: number; message?: string }
    if (failure.code === 404) {
      notFound.value = true
    } else {
      errorText.value = failure.message ?? '加载失败'
    }
    detail.value = null
  } finally {
    if (controller === current) {
      loading.value = false
    }
  }
}

async function loadComments(page = 1): Promise<void> {
  if (!validId.value) {
    return
  }
  commentsController?.abort()
  const current = new AbortController()
  commentsController = current

  commentsLoading.value = true
  try {
    const result = await listComments(
      postId.value,
      { page, size: COMMENT_SIZE },
      { signal: current.signal },
    )
    if (commentsController !== current) {
      return
    }
    comments.value = result.list
    commentTotal.value = result.total
    commentPage.value = result.page
  } catch (error) {
    if (!isCanceled(error)) {
      comments.value = []
    }
  } finally {
    if (commentsController === current) {
      commentsLoading.value = false
    }
  }
}

watch(
  () => route.params.id,
  () => {
    replyTarget.value = null
    commentContent.value = ''
    void loadDetail()
    void loadComments(1)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  controller?.abort()
  commentsController?.abort()
})

function requireLogin(action: string): boolean {
  if (userStore.isLoggedIn) {
    return true
  }
  ElMessage.warning(`登录后才能${action}`)
  void router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function toggleLike(): Promise<void> {
  const current = detail.value
  if (!current || !requireLogin('点赞')) {
    return
  }
  likeBusy.value = true
  try {
    const result = current.liked ? await unlikePost(current.id) : await likePost(current.id)
    current.liked = result.liked
    current.likeCount = result.likeCount
  } catch {
    /* 拦截器已提示 */
  } finally {
    likeBusy.value = false
  }
}

async function submitComment(): Promise<void> {
  const content = commentContent.value.trim()
  if (!content) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  if (!requireLogin('评论')) {
    return
  }
  submitting.value = true
  try {
    await addComment(postId.value, { content })
    commentContent.value = ''
    ElMessage.success('评论成功')
    if (detail.value) {
      detail.value.commentCount += 1
    }
    await loadComments(1)
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

function startReply(comment: CommentVO): void {
  if (!requireLogin('回复')) {
    return
  }
  replyTarget.value = comment
  replyContent.value = ''
  // 回复框在评论列表上方，滚过去让用户看得见
  window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' })
}

function cancelReply(): void {
  replyTarget.value = null
  replyContent.value = ''
}

async function submitReply(): Promise<void> {
  const target = replyTarget.value
  const content = replyContent.value.trim()
  if (!target) {
    return
  }
  if (!content) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  submitting.value = true
  try {
    // parentId 传被回复的那条；后端对「回复的回复」会自动挂回同一一级评论
    await addComment(postId.value, { content, parentId: target.id })
    replyContent.value = ''
    replyTarget.value = null
    ElMessage.success('回复成功')
    if (detail.value) {
      detail.value.commentCount += 1
    }
    await loadComments(commentPage.value)
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function removeComment(comment: CommentVO): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确定删除这条评论吗？', '删除评论', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '再想想',
    })
  } catch {
    return
  }

  try {
    await deleteComment(comment.id)
    ElMessage.success('已删除')
    if (detail.value) {
      detail.value.commentCount = Math.max(0, detail.value.commentCount - 1)
    }
    await loadComments(commentPage.value)
  } catch {
    /* 拦截器已提示 */
  }
}

function openEditor(): void {
  const current = detail.value
  if (!current) {
    return
  }
  editForm.title = current.title
  editForm.content = current.content
  editOpen.value = true
}

async function submitEdit(): Promise<void> {
  const current = detail.value
  if (!current) {
    return
  }
  if (!editForm.title.trim() || !editForm.content.trim()) {
    ElMessage.warning('标题和正文都不能为空')
    return
  }
  editSaving.value = true
  try {
    const updated = await updatePost(current.id, {
      title: editForm.title.trim(),
      content: editForm.content.trim(),
      imageUrls: editImages.value.map((image) => image.url),
    })
    detail.value = updated
    editOpen.value = false
    ElMessage.success('已保存')
  } catch {
    /* 拦截器已提示 */
  } finally {
    editSaving.value = false
  }
}

async function removePost(): Promise<void> {
  const current = detail.value
  if (!current) {
    return
  }
  try {
    await ElMessageBox.confirm('删除后帖子不再展示，确定删除吗？', '删除帖子', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '再想想',
    })
  } catch {
    return
  }

  try {
    await deletePost(current.id)
    ElMessage.success('帖子已删除')
    void router.push('/community')
  } catch {
    /* 拦截器已提示 */
  }
}
</script>

<template>
  <div class="page page--narrow">
    <el-button text :icon="ArrowLeft" style="margin-bottom: 12px" @click="router.back()">
      返回
    </el-button>

    <LoadingState v-if="loading" :rows="6" />

    <EmptyState v-else-if="notFound" title="帖子不存在或已被删除" description="去社区看看别的作品">
      <el-button type="primary" @click="router.push('/community')">回到社区</el-button>
    </EmptyState>

    <el-result v-else-if="errorText" icon="error" title="帖子加载失败" :sub-title="errorText">
      <template #extra>
        <el-button type="primary" @click="loadDetail">重试</el-button>
      </template>
    </el-result>

    <template v-else-if="detail">
      <article class="post surface">
        <h1 class="post__title">{{ detail.title }}</h1>

        <div class="post__meta">
          <el-avatar :size="34" :src="detail.authorAvatar ?? undefined">
            {{ avatarText }}
          </el-avatar>
          <div class="post__meta-text">
            <strong>{{ detail.authorNickname || '匿名用户' }}</strong>
            <span class="muted">{{ formatDateTime(detail.createdAt) }}</span>
          </div>
          <span class="post__meta-right muted">
            <el-icon><View /></el-icon>
            <span class="tnum">{{ detail.viewCount }}</span>
          </span>
        </div>

        <!-- 用户正文：文本插值 + pre-wrap，绝不用 v-html -->
        <p class="post__content prewrap">{{ detail.content }}</p>

        <div v-if="detail.images.length" class="post__images">
          <el-image
            v-for="(image, index) in detail.images"
            :key="image.url"
            class="post__image"
            :src="image.url"
            :preview-src-list="previewUrls"
            :initial-index="index"
            fit="cover"
            lazy
            preview-teleported
          />
        </div>

        <div class="post__actions">
          <el-button
            :type="detail.liked ? 'primary' : 'default'"
            :icon="Pointer"
            :loading="likeBusy"
            @click="toggleLike"
          >
            {{ detail.liked ? '已点赞' : '点赞' }}（{{ detail.likeCount }}）
          </el-button>

          <template v-if="detail.editable">
            <el-button :icon="EditPen" @click="openEditor">编辑</el-button>
            <el-button type="danger" plain :icon="Delete" @click="removePost">删除</el-button>
          </template>

          <span class="muted small">评论 {{ detail.commentCount }}</span>
        </div>
      </article>

      <section class="comments surface">
        <h2 class="comments__title">评论（{{ commentTotal }}）</h2>

        <div v-if="replyTarget" class="comment-editor comment-editor--reply">
          <div class="comment-editor__tip">
            回复 <strong>@{{ replyTarget.authorNickname || '匿名用户' }}</strong>
            <el-button text size="small" @click="cancelReply">取消</el-button>
          </div>
          <el-input
            v-model="replyContent"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="说点什么…"
          />
          <div class="comment-editor__foot">
            <el-button type="primary" size="small" :loading="submitting" @click="submitReply">
              发送回复
            </el-button>
          </div>
        </div>

        <div class="comment-editor">
          <el-input
            v-model="commentContent"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="userStore.isLoggedIn ? '友善发言，说点具体的～' : '登录后即可评论'"
          />
          <div class="comment-editor__foot">
            <el-button type="primary" :loading="submitting" @click="submitComment">
              发表评论
            </el-button>
          </div>
        </div>

        <LoadingState v-if="commentsLoading" :rows="3" />

        <EmptyState
          v-else-if="comments.length === 0"
          title="还没有评论"
          description="来抢第一个沙发"
        />

        <div v-else class="comment-list">
          <CommentItem
            v-for="comment in comments"
            :key="comment.id"
            :comment="comment"
            @reply="startReply"
            @delete="removeComment"
          >
            <template #replies>
              <div v-if="comment.replies.length" class="comment-replies">
                <CommentItem
                  v-for="reply in comment.replies"
                  :key="reply.id"
                  :comment="reply"
                  nested
                  @reply="startReply"
                  @delete="removeComment"
                />
              </div>
            </template>
          </CommentItem>
        </div>

        <div v-if="commentTotal > COMMENT_SIZE" class="pager">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :current-page="commentPage"
            :page-size="COMMENT_SIZE"
            :total="commentTotal"
            @current-change="(page: number) => loadComments(page)"
          />
        </div>
      </section>
    </template>

    <el-dialog v-model="editOpen" title="编辑帖子" width="640px">
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="editForm.content" type="textarea" :rows="6" maxlength="5000" show-word-limit />
        </el-form-item>
        <el-form-item label="图片（最多 9 张）">
          <ImagePicker v-model="editImages" :max="9" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editOpen = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.post {
  padding: 22px;
}

.post__title {
  font-size: 26px;
  line-height: 1.35;
}

.post__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 14px 0 6px;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--foodie-border);
}

.post__meta-text {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
  font-size: 13px;
}

.post__meta-right {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  font-size: 12px;
}

.post__content {
  margin: 12px 0 0;
  font-size: 15px;
  line-height: 1.9;
}

.post__images {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  margin-top: 16px;
}

.post__image {
  width: 100%;
  height: 150px;
  border-radius: 10px;
  overflow: hidden;
}

.post__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px dashed var(--foodie-border);
}

.small {
  font-size: 12px;
}

.comments {
  margin-top: 20px;
  padding: 20px 22px 24px;
}

.comments__title {
  font-size: 17px;
}

.comment-editor {
  margin: 14px 0 18px;
}

.comment-editor--reply {
  padding: 12px;
  border: 1px solid #ffd591;
  border-radius: 12px;
  background: #fffbf0;
}

.comment-editor__tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 13px;
}

.comment-editor__foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.comment-list {
  display: flex;
  flex-direction: column;
}

.comment-list :deep(.comment) {
  border-bottom: 1px dashed var(--foodie-border);
}

.comment-replies {
  margin: 2px 0 6px 20px;
  padding-left: 12px;
  border-left: 2px solid var(--foodie-border);
}
</style>
