<script setup lang="ts">
import { messages, message } from '../utils/message'

const getIcon = (type: string) => {
    switch (type) {
        case 'success':
            return 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z'
        case 'error':
            return 'M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z'
        case 'warning':
            return 'M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z'
        default:
            return 'M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z'
    }
}

const getTypeClass = (type: string) => {
    switch (type) {
        case 'success':
            return 'bg-emerald-50 dark:bg-emerald-900/40 text-emerald-600 dark:text-emerald-400 border-emerald-100 dark:border-emerald-800'
        case 'error':
            return 'bg-rose-50 dark:bg-rose-900/40 text-rose-600 dark:text-rose-400 border-rose-100 dark:border-rose-800'
        case 'warning':
            return 'bg-amber-50 dark:bg-amber-900/40 text-amber-600 dark:text-amber-400 border-amber-100 dark:border-amber-800'
        default:
            return 'bg-indigo-50 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400 border-indigo-100 dark:border-indigo-800'
    }
}
</script>

<template>
    <div class="fixed top-6 left-1/2 -translate-x-1/2 z-[1000] flex flex-col items-center gap-3 pointer-events-none">
        <TransitionGroup enter-from-class="opacity-0 -translate-y-4 scale-95"
            enter-to-class="opacity-100 translate-y-0 scale-100" leave-from-class="opacity-100 translate-y-0 scale-100"
            leave-to-class="opacity-0 -translate-y-2 scale-95" move-class="transition-all duration-300">
            <div v-for="msg in messages" :key="msg.id"
                class="flex items-center gap-3 px-6 py-3.5 rounded-2xl border bg-white dark:bg-gray-800 shadow-xl pointer-events-auto transition-all duration-300 min-w-[300px]"
                :class="getTypeClass(msg.type)">
                <svg class="w-5 h-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"
                    stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" :d="getIcon(msg.type)" />
                </svg>
                <p class="text-sm font-bold tracking-tight">{{ msg.content }}</p>
                <button @click="message.remove(msg.id)"
                    class="ml-auto p-1 hover:bg-black/5 rounded-lg transition-colors">
                    <svg class="w-4 h-4 opacity-50" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                            d="M6 18L18 6M6 6l12 12" />
                    </svg>
                </button>
            </div>
        </TransitionGroup>
    </div>
</template>

<style scoped>
.v-enter-active,
.v-leave-active {
    transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}
</style>
