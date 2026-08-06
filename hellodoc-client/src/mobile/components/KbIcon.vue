<template>
  <!-- 如果是自定义网络图片 URL -->
  <img v-if="isImageUrl" :src="name!" :alt="alt || 'KB Icon'" class="w-full h-full object-cover rounded-lg" />
  
  <!-- 如果是 Lucide 动态矢量图标 -->
  <component
    v-else
    :is="iconComponent"
    :class="customClass || 'w-5 h-5'"
    :style="iconStyle"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import * as Icons from 'lucide-vue-next'
import { getIconStyle } from '../../utils/color'
import { useTheme } from '../composables/useTheme'

const props = defineProps<{
  name?: string | null
  color?: string
  customClass?: string
  alt?: string
}>()

const { isDark } = useTheme()

const isImageUrl = computed(() => {
  if (!props.name) return false
  return props.name.startsWith('http') || props.name.startsWith('/')
})

const iconComponent = computed(() => {
  if (!props.name) return Icons.Book
  const icon = (Icons as any)[props.name]
  if (icon) return icon
  return Icons.Book
})

const iconStyle = computed(() => {
  return getIconStyle(props.color, isDark.value)
})
</script>
