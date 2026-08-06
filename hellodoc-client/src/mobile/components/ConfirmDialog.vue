<template>
  <div
    v-if="show"
    class="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 transition-all"
    @click.self="handleCancel"
  >
    <div class="bg-white dark:bg-slate-800 border border-slate-200/80 dark:border-slate-700/80 rounded-2xl w-full max-w-sm p-5 space-y-4 shadow-2xl animate-in fade-in zoom-in-95 duration-150">
      <!-- 标题区 -->
      <h3 class="text-base font-bold text-slate-900 dark:text-slate-100">
        {{ title || '确认提示' }}
      </h3>

      <!-- 消息正文区 -->
      <div v-if="message || $slots.default" class="text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
        <slot>{{ message }}</slot>
      </div>

      <!-- 操作按钮组 -->
      <div class="flex items-center justify-end gap-2 pt-1">
        <button
          @click="handleCancel"
          :disabled="loading"
          class="px-4 py-2 text-xs font-medium text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-700/60 active:scale-95 rounded-xl transition-all disabled:opacity-50"
        >
          {{ cancelText || '取消' }}
        </button>
        <button
          @click="handleConfirm"
          :disabled="loading"
          :class="[
            confirmType === 'danger'
              ? 'bg-rose-600 hover:bg-rose-500 text-white shadow-rose-500/20'
              : 'bg-blue-600 hover:bg-blue-500 text-white shadow-blue-500/20'
          ]"
          class="px-4 py-2 text-xs font-semibold rounded-xl shadow-md active:scale-95 transition-all flex items-center gap-1.5 disabled:opacity-50"
        >
          <Loader2 v-if="loading" class="w-3 h-3 animate-spin" />
          <span>{{ loading ? '处理中...' : (confirmText || '确定') }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Loader2 } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    show?: boolean
    title?: string
    message?: string
    confirmText?: string
    cancelText?: string
    confirmType?: 'primary' | 'danger'
    loading?: boolean
  }>(),
  {
    show: false,
    title: '确认提示',
    message: '',
    confirmText: '确定',
    cancelText: '取消',
    confirmType: 'primary',
    loading: false
  }
)

const emit = defineEmits<{
  'update:show': [value: boolean]
  confirm: []
  cancel: []
}>()

const handleCancel = () => {
  if (props.loading) return
  emit('update:show', false)
  emit('cancel')
}

const handleConfirm = () => {
  emit('confirm')
}
</script>
