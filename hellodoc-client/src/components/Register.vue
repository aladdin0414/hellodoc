<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/auth'
import { message } from '../utils/message'
import { useTheme } from '../composables/useTheme'
import { useI18n } from 'vue-i18n'

useTheme()
const { t } = useI18n()

const router = useRouter()
const form = ref({
    username: '',
    password: '',
    confirmPassword: '',
    nickname: '',
    email: '',
    phone: '',
    realName: ''
})

const loading = ref(false)
const errorMsg = ref('')

const handleRegister = async () => {
    if (form.value.password !== form.value.confirmPassword) {
        errorMsg.value = t('auth.passwordMismatch')
        return
    }

    loading.value = true
    errorMsg.value = ''
    try {
        const payload: any = {
            username: form.value.username.trim(),
            password: form.value.password
        }

        if (form.value.nickname?.trim()) payload.nickname = form.value.nickname.trim()
        if (form.value.email?.trim()) payload.email = form.value.email.trim()
        if (form.value.phone?.trim()) payload.phone = form.value.phone.trim()
        if (form.value.realName?.trim()) payload.realName = form.value.realName.trim()

        await register(payload)
        message.success(t('auth.registerSuccess'))
        router.push('/login')
    } catch (error: any) {
        console.error('Registration failed:', error)
        errorMsg.value = error.response?.data?.message || t('auth.registerFailed')
    } finally {
        loading.value = false
    }
}
</script>

<template>
    <div class="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 py-12 px-4 sm:px-6 lg:px-8">
        <div
            class="max-w-md w-full space-y-8 p-8 bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-100 dark:border-gray-700">
            <div>
                <h2 class="mt-6 text-center text-3xl font-extrabold text-gray-900 dark:text-gray-100 tracking-tight">
                    {{ t('auth.createAccount') }}
                </h2>
                <p class="mt-2 text-center text-sm text-gray-600 dark:text-gray-400">
                    {{ t('auth.registerSubtitle') }}
                </p>
            </div>

            <form class="mt-8 space-y-4" @submit.prevent="handleRegister">
                <div v-if="errorMsg"
                    class="bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-400 p-3 rounded-lg text-sm text-center">
                    {{ errorMsg }}
                </div>

                <div class="grid grid-cols-1 gap-4">
                    <div>
                        <label for="reg-username"
                            class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('auth.usernameField') }}</label>
                        <input id="reg-username" v-model="form.username" type="text" required
                            class="appearance-none rounded-lg relative block w-full px-4 py-2 border border-gray-300 dark:border-gray-600 placeholder-gray-400 dark:placeholder-gray-500 text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 sm:text-sm transition-all duration-200"
                            :placeholder="t('auth.usernameHint')" />
                    </div>

                    <div class="grid grid-cols-2 gap-4">
                        <div>
                            <label for="reg-password"
                                class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('auth.passwordField') }}</label>
                            <input id="reg-password" v-model="form.password" type="password" required
                                class="appearance-none rounded-lg relative block w-full px-4 py-2 border border-gray-300 dark:border-gray-600 placeholder-gray-400 dark:placeholder-gray-500 text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 sm:text-sm transition-all duration-200"
                                :placeholder="t('auth.passwordHint')" />
                        </div>
                        <div>
                            <label for="reg-confirm"
                                class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('auth.confirmPasswordField') }}</label>
                            <input id="reg-confirm" v-model="form.confirmPassword" type="password" required
                                class="appearance-none rounded-lg relative block w-full px-4 py-2 border border-gray-300 dark:border-gray-600 placeholder-gray-400 dark:placeholder-gray-500 text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 sm:text-sm transition-all duration-200"
                                :placeholder="t('auth.confirmPasswordHint')" />
                        </div>
                    </div>

                    <div>
                        <label for="reg-nickname"
                            class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('auth.nickname') }}</label>
                        <input id="reg-nickname" v-model="form.nickname" type="text"
                            class="appearance-none rounded-lg relative block w-full px-4 py-2 border border-gray-300 dark:border-gray-600 placeholder-gray-400 dark:placeholder-gray-500 text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 sm:text-sm transition-all duration-200"
                            :placeholder="t('auth.nicknameHint')" />
                    </div>

                    <div>
                        <label for="reg-email"
                            class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ t('auth.email') }}</label>
                        <input id="reg-email" v-model="form.email" type="email"
                            class="appearance-none rounded-lg relative block w-full px-4 py-2 border border-gray-300 dark:border-gray-600 placeholder-gray-400 dark:placeholder-gray-500 text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 sm:text-sm transition-all duration-200"
                            placeholder="example@mail.com" />
                    </div>
                </div>

                <div class="pt-4">
                    <button type="submit" :disabled="loading"
                        class="group relative w-full flex justify-center py-3 px-4 border border-transparent text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors duration-200 shadow-md disabled:opacity-50 disabled:cursor-not-allowed">
                        {{ loading ? t('auth.registering') : t('auth.register') }}
                    </button>
                </div>

                <div class="text-center mt-4">
                    <p class="text-sm text-gray-600 dark:text-gray-400">
                        {{ t('auth.alreadyHasAccount') }}
                        <a href="#" @click.prevent="router.push('/login')"
                            class="font-medium text-blue-600 dark:text-blue-400 hover:text-blue-500">
                            {{ t('auth.loginNow') }}
                        </a>
                    </p>
                </div>
            </form>
        </div>
    </div>
</template>
