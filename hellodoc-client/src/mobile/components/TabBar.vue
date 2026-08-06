<template>
  <nav class="fixed bottom-0 inset-x-0 z-40 bg-slate-50/85 dark:bg-slate-900/85 backdrop-blur-2xl border-t border-slate-200/50 dark:border-slate-800/50 flex items-center justify-between transition-colors pb-[calc(0.5rem+env(safe-area-inset-bottom))] select-none">
    <router-link
      to="/m"
      class="flex-1 py-2 flex flex-col items-center justify-center gap-0.5 transition-all active:scale-95 touch-manipulation"
      :class="[route.path === '/m' ? 'text-blue-600 dark:text-blue-400 font-semibold' : 'text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300']"
    >
      <BookOpen class="w-5 h-5 stroke-[2]" />
      <span class="text-[10px] tracking-tight">知识库</span>
    </router-link>

    <router-link
      to="/m/shared"
      class="flex-1 py-2 flex flex-col items-center justify-center gap-0.5 transition-all active:scale-95 touch-manipulation"
      :class="[route.path === '/m/shared' ? 'text-blue-600 dark:text-blue-400 font-semibold' : 'text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300']"
    >
      <Users class="w-5 h-5 stroke-[2]" />
      <span class="text-[10px] tracking-tight">共享</span>
    </router-link>

    <router-link
      to="/m/profile"
      class="flex-1 py-2 flex flex-col items-center justify-center gap-0.5 transition-all active:scale-95 touch-manipulation"
      :class="[route.path === '/m/profile' ? 'text-blue-600 dark:text-blue-400 font-semibold' : 'text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300']"
    >
      <User class="w-5 h-5 stroke-[2]" />
      <span class="text-[10px] tracking-tight">我的</span>
    </router-link>
  </nav>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { BookOpen, Users, User } from 'lucide-vue-next'

const route = useRoute()
</script>
