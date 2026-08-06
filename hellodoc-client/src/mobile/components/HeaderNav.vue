<template>
  <header class="sticky top-0 z-40 bg-white/90 dark:bg-slate-900/90 backdrop-blur-md border-b border-slate-200/80 dark:border-slate-800/80 px-4 min-h-[3rem] h-[calc(3rem+env(safe-area-inset-top))] pt-[env(safe-area-inset-top)] flex items-center justify-between transition-colors">
    <!-- 左侧插槽或默认返回按钮 -->
    <div class="flex items-center gap-2 min-w-[24px]">
      <slot name="left">
        <button
          v-if="showBack"
          @click="handleBack"
          class="p-1.5 -ml-1.5 rounded-xl text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/80 active:scale-95 transition-all"
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

const handleBack = () => {
  const hasBackListener = !!instance?.vnode.props?.onBack
  if (hasBackListener) {
    emit('back')
  } else {
    router.back()
  }
}
</script>
