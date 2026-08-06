<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../api/auth'
import { message } from '../utils/message'
import { useTheme } from '../composables/useTheme'
import { useI18n } from 'vue-i18n'

useTheme()
const { t } = useI18n()

const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)

const isElectron = window.navigator.userAgent.toLowerCase().includes('electron')
const customBackendUrl = ref(localStorage.getItem('customBackendUrl') || '')

const handleLogin = async () => {
    if (isElectron) {
        if (customBackendUrl.value) {
            let formattedUrl = customBackendUrl.value.trim()
            if (formattedUrl && !/^https?:\/\//i.test(formattedUrl)) {
                formattedUrl = `http://${formattedUrl}`
                customBackendUrl.value = formattedUrl
            }
            localStorage.setItem('customBackendUrl', customBackendUrl.value)
        } else {
            localStorage.removeItem('customBackendUrl')
        }
    }

    if (!username.value || !password.value) {
        message.warning(t('auth.usernamePasswordRequired'))
        return
    }

    loading.value = true
    try {
        const data: any = await login({
            username: username.value,
            password: password.value
        })

        // Store token and user info
        localStorage.setItem('accessToken', data.accessToken)
        if (data.refreshToken) localStorage.setItem('refreshToken', data.refreshToken)
        localStorage.setItem('username', username.value)
        localStorage.setItem('nickname', data.nickname || username.value)
        if (data.avatar) localStorage.setItem('avatar', data.avatar)

        const redirect = router.currentRoute.value.query.redirect as string
        if (redirect) {
            router.push(redirect)
        } else {
            router.push('/')
        }
    } catch (error) {
        console.error('Login failed:', error)
    } finally {
        loading.value = false
    }
}
</script>

<template>
    <div class="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 py-12 px-4 sm:px-6 lg:px-8">
        <div
            class="max-w-md w-full space-y-8 p-8 bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-100 dark:border-gray-700">
            <div class="flex flex-col items-center">
                <img src="../assets/logo.svg" alt="HelloDoc Logo" class="h-16 w-16 mb-2" />
                <h2 class="text-center text-3xl font-extrabold text-gray-900 dark:text-gray-100 tracking-tight">
                    HelloDoc
                </h2>
                <p class="mt-2 text-center text-sm text-gray-600 dark:text-gray-400">
                    {{ t('auth.appSlogan') }}
                </p>
            </div>
            <form class="mt-8 space-y-6" @submit.prevent="handleLogin">
                <div class="rounded-md shadow-sm space-y-4">
                    <div>
                        <label for="username" class="sr-only">{{ t('auth.username') }}</label>
                        <input id="username" v-model="username" name="username" type="text" required
                            class="appearance-none rounded-lg relative block w-full px-4 py-3 border border-gray-300 dark:border-gray-600 placeholder-gray-500 dark:placeholder-gray-400 text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:z-10 sm:text-sm transition-all duration-200"
                            :placeholder="t('auth.loginPlaceholder')" />
                    </div>
                    <div>
                        <label for="password" class="sr-only">{{ t('auth.password') }}</label>
                        <input id="password" v-model="password" name="password" type="password" required
                            class="appearance-none rounded-lg relative block w-full px-4 py-3 border border-gray-300 dark:border-gray-600 placeholder-gray-500 dark:placeholder-gray-400 text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:z-10 sm:text-sm transition-all duration-200"
                            :placeholder="t('auth.passwordPlaceholder')" />
                    </div>
                    <div v-if="isElectron">
                        <label for="backendUrl" class="sr-only">{{ t('auth.serverAddress') }}</label>
                        <input id="backendUrl" v-model="customBackendUrl" name="backendUrl" type="text"
                            class="appearance-none rounded-lg relative block w-full px-4 py-3 border border-gray-300 dark:border-gray-600 placeholder-gray-500 dark:placeholder-gray-400 text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 focus:z-10 sm:text-sm transition-all duration-200"
                            :placeholder="t('auth.serverAddressPlaceholder')" />
                    </div>
                </div>

                <div class="flex items-center justify-end">
                    <div class="text-sm">
                        <a href="#" @click.prevent="router.push('/register')"
                            class="font-medium text-blue-600 dark:text-blue-400 hover:text-blue-500">
                            {{ t('auth.registerAccount') }}
                        </a>
                    </div>
                </div>

                <div>
                    <button type="submit" :disabled="loading"
                        class="group relative w-full flex justify-center py-3 px-4 border border-transparent text-sm font-medium rounded-lg text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors duration-200 shadow-md disabled:opacity-50 disabled:cursor-not-allowed">
                        {{ loading ? t('auth.loggingIn') : t('auth.login') }}
                    </button>
                </div>
            </form>
        </div>
    </div>
</template>
