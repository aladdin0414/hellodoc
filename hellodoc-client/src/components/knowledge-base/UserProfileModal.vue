<script setup lang="ts">
import { ref, watch } from 'vue'
import { updateProfile, uploadAvatar } from '../../api/user'
import { useI18n } from 'vue-i18n'
import BaseDialog from '../shared/BaseDialog.vue'

const props = defineProps<{
    nickname: string
    avatar: string
}>()

const emit = defineEmits<{
    (e: 'updated', payload: { nickname: string; avatar: string; email: string; phone: string }): void
}>()

const show = defineModel<boolean>('show', { required: true })
const { t } = useI18n()

const uploading = ref(false)
const profileForm = ref({
    nickname: '',
    realName: '',
    email: '',
    phone: '',
    avatar: ''
})

watch(show, (val) => {
    if (!val) return
    profileForm.value = {
        nickname: props.nickname,
        realName: localStorage.getItem('realName') || '',
        email: localStorage.getItem('email') || '',
        phone: localStorage.getItem('phone') || '',
        avatar: props.avatar
    }
})

const handleAvatarUpload = async (event: any) => {
    const file = event.target?.files?.[0]
    if (!file) return
    const formData = new FormData()
    formData.append('file', file)
    uploading.value = true
    try {
        const data: any = await uploadAvatar(formData)
        const avatarUrl = data?.url || data
        profileForm.value.avatar = avatarUrl
        localStorage.setItem('avatar', avatarUrl)
        emit('updated', {
            nickname: profileForm.value.nickname,
            avatar: avatarUrl,
            email: profileForm.value.email || '',
            phone: profileForm.value.phone || ''
        })
    } catch {
    } finally {
        uploading.value = false
    }
}

const handleUpdateProfile = async () => {
    try {
        await updateProfile({
            nickname: profileForm.value.nickname,
            realName: profileForm.value.realName,
            email: profileForm.value.email,
            phone: profileForm.value.phone
        })
        localStorage.setItem('nickname', profileForm.value.nickname || '')
        localStorage.setItem('email', profileForm.value.email || '')
        localStorage.setItem('phone', profileForm.value.phone || '')
        if (profileForm.value.avatar) {
            localStorage.setItem('avatar', profileForm.value.avatar)
        }
        emit('updated', {
            nickname: profileForm.value.nickname || '',
            avatar: profileForm.value.avatar || '',
            email: profileForm.value.email || '',
            phone: profileForm.value.phone || ''
        })
        show.value = false
    } catch {
    }
}
</script>

<template>
    <BaseDialog :show="show" max-width-class="max-w-lg" @close="show = false">
        <div class="bg-white dark:bg-gray-800 px-6 py-6">
                    <div class="flex justify-between items-center mb-6">
                        <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('userProfile.title') }}</h3>
                        <button @click="show = false"
                            class="text-gray-400 hover:text-gray-500 dark:text-gray-500 dark:hover:text-gray-300 transition">
                            <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M6 18L18 6M6 6l12 12" />
                            </svg>
                        </button>
                    </div>

                    <div class="space-y-6">
                        <div class="flex flex-col items-center space-y-4">
                            <div class="relative group">
                                <div
                                    class="h-24 w-24 rounded-full overflow-hidden bg-gray-100 border-2 border-gray-100 group-hover:border-blue-400 transition-all duration-200 shadow-inner">
                                    <img v-if="profileForm.avatar" :src="profileForm.avatar"
                                        class="h-full w-full object-cover" />
                                    <div v-else
                                        class="h-full w-full flex items-center justify-center bg-blue-50 text-blue-600 text-3xl font-bold">
                                        {{ (profileForm.nickname || 'U').charAt(0).toUpperCase() }}
                                    </div>
                                </div>
                                <label
                                    class="absolute inset-0 flex items-center justify-center bg-black/40 text-white rounded-full opacity-0 group-hover:opacity-100 cursor-pointer transition-opacity duration-200">
                                    <input type="file" class="hidden" @change="handleAvatarUpload" accept="image/*" />
                                    <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                            d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                            d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
                                    </svg>
                                </label>
                                <div v-if="uploading"
                                    class="absolute inset-0 flex items-center justify-center bg-white/60 rounded-full">
                                    <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
                                </div>
                            </div>
                            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('userProfile.uploadHint') }}</p>
                        </div>

                        <div class="space-y-4">
                            <div>
                                <label class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1">{{ t('userProfile.nicknameLabel') }}</label>
                                <input v-model="profileForm.nickname" type="text"
                                    class="w-full px-4 py-2 rounded-xl border border-gray-200 dark:border-gray-600 focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all outline-none bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                    :placeholder="t('userProfile.nicknamePlaceholder')" />
                            </div>
                            <div>
                                <label class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1">{{ t('userProfile.emailLabel') }}</label>
                                <input v-model="profileForm.email" type="email"
                                    class="w-full px-4 py-2 rounded-xl border border-gray-200 dark:border-gray-600 focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all outline-none bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                    :placeholder="t('userProfile.emailPlaceholder')" />
                            </div>
                            <div>
                                <label class="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1">{{ t('userProfile.phoneLabel') }}</label>
                                <input v-model="profileForm.phone" type="text"
                                    class="w-full px-4 py-2 rounded-xl border border-gray-200 dark:border-gray-600 focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all outline-none bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                    :placeholder="t('userProfile.phonePlaceholder')" />
                            </div>
                        </div>
                    </div>
                </div>

                <div class="bg-gray-50 dark:bg-gray-700/50 px-6 py-4 flex justify-end space-x-3">
                    <button @click="show = false"
                        class="px-5 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 rounded-xl hover:bg-gray-100 dark:hover:bg-gray-700 transition-all duration-200">
                        {{ t('nav.cancel') }}
                    </button>
                    <button @click="handleUpdateProfile"
                        class="px-5 py-2 text-sm font-semibold bg-blue-600 hover:bg-blue-700 text-white rounded-xl transition-all duration-200 active:scale-95">
                        {{ t('userProfile.save') }}
                    </button>
                </div>
    </BaseDialog>
</template>
