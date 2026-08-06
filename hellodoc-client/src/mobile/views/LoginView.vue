<template>
  <div class="min-h-screen bg-slate-900 text-white flex flex-col justify-center px-6 py-12 transition-colors select-none">
    <div class="sm:mx-auto sm:w-full sm:max-w-md space-y-8">
      <!-- 品牌 Header -->
      <div class="text-center space-y-3">
        <img src="../../assets/logo.svg" alt="HelloDoc Logo" class="w-16 h-16 mx-auto drop-shadow-2xl" />
        <h1 class="text-2xl font-black text-white tracking-tight">HelloDoc</h1>
        <p class="text-xs text-slate-400 font-medium">轻量优雅的移动端知识库</p>
      </div>

      <!-- 表单卡片 -->
      <div class="bg-slate-800/70 border border-slate-700/60 rounded-3xl p-6 shadow-2xl space-y-5 backdrop-blur-xl">
        <form @submit.prevent="handleLogin" class="space-y-4">
          <div>
            <label class="block text-xs text-slate-400 mb-1.5 font-medium">账号 / 用户名</label>
            <input
              v-model="username"
              type="text"
              required
              placeholder="请输入用户名"
              class="w-full px-4 py-3 bg-slate-900/80 border border-slate-700/70 rounded-xl text-sm text-white placeholder-slate-500 focus:outline-none focus:border-blue-500/50 focus:ring-2 focus:ring-blue-500/20 transition-all"
            />
          </div>

          <div>
            <label class="block text-xs text-slate-400 mb-1.5 font-medium">密码</label>
            <input
              v-model="password"
              type="password"
              required
              placeholder="请输入密码"
              class="w-full px-4 py-3 bg-slate-900/80 border border-slate-700/70 rounded-xl text-sm text-white placeholder-slate-500 focus:outline-none focus:border-blue-500/50 focus:ring-2 focus:ring-blue-500/20 transition-all"
            />
          </div>

          <div v-if="errorMessage" class="p-3 bg-rose-500/10 border border-rose-500/20 rounded-xl text-xs text-rose-400 font-medium">
            {{ errorMessage }}
          </div>

          <button
            type="submit"
            :disabled="submitting"
            class="w-full py-3 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white font-bold rounded-xl text-sm shadow-lg shadow-blue-500/25 active:scale-[0.98] transition-all disabled:opacity-50"
          >
            {{ submitting ? '登录中...' : '登录账户' }}
          </button>
        </form>
      </div>

      <p class="text-center text-xs text-slate-500 font-medium">
        如无账号，请联系管理员配置开通
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../../api/auth'

const router = useRouter()
const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')

const handleLogin = async () => {
  if (!username.value || !password.value) return
  submitting.value = true
  errorMessage.value = ''

  try {
    const res: any = await login({ username: username.value, password: password.value })
    if (res && (res.token || res.accessToken)) {
        const accessToken = res.accessToken || res.token
        localStorage.setItem('accessToken', accessToken)
        localStorage.setItem('token', accessToken)
        if (res.refreshToken) localStorage.setItem('refreshToken', res.refreshToken)
        localStorage.setItem('username', res.username || username.value)
        localStorage.setItem('nickname', res.nickname || res.username || username.value)
        if (res.avatar) localStorage.setItem('avatar', res.avatar)
        if (res.email) localStorage.setItem('email', res.email)
        if (res.role) localStorage.setItem('userRole', res.role)

        const redirect = router.currentRoute.value.query.redirect as string | undefined
        if (redirect) {
          router.push(redirect)
        } else {
          router.push('/m')
        }
    } else {
      errorMessage.value = '登录失败，未收到有效 Token'
    }
  } catch (err: any) {
    console.error('Login failed:', err)
    errorMessage.value = err.response?.data?.message || '登录失败，请检查账号密码'
  } finally {
    submitting.value = false
  }
}
</script>
