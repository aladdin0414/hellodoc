<template>
  <div class="h-screen overflow-y-auto bg-slate-100 dark:bg-slate-900 text-slate-900 dark:text-slate-100 pb-24 transition-colors no-scrollbar select-none">
    <HeaderNav title="个人中心" />

    <main class="p-4 w-full space-y-4 max-w-md mx-auto">
      <!-- 用户资料卡片 -->
      <div class="p-5 bg-white dark:bg-slate-800/90 border border-slate-200/60 dark:border-slate-700/50 rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.03)] flex items-center gap-4 transition-all">
        <!-- 头像区：支持图片头像，若无图片退回水滴首字母头像 -->
        <div class="w-14 h-14 rounded-full overflow-hidden shrink-0 border border-slate-200/50 dark:border-slate-700/50 shadow-md shadow-blue-500/15 flex items-center justify-center bg-gradient-to-tr from-blue-600 to-indigo-500 text-white font-bold text-xl">
          <img v-if="userInfo.avatar" :src="userInfo.avatar" :alt="userInfo.nickname || userInfo.username" class="w-full h-full object-cover" />
          <span v-else>{{ firstLetter }}</span>
        </div>

        <div class="min-w-0 flex-1">
          <h2 class="text-base font-bold text-slate-900 dark:text-white truncate">
            {{ userInfo.nickname || userInfo.username || '已登录用户' }}
          </h2>
          <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5 truncate">
            {{ userInfo.email || (userInfo.username ? `@${userInfo.username}` : 'HelloDoc 极简沉浸模式') }}
          </p>
        </div>
      </div>

      <!-- iOS Grouped Inset 风格设置列表组 -->
      <div class="bg-white dark:bg-slate-800/90 border border-slate-200/60 dark:border-slate-700/50 rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.03)] overflow-hidden divide-y divide-slate-100 dark:divide-slate-700/50 text-[14px]">
        <button
          @click="toggleTheme"
          class="w-full px-4 py-3.5 flex items-center justify-between hover:bg-slate-50 dark:hover:bg-slate-700/40 active:bg-slate-100 dark:active:bg-slate-700/70 transition-colors"
        >
          <div class="flex items-center gap-3">
            <div class="w-7 h-7 rounded-lg bg-amber-500/10 text-amber-500 flex items-center justify-center">
              <Sun v-if="isDark" class="w-4 h-4 text-amber-400 stroke-[2]" />
              <Moon v-else class="w-4 h-4 text-slate-600 stroke-[2]" />
            </div>
            <span class="text-slate-800 dark:text-slate-200 font-medium">外观模式</span>
          </div>
          <span class="text-xs text-slate-400 dark:text-slate-500 font-medium">{{ isDark ? '暗黑模式' : '明亮模式' }}</span>
        </button>

        <button
          @click="handleSwitchToDesktop"
          class="w-full px-4 py-3.5 flex items-center justify-between hover:bg-slate-50 dark:hover:bg-slate-700/40 active:bg-slate-100 dark:active:bg-slate-700/70 transition-colors"
        >
          <div class="flex items-center gap-3">
            <div class="w-7 h-7 rounded-lg bg-blue-500/10 text-blue-500 flex items-center justify-center">
              <Monitor class="w-4 h-4 text-blue-600 dark:text-blue-400 stroke-[2]" />
            </div>
            <span class="text-slate-800 dark:text-slate-200 font-medium">电脑版</span>
          </div>
          <ChevronRight class="w-4 h-4 text-slate-300 dark:text-slate-600 stroke-[2]" />
        </button>

        <button
          @click="handleLogout"
          class="w-full px-4 py-3.5 flex items-center justify-between hover:bg-rose-50 dark:hover:bg-rose-500/10 active:bg-rose-100 dark:active:bg-rose-500/20 transition-colors text-rose-500"
        >
          <div class="flex items-center gap-3">
            <div class="w-7 h-7 rounded-lg bg-rose-500/10 text-rose-500 flex items-center justify-center">
              <LogOut class="w-4 h-4 stroke-[2]" />
            </div>
            <span class="font-medium">退出登录</span>
          </div>
        </button>
      </div>

      <!-- 版本小尾巴 -->
      <div class="pt-4 text-center">
        <p class="text-[11px] text-slate-400 dark:text-slate-500 font-medium">HelloDoc Mobile v{{ versionInfo.version }}</p>
      </div>

      <!-- iOS 底部 Safe Area 物理占位块 -->
      <div class="h-24 w-full shrink-0 pointer-events-none pb-[env(safe-area-inset-bottom)]"></div>
    </main>

    <TabBar />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import HeaderNav from '../components/HeaderNav.vue'
import TabBar from '../components/TabBar.vue'
import { LogOut, Sun, Moon, Monitor, ChevronRight } from 'lucide-vue-next'
import { useTheme } from '../composables/useTheme'
import { getMe } from '../../api/user'
import versionInfo from '../../version'

interface UserProfile {
  nickname?: string
  username?: string
  avatar?: string
  email?: string
  role?: string
}

const router = useRouter()
const { isDark, toggleTheme } = useTheme()

const userInfo = ref<UserProfile>({
  nickname: localStorage.getItem('nickname') || '',
  username: localStorage.getItem('username') || '',
  avatar: localStorage.getItem('avatar') || '',
  email: localStorage.getItem('email') || ''
})

const firstLetter = computed(() => {
  const name = userInfo.value.nickname || userInfo.value.username || 'U'
  return name.charAt(0).toUpperCase()
})

const fetchProfileData = async () => {
  try {
    const data: any = await getMe()
    if (data) {
      userInfo.value = {
        nickname: data.nickname || '',
        username: data.username || '',
        avatar: data.avatar || '',
        email: data.email || '',
        role: data.role || ''
      }
      if (data.username) localStorage.setItem('username', data.username)
      if (data.nickname) localStorage.setItem('nickname', data.nickname)
      if (data.avatar) localStorage.setItem('avatar', data.avatar)
      if (data.email) localStorage.setItem('email', data.email)
    }
  } catch (err) {
    console.error('Failed to fetch profile in mobile view:', err)
  }
}

onMounted(() => {
  fetchProfileData()
})

const handleSwitchToDesktop = () => {
  sessionStorage.setItem('preferDesktop', 'true')
  router.push('/')
}

const handleLogout = () => {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('nickname')
  localStorage.removeItem('username')
  localStorage.removeItem('avatar')
  localStorage.removeItem('email')
  localStorage.removeItem('userRole')
  router.push('/m/login')
}
</script>
