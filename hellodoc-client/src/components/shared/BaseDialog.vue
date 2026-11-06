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
    overlayClass: 'bg-slate-900/35',
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
            'flat-dialog relative w-full max-h-[calc(100dvh-2rem)] overflow-y-auto bg-white dark:bg-gray-800 rounded-lg shadow-lg border border-slate-200 dark:border-gray-700',
            maxWidthClass,
            panelClass
        ]">
            <slot />
        </div>
    </div>
</template>

<style>
.flat-dialog h3 {
    font-size: 1rem;
    line-height: 1.5rem;
    font-weight: 600;
}
.flat-dialog :is(input:not([type='checkbox']):not([type='radio']):not([type='range']):not([type='color']), select, textarea) {
    border-radius: 0.375rem;
}
.flat-dialog :is(button, div, label)[class~='rounded-lg'],
.flat-dialog :is(button, div, label)[class~='rounded-xl'],
.flat-dialog :is(button, div, label)[class~='rounded-2xl'],
.flat-dialog :is(button, div, label)[class~='rounded-3xl'] {
    border-radius: 0.375rem;
}
.flat-dialog :is(button, div, label)[class~='shadow-2xl'] {
    box-shadow: 0 4px 12px rgb(15 23 42 / 0.08);
}
.flat-dialog :is(button, div, label):is([class~='bg-indigo-50'], [class~='bg-indigo-50/80'], [class~='bg-blue-50']) {
    background-color: #f1f5f9;
}
.dark .flat-dialog :is(button, div, label):is([class~='bg-indigo-50'], [class~='bg-indigo-50/80'], [class~='bg-blue-50']) {
    background-color: #1e293b;
}
.flat-dialog button:is([class~='bg-blue-600'], [class~='bg-indigo-600']) {
    background-color: #1e293b;
}
.flat-dialog button:is([class~='bg-blue-600'], [class~='bg-indigo-600']):hover:not(:disabled) {
    background-color: #334155;
}
.dark .flat-dialog button:is([class~='bg-blue-600'], [class~='bg-indigo-600']) {
    background-color: #e2e8f0;
    color: #0f172a;
}
.dark .flat-dialog button:is([class~='bg-blue-600'], [class~='bg-indigo-600']):hover:not(:disabled) {
    background-color: #fff;
}
</style>
