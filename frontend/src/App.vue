<template>
  <div class="app-shell">
    <header class="app-header">
      <h1>Instakill</h1>
      <nav v-if="user" class="nav-menu">
        <span class="user">@{{ user.username }}</span>
        <button
          class="nav-tab-btn"
          :class="{ active: currentTab === 'feed' }"
          @click="currentTab = 'feed'"
        >
          Feed
        </button>
        <button
          class="nav-tab-btn"
          :class="{ active: currentTab === 'analytics' }"
          @click="openAnalytics"
        >
          Analytics
        </button>
        <button class="logout-btn" @click="logout">Logout</button>
      </nav>
    </header>

    <main class="content">
      <section v-if="!user" class="card">
        <div class="tabs">
          <button :class="{ active: authMode === 'login' }" @click="authMode = 'login'">Login</button>
          <button :class="{ active: authMode === 'register' }" @click="authMode = 'register'">Register</button>
        </div>
        <form @submit.prevent="submitAuth" class="form">
          <input v-model="authForm.usernameOrEmail" v-if="authMode === 'login'" placeholder="Username or email" required>
          <input v-model="authForm.username" v-else placeholder="Username" required minlength="3">
          <input v-model="authForm.email" v-if="authMode === 'register'" type="email" placeholder="Email" required>
          <input v-model="authForm.password" type="password" placeholder="Password" required minlength="6">
          <p v-if="error" class="error">{{ error }}</p>
          <button type="submit">Continue</button>
        </form>
      </section>

      <!-- Analytics Tab -->
      <section v-else-if="currentTab === 'analytics'" class="analytics-container">
        <div class="analytics-toolbar">
          <h2>User Analytics</h2>
          <button class="refresh-btn" @click="loadAnalytics" :disabled="analyticsLoading">
            {{ analyticsLoading ? 'Refreshing...' : 'Refresh' }}
          </button>
        </div>

        <div v-if="analyticsLoading && !analytics" class="card loading-box">
          Loading analytics data...
        </div>

        <div v-else-if="analyticsError" class="card error-box">
          <p class="error">{{ analyticsError }}</p>
          <button @click="loadAnalytics">Retry</button>
        </div>

        <div v-else-if="analytics" class="analytics-grid">
          <!-- Requirement 5: Posts Rank in Top X% of Users -->
          <div class="card ranking-banner">
            <div class="ranking-content">
              <span class="ranking-badge">Global Standing</span>
              <h3>
                Your posts rank in the
                <span class="highlight-percentile">Top {{ analytics.engagementRanking.topPercentile }}%</span>
                of users
              </h3>
              <p class="ranking-desc">
                Calculated by average engagement (likes + comments per post) across all users.
              </p>
              <div class="ranking-metrics">
                <div class="metric-pill">
                  <span class="label">Rank:</span>
                  <strong>#{{ analytics.engagementRanking.rank }}</strong>
                  <span class="muted">/ {{ analytics.engagementRanking.totalUsers }}</span>
                </div>
                <div class="metric-pill">
                  <span class="label">Avg Engagement:</span>
                  <strong>{{ analytics.engagementRanking.userAvgEngagement }}</strong>
                  <span class="muted">/ post</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Cards Row: Requirement 3 (Ratio) & Requirement 4 (Media Type) -->
          <div class="analytics-cards-row">
            <!-- Requirement 3: Like-to-Comment Ratio -->
            <div class="card stat-card">
              <h4>Like-to-Comment Ratio</h4>
              <div class="stat-number">{{ analytics.likeCommentRatio.ratio }}:1</div>
              <p class="stat-subtitle">
                <span>{{ analytics.likeCommentRatio.totalLikes }} total likes</span> &bull;
                <span>{{ analytics.likeCommentRatio.totalComments }} total comments</span>
              </p>
            </div>

            <!-- Requirement 4: Average Likes by Media Type (Photos vs Videos) -->
            <div class="card stat-card">
              <h4>Avg Likes by Media Type</h4>
              <div class="media-compare">
                <div class="media-item">
                  <span class="media-label photo-tag">Photos</span>
                  <div class="media-avg">{{ analytics.mediaLikesStats.photoAvgLikes }}</div>
                  <small>{{ analytics.mediaLikesStats.photoPostCount }} posts</small>
                </div>
                <div class="vertical-divider"></div>
                <div class="media-item">
                  <span class="media-label video-tag">Videos</span>
                  <div class="media-avg">{{ analytics.mediaLikesStats.videoAvgLikes }}</div>
                  <small>{{ analytics.mediaLikesStats.videoPostCount }} posts</small>
                </div>
              </div>
            </div>
          </div>

          <!-- Requirement 2: Top 5 Posts by Engagement -->
          <div class="card top-posts-card">
            <div class="card-header-row">
              <div>
                <h3>Top 5 Posts by Engagement</h3>
                <small class="muted">Engagement = Total likes + comments per post</small>
              </div>
            </div>

            <div v-if="!analytics.topPosts || analytics.topPosts.length === 0" class="empty-state">
              No posts found.
            </div>

            <div v-else class="top-posts-list">
              <div v-for="(post, index) in analytics.topPosts" :key="post.id" class="top-post-item">
                <div class="rank-number">#{{ index + 1 }}</div>
                <div class="post-info">
                  <div class="post-content-preview">{{ post.content }}</div>
                  <small class="post-date">{{ formatDate(post.createdAt) }}</small>
                </div>
                <div class="engagement-badge-box">
                  <div class="engagement-score">{{ post.engagement }} eng</div>
                  <div class="engagement-breakdown">
                    <span>{{ post.likeCount }} likes</span> &bull; <span>{{ post.commentCount }} comments</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Requirement 1: Total Posts, Likes, and Comments per Week -->
          <div class="card weekly-card">
            <div class="card-header-row">
              <div>
                <h3>Activity by Week of Creation</h3>
                <small class="muted">Grouped by week of post creation</small>
              </div>
            </div>

            <div v-if="!analytics.weeklyStats || analytics.weeklyStats.length === 0" class="empty-state">
              No activity records found for this user.
            </div>

            <div v-else class="table-container">
              <table class="analytics-table">
                <thead>
                  <tr>
                    <th>Week</th>
                    <th class="num-col">Posts</th>
                    <th class="num-col">Likes</th>
                    <th class="num-col">Comments</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="week in analytics.weeklyStats" :key="week.week">
                    <td class="week-cell"><strong>{{ week.week }}</strong></td>
                    <td class="num-col">{{ week.posts }}</td>
                    <td class="num-col like-text">{{ week.likes }}</td>
                    <td class="num-col comment-text">{{ week.comments }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </section>

      <!-- Feed Tab -->
      <section v-else class="feed">
        <form @submit.prevent="createNewPost" class="card form composer">
          <textarea v-model="composer.content" rows="3" placeholder="Share something..." required></textarea>

          <div class="file-upload-row">
            <label class="file-input-label">Attach Media (Image / Video):</label>
            <input
              ref="fileInputRef"
              type="file"
              accept=".jpg,.jpeg,.png,.gif,.webp,.mp4,.mov,.webm"
              @change="handleFileUpload"
              :disabled="isUploading"
            />
            <div v-if="isUploading" class="upload-status">Uploading media...</div>
            <div v-if="uploadError" class="error">{{ uploadError }}</div>

            <div class="or-divider">— or paste an image/video URL —</div>
            <input v-model="composer.imageUrl" placeholder="Image or Video URL (optional)" :disabled="isUploading">

            <div v-if="composer.imageUrl" class="media-preview-container">
              <div class="media-preview-header">
                <span>Attached: <code>{{ composer.imageUrl }}</code></span>
                <button type="button" @click="clearMedia">Remove</button>
              </div>
              <video v-if="isVideo(composer.imageUrl)" :src="composer.imageUrl" controls class="preview-media"></video>
              <img v-else :src="composer.imageUrl" alt="Preview" class="preview-media">
            </div>
          </div>

          <div class="actions">
            <button type="submit" :disabled="isUploading">{{ isUploading ? 'Uploading...' : 'Post' }}</button>
          </div>
        </form>

        <div class="feed-toolbar">
          <h2>Feed</h2>
          <div class="sort">
            <button :class="{ active: sort === 'new' }" @click="changeSort('new')">Newest</button>
            <button :class="{ active: sort === 'trending' }" @click="changeSort('trending')">Trending</button>
          </div>
        </div>

        <article v-for="post in posts" :key="post.id" class="card post">
          <header>
            <div>
              <h3>@{{ post.author.username }}</h3>
              <small>{{ formatDate(post.createdAt) }}</small>
            </div>
            <div class="metrics">
              <span>{{ post.likeCount }} likes</span>
              <span> | </span>
              <span>{{ post.commentCount }} comments</span>
            </div>
          </header>
          <p>{{ post.content }}</p>
          <video v-if="post.imageUrl && isVideo(post.imageUrl)" :src="post.imageUrl" controls></video>
          <img v-else-if="post.imageUrl" :src="post.imageUrl" alt="post image">
          
          <!-- Comments Section -->
          <section class="comments">
            <div v-if="comments[post.id] && comments[post.id].length">
              <div v-for="comment in comments[post.id]" :key="comment.id" class="comment">
                <b>@{{ comment.author.username }}</b> <small>{{ formatDate(comment.createdAt) }}</small>
                <div>{{ comment.content }}</div>
              </div>
            </div>
            <div v-else class="no-comments">
              <em>No comments yet.</em>
            </div>
            <form v-if="user" @submit.prevent="submitComment(post.id)" class="comment-form">
              <input
                v-model="newComment[post.id]"
                :placeholder="'Add a comment...'"
                required
                minlength="1"
              />
              <button type="submit" :disabled="commentLoading[post.id]">Comment</button>
            </form>
            <div v-if="commentError[post.id]" class="error">{{ commentError[post.id] }}</div>
          </section>

          <footer>
            <button @click="togglePostLike(post)">
              {{ post.likedByCurrentUser ? 'Unlike' : 'Like' }}
            </button>
          </footer>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  createPost,
  fetchMe,
  listFeed,
  login,
  register as registerUser,
  toggleLike,
  listComments,
  addComment,
  uploadMedia,
  fetchAnalytics
} from './api'

const user = ref(null)
const currentTab = ref('feed')
const posts = ref([])
const sort = ref('new')
const authMode = ref('login')
const error = ref('')
const authForm = reactive({ username: '', email: '', password: '', usernameOrEmail: '' })
const composer = reactive({ content: '', imageUrl: '' })
const isUploading = ref(false)
const uploadError = ref('')
const fileInputRef = ref(null)

// Analytics state
const analytics = ref(null)
const analyticsLoading = ref(false)
const analyticsError = ref('')

function isVideo(url) {
  if (!url) return false
  const clean = url.split('?')[0].toLowerCase()
  return clean.endsWith('.mp4') || clean.endsWith('.mov') || clean.endsWith('.webm') || clean.endsWith('.avi') || clean.endsWith('.mkv')
}

async function handleFileUpload(event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploadError.value = ''
  isUploading.value = true
  try {
    const res = await uploadMedia(file)
    composer.imageUrl = res.mediaUrl
  } catch (e) {
    uploadError.value = e.response?.data?.detail || e.response?.data?.message || 'Failed to upload media file'
  } finally {
    isUploading.value = false
  }
}

function clearMedia() {
  composer.imageUrl = ''
  uploadError.value = ''
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

// Comments state
const comments = reactive({})
const newComment = reactive({})
const commentLoading = reactive({})
const commentError = reactive({})

onMounted(async () => {
  await bootstrap()
})

async function bootstrap() {
  try {
    user.value = await fetchMe()
    await loadFeed()
  } catch (e) {
    user.value = null
  }
}

async function submitAuth() {
  error.value = ''
  try {
    if (authMode.value === 'login') {
      const result = await login({
        usernameOrEmail: authForm.usernameOrEmail,
        password: authForm.password
      })
      user.value = result.user
    } else {
      const result = await registerUser({
        username: authForm.username,
        email: authForm.email,
        password: authForm.password
      })
      user.value = result.user
    }
    await loadFeed()
  } catch (e) {
    error.value = e.response?.data?.detail || 'Authentication failed'
  }
}

async function loadFeed() {
  posts.value = await listFeed({ sort: sort.value })
  for (const post of posts.value) {
    await loadComments(post.id)
  }
}

async function openAnalytics() {
  currentTab.value = 'analytics'
  await loadAnalytics()
}

async function loadAnalytics() {
  analyticsLoading.value = true
  analyticsError.value = ''
  try {
    analytics.value = await fetchAnalytics()
  } catch (e) {
    analyticsError.value = e.response?.data?.detail || e.message || 'Failed to load analytics data'
  } finally {
    analyticsLoading.value = false
  }
}

async function loadComments(postId) {
  try {
    commentError[postId] = ''
    comments[postId] = await listComments(postId)
  } catch (e) {
    comments[postId] = []
    commentError[postId] = 'Failed to load comments'
  }
}

async function submitComment(postId) {
  if (!newComment[postId] || !newComment[postId].trim()) return
  commentLoading[postId] = true
  commentError[postId] = ''
  try {
    await addComment(postId, { content: newComment[postId] })
    newComment[postId] = ''
    await loadComments(postId)
    const post = posts.value.find(p => p.id === postId)
    if (post) post.commentCount = (comments[postId]?.length || 0)
  } catch (e) {
    commentError[postId] = e.response?.data?.detail || 'Failed to add comment'
  } finally {
    commentLoading[postId] = false
  }
}

async function createNewPost() {
  if (!composer.content.trim() || isUploading.value) return
  await createPost({ content: composer.content, imageUrl: composer.imageUrl || null })
  composer.content = ''
  composer.imageUrl = ''
  uploadError.value = ''
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
  await loadFeed()
}

async function togglePostLike(post) {
  const result = await toggleLike(post.id)
  post.likedByCurrentUser = result.liked
  post.likeCount = result.likeCount
}

function changeSort(next) {
  if (sort.value === next) return
  sort.value = next
  loadFeed()
}

function formatDate(iso) {
  return new Date(iso).toLocaleString()
}

async function logout() {
  try {
    await fetch('/auth/logout', { method: 'POST', credentials: 'include' })
  } catch (e) {
    // ignore
  }
  document.cookie = 'ACCESS_TOKEN=; Max-Age=0; path=/'
  user.value = null
  posts.value = []
  analytics.value = null
  currentTab.value = 'feed'
}
</script>

<style scoped>
.app-shell {
  max-width: 960px;
  margin: 0 auto;
  padding: 2rem 1rem;
}

.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.app-header nav {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.nav-tab-btn {
  background: transparent;
  color: #4b5563;
  border: 1px solid #d1d5db;
  padding: 0.45rem 1rem;
  border-radius: 999px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.nav-tab-btn.active {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}

.logout-btn {
  background: #111827;
  color: #fff;
  border: none;
  padding: 0.45rem 1rem;
  border-radius: 999px;
  cursor: pointer;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.card {
  background: #fff;
  border-radius: 16px;
  padding: 1.5rem;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.08);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.form input,
.form textarea {
  width: 100%;
  padding: 0.75rem;
  border-radius: 10px;
  border: 1px solid #d1d5db;
}

.form button {
  align-self: flex-start;
  background: #2563eb;
  color: #fff;
  border: none;
  padding: 0.5rem 1.25rem;
  border-radius: 999px;
}

.tabs {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
}

.tabs button {
  background: transparent;
  border: none;
  font-weight: 600;
  color: #6b7280;
}

.tabs button.active {
  color: #111827;
}

.error {
  color: #ef4444;
}

.feed-toolbar,
.analytics-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.refresh-btn {
  background: #e5e7eb;
  color: #1f2937;
  border: none;
  padding: 0.4rem 1rem;
  border-radius: 999px;
  font-weight: 600;
  cursor: pointer;
}

.refresh-btn:hover {
  background: #d1d5db;
}

.sort button {
  margin-left: 0.5rem;
  border: none;
  background: #e5e7eb;
  padding: 0.4rem 0.9rem;
  border-radius: 999px;
}

.sort button.active {
  background: #111827;
  color: #fff;
}

.post header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.post img,
.post video {
  margin-top: 0.75rem;
  width: 100%;
  max-height: 500px;
  border-radius: 12px;
  object-fit: contain;
  background: #000;
}

.file-upload-row {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.file-input-label {
  font-size: 0.85rem;
  font-weight: 600;
  color: #374151;
}

.upload-status {
  font-size: 0.85rem;
  color: #2563eb;
  font-weight: 500;
}

.or-divider {
  font-size: 0.8rem;
  color: #9ca3af;
  text-align: center;
  margin: 0.2rem 0;
}

.media-preview-container {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  background: #f9fafb;
  border: 1px dashed #d1d5db;
  border-radius: 10px;
  padding: 0.75rem;
}

.media-preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.85rem;
  color: #374151;
}

.media-preview-header button {
  background: #ef4444;
  color: #fff;
  border: none;
  padding: 0.2rem 0.6rem;
  border-radius: 6px;
  font-size: 0.75rem;
  cursor: pointer;
}

.preview-media {
  max-width: 100%;
  max-height: 250px;
  border-radius: 8px;
  object-fit: contain;
  background: #000;
}

.post footer {
  margin-top: 1rem;
}

.post footer button {
  background: #111827;
  border: none;
  color: #fff;
  padding: 0.4rem 1.2rem;
  border-radius: 999px;
}

.comments {
  margin-top: 1.2rem;
  padding-top: 1rem;
  border-top: 1px solid #e5e7eb;
}

.comment {
  margin-bottom: 0.7rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #f3f4f6;
}

.no-comments {
  color: #6b7280;
  margin-bottom: 0.7rem;
}

.comment-form {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.comment-form input {
  flex: 1;
  padding: 0.5rem;
  border-radius: 8px;
  border: 1px solid #d1d5db;
}

.comment-form button {
  background: #2563eb;
  color: #fff;
  border: none;
  padding: 0.4rem 1.1rem;
  border-radius: 999px;
}

/* Analytics Styles */
.analytics-container {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.analytics-grid {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.ranking-banner {
  border-left: 6px solid #2563eb;
  background: linear-gradient(135deg, #ffffff 0%, #f0f7ff 100%);
}

.ranking-badge {
  display: inline-block;
  background: #dbeafe;
  color: #1e40af;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  text-transform: uppercase;
  margin-bottom: 0.5rem;
}

.ranking-content h3 {
  margin: 0.25rem 0 0.5rem 0;
  font-size: 1.4rem;
}

.highlight-percentile {
  color: #2563eb;
  font-weight: 800;
}

.ranking-desc {
  color: #6b7280;
  font-size: 0.9rem;
  margin-bottom: 1rem;
}

.ranking-stats {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.metric-pill {
  background: #fff;
  padding: 0.5rem 1rem;
  border-radius: 8px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
  font-size: 0.95rem;
}

.metric-pill .label {
  color: #6b7280;
  margin-right: 0.35rem;
}

.metric-pill .muted {
  color: #9ca3af;
  font-size: 0.85rem;
}

.analytics-cards-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
}

@media (max-width: 640px) {
  .analytics-cards-row {
    grid-template-columns: 1fr;
  }
}

.stat-card h4 {
  margin-top: 0;
  margin-bottom: 0.5rem;
  color: #4b5563;
  font-size: 1rem;
}

.stat-number {
  font-size: 2.25rem;
  font-weight: 800;
  color: #111827;
}

.stat-subtitle {
  color: #6b7280;
  font-size: 0.85rem;
  margin-top: 0.5rem;
}

.media-compare {
  display: flex;
  justify-content: space-around;
  align-items: center;
  margin-top: 0.5rem;
}

.media-item {
  text-align: center;
}

.media-label {
  display: inline-block;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  text-transform: uppercase;
}

.photo-tag {
  background: #ecfdf5;
  color: #047857;
}

.video-tag {
  background: #eef2ff;
  color: #4338ca;
}

.media-avg {
  font-size: 1.75rem;
  font-weight: 800;
  color: #111827;
  margin: 0.25rem 0;
}

.vertical-divider {
  width: 1px;
  height: 50px;
  background: #e5e7eb;
}

.card-header-row {
  margin-bottom: 1rem;
}

.card-header-row h3 {
  margin: 0;
}

.card-header-row .muted {
  color: #6b7280;
  font-size: 0.85rem;
}

.top-posts-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.top-post-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 0;
  border-bottom: 1px solid #f3f4f6;
}

.top-post-item:last-child {
  border-bottom: none;
}

.rank-number {
  font-weight: 800;
  font-size: 1.2rem;
  color: #2563eb;
  min-width: 32px;
}

.post-info {
  flex: 1;
}

.post-content-preview {
  font-weight: 600;
  color: #1f2937;
  font-size: 0.95rem;
}

.post-date {
  color: #9ca3af;
  font-size: 0.8rem;
}

.engagement-badge-box {
  text-align: right;
}

.engagement-score {
  font-weight: 800;
  font-size: 1.1rem;
  color: #111827;
}

.engagement-breakdown {
  font-size: 0.8rem;
  color: #6b7280;
}

.table-container {
  overflow-x: auto;
}

.analytics-table {
  width: 100%;
  border-collapse: collapse;
}

.analytics-table th {
  text-align: left;
  padding: 0.6rem 0.75rem;
  border-bottom: 2px solid #e5e7eb;
  color: #6b7280;
  font-size: 0.85rem;
  font-weight: 600;
}

.analytics-table td {
  padding: 0.75rem;
  border-bottom: 1px solid #f3f4f6;
  font-size: 0.95rem;
}

.analytics-table .num-col {
  text-align: right;
}

.like-text {
  color: #059669;
  font-weight: 600;
}

.comment-text {
  color: #4f46e5;
  font-weight: 600;
}

.empty-state {
  color: #9ca3af;
  font-style: italic;
  padding: 1rem 0;
}

.loading-box {
  text-align: center;
  color: #6b7280;
  padding: 2rem;
}

.error-box {
  text-align: center;
}
</style>
