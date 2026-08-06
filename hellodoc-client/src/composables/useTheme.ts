import { ref, computed, watch } from 'vue'

// 主题类型
export type ThemeMode = 'light' | 'dark'

// 全局单例状态，确保多组件共享同一主题
const theme = ref<ThemeMode>((localStorage.getItem('theme') as ThemeMode) || 'light')

// 初始化时立即同步 DOM，避免闪烁
const applyTheme = (mode: ThemeMode) => {
    const root = document.documentElement
    if (mode === 'dark') {
        root.classList.add('dark')
    } else {
        root.classList.remove('dark')
    }
}

// 初始化
applyTheme(theme.value)

export function useTheme() {
    const isDark = computed(() => theme.value === 'dark')

    const setTheme = (mode: ThemeMode) => {
        theme.value = mode
        localStorage.setItem('theme', mode)
        applyTheme(mode)
    }

    const toggleTheme = () => {
        setTheme(theme.value === 'dark' ? 'light' : 'dark')
    }

    // 监听变化以确保同步
    watch(theme, (newTheme) => {
        applyTheme(newTheme)
    })

    return {
        theme,
        isDark,
        setTheme,
        toggleTheme
    }
}
