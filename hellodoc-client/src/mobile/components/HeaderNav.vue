<template>
  <header class="sticky top-0 z-40 bg-white/90 dark:bg-slate-900/90 backdrop-blur-md border-b border-slate-200/80 dark:border-slate-800/80 px-4 min-h-[3rem] h-[calc(3rem+env(safe-area-inset-top))] pt-[env(safe-area-inset-top)] flex items-center justify-between transition-colors">
    <!-- 左侧插槽或默认返回按钮 -->
    <div class="flex items-center gap-2 min-w-[24px]">
      <slot name="left">
        <button
          v-if="showBack"
          type="button"
          @click="handleBack"
          class="mobile-nav-back-btn p-1.5 -ml-1.5 rounded-xl text-slate-600 dark:text-slate-300 active:scale-95 transition-all focus:outline-none"
        >
          <ChevronLeft class="w-6 h-6" />
        </button>
      </slot>
    </div>

    <!-- 中间标题或插槽 -->
    <div class="flex-1 flex justify-center px-2">
      <slot name="title">
        <h1 v-if="title" class="text-base font-bold text-slate-900 dark:text-slate-100 truncate max-w-[200px] text-center">
          {{ title }}
        </h1>
      </slot>
    </div>

    <!-- 右侧动作按钮区插槽 -->
    <div class="flex items-center gap-1.5">
      <slot name="right"></slot>
    </div>
  </header>
</template>

<script setup lang="ts">
import { getCurrentInstance } from 'vue'
import { ChevronLeft } from 'lucide-vue-next'
import { useRouter } from 'vue-router'

defineProps<{
  title?: string
  showBack?: boolean
}>()

const emit = defineEmits<{
  back: []
}>()

const instance = getCurrentInstance()
const router = useRouter()

const handleBack = (event: MouseEvent) => {
  const button = event.currentTarget as HTMLButtonElement | null
  button?.blur()

  const hasBackListener = !!instance?.vnode.props?.onBack
  if (hasBackListener) {
    emit('back')
  } else {
    router.back()
  }
}
</script>

<style scoped>
.mobile-nav-back-btn {
  -webkit-tap-highlight-color: transparent;
}

@media (hover: hover) and (pointer: fine) {
  .mobile-nav-back-btn:hover {
    background-color: rgb(241 245 249 / 1);
  }

  .dark .mobile-nav-back-btn:hover {
    background-color: rgb(30 41 59 / 0.8);
  }
}
</style>
