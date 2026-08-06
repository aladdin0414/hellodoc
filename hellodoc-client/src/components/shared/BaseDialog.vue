<script setup lang="ts">
const props = withDefaults(defineProps<{
    show: boolean
    maxWidthClass?: string
    panelClass?: string
    overlayClass?: string
    zIndexClass?: string
    closeOnOverlay?: boolean
}>(), {
    maxWidthClass: 'max-w-md',
    panelClass: '',
    overlayClass: 'bg-slate-900/50 backdrop-blur-sm',
    zIndexClass: 'z-[100]',
    closeOnOverlay: true
})

const emit = defineEmits<{
    close: []
}>()

const handleOverlayClick = () => {
    if (!props.closeOnOverlay) return
    emit('close')
}
</script>

<template>
    <div v-if="show" :class="['fixed inset-0 flex items-center justify-center p-4', zIndexClass]">
        <div :class="['absolute inset-0', overlayClass]" @click="handleOverlayClick"></div>
        <div :class="[
            'relative w-full bg-white dark:bg-gray-800 rounded-2xl shadow-2xl border border-slate-100 dark:border-gray-700 overflow-hidden',
            maxWidthClass,
            panelClass
        ]">
            <slot />
        </div>
    </div>
</template>
