<script setup lang="ts">
import { ref } from 'vue'
import { changePassword } from '../api/user'
import { message } from '../utils/message'
import { useI18n } from 'vue-i18n'
import BaseDialog from './shared/BaseDialog.vue'

const props = defineProps<{
    show: boolean
}>()

const emit = defineEmits(['close'])

const { t } = useI18n()

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const loading = ref(false)

const handleClose = () => {
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    emit('close')
}

const handleSubmit = async () => {
    if (!oldPassword.value || !newPassword.value || !confirmPassword.value) {
        message.error(t('changePassword.fillRequired'))
        return
    }

    if (newPassword.value !== confirmPassword.value) {
        message.error(t('changePassword.passwordMismatch'))
        return
    }

    if (newPassword.value.length < 5) {
        message.error(t('changePassword.lengthError'))
        return
    }

    loading.value = true
    try {
        await changePassword({
            oldPassword: oldPassword.value,
            newPassword: newPassword.value
        })
        message.success(t('changePassword.success'))
        handleClose()
    } catch (error: any) {
        console.error('Change password failed:', error)
        // message.error(error.response?.data?.message || '修改失败，请检查原密码是否正确')
        // request.ts already handles basic error reporting, but we might want specific handling here
    } finally {
        loading.value = false
    }
}
</script>

<template>
    <BaseDialog :show="show" max-width-class="max-w-sm" panel-class="animate-in fade-in zoom-in duration-200"
        @close="handleClose">
            <div class="p-6">
                <div class="flex items-center justify-between mb-6">
                    <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ t('changePassword.title') }}</h3>
                    <button @click="handleClose"
                        class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition">
                        <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                </div>

                <form class="space-y-4" @submit.prevent="handleSubmit" autocomplete="off">
                    <div>
                        <label
                            class="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-2">{{ t('changePassword.oldPassword') }}</label>
                        <input v-model="oldPassword" type="password" name="oldPassword" autocomplete="current-password"
                            class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all text-gray-900 dark:text-gray-100"
                            :placeholder="t('changePassword.oldPlaceholder')" />
                    </div>

                    <div>
                        <label
                            class="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-2">{{ t('changePassword.newPassword') }}</label>
                        <input v-model="newPassword" type="password" name="newPassword" autocomplete="new-password"
                            class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all text-gray-900 dark:text-gray-100"
                            :placeholder="t('changePassword.newPlaceholder')" />
                    </div>

                    <div>
                        <label
                            class="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider mb-2">{{ t('changePassword.confirmPassword') }}</label>
                        <input v-model="confirmPassword" type="password" name="confirmPassword"
                            autocomplete="new-password"
                            class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all text-gray-900 dark:text-gray-100"
                            :placeholder="t('changePassword.confirmPlaceholder')" />
                    </div>
                </form>
            </div>

            <div class="px-6 py-4 bg-gray-50 dark:bg-gray-700/50 flex justify-end space-x-3">
                <button @click="handleClose"
                    class="px-5 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 rounded-xl hover:bg-gray-100 dark:hover:bg-gray-700 transition-all">
                    {{ t('nav.cancel') }}
                </button>
                <button @click="handleSubmit" :disabled="loading"
                    class="px-5 py-2 text-sm font-semibold bg-blue-600 hover:bg-blue-700 text-white rounded-xl transition-all active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed flex items-center">
                    <svg v-if="loading" class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" fill="none"
                        viewBox="0 0 24 24">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4">
                        </circle>
                        <path class="opacity-75" fill="currentColor"
                            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z">
                        </path>
                    </svg>
                    {{ loading ? t('changePassword.submitting') : t('changePassword.submit') }}
                </button>
            </div>
    </BaseDialog>
</template>
