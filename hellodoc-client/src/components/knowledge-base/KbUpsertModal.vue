<script setup lang="ts">
import { computed, nextTick, ref, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import * as Icons from 'lucide-vue-next'
import BaseDialog from '../shared/BaseDialog.vue'

type ColorOption = { name: string; value: string }
type KbUpsertMode = 'create' | 'edit'

type KbUpsertModel = {
    id?: number
    title?: string
    description?: string
    color?: string
    icon?: string
    visibility?: string
}

const props = defineProps<{
    mode: KbUpsertMode
    colors: ColorOption[]
    submitting: boolean
}>()

const emit = defineEmits<{
    (e: 'save'): void
}>()

const show = defineModel<boolean>('show', { required: true })
const kb = defineModel<KbUpsertModel>('kb', { required: true })
const { t } = useI18n()

// 面向知识库场景的图标库（按类别挑选），并过滤当前版本不存在的图标
const preferredIconLibrary = [
    // 通用知识/文档
    'Book', 'BookOpen', 'Library', 'Notebook', 'Files', 'Folder', 'FolderOpen', 'Archive',
    'FileText', 'FileCode', 'FileImage', 'FileSpreadsheet', 'ClipboardList', 'NotebookPen', 'Bookmark',
    // 技术研发
    'Code2', 'Terminal', 'Cpu', 'Server', 'Database', 'Cloud', 'GitBranch', 'GitPullRequest', 'Bug',
    'Wrench', 'Cog', 'Settings', 'Workflow', 'Boxes', 'Container',
    // 产品项目
    'Kanban', 'ListTodo', 'Target', 'Lightbulb', 'Rocket', 'Flag', 'Milestone', 'CalendarCheck',
    // 组织协作
    'Users', 'User', 'Briefcase', 'Building2', 'MessageSquare', 'MessagesSquare', 'Handshake',
    'UserCheck', 'UserCog', 'PhoneCall', 'Mail', 'Contact',
    // 运营增长
    'BarChart3', 'BarChart4', 'LineChart', 'PieChart', 'TrendingUp', 'Megaphone', 'ShoppingCart', 'Package',
    'Store', 'Receipt', 'BadgeDollarSign', 'Wallet', 'CreditCard',
    // 设计与内容
    'Palette', 'PenTool', 'Image', 'Video', 'Music', 'Camera', 'Shapes', 'Brush', 'Clapperboard',
    'Podcast', 'Mic', 'Headphones',
    // 研究与行业
    'FlaskConical', 'Microscope', 'GraduationCap', 'School', 'Scale', 'Shield', 'ShieldCheck',
    'Globe', 'Map', 'Landmark', 'Banknote', 'Stethoscope', 'Pill',
    // 运维与安全
    'HardDrive', 'ServerCog', 'Network', 'Wifi', 'Lock', 'KeyRound',
    // AI 与数据
    'Brain', 'Sparkles', 'Bot', 'Binary', 'ChartColumn', 'Search'
]
const iconLibrary = preferredIconLibrary.filter(iconName => !!(Icons as any)[iconName])

const modalTitle = computed(() => (props.mode === 'create' ? t('kb.upsertTitleCreate') : t('kb.upsertTitleEdit')))
const confirmText = computed(() => {
    if (props.submitting) return props.mode === 'create' ? t('kb.upsertSubmittingCreate') : t('kb.upsertSubmittingSave')
    return props.mode === 'create' ? t('kb.upsertConfirmCreate') : t('kb.upsertConfirmSave')
})

const titleInput = ref<HTMLInputElement | null>(null)

watch(show, async (val) => {
    if (!val) {
        showIconPicker.value = false
        return
    }
    await nextTick()
    titleInput.value?.focus()
    titleInput.value?.select()
})

const showIconPicker = ref(false)
const iconPickerRef = ref<HTMLElement | null>(null)

const toggleIconPicker = () => {
    showIconPicker.value = !showIconPicker.value
}

const handleOutsideClick = (e: MouseEvent) => {
    if (iconPickerRef.value && !iconPickerRef.value.contains(e.target as Node)) {
        showIconPicker.value = false
    }
}

const selectIcon = (iconName: string) => {
    kb.value.icon = iconName
    showIconPicker.value = false
}

onMounted(() => {
    document.addEventListener('click', handleOutsideClick)
})

onUnmounted(() => {
    document.removeEventListener('click', handleOutsideClick)
})
</script>

<template>
    <BaseDialog :show="show" max-width-class="max-w-md" @close="show = false">
        <div class="p-6">
                <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100 mb-4">{{ modalTitle }}</h3>
                <div class="space-y-4">
                    <div>
                        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('kb.upsertTitleLabel') }}</label>
                        <input ref="titleInput" v-model="kb.title" type="text"
                            class="w-full px-4 py-2 border dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:outline-none bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                            :placeholder="mode === 'create' ? t('kb.upsertTitlePlaceholder') : ''" />
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('kb.upsertColorLabel') }}</label>
                        <div class="flex flex-wrap gap-2 py-2">
                            <button v-for="c in colors" :key="c.name" @click="kb.color = c.value"
                                class="h-8 w-8 rounded-full border-2 transition-transform hover:scale-110"
                                :class="kb.color === c.value ? 'border-gray-900 dark:border-gray-100 scale-110' : 'border-transparent'"
                                :style="{ backgroundColor: c.value }" :title="c.name"></button>
                        </div>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                            {{ t('kb.upsertIconLabel') }}
                        </label>
                        <div class="relative" ref="iconPickerRef">
                            <button type="button" @click.stop="toggleIconPicker"
                                class="flex items-center space-x-2 px-4 py-2 border dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:outline-none bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-600 transition-colors w-full">
                                <div class="h-6 w-6 rounded-md flex items-center justify-center bg-gray-100 dark:bg-gray-600">
                                    <component :is="(Icons as any)[kb.icon || 'Book']" class="w-4 h-4 text-gray-600 dark:text-gray-300" />
                                </div>
                                <span class="flex-1 text-left">{{ kb.icon || t('kb.upsertIconDefault') }}</span>
                                <svg class="h-4 w-4 text-gray-400 transition-transform" :class="{ 'rotate-180': showIconPicker }" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                                </svg>
                            </button>

                            <div v-if="showIconPicker"
                                class="absolute z-[60] mt-2 p-3 bg-white dark:bg-gray-800 rounded-xl shadow-2xl border border-gray-100 dark:border-gray-700 w-full grid grid-cols-6 gap-2 max-h-48 overflow-y-auto custom-scrollbar animate-in fade-in zoom-in duration-200">
                                <button v-for="iconName in iconLibrary" :key="iconName" @click.stop="selectIcon(iconName)"
                                    class="flex items-center justify-center p-2 rounded-lg border-2 transition-all hover:bg-gray-50 dark:hover:bg-gray-700"
                                    :class="kb.icon === iconName ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400' : 'border-transparent text-gray-400 hover:text-gray-600 dark:hover:text-gray-200'"
                                    :title="iconName">
                                    <component :is="(Icons as any)[iconName]" class="w-5 h-5" />
                                </button>
                            </div>
                        </div>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('kb.upsertDescriptionLabel') }}</label>
                        <textarea v-model="kb.description"
                            class="w-full px-4 py-2 border dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:outline-none bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                            rows="3" :placeholder="mode === 'create' ? t('kb.upsertDescriptionPlaceholder') : ''"></textarea>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">{{ t('kb.upsertVisibilityLabel') }}</label>
                        <div class="grid grid-cols-2 gap-3">
                            <button type="button" @click="kb.visibility = 'private'"
                                class="flex flex-col items-center p-3 rounded-xl border-2 transition-all"
                                :class="kb.visibility === 'private' ? 'border-blue-600 bg-blue-50 dark:bg-blue-900/30' : 'border-gray-100 dark:border-gray-600 hover:border-gray-200 dark:hover:border-gray-500'">
                                <svg class="h-6 w-6 mb-1"
                                    :class="kb.visibility === 'private' ? 'text-blue-600' : 'text-gray-400'" fill="none"
                                    viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                                </svg>
                                <span class="text-sm font-bold"
                                    :class="kb.visibility === 'private' ? 'text-blue-700' : 'text-gray-600'">{{ t('kb.visibilityPrivate') }}</span>
                                <span class="text-[10px] text-gray-400 dark:text-gray-500 mt-0.5">{{ t('kb.upsertVisibilityPrivateDesc') }}</span>
                            </button>
                            <button type="button" @click="kb.visibility = 'public'"
                                class="flex flex-col items-center p-3 rounded-xl border-2 transition-all"
                                :class="kb.visibility === 'public' ? 'border-indigo-600 bg-indigo-50 dark:bg-indigo-900/30' : 'border-gray-100 dark:border-gray-600 hover:border-gray-200 dark:hover:border-gray-500'">
                                <svg class="h-6 w-6 mb-1"
                                    :class="kb.visibility === 'public' ? 'text-indigo-600' : 'text-gray-400'" fill="none"
                                    viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                        d="M12 21a9.004 9.004 0 008.716-6.747M12 21a9.004 9.004 0 01-8.716-6.747M12 21c2.485 0 4.5-4.03 4.5-9S14.485 3 12 3m0 18c-2.485 0-4.5-4.03-4.5-9s2.015-9 4.5-9m0 0a9.015 9.015 0 018.716 6.747M12 3a9.015 9.015 0 00-8.716 6.747M3.75 14.25h16.5" />
                                </svg>
                                <span class="text-sm font-bold"
                                    :class="kb.visibility === 'public' ? 'text-indigo-700' : 'text-gray-600'">{{ t('kb.visibilityPublic') }}</span>
                                <span class="text-[10px] text-gray-400 dark:text-gray-500 mt-0.5">{{ t('kb.upsertVisibilityPublicDesc') }}</span>
                            </button>
                        </div>
                    </div>
                </div>
                <div class="mt-6 flex justify-end space-x-3">
                    <button @click="show = false" :disabled="submitting"
                        class="px-5 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 rounded-xl hover:bg-gray-100 dark:hover:bg-gray-700 transition-all disabled:opacity-50">
                        {{ t('nav.cancel') }}
                    </button>
                    <button @click="emit('save')" :disabled="submitting"
                        class="px-5 py-2 text-sm font-semibold bg-blue-600 hover:bg-blue-700 text-white rounded-xl transition-all active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed flex items-center">
                        <svg v-if="submitting" class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" fill="none"
                            viewBox="0 0 24 24">
                            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4">
                            </circle>
                            <path class="opacity-75" fill="currentColor"
                                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z">
                            </path>
                        </svg>
                        {{ confirmText }}
                    </button>
                </div>
        </div>
    </BaseDialog>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
    width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
    background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
    background: #e2e8f0;
    border-radius: 10px;
}
.dark .custom-scrollbar::-webkit-scrollbar-thumb {
    background: #4a5568;
}
</style>
