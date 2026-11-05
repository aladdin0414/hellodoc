<script setup lang="ts">
import { nextTick, ref, watch, onBeforeUnmount } from 'vue'
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

const panel = ref<HTMLElement | null>(null)
let previousFocus: HTMLElement | null = null
const focusable = () => Array.from(panel.value?.querySelectorAll<HTMLElement>('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])') || [])
watch(() => props.show, async (show) => {
    if (show) {
        previousFocus = document.activeElement as HTMLElement | null
        await nextTick()
        if (props.show && panel.value) {
            const title = panel.value.querySelector('h1, h2, h3, h4')?.textContent?.trim()
            if (title) panel.value.setAttribute('aria-label', title)
            if (!panel.value.contains(document.activeElement)) (focusable()[0] || panel.value).focus()
        }
    } else {
        await nextTick()
        if (previousFocus?.isConnected) previousFocus.focus()
        previousFocus = null
    }
}, { immediate: true })
onBeforeUnmount(() => { if (props.show && previousFocus?.isConnected) previousFocus.focus() })
const handleKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
        event.stopPropagation()
        emit('close')
    } else if (event.key === 'Tab') {
        const items = focusable()
        if (!items.length) { event.preventDefault(); panel.value?.focus(); return }
        const index = items.indexOf(document.activeElement as HTMLElement)
        if (event.shiftKey && index <= 0) { event.preventDefault(); items[items.length - 1]?.focus() }
        else if (!event.shiftKey && (index === -1 || index === items.length - 1)) { event.preventDefault(); items[0]?.focus() }
    }
}
const handleOverlayClick = () => {
    if (!props.closeOnOverlay) return
    emit('close')
}
</script>

<template>
    <div v-if="show" :class="['fixed inset-0 flex items-center justify-center p-4', zIndexClass]">
        <div :class="['absolute inset-0', overlayClass]" @click="handleOverlayClick"></div>
        <div ref="panel" role="dialog" aria-modal="true" tabindex="-1" @keydown="handleKeydown" :class="[
            'relative w-full max-h-[calc(100dvh-2rem)] overflow-y-auto bg-white dark:bg-gray-800 rounded-2xl shadow-2xl border border-slate-100 dark:border-gray-700',
            maxWidthClass,
            panelClass
        ]">
            <slot />
        </div>
    </div>
</template>
