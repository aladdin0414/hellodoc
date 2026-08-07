<template>
  <div class="h-screen overflow-y-auto bg-gray-50 dark:bg-slate-900 text-gray-900 dark:text-slate-100 transition-colors no-scrollbar">
    <!-- 顶部 Navigation：规范组件统一接入 -->
    <HeaderNav>
      <template #left>
        <div class="flex items-center gap-2">
          <img src="../../assets/logo.svg" alt="HelloDoc Logo" class="w-7 h-7 drop-shadow-sm" />
          <span class="text-base font-black tracking-tight text-slate-900 dark:text-white">HelloDoc</span>
        </div>
      </template>

      <template #right>
        <!-- 私人订制插件：速记铅笔图标按钮 (仅目标用户显示，点击直达编辑页) -->
        <QuickNoteButton :username="currentUsername" />

        <!-- 新建知识库图标按钮 -->
        <button
          @click="showCreateModal = true"
          :title="t('kb.createTitle')"
          class="p-1.5 rounded-xl text-slate-600 dark:text-slate-300 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-slate-100 dark:hover:bg-slate-800 active:scale-95 transition-all"
        >
          <Plus class="w-5 h-5 stroke-[2.5]" />
        </button>
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
            :placeholder="t('mobile.home.searchPlaceholder')"
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

      <!-- 知识库卡片列表 -->
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
          <BookOpen class="w-10 h-10 mx-auto text-slate-300 dark:text-slate-600" />
          <p class="text-xs text-slate-500 dark:text-slate-400">{{ t('mobile.home.empty') }}</p>
        </div>

        <!-- 卡片列表 -->
        <template v-else>
          <div
            v-for="kb in filteredKbs"
            :key="kb.id"
            @click="$router.push(`/m/kb/${kb.id}`)"
            class="p-4 bg-white dark:bg-slate-800/90 border border-slate-200/60 dark:border-slate-700/50 rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.03)] hover:border-slate-300 dark:hover:border-slate-600 active:scale-[0.98] transition-all cursor-pointer flex items-center justify-between"
          >
            <div class="flex items-center gap-3.5 min-w-0 flex-1">
              <div
                class="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 border border-slate-200/50 dark:border-slate-700/50 transition-colors shadow-sm"
                :style="getIconBgStyle(kb.color, isDark)"
              >
                <KbIcon :name="kb.icon" :color="kb.color" custom-class="w-5 h-5" />
              </div>
              <div class="min-w-0 flex-1 space-y-0.5">
                <h3 class="text-[15px] font-bold text-slate-900 dark:text-slate-100 truncate leading-snug">{{ kb.title || kb.name }}</h3>
                <p class="text-xs text-slate-400 dark:text-slate-500 truncate font-normal">{{ kb.description || t('search.kbNoDescription') }}</p>
              </div>
            </div>
            <ChevronRight class="w-5 h-5 text-slate-300 dark:text-slate-600 shrink-0 ml-2" />
          </div>
        </template>
      </section>

      <!-- iOS 底部 Safe Area 物理占位块，保障在 iPhone 真机滑动到底部时最后一项完全脱离 TabBar 覆盖 -->
      <div class="h-24 w-full shrink-0 pointer-events-none pb-[env(safe-area-inset-bottom)]"></div>
    </main>

    <!-- 挂载底部导航栏 -->
    <TabBar />

    <!-- 新建知识库弹窗 -->
    <div v-if="showCreateModal" class="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div class="bg-white dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-2xl w-full max-w-sm p-5 space-y-4 shadow-2xl">
        <h3 class="text-base font-bold text-gray-900 dark:text-slate-100">{{ t('kb.createTitle') }}</h3>

        <div class="space-y-3 text-sm">
          <div>
            <label class="block text-xs text-gray-500 dark:text-slate-400 mb-1">{{ t('kb.nameLabel') }}</label>
            <input
              v-model="newKbTitle"
              type="text"
              :placeholder="t('kb.namePlaceholder')"
              class="w-full px-3 py-2 bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/40"
            />
          </div>
        </div>

        <div class="flex items-center justify-end gap-2 pt-2">
          <button
            @click="showCreateModal = false"
            class="px-4 py-2 text-xs font-medium text-gray-600 dark:text-slate-400 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-xl transition-colors"
          >
            {{ t('nav.cancel') }}
          </button>
          <button
            @click="handleCreateKb"
            :disabled="creating || !newKbTitle.trim()"
            class="px-4 py-2 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-500 disabled:opacity-50 rounded-xl shadow-md active:scale-95 transition-all"
          >
            {{ creating ? t('common.loading') : t('common.confirm') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import HeaderNav from '../components/HeaderNav.vue'
import TabBar from '../components/TabBar.vue'
import { Plus, Search, BookOpen, ChevronRight, X } from 'lucide-vue-next'
import KbIcon from '../components/KbIcon.vue'
import { getKbList, createKb } from '../../api/kb'
import { getMe } from '../../api/user'
import type { KnowledgeBase } from '../../types/kb'
import { useTheme } from '../composables/useTheme'
import { getIconBgStyle } from '../../utils/color'

// 引入解耦的私人订制速记插件
import QuickNoteButton from '../plugins/quickNote/QuickNoteButton.vue'

const { t } = useI18n()
const { isDark } = useTheme()

const loading = ref(false)
const kbs = ref<KnowledgeBase[]>([])
const searchQuery = ref('')
const isSearchFocused = ref(false)
const searchInputRef = ref<HTMLInputElement | null>(null)

const currentUsername = ref(localStorage.getItem('username') || '')

const handleCancelSearch = () => {
  searchQuery.value = ''
  isSearchFocused.value = false
  if (searchInputRef.value) {
    searchInputRef.value.blur()
  }
}

const showCreateModal = ref(false)
const creating = ref(false)
const newKbTitle = ref('')

const filteredKbs = computed(() => {
  if (!searchQuery.value.trim()) return kbs.value
  const q = searchQuery.value.toLowerCase()
  return kbs.value.filter(kb => (kb.title || kb.name || '').toLowerCase().includes(q))
})

const fetchUserInfo = async () => {
  try {
    const data: any = await getMe()
    if (data && data.username) {
      currentUsername.value = data.username
      localStorage.setItem('username', data.username)
    }
  } catch (err) {
    // ignore
  }
}

const fetchKbs = async () => {
  loading.value = true
  try {
    const res: any = await getKbList()
    const list: KnowledgeBase[] = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    // 仅保留我创建的知识库（ROLE 严格为 OWNER），过滤掉他人共享的知识库
    kbs.value = list.filter(kb => (kb.role || '').toUpperCase() === 'OWNER')
  } catch (err) {
    kbs.value = []
  } finally {
    loading.value = false
  }
}

const handleCreateKb = async () => {
  if (!newKbTitle.value.trim()) return
  creating.value = true
  try {
    await createKb({
      title: newKbTitle.value.trim()
    })
    showCreateModal.value = false
    newKbTitle.value = ''
    await fetchKbs()
  } catch (err) {
    // ignore
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  fetchUserInfo()
  fetchKbs()
})
</script>

