<template>
  <div class="h-screen overflow-y-auto bg-gray-50 dark:bg-slate-900 text-gray-900 dark:text-slate-100 transition-colors no-scrollbar">
    <!-- 顶部 Navigation：规范组件统一接入 -->
    <HeaderNav>
      <template #left>
        <div class="flex items-center gap-2">
          <img src="../../assets/logo.svg" alt="HelloDoc Logo" class="w-7 h-7 drop-shadow-sm" />
          <span class="text-base font-black tracking-tight text-slate-900 dark:text-white">{{ t('mobile.shared.title') }}</span>
        </div>
      </template>
    </HeaderNav>

    <!-- 搜索与卡片主体区 -->
    <main class="p-4 space-y-4 w-full max-w-md mx-auto pb-[calc(5.5rem+env(safe-area-inset-bottom))]">
      <!-- 搜索框：iOS 原生统一胶囊搜索框 -->
      <div class="flex items-center gap-3">
        <div class="relative flex-1 flex items-center transition-all">
          <Search class="w-4 h-4 absolute left-3.5 text-slate-400 dark:text-slate-500 pointer-events-none stroke-[2]" />
          <input
            ref="searchInputRef"
            v-model="searchQuery"
            @focus="isSearchFocused = true"
            type="text"
            :placeholder="t('mobile.shared.searchPlaceholder')"
            class="w-full pl-9 pr-8 py-2 bg-slate-200/60 dark:bg-slate-800/80 rounded-full text-[14px] text-slate-900 dark:text-slate-100 placeholder-slate-400/90 dark:placeholder-slate-500 focus:outline-none focus:bg-slate-200/90 dark:focus:bg-slate-800 transition-all"
          />
          <button
            v-if="searchQuery"
            @click="searchQuery = ''"
            class="absolute right-2.5 w-4 h-4 rounded-full bg-slate-400/40 dark:bg-slate-600/50 text-white flex items-center justify-center active:scale-95 transition-all"
          >
            <X class="w-3 h-3 stroke-[2.5]" />
          </button>
        </div>

        <!-- iOS 风格动态取消按钮 -->
        <button
          v-if="isSearchFocused || searchQuery"
          @click="handleCancelSearch"
          class="text-[15px] text-blue-500 hover:text-blue-600 active:opacity-60 transition-all font-normal shrink-0 px-0.5"
        >
          {{ t('nav.cancel') }}
        </button>
      </div>

      <!-- 共享知识库卡片列表 -->
      <section class="space-y-3">
        <!-- 骨架屏 Skeleton Loading -->
        <div v-if="loading" class="space-y-3 animate-pulse">
          <div v-for="i in 3" :key="i" class="p-4 bg-white dark:bg-slate-800/60 border border-slate-200/60 dark:border-slate-700/40 rounded-2xl flex items-center justify-between">
            <div class="flex items-center gap-3.5 min-w-0 flex-1">
              <div class="w-10 h-10 rounded-xl bg-slate-200 dark:bg-slate-700/60 shrink-0"></div>
              <div class="space-y-2 flex-1 min-w-0">
                <div class="h-4 bg-slate-200 dark:bg-slate-700/60 rounded-md w-1/3"></div>
                <div class="h-3 bg-slate-100 dark:bg-slate-800/80 rounded-md w-1/5"></div>
              </div>
            </div>
            <div class="w-4 h-4 rounded-full bg-slate-200 dark:bg-slate-700/60 shrink-0 ml-2"></div>
          </div>
        </div>

        <!-- 空数据占位 -->
        <div v-else-if="filteredKbs.length === 0" class="py-12 text-center space-y-2">
          <Users class="w-10 h-10 mx-auto text-slate-300 dark:text-slate-600" />
          <p class="text-xs text-slate-500 dark:text-slate-400">{{ t('mobile.shared.empty') }}</p>
        </div>

        <!-- 共享卡片列表 -->
        <template v-else>
          <div
            v-for="kb in filteredKbs"
            :key="kb.id"
            @click="$router.push({ path: `/m/kb/${kb.id}`, query: { from: '/m/shared' } })"
            class="p-4 bg-white dark:bg-slate-800/90 border border-slate-200/60 dark:border-slate-700/50 rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.03)] hover:border-slate-300 dark:hover:border-slate-600 active:scale-[0.98] transition-all cursor-pointer flex items-center justify-between"
          >
            <div class="flex items-center gap-3.5 min-w-0 flex-1">
              <div
                class="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 border border-slate-200/50 dark:border-slate-700/50 transition-colors shadow-sm"
                :style="getIconBgStyle(kb.color, isDark)"
              >
                <KbIcon :name="kb.icon" :color="kb.color" custom-class="w-5 h-5" />
              </div>
              <div class="min-w-0 flex-1 space-y-1">
                <div class="flex items-center gap-2">
                  <h3 class="text-[15px] font-bold text-slate-900 dark:text-slate-100 truncate leading-snug">{{ kb.title || kb.name }}</h3>
                  
                  <!-- 角色身份 Badge -->
                  <span
                    v-if="getRoleBadge(kb.role).text"
                    :class="getRoleBadge(kb.role).class"
                    class="px-1.5 py-0.5 text-[10px] rounded shrink-0 font-medium border"
                  >
                    {{ getRoleBadge(kb.role).text }}
                  </span>
                </div>

                <!-- 创建者姓名 -->
                <div class="flex items-center gap-1.5 text-xs text-slate-400 dark:text-slate-500">
                  <User class="w-3 h-3 text-slate-400 dark:text-slate-500 shrink-0" />
                  <span class="truncate">{{ t('mobile.shared.creator', { name: kb.ownerName || t('mobile.shared.unknownCreator') }) }}</span>
                </div>
              </div>
            </div>
            <ChevronRight class="w-5 h-5 text-slate-300 dark:text-slate-600 shrink-0 ml-2" />
          </div>
        </template>
      </section>

      <!-- iOS 底部 Safe Area 物理占位块 -->
      <div class="h-24 w-full shrink-0 pointer-events-none pb-[env(safe-area-inset-bottom)]"></div>
    </main>

    <!-- 挂载底部导航栏 -->
    <TabBar />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import HeaderNav from '../components/HeaderNav.vue'
import TabBar from '../components/TabBar.vue'
import { Search, Users, ChevronRight, X, User } from 'lucide-vue-next'
import KbIcon from '../components/KbIcon.vue'
import { getKbList } from '../../api/kb'
import type { KnowledgeBase } from '../../types/kb'
import { useTheme } from '../composables/useTheme'
import { getIconBgStyle } from '../../utils/color'

const { t } = useI18n()
const { isDark } = useTheme()
const loading = ref(false)
const sharedKbs = ref<KnowledgeBase[]>([])
const searchQuery = ref('')
const isSearchFocused = ref(false)
const searchInputRef = ref<HTMLInputElement | null>(null)

const handleCancelSearch = () => {
  searchQuery.value = ''
  isSearchFocused.value = false
  if (searchInputRef.value) {
    searchInputRef.value.blur()
  }
}

const filteredKbs = computed(() => {
  if (!searchQuery.value.trim()) return sharedKbs.value
  const q = searchQuery.value.toLowerCase()
  return sharedKbs.value.filter(kb =>
    (kb.title || kb.name || '').toLowerCase().includes(q) ||
    (kb.ownerName || '').toLowerCase().includes(q)
  )
})

const fetchSharedKbs = async () => {
  loading.value = true
  try {
    const res: any = await getKbList()
    const list: KnowledgeBase[] = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    // 过滤出他人共享给我的知识库（ROLE 严格不等于 OWNER）
    sharedKbs.value = list.filter(kb => (kb.role || '').toUpperCase() !== 'OWNER')
  } catch (err) {
    sharedKbs.value = []
  } finally {
    loading.value = false
  }
}

// 获取角色 Badge 样式与文案
const getRoleBadge = (role?: string) => {
  const r = (role || '').toUpperCase()
  if (r === 'ADMIN') {
    return {
      text: t('mobile.role.admin'),
      class: 'bg-blue-50 dark:bg-blue-500/10 border-blue-200 dark:border-blue-500/20 text-blue-600 dark:text-blue-400'
    }
  }
  if (r === 'EDITOR') {
    return {
      text: t('mobile.role.editor'),
      class: 'bg-emerald-50 dark:bg-emerald-500/10 border-emerald-200 dark:border-emerald-500/20 text-emerald-600 dark:text-emerald-400'
    }
  }
  if (r === 'VIEWER') {
    return {
      text: t('mobile.role.viewer'),
      class: 'bg-slate-100 dark:bg-slate-800 border-slate-200 dark:border-slate-700 text-slate-500 dark:text-slate-400'
    }
  }
  return { text: '', class: '' }
}

onMounted(() => {
  fetchSharedKbs()
})
</script>
