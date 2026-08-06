<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'

type TabKey = 'my' | 'recent' | 'shared' | 'favorites'
type SearchType = 'doc' | 'kb'

const props = defineProps<{
    activeTab: TabKey
    searchType: SearchType
    searchQuery: string
    searchPlaceholder: string
    searchFocused: boolean
    nickname: string
    avatar: string
    userRole: string
    showUserDropdown: boolean
    searchEnabled: boolean
    navStyle?: 'top' | 'left'
}>()
const { t } = useI18n()

const isMac = computed(() => /Mac|iPhone|iPod|iPad/.test(navigator.platform))

const emit = defineEmits<{
    (e: 'update:searchType', value: SearchType): void
    (e: 'update:searchQuery', value: string): void
    (e: 'update:searchFocused', value: boolean): void
    (e: 'update:showUserDropdown', value: boolean): void
    (e: 'navigate', value: TabKey): void
    (e: 'open-profile'): void
    (e: 'open-change-password'): void
    (e: 'open-system-settings'): void
    (e: 'logout'): void
    (e: 'go-admin'): void
    (e: 'open-search-modal'): void
}>()


const searchQueryModel = computed({
    get: () => props.searchQuery,
    set: (value: string) => emit('update:searchQuery', value),
})

const searchFocusedModel = computed({
    get: () => props.searchFocused,
    set: (value: boolean) => emit('update:searchFocused', value),
})

const showUserDropdownModel = computed({
    get: () => props.showUserDropdown,
    set: (value: boolean) => emit('update:showUserDropdown', value),
})

const showMobileSearch = ref(false)

const openMobileSearch = async () => {
    emit('open-search-modal')
}

const closeMobileSearch = () => {
    showMobileSearch.value = false
    searchFocusedModel.value = false
}

const toggleDropdown = (event: Event) => {
    event.stopPropagation()
    showUserDropdownModel.value = !showUserDropdownModel.value
}

const handleClickOutside = (event: MouseEvent) => {
    const target = event.target as HTMLElement
    // 如果点击的不是下拉菜单本身，也不是触发按钮，则关闭
    if (showUserDropdownModel.value && !target.closest('.dropdown-toggle')) {
        showUserDropdownModel.value = false
    }
}

onMounted(() => {
    document.addEventListener('click', handleClickOutside)
    window.addEventListener('keydown', handleGlobalKeyDown)
})

onUnmounted(() => {
    document.removeEventListener('click', handleClickOutside)
    window.removeEventListener('keydown', handleGlobalKeyDown)
})

const handleGlobalKeyDown = (e: KeyboardEvent) => {
    // Cmd+K or Ctrl+K or /
    if (((e.metaKey || e.ctrlKey) && e.key === 'k') || (e.key === '/' && document.activeElement?.tagName !== 'INPUT' && document.activeElement?.tagName !== 'TEXTAREA')) {
        e.preventDefault()
        emit('open-search-modal')
    }
}

const openProfile = () => {
    emit('open-profile')
    showUserDropdownModel.value = false
}

const openChangePassword = () => {
    emit('open-change-password')
    showUserDropdownModel.value = false
}

const openSystemSettings = () => {
    emit('open-system-settings')
    showUserDropdownModel.value = false
}

const goAdmin = () => {
    emit('go-admin')
    showUserDropdownModel.value = false
}

const logout = () => {
    emit('logout')
}
</script>

<template>
    <nav class="bg-white dark:bg-gray-800 shadow-sm border-b border-gray-200 dark:border-gray-700 transition-colors duration-300">
        <div :class="navStyle === 'left' ? 'px-4 sm:px-6 lg:px-8' : 'max-w-7xl mx-auto px-4 sm:px-6 lg:px-8'">
            <div class="flex justify-between h-16 items-center">
                <div class="flex items-center space-x-4 min-w-0 flex-1">
                    <div v-if="navStyle !== 'left'" class="flex items-center space-x-2">
                        <img src="../../assets/logo.svg" alt="HelloDoc Logo" class="h-8 w-8" />
                        <span class="text-xl font-bold text-gray-900 dark:text-white">HelloDoc</span>
                    </div>
                    <div :class="[navStyle === 'left' ? 'ml-0' : 'ml-2 sm:ml-6', { 'min-[975px]:hidden': navStyle === 'left' }]"
                        class="flex items-center min-w-0 flex-1 min-[975px]:flex-none">
                        <div
                            class="flex items-center bg-gray-100 dark:bg-gray-700 p-1 rounded-xl overflow-x-auto max-w-full w-auto">
                            <button @click="emit('navigate', 'my')"
                                :class="activeTab === 'my' ? 'bg-white dark:bg-gray-600 shadow-sm text-blue-600 dark:text-blue-400 font-bold' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'"
                                class="inline-flex items-center flex-shrink-0 px-2.5 min-[975px]:px-4 py-1.5 text-sm rounded-lg transition-all duration-200">
                                <svg class="h-4 w-4 min-[975px]:mr-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
                                </svg>
                                <span class="hidden min-[975px]:inline">{{ t('nav.home') }}</span>
                            </button>
                            <button @click="emit('navigate', 'shared')"
                                :class="activeTab === 'shared' ? 'bg-white dark:bg-gray-600 shadow-sm text-blue-600 dark:text-blue-400 font-bold' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'"
                                class="inline-flex items-center flex-shrink-0 px-2.5 min-[975px]:px-4 py-1.5 text-sm rounded-lg transition-all duration-200">
                                <svg class="h-4 w-4 min-[975px]:mr-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                                </svg>
                                <span class="hidden min-[975px]:inline">{{ t('nav.shared') }}</span>
                            </button>
                            <button @click="emit('navigate', 'favorites')"
                                :class="activeTab === 'favorites' ? 'bg-white dark:bg-gray-600 shadow-sm text-blue-600 dark:text-blue-400 font-bold' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'"
                                class="inline-flex items-center flex-shrink-0 px-2.5 min-[975px]:px-4 py-1.5 text-sm rounded-lg transition-all duration-200">
                                <svg class="h-4 w-4 min-[975px]:mr-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.175 0l-3.976 2.888c-.783.57-1.837-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.382-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
                                </svg>
                                <span class="hidden min-[975px]:inline">{{ t('nav.favorites') }}</span>
                            </button>
                            <button @click="emit('navigate', 'recent')"
                                :class="activeTab === 'recent' ? 'bg-white dark:bg-gray-600 shadow-sm text-blue-600 dark:text-blue-400 font-bold' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'"
                                class="inline-flex items-center flex-shrink-0 px-2.5 min-[975px]:px-4 py-1.5 text-sm rounded-lg transition-all duration-200">
                                <svg class="h-4 w-4 min-[975px]:mr-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                                </svg>
                                <span class="hidden min-[975px]:inline">{{ t('nav.recent') }}</span>
                            </button>
                        </div>
                    </div>
                </div>
                <div class="flex items-center space-x-2 sm:space-x-4 flex-shrink-0 pl-2">
                    <button v-if="searchEnabled" type="button" @click="openMobileSearch"
                        class="min-[975px]:hidden inline-flex items-center justify-center h-9 w-9 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition focus:outline-none">
                        <svg class="h-5 w-5 text-gray-500 dark:text-gray-300" fill="none" viewBox="0 0 24 24"
                            stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                        </svg>
                    </button>

                    <div v-if="searchEnabled" class="hidden min-[975px]:flex items-center space-x-2">
                        <div class="relative group">
                            <input type="text" name="search_keyword" :placeholder="`${searchPlaceholder} (${isMac ? '⌘K' : 'Ctrl+K'})`"
                                v-model="searchQueryModel" autocomplete="new-password" readonly
                                @click="emit('open-search-modal')"
                                class="pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all w-64 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 cursor-pointer group-hover:border-gray-400 dark:group-hover:border-gray-500">
                            <svg class="h-4 w-4 text-gray-400 absolute left-3 top-3" fill="none" viewBox="0 0 24 24"
                                stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                            </svg>
                        </div>
                    </div>
                    <div class="relative">
                        <button @click="toggleDropdown"
                            class="dropdown-toggle flex items-center space-x-2 p-1 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 transition focus:outline-none">
                            <div
                                class="h-8 w-8 rounded-full overflow-hidden bg-gray-200 dark:bg-gray-600 border border-gray-300 dark:border-gray-500 flex items-center justify-center">
                                <img v-if="avatar" :src="avatar" class="h-full w-full object-cover" />
                                <span v-else class="text-xs font-bold text-gray-600">{{ nickname.charAt(0).toUpperCase()
                                }}</span>
                            </div>
                            <svg class="h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M19 9l-7 7-7-7" />
                            </svg>
                        </button>

                        <div v-if="showUserDropdownModel"
                            class="absolute right-0 mt-2 w-48 bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-100 dark:border-gray-700 py-1 z-50">
                            <div class="px-4 py-3 border-b border-gray-50 dark:border-gray-700">
                                <p class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">{{ nickname
                                }}</p>
                            </div>
                            <button @click="openProfile"
                                class="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 flex items-center space-x-2">
                                <svg class="h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24"
                                    stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                                </svg>
                                <span>{{ t('nav.profile') }}</span>
                            </button>
                            <button @click="openChangePassword"
                                class="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 flex items-center space-x-2">
                                <svg class="h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24"
                                    stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                                </svg>
                                <span>{{ t('nav.changePassword') }}</span>
                            </button>
                            <button v-if="userRole === 'admin'" @click="goAdmin"
                                class="w-full text-left px-4 py-2 text-sm text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 flex items-center space-x-2 font-semibold">
                                <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                </svg>
                                <span>{{ t('nav.admin') }}</span>
                            </button>
                            <button @click="openSystemSettings"
                                class="w-full text-left px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 flex items-center space-x-2">
                                <svg class="h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24"
                                    stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                </svg>
                                <span>{{ t('nav.settings') }}</span>
                            </button>
                            <button @click="logout"
                                class="w-full text-left px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 flex items-center space-x-2">
                                <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                                </svg>
                                <span>{{ t('nav.logout') }}</span>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div v-if="showMobileSearch" class="fixed inset-0 z-50 min-[975px]:hidden" @keydown.esc="closeMobileSearch">
            <div class="absolute inset-0 bg-black/40" @click="closeMobileSearch"></div>
            <div class="absolute left-0 right-0 top-0 p-4">
                <div
                    class="bg-white dark:bg-gray-800 rounded-2xl shadow-xl border border-gray-200 dark:border-gray-700 p-3">
                    <div class="flex items-center gap-2">
                        <div class="relative flex-1">
                            <input ref="mobileSearchInputRef" type="text" name="search_keyword_mobile"
                                :placeholder="searchPlaceholder" v-model="searchQueryModel" autocomplete="new-password"
                                class="w-full pl-10 pr-4 h-9 border border-gray-300 dark:border-gray-600 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500">
                            <svg class="h-4 w-4 text-gray-400 absolute left-3 top-2.5" fill="none" viewBox="0 0 24 24"
                                stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                            </svg>
                        </div>
                        <button type="button" @click="closeMobileSearch"
                            class="h-9 px-3 rounded-lg text-sm text-gray-500 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition">
                            {{ t('nav.cancel') }}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </nav>
</template>
