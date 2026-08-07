<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useTheme } from '../composables/useTheme'
import { useLocale } from '../composables/useLocale'
import { useEditorPreference } from '../composables/useEditorPreference'
import { useI18n } from 'vue-i18n'
import BaseDialog from './shared/BaseDialog.vue'
import versionInfo from '../version'
import axios from 'axios'

const props = defineProps<{
    show: boolean
    showHomeRecent: boolean
    showHomeFavorites: boolean
}>()

const emit = defineEmits(['close', 'update:showHomeRecent', 'update:showHomeFavorites'])

const { theme, setTheme } = useTheme()
const { localeMode, setLocaleMode } = useLocale()
const { editorType, setEditorType } = useEditorPreference()
const { t } = useI18n()

// 关于 HelloDoc 版本数据
const clientVersion = ref(versionInfo.version)
const buildTime = ref(versionInfo.buildTime)
const gitCommit = ref(versionInfo.gitCommit)

const serverVersion = ref('')
const serverOnline = ref(false)
const loadingServer = ref(false)
const copied = ref(false)

// 获取后端系统信息
const fetchServerInfo = async () => {
    loadingServer.value = true
    try {
        const res = await axios.get('/api/system/info')
        if (res.data && res.data.version) {
            serverVersion.value = res.data.version
            serverOnline.value = true
        } else {
            serverOnline.value = false
        }
    } catch {
        serverVersion.value = ''
        serverOnline.value = false
    } finally {
        loadingServer.value = false
    }
}

watch(() => props.show, (newVal) => {
    if (newVal) {
        fetchServerInfo()
    }
})

onMounted(() => {
    if (props.show) {
        fetchServerInfo()
    }
})

const copyDiagnosticInfo = () => {
    const text = `${t('settings.diagnosticTitle')}:
${t('settings.clientVersion')}: v${clientVersion.value}
${t('settings.serverVersion')}: ${serverVersion.value ? 'v' + serverVersion.value : t('settings.disconnected')}
${t('settings.buildTime')}: ${buildTime.value}
${t('settings.gitCommit')}: ${gitCommit.value}
${t('settings.userAgent')}: ${navigator.userAgent}`
    navigator.clipboard.writeText(text)
    copied.value = true
    setTimeout(() => {
        copied.value = false
    }, 2000)
}

const handleSetTheme = (mode: 'light' | 'dark') => {
    // 添加过渡 class 实现平滑切换
    document.documentElement.classList.add('theme-transition')
    setTheme(mode)
    setTimeout(() => {
        document.documentElement.classList.remove('theme-transition')
    }, 300)
}

const handleToggleHomeRecent = (e: Event) => {
    emit('update:showHomeRecent', (e.target as HTMLInputElement).checked)
}

const handleToggleHomeFavorites = (e: Event) => {
    emit('update:showHomeFavorites', (e.target as HTMLInputElement).checked)
}

const handleSetLocale = async (mode: 'AUTO' | 'zh-CN' | 'en-US') => {
    try {
        await setLocaleMode(mode)
    } catch {
    }
}
</script>

<template>
    <BaseDialog :show="show" max-width-class="max-w-md" @close="emit('close')">
        <div class="px-6 py-6">
                    <div class="flex justify-between items-center mb-6">
                        <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('settings.title') }}</h3>
                        <button @click="emit('close')"
                            class="text-gray-400 hover:text-gray-500 dark:text-gray-500 dark:hover:text-gray-300 transition">
                            <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M6 18L18 6M6 6l12 12" />
                            </svg>
                        </button>
                    </div>

                    <div class="space-y-6">
                        <!-- 主题设置 -->
                        <div>
                            <label
                                class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">{{ t('settings.themeTitle') }}</label>
                            <div class="grid grid-cols-2 gap-3">
                                <!-- 白天模式 -->
                                <button @click="handleSetTheme('light')"
                                    class="flex flex-col items-center p-4 rounded-xl border-2 transition-all duration-200"
                                    :class="theme === 'light' ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 shadow-sm' : 'border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500 hover:bg-gray-50 dark:hover:bg-gray-700/50'">
                                    <!-- 太阳图标 -->
                                    <div class="h-12 w-12 rounded-full flex items-center justify-center mb-2"
                                        :class="theme === 'light' ? 'bg-amber-100 dark:bg-amber-900/50' : 'bg-gray-100 dark:bg-gray-700'">
                                        <svg class="h-6 w-6"
                                            :class="theme === 'light' ? 'text-amber-500' : 'text-gray-400 dark:text-gray-500'"
                                            fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                            <circle cx="12" cy="12" r="5" />
                                            <line x1="12" y1="1" x2="12" y2="3" />
                                            <line x1="12" y1="21" x2="12" y2="23" />
                                            <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
                                            <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
                                            <line x1="1" y1="12" x2="3" y2="12" />
                                            <line x1="21" y1="12" x2="23" y2="12" />
                                            <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
                                            <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
                                        </svg>
                                    </div>
                                    <span class="text-sm font-bold"
                                        :class="theme === 'light' ? 'text-blue-700 dark:text-blue-300' : 'text-gray-600 dark:text-gray-400'">{{ t('settings.lightMode') }}</span>
                                    <span class="text-[10px] text-gray-400 dark:text-gray-500 mt-0.5">{{ t('settings.lightModeDesc') }}</span>
                                </button>

                                <!-- 黑夜模式 -->
                                <button @click="handleSetTheme('dark')"
                                    class="flex flex-col items-center p-4 rounded-xl border-2 transition-all duration-200"
                                    :class="theme === 'dark' ? 'border-indigo-500 bg-indigo-50 dark:bg-indigo-900/30 shadow-sm' : 'border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500 hover:bg-gray-50 dark:hover:bg-gray-700/50'">
                                    <!-- 月亮图标 -->
                                    <div class="h-12 w-12 rounded-full flex items-center justify-center mb-2"
                                        :class="theme === 'dark' ? 'bg-indigo-100 dark:bg-indigo-900/50' : 'bg-gray-100 dark:bg-gray-700'">
                                        <svg class="h-6 w-6"
                                            :class="theme === 'dark' ? 'text-indigo-500' : 'text-gray-400 dark:text-gray-500'"
                                            fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                            <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
                                        </svg>
                                    </div>
                                    <span class="text-sm font-bold"
                                        :class="theme === 'dark' ? 'text-indigo-700 dark:text-indigo-300' : 'text-gray-600 dark:text-gray-400'">{{ t('settings.darkMode') }}</span>
                                    <span class="text-[10px] text-gray-400 dark:text-gray-500 mt-0.5">{{ t('settings.darkModeDesc') }}</span>
                                </button>
                            </div>
                        </div>

                        <div>
                            <label
                                class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">{{ t('locale.title') }}</label>
                            <div class="grid grid-cols-3 gap-3">
                                <button @click="handleSetLocale('AUTO')"
                                    class="flex flex-col items-center p-3 rounded-xl border-2 transition-all duration-200"
                                    :class="localeMode === 'AUTO' ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 shadow-sm text-blue-700 dark:text-blue-300' : 'border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-500 hover:bg-gray-50 dark:hover:bg-gray-700/50'">
                                    <span class="text-xs font-semibold text-center">{{ t('locale.followBrowser') }}</span>
                                </button>
                                <button @click="handleSetLocale('zh-CN')"
                                    class="flex flex-col items-center p-3 rounded-xl border-2 transition-all duration-200"
                                    :class="localeMode === 'zh-CN' ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 shadow-sm text-blue-700 dark:text-blue-300' : 'border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-500 hover:bg-gray-50 dark:hover:bg-gray-700/50'">
                                    <span class="text-xs font-semibold">{{ t('locale.zhCN') }}</span>
                                </button>
                                <button @click="handleSetLocale('en-US')"
                                    class="flex flex-col items-center p-3 rounded-xl border-2 transition-all duration-200"
                                    :class="localeMode === 'en-US' ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 shadow-sm text-blue-700 dark:text-blue-300' : 'border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-500 hover:bg-gray-50 dark:hover:bg-gray-700/50'">
                                    <span class="text-xs font-semibold">{{ t('locale.enUS') }}</span>
                                </button>
                            </div>
                        </div>

                        <div>
                            <label
                                class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">{{ t('settings.editorTitle') }}</label>
                            <div class="grid grid-cols-2 gap-3">
                                <!-- Markdown 编辑器 -->
                                <button @click="setEditorType('markdown')"
                                    class="flex flex-col items-center p-4 rounded-xl border-2 transition-all duration-200 text-center"
                                    :class="editorType === 'markdown' ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 shadow-sm' : 'border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500 hover:bg-gray-50 dark:hover:bg-gray-700/50'">
                                    <div class="h-12 w-12 rounded-full flex items-center justify-center mb-2"
                                        :class="editorType === 'markdown' ? 'bg-blue-100 dark:bg-blue-900/50' : 'bg-gray-100 dark:bg-gray-700'">
                                        <svg class="h-6 w-6"
                                            :class="editorType === 'markdown' ? 'text-blue-500' : 'text-gray-400 dark:text-gray-500'"
                                            fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                            <path d="M19 3H5a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2V5a2 2 0 00-2-2z" />
                                            <path d="M9 7l-3 5 3 5M15 7l3 5-3 5" />
                                        </svg>
                                    </div>
                                    <span class="text-sm font-bold"
                                        :class="editorType === 'markdown' ? 'text-blue-700 dark:text-blue-300' : 'text-gray-600 dark:text-gray-400'">{{ t('settings.editorMarkdown') }}</span>
                                    <span class="text-[10px] text-gray-400 dark:text-gray-500 mt-0.5">{{ t('settings.editorMarkdownDesc') }}</span>
                                </button>

                                <!-- 可视化编辑器 -->
                                <button @click="setEditorType('visual')"
                                    class="flex flex-col items-center p-4 rounded-xl border-2 transition-all duration-200 text-center"
                                    :class="editorType === 'visual' ? 'border-indigo-500 bg-indigo-50 dark:bg-indigo-900/30 shadow-sm' : 'border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500 hover:bg-gray-50 dark:hover:bg-gray-700/50'">
                                    <div class="h-12 w-12 rounded-full flex items-center justify-center mb-2"
                                        :class="editorType === 'visual' ? 'bg-indigo-100 dark:bg-indigo-900/50' : 'bg-gray-100 dark:bg-gray-700'">
                                        <svg class="h-6 w-6"
                                            :class="editorType === 'visual' ? 'text-indigo-500' : 'text-gray-400 dark:text-gray-500'"
                                            fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                            <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7" />
                                            <path d="M18.5 2.5a2.121 2.121 0 113 3L12 15l-4 1 1-4 9.5-9.5z" />
                                        </svg>
                                    </div>
                                    <span class="text-sm font-bold"
                                        :class="editorType === 'visual' ? 'text-indigo-700 dark:text-indigo-300' : 'text-gray-600 dark:text-gray-400'">{{ t('settings.editorVisual') }}</span>
                                    <span class="text-[10px] text-gray-400 dark:text-gray-500 mt-0.5">{{ t('settings.editorVisualDesc') }}</span>
                                </button>
                            </div>
                        </div>

                        <div>
                            <label
                                class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">{{ t('settings.homeTitle') }}</label>
                            <div class="space-y-3">
                                <div
                                    class="flex items-center justify-between p-3 rounded-xl border border-gray-200 dark:border-gray-600">
                                    <div class="min-w-0 pr-3">
                                        <div class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.recentTitle') }}</div>
                                        <div class="text-[11px] text-gray-400 dark:text-gray-500 truncate">{{ t('settings.recentDesc') }}</div>
                                    </div>
                                    <label class="relative inline-flex items-center cursor-pointer flex-shrink-0">
                                        <input type="checkbox" class="sr-only peer" :checked="props.showHomeRecent"
                                            @change="handleToggleHomeRecent" />
                                        <div
                                            class="w-11 h-6 bg-gray-200 dark:bg-gray-600 rounded-full peer peer-checked:bg-blue-600 transition-colors">
                                        </div>
                                        <div
                                            class="absolute left-0.5 top-0.5 h-5 w-5 bg-white rounded-full transition-transform peer-checked:translate-x-5">
                                        </div>
                                    </label>
                                </div>

                                <div
                                    class="flex items-center justify-between p-3 rounded-xl border border-gray-200 dark:border-gray-600">
                                    <div class="min-w-0 pr-3">
                                        <div class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ t('settings.favoritesTitle') }}</div>
                                        <div class="text-[11px] text-gray-400 dark:text-gray-500 truncate">{{ t('settings.favoritesDesc') }}</div>
                                    </div>
                                    <label class="relative inline-flex items-center cursor-pointer flex-shrink-0">
                                        <input type="checkbox" class="sr-only peer" :checked="props.showHomeFavorites"
                                            @change="handleToggleHomeFavorites" />
                                        <div
                                            class="w-11 h-6 bg-gray-200 dark:bg-gray-600 rounded-full peer peer-checked:bg-blue-600 transition-colors">
                                        </div>
                                        <div
                                            class="absolute left-0.5 top-0.5 h-5 w-5 bg-white rounded-full transition-transform peer-checked:translate-x-5">
                                        </div>
                                    </label>
                                </div>
                            </div>
                        </div>

                        <!-- 关于 HelloDoc -->
                        <div class="pt-4 border-t border-gray-100 dark:border-gray-700/80">
                            <div class="flex items-center justify-between mb-3">
                                <label class="text-sm font-semibold text-gray-700 dark:text-gray-300">{{ t('settings.aboutTitle') }}</label>
                                <span class="px-2 py-0.5 text-xs font-mono font-bold text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-900/40 rounded-md border border-indigo-100 dark:border-indigo-800">
                                    v{{ clientVersion }}
                                </span>
                            </div>
                            
                            <div class="p-3.5 bg-gray-50 dark:bg-gray-800/80 rounded-xl border border-gray-200/80 dark:border-gray-700 space-y-2.5 text-xs">
                                <div class="flex justify-between items-center text-gray-600 dark:text-gray-400">
                                    <span>{{ t('settings.serverVersion') }}</span>
                                    <div class="flex items-center space-x-1.5">
                                        <span class="w-2 h-2 rounded-full" :class="serverOnline ? 'bg-emerald-500 animate-pulse' : 'bg-rose-500'"></span>
                                        <span class="font-medium text-gray-800 dark:text-gray-200">
                                            {{ serverVersion ? `v${serverVersion}` : (loadingServer ? t('settings.fetching') : t('settings.disconnected')) }}
                                        </span>
                                    </div>
                                </div>
                                <div class="flex justify-between text-gray-500 dark:text-gray-400">
                                    <span>{{ t('settings.buildTime') }}</span>
                                    <span class="font-mono text-gray-700 dark:text-gray-300">{{ buildTime }}</span>
                                </div>
                                <div class="flex justify-between text-gray-500 dark:text-gray-400">
                                    <span>{{ t('settings.gitCommit') }}</span>
                                    <span class="font-mono text-gray-700 dark:text-gray-300">{{ gitCommit }}</span>
                                </div>
                                
                                <div class="pt-2 flex justify-between items-center text-[11px] border-t border-gray-200/50 dark:border-gray-700/50">
                                    <button @click="copyDiagnosticInfo" class="text-indigo-600 dark:text-indigo-400 font-medium hover:underline flex items-center space-x-1">
                                        <span>{{ copied ? t('settings.diagnosticsCopied') : t('settings.copyDiagnostics') }}</span>
                                    </button>
                                    <span class="text-gray-400">© 2026 HelloDoc</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="bg-gray-50 dark:bg-gray-700/50 px-6 py-4 flex justify-end">
                    <button @click="emit('close')"
                        class="px-5 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 rounded-xl hover:bg-gray-100 dark:hover:bg-gray-700 transition-all duration-200">{{ t('common.close') }}</button>
                </div>
    </BaseDialog>
</template>
