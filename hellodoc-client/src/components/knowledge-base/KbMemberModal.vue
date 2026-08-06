<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { addKbMember, deleteKbMember, getKbMembers, updateKbMember } from '../../api/kb'
import { searchUsers } from '../../api/user'
import { useI18n } from 'vue-i18n'
import ConfirmDialog from '../ConfirmDialog.vue'
import BaseDialog from '../shared/BaseDialog.vue'

type KbMember = {
    id: number
    userId: number
    nickname: string
    email: string
    avatar: string
    role: string
}

type SysUser = {
    id: number
    username: string
    nickname: string
    avatar: string
}

const props = defineProps<{
    kbId?: number
    kbTitle?: string
}>()

const show = defineModel<boolean>('show', { required: true })
const { t } = useI18n()

const members = ref<KbMember[]>([])
const memberLoading = ref(false)
const userSearchKeyword = ref('')
const userSearchResults = ref<SysUser[]>([])
const searchingUsers = ref(false)

const showConfirmModal = ref(false)
const confirmOptions = ref({
    title: '',
    message: '',
    type: 'info' as 'info' | 'danger',
    hideCancel: false,
    onConfirm: () => { }
})

const fetchMembers = async () => {
    if (!props.kbId) return
    memberLoading.value = true
    try {
        const data: any = await getKbMembers(props.kbId)
        members.value = Array.isArray(data) ? data : []
    } catch {
        members.value = []
    } finally {
        memberLoading.value = false
    }
}

const handleUserSearch = async () => {
    if (!userSearchKeyword.value.trim()) {
        userSearchResults.value = []
        return
    }
    searchingUsers.value = true
    try {
        const data: any = await searchUsers(userSearchKeyword.value)
        const userList = Array.isArray(data) ? data : (data?.content || [])
        const memberIds = members.value.map(m => m.userId)
        userSearchResults.value = userList.filter((u: SysUser) => !memberIds.includes(u.id))
    } catch {
        userSearchResults.value = []
    } finally {
        searchingUsers.value = false
    }
}

const filteredSearchResults = computed(() => {
    return userSearchResults.value.filter(user => !members.value.some(member => member.userId === user.id))
})

const sortedMembers = computed(() => {
    const roleOrder: Record<string, number> = {
        owner: 0,
        admin: 1,
        editor: 2,
        viewer: 3
    }
    return [...members.value].sort((a, b) => {
        const roleA = String(a.role || '').toLowerCase()
        const roleB = String(b.role || '').toLowerCase()
        return (roleOrder[roleA] ?? 99) - (roleOrder[roleB] ?? 99)
    })
})

let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(userSearchKeyword, () => {
    if (searchTimer) clearTimeout(searchTimer)
    searchTimer = setTimeout(() => {
        handleUserSearch()
    }, 300)
})

watch(show, (val) => {
    if (val) {
        userSearchKeyword.value = ''
        userSearchResults.value = []
        fetchMembers()
        return
    }
    if (searchTimer) clearTimeout(searchTimer)
    userSearchKeyword.value = ''
    userSearchResults.value = []
})

watch(() => props.kbId, () => {
    if (show.value) fetchMembers()
})

const addMember = async (user: SysUser) => {
    if (!props.kbId) return
    try {
        await addKbMember(props.kbId, { userId: user.id, role: 'editor' })
        userSearchKeyword.value = ''
        userSearchResults.value = []
        fetchMembers()
    } catch {
    }
}

const updateMemberRole = async (member: KbMember, role: string) => {
    if (!props.kbId) return
    try {
        await updateKbMember(props.kbId, member.userId, { role })
        fetchMembers()
    } catch {
    }
}

const removeMember = (member: KbMember) => {
    confirmOptions.value = {
        title: t('kbMember.removeTitle'),
        message: t('kbMember.removeMessage', { nickname: member.nickname }),
        type: 'danger',
        hideCancel: false,
        onConfirm: async () => {
            if (!props.kbId) return
            try {
                await deleteKbMember(props.kbId, member.userId)
                showConfirmModal.value = false
                fetchMembers()
            } catch {
            }
        }
    }
    showConfirmModal.value = true
}
</script>

<template>
    <BaseDialog :show="show" max-width-class="max-w-2xl" panel-class="flex flex-col max-h-[90vh]"
        @close="show = false">
            <div class="p-6 border-b border-gray-100 dark:border-gray-700 flex justify-between items-center">
                <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('kbMember.title') }} - {{ kbTitle }}</h3>
                <button @click="show = false" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
                    <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                </button>
            </div>

            <div class="p-6 overflow-y-auto flex-1">
                <div class="mb-6 relative">
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">{{ t('kbMember.invite') }}</label>
                    <div class="relative">
                        <input v-model="userSearchKeyword" type="text"
                            class="w-full pl-10 pr-4 py-2 border dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:outline-none bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                            :placeholder="t('kbMember.searchPlaceholder')" />
                        <svg class="absolute left-3 top-2.5 h-5 w-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                        </svg>
                    </div>

                    <div v-if="filteredSearchResults.length > 0"
                        class="absolute z-10 w-full mt-1 bg-white dark:bg-gray-700 border dark:border-gray-600 rounded-lg shadow-lg max-h-48 overflow-y-auto">
                        <div v-for="user in filteredSearchResults" :key="user.id" @click="addMember(user)"
                            class="p-3 hover:bg-gray-50 dark:hover:bg-gray-600 cursor-pointer flex items-center space-x-3 border-b dark:border-gray-600 last:border-0">
                            <div class="h-8 w-8 rounded-full overflow-hidden bg-gray-100 flex items-center justify-center text-xs">
                                <img v-if="user.avatar" :src="user.avatar" class="h-full w-full object-cover" />
                                <span v-else>{{ user.nickname.charAt(0) }}</span>
                            </div>
                            <div>
                                <div class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ user.nickname }}</div>
                                <div class="text-xs text-gray-500 dark:text-gray-400">@{{ user.username }}</div>
                            </div>
                            <div class="flex-1 text-right">
                                <span class="text-xs text-blue-600 font-medium">{{ t('kbMember.clickToAdd') }}</span>
                            </div>
                        </div>
                    </div>
                    <div v-else-if="userSearchKeyword && !searchingUsers"
                        class="absolute z-10 w-full mt-1 bg-white dark:bg-gray-700 border dark:border-gray-600 rounded-lg shadow-lg p-3 text-center text-sm text-gray-500 dark:text-gray-400">
                        {{ t('kbMember.userNotFound') }}
                    </div>
                </div>

                <div>
                    <h4 class="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-4">
                        {{ t('kbMember.currentMembers', { count: members.length }) }}
                    </h4>
                    <div v-if="memberLoading" class="text-center py-4">
                        <div class="inline-block animate-spin rounded-full h-5 w-5 border-2 border-blue-600 border-t-transparent">
                        </div>
                    </div>
                    <div v-else class="space-y-3">
                        <div v-for="member in sortedMembers" :key="member.id"
                            class="flex items-center justify-between p-3 rounded-lg border border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50">
                            <div class="flex items-center space-x-3">
                                <div
                                    class="h-10 w-10 rounded-full overflow-hidden bg-blue-50 flex items-center justify-center text-sm font-bold text-blue-600">
                                    <img v-if="member.avatar" :src="member.avatar" class="h-full w-full object-cover" />
                                    <span v-else>{{ member.nickname.charAt(0).toUpperCase() }}</span>
                                </div>
                                <div>
                                    <div class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ member.nickname }}</div>
                                    <div class="text-xs text-gray-500 dark:text-gray-400">{{ member.email }}</div>
                                </div>
                            </div>
                            <div class="flex items-center space-x-3">
                                <div class="flex items-center space-x-2">
                                    <div v-if="(member.role || '').toLowerCase() === 'owner'"
                                        class="text-xs font-semibold text-amber-600 bg-amber-50 px-2 py-1 rounded-md">
                                        {{ t('kbMember.role.owner') }}
                                    </div>
                                    <div v-else-if="(member.role || '').toLowerCase() === 'admin'"
                                        class="text-xs font-semibold text-blue-600 bg-blue-50 px-2 py-1 rounded-md">
                                        {{ t('kbMember.role.admin') }}
                                    </div>
                                    <div v-else-if="(member.role || '').toLowerCase() === 'editor'"
                                        class="text-xs font-semibold text-green-600 bg-green-50 px-2 py-1 rounded-md">
                                        {{ t('kbMember.role.editor') }}
                                    </div>
                                    <div v-else class="text-xs font-semibold text-gray-500 bg-gray-50 px-2 py-1 rounded-md">
                                        {{ t('kbMember.role.viewer') }}
                                    </div>

                                    <div v-if="(member.role || '').toLowerCase() !== 'owner'" class="relative flex items-center">
                                        <select :value="(member.role || '').toLowerCase()"
                                            @change="(e: any) => updateMemberRole(member, e.target.value)"
                                            class="absolute inset-0 opacity-0 cursor-pointer z-10 w-full h-full">
                                            <option value="admin">{{ t('kbMember.role.admin') }}</option>
                                            <option value="editor">{{ t('kbMember.role.editor') }}</option>
                                            <option value="viewer">{{ t('kbMember.role.viewer') }}</option>
                                        </select>
                                        <div class="p-1 text-gray-400 hover:text-blue-600">
                                            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                                            </svg>
                                        </div>
                                    </div>
                                </div>

                                <button v-if="(member.role || '').toLowerCase() !== 'owner'" @click="removeMember(member)"
                                    class="text-gray-400 hover:text-red-500 p-1">
                                    <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                            d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                    </svg>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

        <ConfirmDialog :show="showConfirmModal" :title="confirmOptions.title" :message="confirmOptions.message"
            :type="confirmOptions.type" :hide-cancel="confirmOptions.hideCancel" @confirm="confirmOptions.onConfirm"
            @cancel="showConfirmModal = false" />
    </BaseDialog>
</template>
